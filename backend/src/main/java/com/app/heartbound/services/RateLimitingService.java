package com.app.heartbound.services;

import com.app.heartbound.config.security.RateLimited;
import com.app.heartbound.enums.RateLimitKeyType;
import com.app.heartbound.exceptions.RateLimitExceededException;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.Refill;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateLimitingService {
    
    private static final Logger logger = LoggerFactory.getLogger(RateLimitingService.class);
    
    // Separate caches for different rate limit types
    private final Cache<String, Bucket> userMinuteBuckets;
    private final Cache<String, Bucket> userHourBuckets;
    private final Cache<String, Bucket> ipMinuteBuckets;
    private final Cache<String, Bucket> ipHourBuckets;
    
    // Metrics tracking
    private final ConcurrentHashMap<String, Long> rateLimitHits = new ConcurrentHashMap<>();
    
    public RateLimitingService() {
        // Configure caches with appropriate expiration times
        this.userMinuteBuckets = Caffeine.newBuilder()
                .maximumSize(50000)
                .expireAfterAccess(Duration.ofMinutes(5))
                .build();
                
        this.userHourBuckets = Caffeine.newBuilder()
                .maximumSize(50000)
                .expireAfterAccess(Duration.ofHours(2))
                .build();
                
        this.ipMinuteBuckets = Caffeine.newBuilder()
                .maximumSize(100000)
                .expireAfterAccess(Duration.ofMinutes(5))
                .build();
                
        this.ipHourBuckets = Caffeine.newBuilder()
                .maximumSize(100000)
                .expireAfterAccess(Duration.ofHours(2))
                .build();
    }
    
    /**
     * Check rate limits for an endpoint with custom configuration
     */
    public void checkRateLimit(RateLimited rateLimitConfig, HttpServletRequest request) {
        if (!rateLimitConfig.enabled()) {
            return;
        }
        
        String limitKey = generateRateLimitKey(rateLimitConfig, request);
        
        // Check minute-based limit
        checkSpecificLimit(
            limitKey + ":minute", 
            rateLimitConfig.requestsPerMinute(),
            rateLimitConfig.burstCapacity() > 0 ? rateLimitConfig.burstCapacity() : (int)(rateLimitConfig.requestsPerMinute() * 1.2),
            Duration.ofMinutes(1),
            getMinuteBucketCache(rateLimitConfig.keyType()),
            "per minute"
        );
        
        // Check hour-based limit
        checkSpecificLimit(
            limitKey + ":hour", 
            rateLimitConfig.requestsPerHour(),
            Math.max(rateLimitConfig.requestsPerHour(), rateLimitConfig.requestsPerMinute() * 2),
            Duration.ofHours(1),
            getHourBucketCache(rateLimitConfig.keyType()),
            "per hour"
        );
        
        logger.debug("Rate limit check passed for key: {}", limitKey);
    }
    
    /**
     * Generate a rate limiting key based on the configuration
     */
    private String generateRateLimitKey(RateLimited rateLimitConfig, HttpServletRequest request) {
        String prefix = rateLimitConfig.keyPrefix().isEmpty() ? "endpoint" : rateLimitConfig.keyPrefix();
        
        switch (rateLimitConfig.keyType()) {
            case USER:
                String userId = getCurrentUserId();
                if (userId == null) {
                    // Fall back to IP if user is not authenticated
                    logger.debug("User not authenticated, falling back to IP-based rate limiting");
                    return prefix + ":ip:" + getClientIpAddress(request);
                }
                return prefix + ":user:" + userId;
                
            case USER_IP:
                String userIdForCombo = getCurrentUserId();
                String clientIp = getClientIpAddress(request);
                if (userIdForCombo == null) {
                    return prefix + ":ip:" + clientIp;
                }
                return prefix + ":user_ip:" + userIdForCombo + ":" + clientIp;
                
            case IP:
            default:
                return prefix + ":ip:" + getClientIpAddress(request);
        }
    }
    
    /**
     * Check a specific rate limit with given parameters
     */
    private void checkSpecificLimit(String key, int maxRequests, int burstCapacity, Duration refillPeriod, 
                                  Cache<String, Bucket> cache, String limitType) {
        
        Bucket bucket = cache.get(key, k -> createBucket(maxRequests, burstCapacity, refillPeriod));
        
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        
        if (!probe.isConsumed()) {
            // Record rate limit hit for monitoring
            rateLimitHits.merge(key, 1L, Long::sum);
            
            long waitSeconds = probe.getNanosToWaitForRefill() / 1_000_000_000;
            String message = String.format("Rate limit exceeded: %d requests %s. Please try again in %d seconds.", 
                                         maxRequests, limitType, waitSeconds);
            
            logger.warn("Rate limit exceeded for key: {} ({})", key, limitType);
            throw new RateLimitExceededException(message);
        }
        
        logger.debug("Rate limit check passed for key: {} ({}). Remaining: {}", 
                   key, limitType, probe.getRemainingTokens());
    }
    
    /**
     * Create a bucket with specified parameters
     */
    private Bucket createBucket(int maxRequests, int burstCapacity, Duration refillPeriod) {
        Bandwidth limit = Bandwidth.classic(burstCapacity, Refill.intervally(maxRequests, refillPeriod));
        return Bucket.builder().addLimit(limit).build();
    }
    
    /**
     * Get current authenticated user ID
     */
    private String getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && 
            !"anonymousUser".equals(authentication.getPrincipal())) {
            return authentication.getName();
        }
        return null;
    }
    
    /**
     * Extract client IP address from request
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
    
    /**
     * Get appropriate minute bucket cache based on key type
     */
    private Cache<String, Bucket> getMinuteBucketCache(RateLimitKeyType keyType) {
        return keyType == RateLimitKeyType.USER || keyType == RateLimitKeyType.USER_IP ? 
               userMinuteBuckets : ipMinuteBuckets;
    }
    
    /**
     * Get appropriate hour bucket cache based on key type
     */
    private Cache<String, Bucket> getHourBucketCache(RateLimitKeyType keyType) {
        return keyType == RateLimitKeyType.USER || keyType == RateLimitKeyType.USER_IP ? 
               userHourBuckets : ipHourBuckets;
    }
    
    /**
     * Clean up all rate limiting caches
     */
    public void cleanupCaches() {
        userMinuteBuckets.cleanUp();
        userHourBuckets.cleanUp();
        ipMinuteBuckets.cleanUp();
        ipHourBuckets.cleanUp();
        logger.info("Cleaned up rate limiting caches");
    }
    
    /**
     * Get rate limiting metrics for monitoring
     */
    public ConcurrentHashMap<String, Long> getRateLimitHits() {
        return new ConcurrentHashMap<>(rateLimitHits);
    }
    
    /**
     * Clear rate limiting metrics
     */
    public void clearMetrics() {
        rateLimitHits.clear();
        logger.info("Cleared rate limiting metrics");
    }
} 