package com.app.heartbound.services.pairing;

import com.app.heartbound.dto.pairing.JoinQueueRequestDTO; 
import com.app.heartbound.dto.pairing.QueueStatusDTO;
import com.app.heartbound.dto.pairing.QueueConfigDTO;
import com.app.heartbound.entities.MatchQueueUser;
import com.app.heartbound.repositories.pairing.MatchQueueUserRepository;
import com.app.heartbound.repositories.pairing.PairingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class QueueService {

    private static final Logger logger = LoggerFactory.getLogger(QueueService.class);

    private final MatchQueueUserRepository queueRepository;
    private final PairingRepository pairingRepository;
    private final SimpMessagingTemplate messagingTemplate;

    // Add queue enabled state - default to true
    private volatile boolean queueEnabled = true;
    private String lastUpdatedBy = "SYSTEM";

    @Transactional
    public QueueStatusDTO joinQueue(JoinQueueRequestDTO request) {
        // Check if queue is enabled before allowing joins
        if (!queueEnabled) {
            throw new IllegalStateException("Matchmaking queue is currently disabled. Please try again later.");
        }

        String userId = request.getUserId();
        log.info("User {} attempting to join queue", userId);

        // Check if user is already in an active pairing
        if (pairingRepository.findActivePairingByUserId(userId).isPresent()) {
            throw new IllegalStateException("User is already in an active pairing");
        }

        // Check if user is already in queue
        Optional<MatchQueueUser> existingEntry = queueRepository.findByUserId(userId);
        
        MatchQueueUser queueUser;
        if (existingEntry.isPresent()) {
            queueUser = existingEntry.get();
            if (queueUser.isInQueue()) {
                log.info("User {} is already in queue", userId);
            } else {
                // Update existing entry
                queueUser.setAge(request.getAge());
                queueUser.setRegion(request.getRegion());
                queueUser.setRank(request.getRank());
                queueUser.setQueuedAt(LocalDateTime.now());
                queueUser.setInQueue(true);
                log.info("User {} rejoined queue with updated preferences", userId);
            }
        } else {
            // Create new queue entry
            queueUser = MatchQueueUser.builder()
                    .userId(userId)
                    .age(request.getAge())
                    .region(request.getRegion())
                    .rank(request.getRank())
                    .queuedAt(LocalDateTime.now())
                    .inQueue(true)
                    .build();
            log.info("User {} joined queue for the first time", userId);
        }

        queueUser = queueRepository.save(queueUser);

        // Broadcast queue update via WebSocket
        broadcastQueueUpdate();

        return buildQueueStatus(queueUser);
    }

    @Transactional
    public void leaveQueue(String userId) {
        log.info("User {} attempting to leave queue", userId);

        Optional<MatchQueueUser> queueUser = queueRepository.findByUserId(userId);
        if (queueUser.isPresent() && queueUser.get().isInQueue()) {
            queueUser.get().setInQueue(false);
            queueRepository.save(queueUser.get());
            log.info("User {} left the queue", userId);
            
            // Broadcast queue update via WebSocket
            broadcastQueueUpdate();
        } else {
            log.warn("User {} was not in queue", userId);
        }
    }

    public QueueStatusDTO getQueueStatus(String userId) {
        Optional<MatchQueueUser> queueUser = queueRepository.findByUserId(userId);
        
        if (queueUser.isPresent() && queueUser.get().isInQueue()) {
            return buildQueueStatus(queueUser.get());
        }
        
        return QueueStatusDTO.builder()
                .inQueue(false)
                .totalQueueSize(getActiveQueueSize())
                .build();
    }

    public List<MatchQueueUser> getActiveQueueUsers() {
        return queueRepository.findByInQueueTrue();
    }

    private QueueStatusDTO buildQueueStatus(MatchQueueUser queueUser) {
        List<MatchQueueUser> allInQueue = queueRepository.findByInQueueTrue();
        int position = calculateQueuePosition(queueUser, allInQueue);
        int estimatedWaitTime = calculateEstimatedWaitTime(position);

        return QueueStatusDTO.builder()
                .inQueue(queueUser.isInQueue())
                .queuedAt(queueUser.getQueuedAt())
                .queuePosition(position)
                .totalQueueSize(allInQueue.size())
                .estimatedWaitTime(estimatedWaitTime)
                .build();
    }

    private int calculateQueuePosition(MatchQueueUser user, List<MatchQueueUser> allInQueue) {
        int position = 1;
        for (MatchQueueUser other : allInQueue) {
            if (other.getQueuedAt().isBefore(user.getQueuedAt())) {
                position++;
            }
        }
        return position;
    }

    private int calculateEstimatedWaitTime(int position) {
        // Simple estimation: 2-5 minutes per pair ahead in queue
        int pairsAhead = (position - 1) / 2;
        return Math.max(2, pairsAhead * 3 + 2); // 2-5 minutes base + 3 min per pair
    }

    private int getActiveQueueSize() {
        return queueRepository.findByInQueueTrue().size();
    }

    public void broadcastQueueUpdate() {
        try {
            int queueSize = getActiveQueueSize();
            messagingTemplate.convertAndSend("/topic/queue", 
                    QueueStatusDTO.builder()
                            .totalQueueSize(queueSize)
                            .build());
            log.debug("Broadcasted queue update: {} users in queue", queueSize);
        } catch (Exception e) {
            log.error("Failed to broadcast queue update: {}", e.getMessage());
        }
    }

    // Add methods to manage queue state
    public void setQueueEnabled(boolean enabled, String updatedBy) {
        this.queueEnabled = enabled;
        this.lastUpdatedBy = updatedBy;
        
        // Broadcast queue status change to all connected clients
        QueueConfigDTO configUpdate = new QueueConfigDTO(
            enabled, 
            enabled ? "Matchmaking queue has been enabled" : "Matchmaking queue has been disabled",
            updatedBy
        );
        
        messagingTemplate.convertAndSend("/topic/queue/config", configUpdate);
        logger.info("Queue status changed to {} by {}", enabled ? "ENABLED" : "DISABLED", updatedBy);
    }

    public boolean isQueueEnabled() {
        return queueEnabled;
    }

    public QueueConfigDTO getQueueConfig() {
        return new QueueConfigDTO(
            queueEnabled,
            queueEnabled ? "Matchmaking queue is currently enabled" : "Matchmaking queue is currently disabled",
            lastUpdatedBy
        );
    }
} 