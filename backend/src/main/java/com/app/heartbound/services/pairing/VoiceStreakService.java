package com.app.heartbound.services.pairing;

import com.app.heartbound.entities.VoiceStreak;
import com.app.heartbound.entities.Pairing;
import com.app.heartbound.repositories.pairing.VoiceStreakRepository;
import com.app.heartbound.repositories.pairing.PairingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * VoiceStreakService
 * 
 * Service for tracking daily voice activity streaks for pairings.
 * Handles streak calculations, updates, and milestone notifications.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VoiceStreakService {

    private final VoiceStreakRepository voiceStreakRepository;
    private final PairingRepository pairingRepository;
    private final PairLevelService pairLevelService;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Update voice activity for a pairing on a specific date
     */
    @Transactional
    public VoiceStreak updateVoiceActivity(Long pairingId, LocalDate date, int voiceMinutes) {
        Optional<Pairing> pairingOpt = pairingRepository.findById(pairingId);
        if (pairingOpt.isEmpty()) {
            throw new IllegalArgumentException("Pairing not found with ID: " + pairingId);
        }

        Pairing pairing = pairingOpt.get();
        
        // Find or create voice streak for this date
        Optional<VoiceStreak> existingStreak = voiceStreakRepository.findByPairingAndStreakDate(pairing, date);
        
        VoiceStreak voiceStreak;
        boolean isNewStreak = false;
        
        if (existingStreak.isPresent()) {
            voiceStreak = existingStreak.get();
            voiceStreak.setVoiceMinutes(voiceMinutes);
        } else {
            // Calculate streak count
            int streakCount = calculateStreakCount(pairing, date);
            
            voiceStreak = VoiceStreak.builder()
                .pairing(pairing)
                .streakDate(date)
                .voiceMinutes(voiceMinutes)
                .streakCount(streakCount)
                .active(true)
                .build();
            
            isNewStreak = true;
        }

        VoiceStreak savedStreak = voiceStreakRepository.save(voiceStreak);
        
        // Check for streak milestones and award XP
        if (isNewStreak && savedStreak.meetsMinimumActivity()) {
            checkStreakMilestones(savedStreak);
        }

        log.info("Updated voice activity for pairing {} on {}: {} minutes, streak: {}", 
                pairingId, date, voiceMinutes, savedStreak.getStreakCount());

        return savedStreak;
    }

    /**
     * Update today's voice activity for a pairing
     */
    @Transactional
    public VoiceStreak updateTodaysVoiceActivity(Long pairingId, int voiceMinutes) {
        return updateVoiceActivity(pairingId, LocalDate.now(), voiceMinutes);
    }

    /**
     * Calculate the streak count for a specific date
     */
    private int calculateStreakCount(Pairing pairing, LocalDate date) {
        int streakCount = 1; // Start with 1 for current day
        LocalDate checkDate = date.minusDays(1);
        
        // Check backwards for consecutive days
        for (int i = 0; i < 365; i++) { // Limit to 365 days to prevent infinite loops
            Optional<VoiceStreak> previousStreak = voiceStreakRepository.findByPairingAndStreakDate(pairing, checkDate);
            
            if (previousStreak.isPresent() && previousStreak.get().meetsMinimumActivity()) {
                streakCount++;
                checkDate = checkDate.minusDays(1);
            } else {
                break; // Streak broken
            }
        }
        
        return streakCount;
    }

    /**
     * Check for streak milestones and award XP
     */
    private void checkStreakMilestones(VoiceStreak voiceStreak) {
        int streakCount = voiceStreak.getStreakCount();
        int xpReward = voiceStreak.getStreakXPReward();
        
        if (xpReward > 0) {
            String milestoneMessage = String.format("%d day voice streak!", streakCount);
            
            // Award XP for streak milestone
            pairLevelService.addXP(
                voiceStreak.getPairing().getId(),
                xpReward,
                milestoneMessage
            );
            
            // Broadcast streak milestone notification
            broadcastStreakMilestone(voiceStreak, milestoneMessage);
            
            log.info("Streak milestone reached for pairing {}: {} days, {} XP awarded", 
                    voiceStreak.getPairing().getId(), streakCount, xpReward);
        }
    }

    /**
     * Broadcast streak milestone notification
     */
    private void broadcastStreakMilestone(VoiceStreak voiceStreak, String message) {
        try {
            Pairing pairing = voiceStreak.getPairing();
            
            Map<String, Object> streakNotification = Map.of(
                "eventType", "STREAK_MILESTONE",
                "pairingId", pairing.getId(),
                "streakCount", voiceStreak.getStreakCount(),
                "tier", voiceStreak.getStreakTier(),
                "xpReward", voiceStreak.getStreakXPReward(),
                "message", message,
                "timestamp", LocalDateTime.now().toString()
            );

            // Send to both users
            messagingTemplate.convertAndSend("/user/" + pairing.getUser1Id() + "/topic/streaks", streakNotification);
            messagingTemplate.convertAndSend("/user/" + pairing.getUser2Id() + "/topic/streaks", streakNotification);

            log.info("Broadcasted streak milestone for pairing {}: {}", pairing.getId(), message);

        } catch (Exception e) {
            log.error("Failed to broadcast streak milestone for pairing {}: {}", 
                    voiceStreak.getPairing().getId(), e.getMessage());
        }
    }

    /**
     * Get current streak count for a pairing
     */
    @Transactional(readOnly = true)
    public int getCurrentStreakCount(Long pairingId) {
        return voiceStreakRepository.getCurrentStreakCount(pairingId);
    }

    /**
     * Get highest streak count for a pairing
     */
    @Transactional(readOnly = true)
    public int getHighestStreakCount(Long pairingId) {
        return voiceStreakRepository.getHighestStreakCount(pairingId);
    }

    /**
     * Get voice streaks for a pairing
     */
    @Transactional(readOnly = true)
    public List<VoiceStreak> getVoiceStreaks(Long pairingId) {
        return voiceStreakRepository.findByPairingId(pairingId);
    }

    /**
     * Get voice streaks within a date range
     */
    @Transactional(readOnly = true)
    public List<VoiceStreak> getVoiceStreaksInRange(Long pairingId, LocalDate startDate, LocalDate endDate) {
        return voiceStreakRepository.findStreaksInDateRange(pairingId, startDate, endDate);
    }

    /**
     * Check if pairing has voice activity today
     */
    @Transactional(readOnly = true)
    public boolean hasVoiceActivityToday(Long pairingId) {
        Optional<VoiceStreak> todayStreak = voiceStreakRepository.findTodayStreakByPairingId(pairingId, LocalDate.now());
        return todayStreak.isPresent() && todayStreak.get().meetsMinimumActivity();
    }

    /**
     * Get streak statistics for a pairing
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getStreakStatistics(Long pairingId) {
        int currentStreak = getCurrentStreakCount(pairingId);
        int highestStreak = getHighestStreakCount(pairingId);
        int totalVoiceMinutes = voiceStreakRepository.getTotalVoiceMinutesByPairingId(pairingId);
        long totalActiveDays = voiceStreakRepository.countActiveStreakDaysByPairingId(pairingId);
        
        return Map.of(
            "currentStreak", currentStreak,
            "highestStreak", highestStreak,
            "totalVoiceMinutes", totalVoiceMinutes,
            "totalVoiceHours", totalVoiceMinutes / 60,
            "totalActiveDays", totalActiveDays,
            "hasActivityToday", hasVoiceActivityToday(pairingId)
        );
    }

    /**
     * Break current streak for a pairing (called when no activity for a day)
     */
    @Transactional
    public void breakStreak(Long pairingId, LocalDate missedDate) {
        Optional<Pairing> pairingOpt = pairingRepository.findById(pairingId);
        if (pairingOpt.isEmpty()) {
            return;
        }

        Pairing pairing = pairingOpt.get();
        
        // Mark previous streaks as inactive if this day was missed
        List<VoiceStreak> recentStreaks = voiceStreakRepository.findStreaksInDateRange(
            pairingId, missedDate.minusDays(30), missedDate);
        
        for (VoiceStreak streak : recentStreaks) {
            if (streak.getStreakDate().isAfter(missedDate)) {
                streak.setActive(false);
                voiceStreakRepository.save(streak);
            }
        }
        
        log.info("Broke voice streak for pairing {} on missed date: {}", pairingId, missedDate);
    }

    /**
     * Get top streak performers (leaderboard)
     */
    @Transactional(readOnly = true)
    public List<Object[]> getTopStreakPerformers(int limit) {
        List<Object[]> topPerformers = voiceStreakRepository.findTopStreakPerformers();
        return topPerformers.stream().limit(limit).toList();
    }

    /**
     * Daily maintenance task to check for broken streaks
     */
    @Transactional
    public void performDailyMaintenanceTask() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        
        // Find all active pairings
        List<Pairing> activePairings = pairingRepository.findByActiveTrue();
        
        for (Pairing pairing : activePairings) {
            try {
                // Check if pairing had voice activity yesterday
                Optional<VoiceStreak> yesterdayStreak = voiceStreakRepository.findYesterdayStreakByPairingId(
                    pairing.getId(), yesterday);
                
                if (yesterdayStreak.isEmpty() || !yesterdayStreak.get().meetsMinimumActivity()) {
                    // No activity yesterday, check if we need to break streaks
                    int currentStreak = getCurrentStreakCount(pairing.getId());
                    if (currentStreak > 0) {
                        breakStreak(pairing.getId(), yesterday);
                        log.info("Broke streak for pairing {} due to no activity on {}", pairing.getId(), yesterday);
                    }
                }
            } catch (Exception e) {
                log.error("Error during daily maintenance for pairing {}: {}", pairing.getId(), e.getMessage());
            }
        }
        
        log.info("Completed daily voice streak maintenance task");
    }

    /**
     * Admin: Update voice streak
     */
    @Transactional
    public VoiceStreak updateVoiceStreakAdmin(Long streakId, com.app.heartbound.dto.pairing.UpdateVoiceStreakDTO updateRequest) {
        log.info("Admin updating voice streak {}: {}", streakId, updateRequest);
        
        VoiceStreak voiceStreak = voiceStreakRepository.findById(streakId)
            .orElseThrow(() -> new IllegalArgumentException("Voice streak not found: " + streakId));
        
        // Update fields if provided
        if (updateRequest.getStreakDate() != null) {
            voiceStreak.setStreakDate(LocalDate.parse(updateRequest.getStreakDate()));
        }
        
        if (updateRequest.getVoiceMinutes() != null) {
            voiceStreak.setVoiceMinutes(Math.max(0, updateRequest.getVoiceMinutes()));
        }
        
        if (updateRequest.getStreakCount() != null) {
            voiceStreak.setStreakCount(Math.max(1, updateRequest.getStreakCount()));
        }
        
        if (updateRequest.getActive() != null) {
            voiceStreak.setActive(updateRequest.getActive());
        }
        
        VoiceStreak saved = voiceStreakRepository.save(voiceStreak);
        log.info("Admin updated voice streak {}: Date={}, Minutes={}, Count={}, Active={}", 
                 streakId, saved.getStreakDate(), saved.getVoiceMinutes(), saved.getStreakCount(), saved.isActive());
        
        return saved;
    }
    
    /**
     * Admin: Create voice streak
     */
    @Transactional
    public VoiceStreak createVoiceStreakAdmin(Long pairingId, com.app.heartbound.dto.pairing.CreateVoiceStreakDTO createRequest) {
        log.info("Admin creating voice streak for pairing {}: {}", pairingId, createRequest);
        
        Pairing pairing = pairingRepository.findById(pairingId)
            .orElseThrow(() -> new IllegalArgumentException("Pairing not found: " + pairingId));
        
        LocalDate streakDate = LocalDate.parse(createRequest.getStreakDate());
        
        // Check if streak already exists for this date
        Optional<VoiceStreak> existing = voiceStreakRepository.findByPairingAndStreakDate(pairing, streakDate);
        if (existing.isPresent()) {
            throw new IllegalArgumentException("Voice streak already exists for date: " + streakDate);
        }
        
        VoiceStreak voiceStreak = VoiceStreak.builder()
            .pairing(pairing)
            .streakDate(streakDate)
            .voiceMinutes(Math.max(0, createRequest.getVoiceMinutes()))
            .streakCount(Math.max(1, createRequest.getStreakCount()))
            .active(createRequest.getActive())
            .build();
        
        VoiceStreak saved = voiceStreakRepository.save(voiceStreak);
        log.info("Admin created voice streak for pairing {}: Date={}, Minutes={}, Count={}, Active={}", 
                 pairingId, saved.getStreakDate(), saved.getVoiceMinutes(), saved.getStreakCount(), saved.isActive());
        
        return saved;
    }
    
    /**
     * Admin: Delete voice streak
     */
    @Transactional
    public void deleteVoiceStreakAdmin(Long streakId) {
        log.info("Admin deleting voice streak {}", streakId);
        
        VoiceStreak voiceStreak = voiceStreakRepository.findById(streakId)
            .orElseThrow(() -> new IllegalArgumentException("Voice streak not found: " + streakId));
        
        voiceStreakRepository.delete(voiceStreak);
        log.info("Admin deleted voice streak {}: Date={}, Pairing={}", 
                 streakId, voiceStreak.getStreakDate(), voiceStreak.getPairing().getId());
    }

    /**
     * Delete all voice streaks for a pairing
     */
    @Transactional
    public void deleteAllVoiceStreaks(Long pairingId) {
        log.info("Deleting all voice streaks for pairing ID: {}", pairingId);
        
        List<VoiceStreak> streaks = voiceStreakRepository.findByPairingId(pairingId);
        if (!streaks.isEmpty()) {
            voiceStreakRepository.deleteAll(streaks);
            log.info("Successfully deleted {} voice streak records for pairing {}", streaks.size(), pairingId);
        } else {
            log.info("No voice streak records found to delete for pairing {}", pairingId);
        }
    }
} 