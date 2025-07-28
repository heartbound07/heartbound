package com.app.heartbound.config;

import com.app.heartbound.config.security.RateLimitingFilter;
import com.app.heartbound.services.RateLimitingService;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
@EnableAsync(proxyTargetClass = true)
public class RateLimitingConfig {

    private final RateLimitingFilter rateLimitingFilter;
    private final RateLimitingService rateLimitingService;

    public RateLimitingConfig(RateLimitingFilter rateLimitingFilter, RateLimitingService rateLimitingService) {
        this.rateLimitingFilter = rateLimitingFilter;
        this.rateLimitingService = rateLimitingService;
    }

    /**
     * Periodically cleans up rate limiting buckets to prevent memory leaks in very large applications.
     * For most applications, this is not necessary as the number of unique IP addresses will be manageable.
     */
    @Scheduled(fixedRateString = "${rate.limit.cleanup-interval-ms:3600000}")  // Default: once per hour
    public void cleanupBuckets() {
        rateLimitingFilter.cleanupBuckets();
        rateLimitingService.cleanupCaches();
    }
    
    /**
     * Periodically clear rate limiting metrics to prevent memory accumulation
     */
    @Scheduled(fixedRateString = "${rate.limit.metrics-cleanup-interval-ms:7200000}")  // Default: every 2 hours
    public void clearMetrics() {
        rateLimitingService.clearMetrics();
    }
} 