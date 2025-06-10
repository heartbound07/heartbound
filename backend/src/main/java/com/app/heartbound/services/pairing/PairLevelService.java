package com.app.heartbound.services.pairing;

import com.app.heartbound.entities.PairLevel;
import com.app.heartbound.entities.Pairing;
import com.app.heartbound.repositories.pairing.PairLevelRepository;
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
 * PairLevelService
 * 
 * Service for managing XP and level progression for pairings.
 * Handles XP calculations, level ups, and integration with achievements.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PairLevelService {

    private final PairLevelRepository pairLevelRepository;
    private final PairingRepository pairingRepository;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Get or create pair level for a pairing
     */
    @Transactional
    public PairLevel getOrCreatePairLevel(Long pairingId) {
        Optional<Pairing> pairingOpt = pairingRepository.findById(pairingId);
        if (pairingOpt.isEmpty()) {
            throw new IllegalArgumentException("Pairing not found with ID: " + pairingId);
        }

        Pairing pairing = pairingOpt.get();
        return getOrCreatePairLevel(pairing);
    }

    /**
     * Get or create pair level for a pairing entity
     */
    @Transactional
    public PairLevel getOrCreatePairLevel(Pairing pairing) {
        Optional<PairLevel> existingLevel = pairLevelRepository.findByPairing(pairing);
        
        if (existingLevel.isPresent()) {
            return existingLevel.get();
        }

        // Create new pair level
        PairLevel newLevel = PairLevel.builder()
                .pairing(pairing)
                .currentLevel(1)
                .totalXP(0)
                .currentLevelXP(0)
                .nextLevelXP(100)
                .build();

        PairLevel savedLevel = pairLevelRepository.save(newLevel);
        log.info("Created new pair level for pairing {}: Level {}", pairing.getId(), savedLevel.getCurrentLevel());
        
        return savedLevel;
    }

    /**
     * Add XP to a pairing and handle level progression
     */
    @Transactional
    public PairLevel addXP(Long pairingId, int xpToAdd, String reason) {
        if (xpToAdd <= 0) {
            throw new IllegalArgumentException("XP to add must be positive");
        }

        PairLevel pairLevel = getOrCreatePairLevel(pairingId);
        int oldLevel = pairLevel.getCurrentLevel();
        
        // Add XP
        pairLevel.setTotalXP(pairLevel.getTotalXP() + xpToAdd);
        pairLevel.setCurrentLevelXP(pairLevel.getCurrentLevelXP() + xpToAdd);

        // Check for level ups
        boolean leveledUp = false;
        while (pairLevel.isReadyToLevelUp()) {
            leveledUp = true;
            int overflow = pairLevel.getCurrentLevelXP() - pairLevel.getNextLevelXP();
            pairLevel.levelUp();
            pairLevel.setCurrentLevelXP(overflow);
        }

        PairLevel savedLevel = pairLevelRepository.save(pairLevel);
        
        // Broadcast XP gain notification
        broadcastXPUpdate(savedLevel, xpToAdd, reason, leveledUp, oldLevel);
        
        log.info("Added {} XP to pairing {}: Total XP: {}, Level: {}, Reason: {}", 
                xpToAdd, pairingId, savedLevel.getTotalXP(), savedLevel.getCurrentLevel(), reason);

        return savedLevel;
    }

    /**
     * Calculate XP for message milestones
     */
    public int calculateMessageXP(int messageCount) {
        // Every 1000 messages = 100 XP
        int milestones = messageCount / 1000;
        return milestones * 100;
    }

    /**
     * Calculate XP for weekly activity
     */
    public int calculateWeeklyActivityXP(int activeDays) {
        // Every 7 days (1 week) = 100 XP
        int weeks = activeDays / 7;
        return weeks * 100;
    }

    /**
     * Calculate XP for voice time
     */
    public int calculateVoiceTimeXP(int voiceMinutes) {
        // Every 60 minutes (1 hour) = 25 XP
        int hours = voiceMinutes / 60;
        return hours * 25;
    }

    /**
     * Calculate XP for word count
     */
    public int calculateWordCountXP(int wordCount) {
        // Every 10,000 words = 50 XP
        int milestones = wordCount / 10000;
        return milestones * 50;
    }

    /**
     * Update pair level based on current pairing activity
     */
    @Transactional
    public PairLevel updatePairLevelFromActivity(Long pairingId) {
        Optional<Pairing> pairingOpt = pairingRepository.findById(pairingId);
        if (pairingOpt.isEmpty()) {
            throw new IllegalArgumentException("Pairing not found with ID: " + pairingId);
        }

        Pairing pairing = pairingOpt.get();
        PairLevel pairLevel = getOrCreatePairLevel(pairing);

        // Calculate total XP based on current activity
        int messageXP = calculateMessageXP(pairing.getMessageCount());
        int weeklyXP = calculateWeeklyActivityXP(pairing.getActiveDays());
        int voiceXP = calculateVoiceTimeXP(pairing.getVoiceTimeMinutes());
        int wordXP = calculateWordCountXP(pairing.getWordCount());

        int totalCalculatedXP = messageXP + weeklyXP + voiceXP + wordXP;

        // Only update if calculated XP is higher than current total XP
        if (totalCalculatedXP > pairLevel.getTotalXP()) {
            int xpToAdd = totalCalculatedXP - pairLevel.getTotalXP();
            return addXP(pairingId, xpToAdd, "Activity sync");
        }

        return pairLevel;
    }

    /**
     * Get pair level by pairing ID
     */
    @Transactional(readOnly = true)
    public Optional<PairLevel> getPairLevel(Long pairingId) {
        return pairLevelRepository.findByPairingId(pairingId);
    }

    /**
     * Get leaderboard of top level pairs
     */
    @Transactional(readOnly = true)
    public List<PairLevel> getTopLevelPairs(int limit) {
        List<PairLevel> allTopPairs = pairLevelRepository.findTopLevelPairs();
        return allTopPairs.stream().limit(limit).toList();
    }

    /**
     * Get leaderboard by total XP
     */
    @Transactional(readOnly = true)
    public List<PairLevel> getTopXPPairs(int limit) {
        List<PairLevel> allTopPairs = pairLevelRepository.findByTotalXPDesc();
        return allTopPairs.stream().limit(limit).toList();
    }

    /**
     * Get level statistics
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getLevelStatistics() {
        List<Object[]> levelCounts = pairLevelRepository.countPairsByLevel();
        Double averageLevel = pairLevelRepository.getAverageLevelOfActivePairs();
        
        return Map.of(
            "levelDistribution", levelCounts,
            "averageLevel", averageLevel != null ? averageLevel : 0.0,
            "totalPairs", pairLevelRepository.count()
        );
    }

    /**
     * Broadcast XP update to pairing users via WebSocket
     */
    private void broadcastXPUpdate(PairLevel pairLevel, int xpGained, String reason, boolean leveledUp, int oldLevel) {
        try {
            Pairing pairing = pairLevel.getPairing();
            
            Map<String, Object> xpUpdate = Map.of(
                "eventType", leveledUp ? "LEVEL_UP" : "XP_GAINED",
                "pairingId", pairing.getId(),
                "xpGained", xpGained,
                "totalXP", pairLevel.getTotalXP(),
                "currentLevel", pairLevel.getCurrentLevel(),
                "currentLevelXP", pairLevel.getCurrentLevelXP(),
                "nextLevelXP", pairLevel.getNextLevelXP(),
                "reason", reason,
                "timestamp", LocalDateTime.now().toString()
            );

            if (leveledUp) {
                xpUpdate = Map.of(
                    "eventType", "LEVEL_UP",
                    "pairingId", pairing.getId(),
                    "oldLevel", oldLevel,
                    "newLevel", pairLevel.getCurrentLevel(),
                    "totalXP", pairLevel.getTotalXP(),
                    "xpGained", xpGained,
                    "reason", reason,
                    "timestamp", LocalDateTime.now().toString()
                );
            }

            // Send to both users
            messagingTemplate.convertAndSend("/user/" + pairing.getUser1Id() + "/topic/xp", xpUpdate);
            messagingTemplate.convertAndSend("/user/" + pairing.getUser2Id() + "/topic/xp", xpUpdate);

            log.info("Broadcasted XP update for pairing {}: {} XP gained, Level: {}", 
                    pairing.getId(), xpGained, pairLevel.getCurrentLevel());

        } catch (Exception e) {
            log.error("Failed to broadcast XP update for pairing {}: {}", 
                    pairLevel.getPairing().getId(), e.getMessage());
        }
    }

    /**
     * Reset pair level (admin function)
     */
    @Transactional
    public PairLevel resetPairLevel(Long pairingId) {
        PairLevel pairLevel = getOrCreatePairLevel(pairingId);
        
        pairLevel.setCurrentLevel(1);
        pairLevel.setTotalXP(0);
        pairLevel.setCurrentLevelXP(0);
        pairLevel.setNextLevelXP(100);
        
        PairLevel savedLevel = pairLevelRepository.save(pairLevel);
        log.info("Reset pair level for pairing {}", pairingId);
        
        return savedLevel;
    }
} 