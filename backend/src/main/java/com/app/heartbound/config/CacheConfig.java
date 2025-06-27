package com.app.heartbound.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalListener;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Centralized Cache Configuration for Pairing System Performance Optimization.
 * 
 * This configuration manages caches for:
 * - Pair Level/XP data (frequently accessed)
 * - Achievement data (rarely changes)
 * - Voice streak statistics (calculated data)
 * - User profile information (external API data)
 * - Batch operation results
 */
@Configuration
@Component
@Getter
@Slf4j
public class CacheConfig {

    // Pair Level Cache Configuration
    @Value("${cache.pair-level.max-size:5000}")
    private long pairLevelCacheMaxSize;

    @Value("${cache.pair-level.expire-after-write-minutes:15}")
    private long pairLevelCacheExpireMinutes;

    // Achievement Cache Configuration  
    @Value("${cache.achievement.max-size:1000}")
    private long achievementCacheMaxSize;

    @Value("${cache.achievement.expire-after-write-minutes:60}")
    private long achievementCacheExpireMinutes;

    // Voice Streak Cache Configuration
    @Value("${cache.voice-streak.max-size:2000}")
    private long voiceStreakCacheMaxSize;

    @Value("${cache.voice-streak.expire-after-write-minutes:10}")
    private long voiceStreakCacheExpireMinutes;

    // User Profile Cache Configuration
    @Value("${cache.user-profile.max-size:10000}")
    private long userProfileCacheMaxSize;

    @Value("${cache.user-profile.expire-after-write-minutes:30}")
    private long userProfileCacheExpireMinutes;

    // Batch Operation Cache Configuration
    @Value("${cache.batch-operations.max-size:100}")
    private long batchOperationsCacheMaxSize;

    @Value("${cache.batch-operations.expire-after-write-minutes:5}")
    private long batchOperationsCacheExpireMinutes;

    // Daily Message Activity Cache Configuration
    @Value("${cache.daily-activity.max-size:5000}")
    private long dailyActivityCacheMaxSize;

    @Value("${cache.daily-activity.expire-after-write-minutes:30}")
    private long dailyActivityCacheExpireMinutes;

    // Daily Claim Cache Configuration
    @Value("${cache.daily-claim.max-size:10000}")
    private long dailyClaimCacheMaxSize;

    @Value("${cache.daily-claim.expire-after-write-minutes:60}")
    private long dailyClaimCacheExpireMinutes;

    // Discord Bot Settings Cache Configuration
    @Value("${cache.discord-bot-settings.max-size:1}")
    private long discordBotSettingsCacheMaxSize;

    @Value("${cache.discord-bot-settings.expire-after-write-minutes:30}")
    private long discordBotSettingsCacheExpireMinutes;

    // Prison Cache Configuration
    @Value("${cache.prison.max-size:1000}")
    private long prisonCacheMaxSize;

    @Value("${cache.prison.expire-after-write-hours:72}")
    private long prisonCacheExpireHours;

    // Cache instances
    private Cache<Long, Object> pairLevelCache;
    private Cache<String, List<Object>> achievementListCache;
    private Cache<Long, Object> achievementCache;
    private Cache<Long, Object> voiceStreakStatsCache;
    private Cache<String, List<Object>> voiceStreakListCache;
    private Cache<String, Object> userProfileCache;
    private Cache<String, Map<String, Object>> batchOperationsCache;
    private Cache<String, List<Object>> dailyMessageActivityCache;
    private Cache<String, Object> dailyClaimCache;
    private Cache<Long, Object> discordBotSettingsCache;
    private Cache<String, List<String>> prisonCache;

