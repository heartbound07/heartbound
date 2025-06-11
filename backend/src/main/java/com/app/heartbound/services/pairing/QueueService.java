package com.app.heartbound.services.pairing;

import com.app.heartbound.dto.pairing.JoinQueueRequestDTO; 
import com.app.heartbound.dto.pairing.QueueStatusDTO;
import com.app.heartbound.dto.pairing.QueueConfigDTO;
import com.app.heartbound.dto.pairing.QueueStatsDTO;
import com.app.heartbound.dto.pairing.QueueUserDetailsDTO;
import com.app.heartbound.entities.MatchQueueUser;
import com.app.heartbound.entities.User;
import com.app.heartbound.repositories.pairing.MatchQueueUserRepository;
import com.app.heartbound.repositories.pairing.PairingRepository;
import com.app.heartbound.repositories.UserRepository;
import com.app.heartbound.enums.Gender;
import com.app.heartbound.enums.Rank;
import com.app.heartbound.enums.Region;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class QueueService {

    private static final Logger logger = LoggerFactory.getLogger(QueueService.class);

    private final MatchQueueUserRepository queueRepository;
    private final PairingRepository pairingRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final UserRepository userRepository;

    // Add queue enabled state - default to true
    private volatile boolean queueEnabled = true;
    private String lastUpdatedBy = "SYSTEM";
    
    // Track queue start time for statistics
    private LocalDateTime queueStartTime = LocalDateTime.now();
    private LocalDateTime lastMatchmakingRun = LocalDateTime.now();

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
                queueUser.setGender(request.getGender());
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
                    .gender(request.getGender())
                    .queuedAt(LocalDateTime.now())
                    .inQueue(true)
                    .build();
            log.info("User {} joined queue for the first time", userId);
        }

        queueUser = queueRepository.save(queueUser);

        // Broadcast queue update via WebSocket
        broadcastQueueUpdate();

        log.info("Saved queue user: ID={}, Age={}, Gender={}, Region={}, Rank={}, InQueue={}", 
                 queueUser.getUserId(), queueUser.getAge(), queueUser.getGender(), 
                 queueUser.getRegion(), queueUser.getRank(), queueUser.isInQueue());

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
        // If disabling the queue, remove all users currently in queue
        if (!enabled) {
            List<MatchQueueUser> usersInQueue = queueRepository.findByInQueueTrue();
            
            if (!usersInQueue.isEmpty()) {
                log.info("Removing {} users from queue due to queue being disabled by {}", usersInQueue.size(), updatedBy);
                
                // Create notification payload for queue removal
                Map<String, Object> removalNotification = new HashMap<>();
                removalNotification.put("eventType", "QUEUE_REMOVED");
                removalNotification.put("message", "This matchmaking season has ended. You have been removed from the queue.");
                removalNotification.put("timestamp", LocalDateTime.now().toString());
                
                // Remove each user from queue and notify them
                for (MatchQueueUser user : usersInQueue) {
                    try {
                        // Send notification to user's personal WebSocket topic
                        messagingTemplate.convertAndSend("/user/" + user.getUserId() + "/topic/pairings", removalNotification);
                        
                        // Remove user from queue
                        user.setInQueue(false);
                        queueRepository.save(user);
                        
                        log.info("Removed user {} from queue and sent notification", user.getUserId());
                    } catch (Exception e) {
                        log.error("Failed to notify user {} of queue removal: {}", user.getUserId(), e.getMessage());
                        // Still remove them from queue even if notification fails
                        user.setInQueue(false);
                        queueRepository.save(user);
                    }
                }
            }
        }
        
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

    public List<MatchQueueUser> getEligibleUsersForMatching() {
        if (!queueEnabled) {
            log.info("Queue is disabled, returning empty list for matching");
            return List.of();
        }
        
        List<MatchQueueUser> eligibleUsers = queueRepository.findByInQueueTrue();
        log.info("Found {} eligible users for matching", eligibleUsers.size());
        return eligibleUsers;
    }
    
    // Update last matchmaking run time - called from MatchmakingService
    public void updateLastMatchmakingRun() {
        this.lastMatchmakingRun = LocalDateTime.now();
        
        // Broadcast admin-specific queue update with timing info
        broadcastAdminQueueUpdate();
    }
    
    // ADMIN-ONLY: Get comprehensive queue statistics
    public QueueStatsDTO getQueueStatistics() {
        try {
            log.info("Fetching queue statistics...");
            
            List<MatchQueueUser> activeUsers = queueRepository.findByInQueueTrue();
            log.info("Found {} active users in queue", activeUsers.size());
            
            // Calculate breakdowns
            Map<String, Integer> regionBreakdown = calculateRegionBreakdown(activeUsers);
            Map<String, Integer> rankBreakdown = calculateRankBreakdown(activeUsers);
            Map<String, Integer> genderBreakdown = calculateGenderBreakdown(activeUsers);
            Map<String, Integer> ageBreakdown = calculateAgeRangeBreakdown(activeUsers);
            
            // Calculate average wait time
            double avgWaitTime = calculateAverageWaitTime(activeUsers);
            
            // Calculate today's match statistics
            LocalDateTime startOfDay = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);
            int matchesToday = pairingRepository.countByMatchedAtAfter(startOfDay);
            int usersMatchedToday = matchesToday * 2; // Each match involves 2 users
            
            // Simple match success rate calculation (matches created vs total users processed)
            double matchSuccessRate = activeUsers.isEmpty() ? 0.0 : 
                Math.min(100.0, (double) matchesToday / Math.max(1, activeUsers.size() + matchesToday) * 100.0);
            
            QueueStatsDTO stats = QueueStatsDTO.builder()
                    .totalUsersInQueue(activeUsers.size())
                    .averageWaitTimeMinutes(avgWaitTime)
                    .lastMatchmakingRun(lastMatchmakingRun != null ? lastMatchmakingRun : LocalDateTime.now())
                    .queueByRegion(regionBreakdown)
                    .queueByRank(rankBreakdown)
                    .queueByGender(genderBreakdown)
                    .queueByAgeRange(ageBreakdown)
                    .matchSuccessRate(matchSuccessRate)
                    .totalMatchesCreatedToday(matchesToday)
                    .totalUsersMatchedToday(usersMatchedToday)
                    .queueSizeHistory(generateHourlyHistory()) // Simple implementation
                    .waitTimeHistory(generateWaitTimeHistory()) // Simple implementation
                    .queueStartTime(queueStartTime != null ? queueStartTime : LocalDateTime.now())
                    .queueEnabled(queueEnabled)
                    .lastUpdatedBy(lastUpdatedBy != null ? lastUpdatedBy : "SYSTEM")
                    .build();
            
            log.info("Successfully built queue statistics");
            return stats;
            
        } catch (Exception e) {
            log.error("Error calculating queue statistics: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to calculate queue statistics", e);
        }
    }
    
    // ADMIN-ONLY: Get detailed queue user information
    public List<QueueUserDetailsDTO> getQueueUserDetails() {
        try {
            log.info("Fetching queue user details...");
            
            List<MatchQueueUser> activeUsers = queueRepository.findByInQueueTrue();
            log.info("Found {} active users in queue for details", activeUsers.size());
            
            List<QueueUserDetailsDTO> userDetails = activeUsers.stream()
                    .map(this::convertToQueueUserDetails)
                    .filter(detail -> detail != null) // Filter out any null results
                    .sorted((a, b) -> a.getQueuedAt().compareTo(b.getQueuedAt())) // Sort by queue time
                    .collect(Collectors.toList());
            
            log.info("Successfully converted {} users to detail DTOs", userDetails.size());
            return userDetails;
            
        } catch (Exception e) {
            log.error("Error fetching queue user details: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to fetch queue user details", e);
        }
    }
    
    private QueueUserDetailsDTO convertToQueueUserDetails(MatchQueueUser queueUser) {
        try {
            if (queueUser == null) {
                log.warn("Null queue user provided to convertToQueueUserDetails");
                return null;
            }
            
            // Get user profile information
            Optional<User> userOpt = userRepository.findById(queueUser.getUserId());
            String username = userOpt.map(User::getUsername).orElse("Unknown User");
            String avatar = userOpt.map(User::getAvatar).orElse("/default-avatar.png");
            
            // Calculate wait time
            LocalDateTime queuedAt = queueUser.getQueuedAt() != null ? queueUser.getQueuedAt() : LocalDateTime.now();
            long waitMinutes = Duration.between(queuedAt, LocalDateTime.now()).toMinutes();
            
            // Calculate queue position
            List<MatchQueueUser> allInQueue = queueRepository.findByInQueueTrue();
            int position = calculateQueuePosition(queueUser, allInQueue);
            int estimatedWait = calculateEstimatedWaitTime(position);
            
            // Check if recently queued (last 5 minutes)
            boolean recentlyQueued = waitMinutes <= 5;
            
            return QueueUserDetailsDTO.builder()
                    .userId(queueUser.getUserId())
                    .username(username)
                    .avatar(avatar)
                    .age(queueUser.getAge())
                    .region(queueUser.getRegion())
                    .rank(queueUser.getRank())
                    .gender(queueUser.getGender())
                    .queuedAt(queuedAt)
                    .waitTimeMinutes(waitMinutes)
                    .queuePosition(position)
                    .estimatedWaitTimeMinutes(estimatedWait)
                    .recentlyQueued(recentlyQueued)
                    .build();
                    
        } catch (Exception e) {
            log.error("Error converting queue user {} to details DTO: {}", 
                queueUser != null ? queueUser.getUserId() : "null", e.getMessage(), e);
            return null;
        }
    }
    
    private Map<String, Integer> calculateRegionBreakdown(List<MatchQueueUser> users) {
        Map<String, Integer> breakdown = new LinkedHashMap<>();
        
        // Initialize all regions with 0
        for (Region region : Region.values()) {
            breakdown.put(region.name(), 0);
        }
        
        // Count users by region
        for (MatchQueueUser user : users) {
            String regionName = user.getRegion().name();
            breakdown.put(regionName, breakdown.get(regionName) + 1);
        }
        
        return breakdown;
    }
    
    private Map<String, Integer> calculateRankBreakdown(List<MatchQueueUser> users) {
        Map<String, Integer> breakdown = new LinkedHashMap<>();
        
        // Initialize all ranks with 0
        for (Rank rank : Rank.values()) {
            breakdown.put(rank.name(), 0);
        }
        
        // Count users by rank
        for (MatchQueueUser user : users) {
            String rankName = user.getRank().name();
            breakdown.put(rankName, breakdown.get(rankName) + 1);
        }
        
        return breakdown;
    }
    
    private Map<String, Integer> calculateGenderBreakdown(List<MatchQueueUser> users) {
        Map<String, Integer> breakdown = new LinkedHashMap<>();
        
        // Initialize all genders with 0
        for (Gender gender : Gender.values()) {
            breakdown.put(gender.name(), 0);
        }
        
        // Count users by gender
        for (MatchQueueUser user : users) {
            String genderName = user.getGender().name();
            breakdown.put(genderName, breakdown.get(genderName) + 1);
        }
        
        return breakdown;
    }
    
    private Map<String, Integer> calculateAgeRangeBreakdown(List<MatchQueueUser> users) {
        Map<String, Integer> breakdown = new LinkedHashMap<>();
        breakdown.put("18-20", 0);
        breakdown.put("21-25", 0);
        breakdown.put("26-30", 0);
        breakdown.put("31-35", 0);
        breakdown.put("36+", 0);
        
        for (MatchQueueUser user : users) {
            int age = user.getAge();
            if (age >= 18 && age <= 20) {
                breakdown.put("18-20", breakdown.get("18-20") + 1);
            } else if (age >= 21 && age <= 25) {
                breakdown.put("21-25", breakdown.get("21-25") + 1);
            } else if (age >= 26 && age <= 30) {
                breakdown.put("26-30", breakdown.get("26-30") + 1);
            } else if (age >= 31 && age <= 35) {
                breakdown.put("31-35", breakdown.get("31-35") + 1);
            } else if (age >= 36) {
                breakdown.put("36+", breakdown.get("36+") + 1);
            }
        }
        
        return breakdown;
    }
    
    private double calculateAverageWaitTime(List<MatchQueueUser> users) {
        if (users.isEmpty()) {
            return 0.0;
        }
        
        LocalDateTime now = LocalDateTime.now();
        long totalWaitMinutes = users.stream()
                .mapToLong(user -> Duration.between(user.getQueuedAt(), now).toMinutes())
                .sum();
        
        return (double) totalWaitMinutes / users.size();
    }
    
    private Map<String, Integer> generateHourlyHistory() {
        // Simple implementation - in production this would come from a time-series database
        Map<String, Integer> history = new LinkedHashMap<>();
        LocalDateTime now = LocalDateTime.now();
        
        for (int i = 23; i >= 0; i--) {
            LocalDateTime hour = now.minusHours(i);
            String hourKey = String.format("%02d:00", hour.getHour());
            // For now, return current queue size for all hours (placeholder)
            history.put(hourKey, getActiveQueueSize());
        }
        
        return history;
    }
    
    private Map<String, Double> generateWaitTimeHistory() {
        // Simple implementation - in production this would come from historical data
        Map<String, Double> history = new LinkedHashMap<>();
        LocalDateTime now = LocalDateTime.now();
        double currentAvgWait = calculateAverageWaitTime(queueRepository.findByInQueueTrue());
        
        for (int i = 23; i >= 0; i--) {
            LocalDateTime hour = now.minusHours(i);
            String hourKey = String.format("%02d:00", hour.getHour());
            // For now, return current average for all hours (placeholder)
            history.put(hourKey, currentAvgWait);
        }
        
        return history;
    }
    
    // Broadcast admin-specific queue updates with statistics
    public void broadcastAdminQueueUpdate() {
        try {
            QueueStatsDTO stats = getQueueStatistics();
            messagingTemplate.convertAndSend("/topic/queue/admin", stats);
            log.debug("Broadcasted admin queue update with comprehensive statistics");
        } catch (Exception e) {
            log.error("Failed to broadcast admin queue update: {}", e.getMessage());
        }
    }
} 