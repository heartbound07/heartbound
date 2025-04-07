package com.app.heartbound.config;

import com.app.heartbound.config.security.RateLimitingFilter;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
public class RateLimitingConfig {

    private final RateLimitingFilter rateLimitingFilter;


    public RateLimitingConfig(RateLimitingFilter rateLimitingFilter) {
        this.rateLimitingFilter = rateLimitingFilter;
    }

    /**
     * Periodically cleans up rate limiting buckets to prevent memory leaks in very large applications.
     * For most applications, this is not necessary as the number of unique IP addresses will be manageable.
     */
    @Scheduled(fixedRateString = "${rate.limit.cleanup-interval-ms:3600000}")  // Default: once per hour
    public void cleanupBuckets() {
        rateLimitingFilter.cleanupBuckets();
    }
} 