    @PostConstruct
    public void initializeCaches() {
        log.info("Initializing Pairing System Performance Caches...");

        // Pair Level Cache - stores PairLevel entities and calculations
        this.pairLevelCache = Caffeine.newBuilder()
                .maximumSize(pairLevelCacheMaxSize)
                .expireAfterWrite(pairLevelCacheExpireMinutes, TimeUnit.MINUTES)
                .removalListener((RemovalListener<Long, Object>) (key, value, cause) -> {
                    if (log.isDebugEnabled()) {
                        log.debug("Pair level cache entry removed: pairingId={}, cause={}", key, cause);
                    }
                })
                .recordStats()
                .build();

        // Achievement List Cache - stores lists of achievements per pairing
        this.achievementListCache = Caffeine.newBuilder()
                .maximumSize(achievementCacheMaxSize)
                .expireAfterWrite(achievementCacheExpireMinutes, TimeUnit.MINUTES)
                .removalListener((RemovalListener<String, List<Object>>) (key, value, cause) -> {
                    if (log.isDebugEnabled()) {
                        log.debug("Achievement list cache entry removed: key={}, cause={}", key, cause);
                    }
                })
                .recordStats()
                .build();

        // Individual Achievement Cache - stores single achievement data
        this.achievementCache = Caffeine.newBuilder()
                .maximumSize(achievementCacheMaxSize)
                .expireAfterWrite(achievementCacheExpireMinutes, TimeUnit.MINUTES)
                .removalListener((RemovalListener<Long, Object>) (key, value, cause) -> {
                    if (log.isDebugEnabled()) {
                        log.debug("Achievement cache entry removed: achievementId={}, cause={}", key, cause);
                    }
                })
                .recordStats()
                .build();

        // Voice Streak Statistics Cache - stores calculated streak data
        this.voiceStreakStatsCache = Caffeine.newBuilder()
                .maximumSize(voiceStreakCacheMaxSize)
                .expireAfterWrite(voiceStreakCacheExpireMinutes, TimeUnit.MINUTES)
                .removalListener((RemovalListener<Long, Object>) (key, value, cause) -> {
                    if (log.isDebugEnabled()) {
                        log.debug("Voice streak stats cache entry removed: pairingId={}, cause={}", key, cause);
                    }
                })
                .recordStats()
                .build();

        // Voice Streak List Cache - stores lists of voice streaks
        this.voiceStreakListCache = Caffeine.newBuilder()
                .maximumSize(voiceStreakCacheMaxSize)
                .expireAfterWrite(voiceStreakCacheExpireMinutes, TimeUnit.MINUTES)
                .removalListener((RemovalListener<String, List<Object>>) (key, value, cause) -> {
                    if (log.isDebugEnabled()) {
                        log.debug("Voice streak list cache entry removed: key={}, cause={}", key, cause);
                    }
                })
                .recordStats()
                .build();

        // User Profile Cache - stores user profile data
        this.userProfileCache = Caffeine.newBuilder()
                .maximumSize(userProfileCacheMaxSize)
                .expireAfterWrite(userProfileCacheExpireMinutes, TimeUnit.MINUTES)
                .removalListener((RemovalListener<String, Object>) (key, value, cause) -> {
                    if (log.isDebugEnabled()) {
                        log.debug("User profile cache entry removed: userId={}, cause={}", key, cause);
                    }
                })
                .recordStats()
                .build();

        // Batch Operations Cache - stores results of batch operations
        this.batchOperationsCache = Caffeine.newBuilder()
                .maximumSize(batchOperationsCacheMaxSize)
                .expireAfterWrite(batchOperationsCacheExpireMinutes, TimeUnit.MINUTES)
                .removalListener((RemovalListener<String, Map<String, Object>>) (key, value, cause) -> {
                    if (log.isDebugEnabled()) {
                        log.debug("Batch operations cache entry removed: key={}, cause={}", key, cause);
                    }
                })
                .recordStats()
                .build();

        // Daily Message Activity Cache - stores daily activity statistics
        this.dailyMessageActivityCache = Caffeine.newBuilder()
                .maximumSize(dailyActivityCacheMaxSize)
                .expireAfterWrite(dailyActivityCacheExpireMinutes, TimeUnit.MINUTES)
                .removalListener((RemovalListener<String, List<Object>>) (key, value, cause) -> {
                    if (log.isDebugEnabled()) {
                        log.debug("Daily message activity cache entry removed: key={}, cause={}", key, cause);
                    }
                })
                .recordStats()
                .build();

        // Daily Claim Cache - stores user daily claim status
        this.dailyClaimCache = Caffeine.newBuilder()
                .maximumSize(dailyClaimCacheMaxSize)
                .expireAfterWrite(dailyClaimCacheExpireMinutes, TimeUnit.MINUTES)
                .removalListener((RemovalListener<String, Object>) (key, value, cause) -> {
                    if (log.isDebugEnabled()) {
                        log.debug("Daily claim cache entry removed: userId={}, cause={}", key, cause);
                    }
                })
                .recordStats()
                .build();

        // Discord Bot Settings Cache - stores Discord bot settings
        this.discordBotSettingsCache = Caffeine.newBuilder()
                .maximumSize(discordBotSettingsCacheMaxSize)
                .expireAfterWrite(discordBotSettingsCacheExpireMinutes, TimeUnit.MINUTES)
                .removalListener((RemovalListener<Long, Object>) (key, value, cause) -> {
                    if (log.isDebugEnabled()) {
                        log.debug("Discord bot settings cache entry removed: key={}, cause={}", key, cause);
                    }
                })
                .recordStats()
                .build();

        // Prison Cache - stores user role data for prison system
        this.prisonCache = Caffeine.newBuilder()
                .maximumSize(prisonCacheMaxSize)
                .expireAfterWrite(prisonCacheExpireHours, TimeUnit.HOURS)
                .removalListener((RemovalListener<String, List<String>>) (key, value, cause) -> {
                    if (log.isDebugEnabled()) {
                        log.debug("Prison cache entry removed: userId={}, cause={}", key, cause);
                    }
                })
                .recordStats()
                .build();

        log.info("Pairing System Performance Caches initialized successfully - " +
                "PairLevel: {}/{} entries/minutes, " +
                "Achievement: {}/{} entries/minutes, " +
                "VoiceStreak: {}/{} entries/minutes, " +
                "UserProfile: {}/{} entries/minutes, " +
                "BatchOps: {}/{} entries/minutes, " +
                "DailyActivity: {}/{} entries/minutes, " +
                "DailyClaim: {}/{} entries/minutes, " +
                "DiscordBotSettings: {}/{} entries/minutes, " +
                "Prison: {}/{} entries/hours",
                pairLevelCacheMaxSize, pairLevelCacheExpireMinutes,
                achievementCacheMaxSize, achievementCacheExpireMinutes,
                voiceStreakCacheMaxSize, voiceStreakCacheExpireMinutes,
                userProfileCacheMaxSize, userProfileCacheExpireMinutes,
                batchOperationsCacheMaxSize, batchOperationsCacheExpireMinutes,
                dailyActivityCacheMaxSize, dailyActivityCacheExpireMinutes,
                dailyClaimCacheMaxSize, dailyClaimCacheExpireMinutes,
                discordBotSettingsCacheMaxSize, discordBotSettingsCacheExpireMinutes,
                prisonCacheMaxSize, prisonCacheExpireHours);
    }

