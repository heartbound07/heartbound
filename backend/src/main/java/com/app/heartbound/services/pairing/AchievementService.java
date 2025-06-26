package com.app.heartbound.services.pairing;

import com.app.heartbound.entities.Achievement;
import com.app.heartbound.entities.PairAchievement;
import com.app.heartbound.entities.Pairing;
import com.app.heartbound.enums.AchievementType;
import com.app.heartbound.dto.pairing.ManageAchievementDTO;
import com.app.heartbound.repositories.pairing.AchievementRepository;
import com.app.heartbound.repositories.pairing.PairAchievementRepository;
import com.app.heartbound.repositories.pairing.PairingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * AchievementService
 * 
 * Service for managing achievements and their completion logic.
 * Uses strategy pattern for different achievement types.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AchievementService {

    private final AchievementRepository achievementRepository;
    private final PairAchievementRepository pairAchievementRepository;
    private final PairingRepository pairingRepository;
    private final PairLevelService pairLevelService;
    private final SimpMessagingTemplate messagingTemplate;
    
    // Callback for Discord leaderboard refresh (set by PairingService to avoid circular dependency)
    private Consumer<Long> discordLeaderboardRefreshCallback;
    
    // 🎉 NEW: Callback for Discord achievement notifications (set by PairingService to avoid circular dependency)
    private AchievementNotificationCallback discordAchievementNotificationCallback;

    /**
     * Functional interface for Discord achievement notification callback
     */
    @FunctionalInterface
    public interface AchievementNotificationCallback {
        void sendNotification(String channelId, String user1Id, String user2Id, 
                             String achievementName, String achievementDescription, 
                             String achievementRarity, int progressValue);
    }

    /**
     * Check and unlock achievements for a pairing based on current activity
     */
    @Transactional
    public List<PairAchievement> checkAndUnlockAchievements(Long pairingId) {
        Optional<Pairing> pairingOpt = pairingRepository.findById(pairingId);
        if (pairingOpt.isEmpty()) {
            throw new IllegalArgumentException("Pairing not found with ID: " + pairingId);
        }

        Pairing pairing = pairingOpt.get();
        List<PairAchievement> newAchievements = List.of();

        try {
            // Check all achievement types
            newAchievements = List.of(
                checkMessageMilestones(pairing),
                checkWeeklyActivity(pairing),
                checkVoiceTime(pairing),
                checkWordCount(pairing),
                checkEmojiCount(pairing),
                checkLongevity(pairing)
            ).stream()
            .flatMap(List::stream)
            .toList();

            // Award XP and broadcast notifications for new achievements
            for (PairAchievement achievement : newAchievements) {
                awardAchievementXP(achievement);
                broadcastAchievementUnlock(achievement);
            }

            if (!newAchievements.isEmpty()) {
                log.info("Unlocked {} achievements for pairing {}", newAchievements.size(), pairingId);
                
                // 🚀 NEW: Refresh Discord leaderboard after achievement unlocks
                if (discordLeaderboardRefreshCallback != null) {
                    try {
                        discordLeaderboardRefreshCallback.accept(pairingId);
                    } catch (Exception e) {
                        log.error("Failed to refresh Discord leaderboard after achievement unlock for pairing {}: {}", pairingId, e.getMessage());
                    }
                }
            }

        } catch (Exception e) {
            log.error("Error checking achievements for pairing {}: {}", pairingId, e.getMessage(), e);
        }

        return newAchievements;
    }

    /**
     * Check message milestone achievements
     */
    private List<PairAchievement> checkMessageMilestones(Pairing pairing) {
        List<Achievement> messageAchievements = achievementRepository.findEligibleAchievements(
            AchievementType.MESSAGE_MILESTONE, pairing.getMessageCount());

        return messageAchievements.stream()
            .filter(achievement -> !pairAchievementRepository.existsByPairingAndAchievement(pairing, achievement))
            .map(achievement -> unlockAchievement(pairing, achievement, pairing.getMessageCount()))
            .toList();
    }

    /**
     * Check weekly activity achievements
     */
    private List<PairAchievement> checkWeeklyActivity(Pairing pairing) {
        List<Achievement> weeklyAchievements = achievementRepository.findEligibleAchievements(
            AchievementType.WEEKLY_ACTIVITY, pairing.getActiveDays());

        return weeklyAchievements.stream()
            .filter(achievement -> !pairAchievementRepository.existsByPairingAndAchievement(pairing, achievement))
            .map(achievement -> unlockAchievement(pairing, achievement, pairing.getActiveDays()))
            .toList();
    }

    /**
     * Check voice time achievements
     */
    private List<PairAchievement> checkVoiceTime(Pairing pairing) {
        int voiceHours = pairing.getVoiceTimeMinutes() / 60;
        List<Achievement> voiceAchievements = achievementRepository.findEligibleAchievements(
            AchievementType.VOICE_TIME, voiceHours);

        return voiceAchievements.stream()
            .filter(achievement -> !pairAchievementRepository.existsByPairingAndAchievement(pairing, achievement))
            .map(achievement -> unlockAchievement(pairing, achievement, voiceHours))
            .toList();
    }

    /**
     * Check word count achievements
     */
    private List<PairAchievement> checkWordCount(Pairing pairing) {
        List<Achievement> wordAchievements = achievementRepository.findEligibleAchievements(
            AchievementType.WORD_COUNT, pairing.getWordCount());

        return wordAchievements.stream()
            .filter(achievement -> !pairAchievementRepository.existsByPairingAndAchievement(pairing, achievement))
            .map(achievement -> unlockAchievement(pairing, achievement, pairing.getWordCount()))
            .toList();
    }

    /**
     * Check emoji count achievements
     */
    private List<PairAchievement> checkEmojiCount(Pairing pairing) {
        List<Achievement> emojiAchievements = achievementRepository.findEligibleAchievements(
            AchievementType.EMOJI_COUNT, pairing.getEmojiCount());

        return emojiAchievements.stream()
            .filter(achievement -> !pairAchievementRepository.existsByPairingAndAchievement(pairing, achievement))
            .map(achievement -> unlockAchievement(pairing, achievement, pairing.getEmojiCount()))
            .toList();
    }

    /**
     * Check longevity achievements (days since matching)
     */
    private List<PairAchievement> checkLongevity(Pairing pairing) {
        long daysSinceMatching = java.time.temporal.ChronoUnit.DAYS.between(
            pairing.getMatchedAt().toLocalDate(), 
            LocalDateTime.now().toLocalDate()
        );

        List<Achievement> longevityAchievements = achievementRepository.findEligibleAchievements(
            AchievementType.LONGEVITY, (int) daysSinceMatching);

        return longevityAchievements.stream()
            .filter(achievement -> !pairAchievementRepository.existsByPairingAndAchievement(pairing, achievement))
            .map(achievement -> unlockAchievement(pairing, achievement, (int) daysSinceMatching))
            .toList();
    }

    /**
     * Unlock a specific achievement for a pairing
     */
    @Transactional
    public PairAchievement unlockAchievement(Pairing pairing, Achievement achievement, int progressValue) {
        // Check if already unlocked
        if (pairAchievementRepository.existsByPairingAndAchievement(pairing, achievement)) {
            log.warn("Achievement {} already unlocked for pairing {}", achievement.getAchievementKey(), pairing.getId());
            return pairAchievementRepository.findByPairingAndAchievement(pairing, achievement).orElse(null);
        }

        PairAchievement pairAchievement = PairAchievement.builder()
            .pairing(pairing)
            .achievement(achievement)
            .progressValue(progressValue)
            .xpAwarded(achievement.getXpReward())
            .notified(false)
            .build();

        PairAchievement saved = pairAchievementRepository.save(pairAchievement);
        log.info("Unlocked achievement '{}' for pairing {}: {} XP awarded", 
                achievement.getName(), pairing.getId(), achievement.getXpReward());

        return saved;
    }

    /**
     * Award XP for achievement unlock
     */
    private void awardAchievementXP(PairAchievement pairAchievement) {
        try {
            pairLevelService.addXP(
                pairAchievement.getPairing().getId(),
                pairAchievement.getXpAwarded(),
                "Achievement: " + pairAchievement.getAchievement().getName()
            );
        } catch (Exception e) {
            log.error("Failed to award XP for achievement {}: {}", 
                    pairAchievement.getAchievement().getAchievementKey(), e.getMessage());
        }
    }

    /**
     * Broadcast achievement unlock notification
     */
    private void broadcastAchievementUnlock(PairAchievement pairAchievement) {
        try {
            Pairing pairing = pairAchievement.getPairing();
            Achievement achievement = pairAchievement.getAchievement();

            Map<String, Object> achievementNotification = Map.of(
                "eventType", "ACHIEVEMENT_UNLOCKED",
                "pairingId", pairing.getId(),
                "achievement", Map.of(
                    "id", achievement.getId(),
                    "key", achievement.getAchievementKey(),
                    "name", achievement.getName(),
                    "description", achievement.getDescription(),
                    "xpReward", achievement.getXpReward(),
                    "rarity", achievement.getRarity(),
                    "tier", achievement.getTier()
                ),
                "progressValue", pairAchievement.getProgressValue(),
                "xpAwarded", pairAchievement.getXpAwarded(),
                "timestamp", LocalDateTime.now().toString()
            );

            // Send to both users
            messagingTemplate.convertAndSend("/user/" + pairing.getUser1Id() + "/topic/achievements", achievementNotification);
            messagingTemplate.convertAndSend("/user/" + pairing.getUser2Id() + "/topic/achievements", achievementNotification);

            // 🎉 NEW: Send Discord achievement notification to pairing channel using callback
            if (discordAchievementNotificationCallback != null && pairing.getDiscordChannelId() != null) {
                try {
                    String channelId = pairing.getDiscordChannelId().toString();
                    discordAchievementNotificationCallback.sendNotification(
                        channelId,
                        pairing.getUser1Id(),
                        pairing.getUser2Id(),
                        achievement.getName(),
                        achievement.getDescription(),
                        achievement.getRarity(),
                        pairAchievement.getProgressValue() != null ? pairAchievement.getProgressValue() : 0
                    );
                    
                    log.debug("Initiated Discord achievement notification for pairing {} in channel {}", 
                             pairing.getId(), channelId);
                } catch (Exception e) {
                    log.warn("Error initiating Discord achievement notification for pairing {}: {}", 
                            pairing.getId(), e.getMessage());
                }
            } else {
                log.debug("No Discord achievement notification callback or channel ID found for pairing {}, skipping Discord achievement notification", 
                         pairing.getId());
            }

            // Mark as notified
            pairAchievement.setNotified(true);
            pairAchievementRepository.save(pairAchievement);

            log.info("Broadcasted achievement unlock for pairing {}: {}", pairing.getId(), achievement.getName());

        } catch (Exception e) {
            log.error("Failed to broadcast achievement unlock for pairing {}: {}", 
                    pairAchievement.getPairing().getId(), e.getMessage());
        }
    }

    /**
     * Get achievements for a pairing
     */
    @Transactional(readOnly = true)
    public List<PairAchievement> getPairingAchievements(Long pairingId) {
        return pairAchievementRepository.findByPairingId(pairingId);
    }

    /**
     * Get recent achievements for a pairing
     */
    @Transactional(readOnly = true)
    public List<PairAchievement> getRecentAchievements(Long pairingId, int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        return pairAchievementRepository.findRecentAchievements(pairingId, since);
    }

    /**
     * Get available achievements (not yet unlocked) for a pairing
     */
    @Transactional(readOnly = true)
    public List<Achievement> getAvailableAchievements(Long pairingId) {
        List<Achievement> allAchievements = achievementRepository.findByActiveTrue();
        List<PairAchievement> unlockedAchievements = pairAchievementRepository.findByPairingId(pairingId);
        
        List<Long> unlockedIds = unlockedAchievements.stream()
            .map(pa -> pa.getAchievement().getId())
            .toList();

        return allAchievements.stream()
            .filter(achievement -> !unlockedIds.contains(achievement.getId()))
            .filter(achievement -> !achievement.isHidden())
            .toList();
    }

    /**
     * Initialize default achievements (called during application startup)
     */
    @Transactional
    public void initializeDefaultAchievements() {
        if (achievementRepository.count() > 0) {
            log.info("Achievements already initialized, skipping default creation");
            return;
        }

        log.info("Initializing default achievements...");

        // Message milestones
        createAchievement("MESSAGE_1000", "First Milestone", "Send 1000 messages together", 
                         AchievementType.MESSAGE_MILESTONE, 100, 1000, "silver");
        createAchievement("MESSAGE_5000", "Chatterbox", "Send 5000 messages together", 
                         AchievementType.MESSAGE_MILESTONE, 250, 5000, "gold");
        createAchievement("MESSAGE_10000", "Conversation Master", "Send 10000 messages together", 
                         AchievementType.MESSAGE_MILESTONE, 500, 10000, "diamond");

        // Weekly activity
        createAchievement("WEEKLY_1", "First Week", "Stay active for 1 week", 
                         AchievementType.WEEKLY_ACTIVITY, 100, 7, "bronze");
        createAchievement("WEEKLY_4", "Monthly Commitment", "Stay active for 4 weeks", 
                         AchievementType.WEEKLY_ACTIVITY, 300, 28, "silver");
        createAchievement("WEEKLY_12", "Quarterly Champions", "Stay active for 12 weeks", 
                         AchievementType.WEEKLY_ACTIVITY, 750, 84, "gold");

        // Voice time
        createAchievement("VOICE_10", "Voice Starter", "Spend 10 hours in voice together", 
                         AchievementType.VOICE_TIME, 150, 10, "bronze");
        createAchievement("VOICE_50", "Voice Veterans", "Spend 50 hours in voice together", 
                         AchievementType.VOICE_TIME, 400, 50, "silver");
        createAchievement("VOICE_100", "Voice Legends", "Spend 100 hours in voice together", 
                         AchievementType.VOICE_TIME, 800, 100, "gold");

        log.info("Default achievements initialized successfully");
    }

    private void createAchievement(String key, String name, String description, AchievementType type, 
                                 int xpReward, int requirementValue, String rarity) {
        if (achievementRepository.existsByAchievementKey(key)) {
            return;
        }

        Achievement achievement = Achievement.builder()
            .achievementKey(key)
            .name(name)
            .description(description)
            .achievementType(type)
            .xpReward(xpReward)
            .requirementValue(requirementValue)
            .rarity(rarity)
            .active(true)
            .build();

        achievementRepository.save(achievement);
        log.debug("Created achievement: {}", key);
    }

    /**
     * Admin: Manage achievement (unlock/lock)
     */
    @Transactional
    public Map<String, Object> manageAchievementAdmin(Long pairingId, ManageAchievementDTO manageRequest) {
        log.info("Admin managing achievement for pairing {}: {}", pairingId, manageRequest);
        
        // Get pairing
        Pairing pairing = pairingRepository.findById(pairingId)
            .orElseThrow(() -> new IllegalArgumentException("Pairing not found: " + pairingId));
        
        // Get achievement
        Achievement achievement = achievementRepository.findById(manageRequest.getAchievementId())
            .orElseThrow(() -> new IllegalArgumentException("Achievement not found: " + manageRequest.getAchievementId()));
        
        String action = manageRequest.getAction().toLowerCase();
        
        if ("unlock".equals(action)) {
            return unlockAchievementAdmin(pairing, achievement, manageRequest.getCustomXP());
        } else if ("lock".equals(action)) {
            return lockAchievementAdmin(pairing, achievement);
        } else {
            throw new IllegalArgumentException("Invalid action: " + action + ". Must be 'unlock' or 'lock'");
        }
    }
    
    private Map<String, Object> unlockAchievementAdmin(Pairing pairing, Achievement achievement, Integer customXP) {
        // Check if already unlocked
        Optional<PairAchievement> existing = pairAchievementRepository.findByPairingAndAchievement(pairing, achievement);
        if (existing.isPresent()) {
            return Map.of(
                "message", "Achievement already unlocked",
                "achievement", existing.get()
            );
        }
        
        // Use custom XP if provided, otherwise use default
        int xpToAward = customXP != null ? customXP : achievement.getXpReward();
        
        // Create achievement
        PairAchievement pairAchievement = PairAchievement.builder()
            .pairing(pairing)
            .achievement(achievement)
            .progressValue(achievement.getRequirementValue()) // Set to requirement value for admin unlock
            .xpAwarded(xpToAward)
            .notified(false)
            .build();
        
        PairAchievement saved = pairAchievementRepository.save(pairAchievement);
        
        // Award XP
        if (xpToAward > 0) {
            try {
                pairLevelService.addXP(pairing.getId(), xpToAward, "Admin Achievement: " + achievement.getName());
            } catch (Exception e) {
                log.error("Failed to award XP for admin achievement unlock: {}", e.getMessage());
            }
        }
        
        // 🎉 NEW: Send Discord achievement notification for admin unlocks using callback
        if (discordAchievementNotificationCallback != null && pairing.getDiscordChannelId() != null) {
            try {
                String channelId = pairing.getDiscordChannelId().toString();
                discordAchievementNotificationCallback.sendNotification(
                    channelId,
                    pairing.getUser1Id(),
                    pairing.getUser2Id(),
                    achievement.getName(),
                    achievement.getDescription(),
                    achievement.getRarity(),
                    achievement.getRequirementValue()
                );
                
                log.debug("Initiated Discord achievement notification for admin unlock pairing {} in channel {}", 
                         pairing.getId(), channelId);
            } catch (Exception e) {
                log.warn("Error initiating Discord achievement notification for admin unlock pairing {}: {}", 
                        pairing.getId(), e.getMessage());
            }
        }
        
        log.info("Admin unlocked achievement '{}' for pairing {}: {} XP awarded", 
                achievement.getName(), pairing.getId(), xpToAward);
        
        return Map.of(
            "message", "Achievement unlocked successfully",
            "achievement", saved,
            "xpAwarded", xpToAward
        );
    }
    
    private Map<String, Object> lockAchievementAdmin(Pairing pairing, Achievement achievement) {
        // Find existing achievement
        Optional<PairAchievement> existing = pairAchievementRepository.findByPairingAndAchievement(pairing, achievement);
        if (existing.isEmpty()) {
            return Map.of(
                "message", "Achievement was not unlocked",
                "achievement", null
            );
        }
        
        PairAchievement pairAchievement = existing.get();
        int xpToRemove = pairAchievement.getXpAwarded();
        
        // Remove the achievement
        pairAchievementRepository.delete(pairAchievement);
        
        // Remove XP
        if (xpToRemove > 0) {
            try {
                pairLevelService.removeXP(pairing.getId(), xpToRemove, "Admin Achievement Removal: " + achievement.getName());
            } catch (Exception e) {
                log.error("Failed to remove XP for admin achievement lock: {}", e.getMessage());
            }
        }
        
        log.info("Admin locked achievement '{}' for pairing {}: {} XP removed", 
                achievement.getName(), pairing.getId(), xpToRemove);
        
        return Map.of(
            "message", "Achievement locked successfully",
            "achievement", pairAchievement,
            "xpRemoved", xpToRemove
        );
    }

    /**
     * Delete all achievements for a pairing
     */
    @Transactional
    public void deleteAllPairAchievements(Long pairingId) {
        log.info("Deleting all achievements for pairing ID: {}", pairingId);
        
        List<PairAchievement> achievements = pairAchievementRepository.findByPairingId(pairingId);
        if (!achievements.isEmpty()) {
            pairAchievementRepository.deleteAll(achievements);
            log.info("Successfully deleted {} achievements for pairing {}", achievements.size(), pairingId);
        } else {
            log.info("No achievements found to delete for pairing {}", pairingId);
        }
    }

    /**
     * Set the Discord leaderboard refresh callback (called by PairingService to avoid circular dependency)
     */
    public void setDiscordLeaderboardRefreshCallback(Consumer<Long> callback) {
        this.discordLeaderboardRefreshCallback = callback;
    }
    
    /**
     * Set the Discord achievement notification callback (called by PairingService to avoid circular dependency)
     */
    public void setDiscordAchievementNotificationCallback(AchievementNotificationCallback callback) {
        this.discordAchievementNotificationCallback = callback;
    }
} 