package com.app.heartbound.services.pairing;

import com.app.heartbound.config.CacheConfig;
import com.app.heartbound.entities.PairLevel;
import com.app.heartbound.entities.Pairing;
import com.app.heartbound.repositories.pairing.PairLevelRepository;
import com.app.heartbound.repositories.pairing.PairingRepository;
import com.app.heartbound.dto.pairing.UpdatePairLevelDTO;
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
    private final CacheConfig cacheConfig;
    
    // Callback for Discord leaderboard refresh (set by PairingService to avoid circular dependency)
    private Consumer<Long> discordLeaderboardRefreshCallback;

    /**
     * Get or create pair level for a pairing with caching optimization
     */
    @Transactional
    public PairLevel getOrCreatePairLevel(Long pairingId) {
        // Check cache first
        PairLevel cachedLevel = (PairLevel) cacheConfig.getPairLevelCache().getIfPresent(pairingId);
        if (cachedLevel != null) {
            log.debug("Pair level cache HIT for pairingId: {}", pairingId);
            return cachedLevel;
        }

        log.debug("Pair level cache MISS for pairingId: {}", pairingId);
        
        Optional<Pairing> pairingOpt = pairingRepository.findById(pairingId);
        if (pairingOpt.isEmpty()) {
            throw new IllegalArgumentException("Pairing not found with ID: " + pairingId);
        }

        Pairing pairing = pairingOpt.get();
        PairLevel pairLevel = getOrCreatePairLevel(pairing);
        
        // Cache the result
        cacheConfig.getPairLevelCache().put(pairingId, pairLevel);
        log.debug("Cached pair level for pairingId: {}", pairingId);
        
        return pairLevel;
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
        
        // Invalidate cache after modification
        cacheConfig.invalidatePairingCaches(pairingId);
        
        // Broadcast XP gain notification
        broadcastXPUpdate(savedLevel, xpToAdd, reason, leveledUp, oldLevel);
        
        // 🚀 NEW: Refresh Discord leaderboard after XP gain
        if (discordLeaderboardRefreshCallback != null) {
            try {
                discordLeaderboardRefreshCallback.accept(pairingId);
            } catch (Exception e) {
                log.error("Failed to refresh Discord leaderboard for pairing {}: {}", pairingId, e.getMessage());
            }
        }
        
        log.info("Added {} XP to pairing {}: Total XP: {}, Level: {}, Reason: {}", 
                xpToAdd, pairingId, savedLevel.getTotalXP(), savedLevel.getCurrentLevel(), reason);

        return savedLevel;
    }

    /**
     * Remove XP from a pairing (for admin actions like achievement removal)
     */
    @Transactional
    public PairLevel removeXP(Long pairingId, int xpToRemove, String reason) {
        if (xpToRemove <= 0) {
            throw new IllegalArgumentException("XP to remove must be positive");
        }

        PairLevel pairLevel = getOrCreatePairLevel(pairingId);
        int oldLevel = pairLevel.getCurrentLevel();
        
        // Remove XP (ensure it doesn't go below 0)
        int newTotalXP = Math.max(0, pairLevel.getTotalXP() - xpToRemove);
        pairLevel.setTotalXP(newTotalXP);
        
        // Recalculate level data based on new total XP
        recalculateLevelData(pairLevel);

        PairLevel savedLevel = pairLevelRepository.save(pairLevel);
        
        // Invalidate cache after modification
        cacheConfig.invalidatePairingCaches(pairingId);
        
        // Broadcast XP removal notification
        broadcastXPUpdate(savedLevel, -xpToRemove, reason, savedLevel.getCurrentLevel() < oldLevel, oldLevel);
        
        // 🚀 NEW: Refresh Discord leaderboard after XP removal
        if (discordLeaderboardRefreshCallback != null) {
            try {
                discordLeaderboardRefreshCallback.accept(pairingId);
            } catch (Exception e) {
                log.error("Failed to refresh Discord leaderboard for pairing {}: {}", pairingId, e.getMessage());
            }
        }
        
        log.info("Removed {} XP from pairing {}: Total XP: {}, Level: {}, Reason: {}", 
                xpToRemove, pairingId, savedLevel.getTotalXP(), savedLevel.getCurrentLevel(), reason);

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
     * Admin: Update pair level and XP directly
     */
    @Transactional
    public PairLevel updatePairLevelAdmin(Long pairingId, UpdatePairLevelDTO updateRequest) {
        log.info("Admin updating pair level for pairing {}: {}", pairingId, updateRequest);
        
        // Get or create pair level
        PairLevel pairLevel = getOrCreatePairLevel(pairingId);
        
        // Track whether we need to recalculate based on XP or level
        boolean levelWasSetManually = false;
        boolean xpWasModified = false;
        
        // Apply updates
        if (updateRequest.getCurrentLevel() != null) {
            int newLevel = Math.max(1, updateRequest.getCurrentLevel());
            pairLevel.setCurrentLevel(newLevel);
            levelWasSetManually = true;
            
            // Calculate the minimum XP required for this level
            int xpForLevel = calculateMinimumXPForLevel(newLevel);
            pairLevel.setTotalXP(xpForLevel);
            
            log.info("Set level to {} with minimum XP {}", newLevel, xpForLevel);
        }
        
        if (updateRequest.getTotalXP() != null) {
            pairLevel.setTotalXP(Math.max(0, updateRequest.getTotalXP()));
            xpWasModified = true;
            log.info("Set total XP to {}", updateRequest.getTotalXP());
        }
        
        if (updateRequest.getXpIncrement() != null && updateRequest.getXpIncrement() != 0) {
            int newTotalXP = Math.max(0, pairLevel.getTotalXP() + updateRequest.getXpIncrement());
            pairLevel.setTotalXP(newTotalXP);
            xpWasModified = true;
            log.info("Applied XP increment of {}, new total: {}", updateRequest.getXpIncrement(), newTotalXP);
        }
        
        // Only recalculate level if XP was modified but level wasn't manually set
        if (xpWasModified && !levelWasSetManually) {
            recalculateLevelData(pairLevel);
        } else if (levelWasSetManually || xpWasModified) {
            // Just recalculate the level progression fields without changing the level
            recalculateLevelProgressFields(pairLevel);
        }
        
        // Invalidate cache after modification
        cacheConfig.invalidatePairingCaches(pairingId);
        
        // Save and return
        PairLevel savedLevel = pairLevelRepository.save(pairLevel);
        log.info("Admin updated pair level for pairing {}: Level {}, XP {}", 
                 pairingId, savedLevel.getCurrentLevel(), savedLevel.getTotalXP());
        
        return savedLevel;
    }

    /**
     * Recalculate level data based on total XP
     */
    private void recalculateLevelData(PairLevel pairLevel) {
        int totalXP = pairLevel.getTotalXP();
        int level = 1;
        int xpUsed = 0;
        
        // Calculate what level this XP should be
        while (true) {
            int xpForNextLevel = PairLevel.calculateNextLevelXP(level);
            if (xpUsed + xpForNextLevel > totalXP) {
                break;
            }
            xpUsed += xpForNextLevel;
            level++;
        }
        
        // Set the calculated values
        pairLevel.setCurrentLevel(level);
        pairLevel.setCurrentLevelXP(totalXP - xpUsed);
        pairLevel.setNextLevelXP(PairLevel.calculateNextLevelXP(level));
    }

    /**
     * Calculate minimum XP required for a specific level
     */
    private int calculateMinimumXPForLevel(int targetLevel) {
        int totalXP = 0;
        for (int level = 1; level < targetLevel; level++) {
            totalXP += PairLevel.calculateNextLevelXP(level);
        }
        return totalXP;
    }

    /**
     * Recalculate level progression fields without changing the current level
     */
    private void recalculateLevelProgressFields(PairLevel pairLevel) {
        int currentLevel = pairLevel.getCurrentLevel();
        int totalXP = pairLevel.getTotalXP();
        
        // Calculate XP used up to current level
        int xpUsedForCurrentLevel = calculateMinimumXPForLevel(currentLevel);
        
        // Calculate current level progress
        int currentLevelXP = totalXP - xpUsedForCurrentLevel;
        int nextLevelXP = PairLevel.calculateNextLevelXP(currentLevel);
        
        // Set the calculated values
        pairLevel.setCurrentLevelXP(Math.max(0, currentLevelXP));
        pairLevel.setNextLevelXP(nextLevelXP);
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
        
        // Note: Leaderboard refresh handled by PairingService when needed
        
        return savedLevel;
    }

    /**
     * Delete pair level data for a pairing
     */
    @Transactional
    public void deletePairLevel(Long pairingId) {
        log.info("Deleting pair level data for pairing ID: {}", pairingId);
        
        pairLevelRepository.findByPairingId(pairingId).ifPresent(pairLevel -> {
            pairLevelRepository.delete(pairLevel);
            log.info("Successfully deleted pair level data for pairing {}", pairingId);
        });
    }

    /**
     * Set the Discord leaderboard refresh callback (called by PairingService to avoid circular dependency)
     */
    public void setDiscordLeaderboardRefreshCallback(Consumer<Long> callback) {
        this.discordLeaderboardRefreshCallback = callback;
    }
} 