    /**
     * Invalidates all pairing-related caches for a specific pairing.
     * Use when pairing data is modified.
     */
    public void invalidatePairingCaches(Long pairingId) {
        if (pairingId != null) {
            pairLevelCache.invalidate(pairingId);
            voiceStreakStatsCache.invalidate(pairingId);
            
            // Invalidate achievement lists for this pairing
            achievementListCache.invalidate("pairing_" + pairingId + "_unlocked");
            achievementListCache.invalidate("pairing_" + pairingId + "_available");
            
            // Invalidate voice streak lists for this pairing
            voiceStreakListCache.invalidate("pairing_" + pairingId + "_streaks");
            voiceStreakListCache.invalidate("pairing_" + pairingId + "_recent");
            
            log.debug("Invalidated all caches for pairingId: {}", pairingId);
        }
    }

    /**
     * Invalidates user profile cache for a specific user.
     * Use when user data is updated.
     */
    public void invalidateUserProfileCache(String userId) {
        if (userId != null) {
            userProfileCache.invalidate(userId);
            log.debug("Invalidated user profile cache for userId: {}", userId);
        }
    }

    /**
     * Invalidates batch operation caches.
     * Use when batch operations need to be re-calculated.
     */
    public void invalidateBatchCaches() {
        batchOperationsCache.invalidateAll();
        log.debug("Invalidated all batch operation caches");
    }

    /**
     * Invalidates daily message activity cache for a specific user.
     * Use when a user's daily message statistics are updated.
     */
    public void invalidateDailyMessageActivityCache(String userId) {
        if (userId != null) {
            dailyMessageActivityCache.invalidate("activity_stats_" + userId);
            log.debug("Daily message activity cache invalidated for user: {}", userId);
        }
    }

    /**
     * Invalidates daily voice activity cache for a specific user.
     * Use when a user's daily voice statistics are updated.
     */
    public void invalidateDailyVoiceActivityCache(String userId) {
        if (userId != null) {
            dailyMessageActivityCache.invalidate("voice_activity_stats_" + userId);
            log.debug("Daily voice activity cache invalidated for user: {}", userId);
        }
    }

    /**
     * Invalidates daily claim cache for a specific user.
     * Use when a user's daily claim status is updated.
     */
    public void invalidateDailyClaimCache(String userId) {
        if (userId != null) {
            dailyClaimCache.invalidate(userId);
            log.debug("Daily claim cache invalidated for user: {}", userId);
        }
    }

    /**
     * Invalidates Discord bot settings cache.
     * Use when Discord bot settings are updated.
     */
    public void invalidateDiscordBotSettingsCache() {
        discordBotSettingsCache.invalidateAll();
        log.debug("Discord bot settings cache invalidated");
    }

