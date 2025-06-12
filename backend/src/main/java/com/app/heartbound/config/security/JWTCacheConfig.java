package com.app.heartbound.config.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalListener;
import io.jsonwebtoken.Claims;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Centralized JWT Cache Configuration for optimizing JWT validation performance.
 * This class manages multiple caches to eliminate redundant token parsing and validation operations.
 */
@Configuration
@Component
@Getter
public class JWTCacheConfig {

    private static final Logger logger = LoggerFactory.getLogger(JWTCacheConfig.class);

    // Cache size configurations from properties
    @Value("${jwt.cache.token-validation.max-size:10000}")
    private long tokenValidationCacheMaxSize;

    @Value("${jwt.cache.token-validation.expire-after-write-minutes:10}")
    private long tokenValidationCacheExpireMinutes;

    @Value("${jwt.cache.claims.max-size:5000}")
    private long claimsCacheMaxSize;

    @Value("${jwt.cache.claims.expire-after-write-minutes:10}")
    private long claimsCacheExpireMinutes;

    @Value("${jwt.cache.user-details.max-size:5000}")
    private long userDetailsCacheMaxSize;

    @Value("${jwt.cache.user-details.expire-after-write-minutes:10}")
    private long userDetailsCacheExpireMinutes;

    // Cache instances
    private Cache<String, Boolean> tokenValidationCache;
    private Cache<String, Claims> claimsCache;
    private Cache<String, JWTUserDetails> userDetailsCache;

    @PostConstruct
    public void initializeCaches() {
        logger.info("Initializing JWT Performance Caches...");

        // Token Validation Cache - stores validation results
        this.tokenValidationCache = Caffeine.newBuilder()
                .maximumSize(tokenValidationCacheMaxSize)
                .expireAfterWrite(tokenValidationCacheExpireMinutes, TimeUnit.MINUTES)
                .removalListener((RemovalListener<String, Boolean>) (key, value, cause) -> {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Token validation cache entry removed: cause={}, key_hash={}", 
                                cause, key != null ? key.hashCode() : "null");
                    }
                })
                .recordStats()
                .build();

        // Claims Cache - stores parsed JWT claims to avoid re-parsing
        this.claimsCache = Caffeine.newBuilder()
                .maximumSize(claimsCacheMaxSize)
                .expireAfterWrite(claimsCacheExpireMinutes, TimeUnit.MINUTES)
                .removalListener((RemovalListener<String, Claims>) (key, value, cause) -> {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Claims cache entry removed: cause={}, key_hash={}", 
                                cause, key != null ? key.hashCode() : "null");
                    }
                })
                .recordStats()
                .build();

        // User Details Cache - stores extracted user information
        this.userDetailsCache = Caffeine.newBuilder()
                .maximumSize(userDetailsCacheMaxSize)
                .expireAfterWrite(userDetailsCacheExpireMinutes, TimeUnit.MINUTES)
                .removalListener((RemovalListener<String, JWTUserDetails>) (key, value, cause) -> {
                    if (logger.isDebugEnabled()) {
                        logger.debug("User details cache entry removed: cause={}, key_hash={}", 
                                cause, key != null ? key.hashCode() : "null");
                    }
                })
                .recordStats()
                .build();

        logger.info("JWT Performance Caches initialized successfully - " +
                "TokenValidation: {}/{} entries/minutes, " +
                "Claims: {}/{} entries/minutes, " +
                "UserDetails: {}/{} entries/minutes",
                tokenValidationCacheMaxSize, tokenValidationCacheExpireMinutes,
                claimsCacheMaxSize, claimsCacheExpireMinutes,
                userDetailsCacheMaxSize, userDetailsCacheExpireMinutes);
    }

    /**
     * Invalidates all JWT caches. Use with caution - only for scenarios like
     * security breaches, configuration changes, or administrative cache refresh.
     */
    public void invalidateAllCaches() {
        logger.warn("Invalidating ALL JWT caches - this will cause temporary performance degradation");
        tokenValidationCache.invalidateAll();
        claimsCache.invalidateAll();
        userDetailsCache.invalidateAll();
        logger.info("All JWT caches invalidated successfully");
    }

    /**
     * Invalidates a specific token from all caches.
     * Use this when a token is revoked, blacklisted, or manually invalidated.
     */
    public void invalidateToken(String token) {
        if (token != null && !token.trim().isEmpty()) {
            String tokenHash = generateTokenCacheKey(token);
            tokenValidationCache.invalidate(tokenHash);
            claimsCache.invalidate(tokenHash);
            userDetailsCache.invalidate(tokenHash);
            logger.debug("Token invalidated from all caches: key_hash={}", tokenHash.hashCode());
        }
    }

    /**
     * Generates a consistent cache key for a token.
     * Uses hash to avoid storing full tokens in memory and for security.
     */
    public String generateTokenCacheKey(String token) {
        if (token == null || token.trim().isEmpty()) {
            throw new IllegalArgumentException("Token cannot be null or empty");
        }
        // Use consistent hashing strategy for cache keys
        return String.valueOf(token.hashCode());
    }

    /**
     * Gets cache statistics for monitoring and debugging.
     */
    public CacheStats getCacheStats() {
        return CacheStats.builder()
                .tokenValidationHitRate(tokenValidationCache.stats().hitRate())
                .tokenValidationSize(tokenValidationCache.estimatedSize())
                .claimsHitRate(claimsCache.stats().hitRate())
                .claimsSize(claimsCache.estimatedSize())
                .userDetailsHitRate(userDetailsCache.stats().hitRate())
                .userDetailsSize(userDetailsCache.estimatedSize())
                .build();
    }

    /**
     * Performs manual cleanup of expired entries.
     * Normally Caffeine handles this automatically, but this can be called
     * during low-traffic periods for proactive memory management.
     */
    public void performMaintenance() {
        logger.debug("Performing manual JWT cache maintenance...");
        tokenValidationCache.cleanUp();
        claimsCache.cleanUp();
        userDetailsCache.cleanUp();
        logger.debug("JWT cache maintenance completed");
    }

    /**
     * Cache statistics data structure for monitoring.
     */
    @lombok.Builder
    @lombok.Data
    public static class CacheStats {
        private final double tokenValidationHitRate;
        private final long tokenValidationSize;
        private final double claimsHitRate;
        private final long claimsSize;
        private final double userDetailsHitRate;
        private final long userDetailsSize;
    }
} 