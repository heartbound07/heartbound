package com.app.heartbound.services.pairing;

import com.app.heartbound.entities.Achievement;
import com.app.heartbound.entities.PairAchievement;
import com.app.heartbound.entities.Pairing;
import com.app.heartbound.enums.AchievementType;
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
} 