    /**
     * Invalidates prison cache for a specific user.
     * Use when a user's prison status is updated externally.
     */
    public void invalidatePrisonCache(String userId) {
        if (userId != null) {
            prisonCache.invalidate(userId);
            log.debug("Prison cache invalidated for user: {}", userId);
        }
    }

    /**
     * Invalidates all caches. Use with caution - only for scenarios like
     * system maintenance or emergency cache refresh.
     */
    public void invalidateAllCaches() {
        log.warn("Invalidating ALL pairing system caches - this will cause temporary performance degradation");
        pairLevelCache.invalidateAll();
        achievementListCache.invalidateAll();
        achievementCache.invalidateAll();
        voiceStreakStatsCache.invalidateAll();
        voiceStreakListCache.invalidateAll();
        userProfileCache.invalidateAll();
        batchOperationsCache.invalidateAll();
        dailyMessageActivityCache.invalidateAll();
        dailyClaimCache.invalidateAll();
        discordBotSettingsCache.invalidateAll();
        prisonCache.invalidateAll();
        log.info("All pairing system caches invalidated successfully");
    }

    /**
     * Gets cache statistics for monitoring and debugging.
     */
    public CacheStats getCacheStats() {
        return CacheStats.builder()
                .pairLevelHitRate(pairLevelCache.stats().hitRate())
                .pairLevelSize(pairLevelCache.estimatedSize())
                .achievementListHitRate(achievementListCache.stats().hitRate())
                .achievementListSize(achievementListCache.estimatedSize())
                .achievementHitRate(achievementCache.stats().hitRate())
                .achievementSize(achievementCache.estimatedSize())
                .voiceStreakStatsHitRate(voiceStreakStatsCache.stats().hitRate())
                .voiceStreakStatsSize(voiceStreakStatsCache.estimatedSize())
                .voiceStreakListHitRate(voiceStreakListCache.stats().hitRate())
                .voiceStreakListSize(voiceStreakListCache.estimatedSize())
                .userProfileHitRate(userProfileCache.stats().hitRate())
                .userProfileSize(userProfileCache.estimatedSize())
                .batchOperationsHitRate(batchOperationsCache.stats().hitRate())
                .batchOperationsSize(batchOperationsCache.estimatedSize())
                .dailyMessageActivityHitRate(dailyMessageActivityCache.stats().hitRate())
                .dailyMessageActivitySize(dailyMessageActivityCache.estimatedSize())
                .dailyClaimHitRate(dailyClaimCache.stats().hitRate())
                .dailyClaimSize(dailyClaimCache.estimatedSize())
                .discordBotSettingsHitRate(discordBotSettingsCache.stats().hitRate())
                .discordBotSettingsSize(discordBotSettingsCache.estimatedSize())
                .prisonHitRate(prisonCache.stats().hitRate())
                .prisonSize(prisonCache.estimatedSize())
                .build();
    }

    /**
     * Performs manual cleanup of expired entries.
     * Can be called during low-traffic periods for proactive memory management.
     */
    public void performMaintenance() {
        log.debug("Performing manual pairing cache maintenance...");
        pairLevelCache.cleanUp();
        achievementListCache.cleanUp();
        achievementCache.cleanUp();
        voiceStreakStatsCache.cleanUp();
        voiceStreakListCache.cleanUp();
        userProfileCache.cleanUp();
        batchOperationsCache.cleanUp();
        dailyMessageActivityCache.cleanUp();
        dailyClaimCache.cleanUp();
        discordBotSettingsCache.cleanUp();
        prisonCache.cleanUp();
        log.debug("Pairing cache maintenance completed");
    }

    /**
     * Cache statistics data structure for monitoring.
     */
    @lombok.Builder
    @lombok.Data
    public static class CacheStats {
        private final double pairLevelHitRate;
        private final long pairLevelSize;
        private final double achievementListHitRate;
        private final long achievementListSize;
        private final double achievementHitRate;
        private final long achievementSize;
        private final double voiceStreakStatsHitRate;
        private final long voiceStreakStatsSize;
        private final double voiceStreakListHitRate;
        private final long voiceStreakListSize;
        private final double userProfileHitRate;
        private final long userProfileSize;
        private final double batchOperationsHitRate;
        private final long batchOperationsSize;
        private final double dailyMessageActivityHitRate;
        private final long dailyMessageActivitySize;
        private final double dailyClaimHitRate;
        private final long dailyClaimSize;
        private final double discordBotSettingsHitRate;
        private final long discordBotSettingsSize;
        private final double prisonHitRate;
        private final long prisonSize;
    }
} 