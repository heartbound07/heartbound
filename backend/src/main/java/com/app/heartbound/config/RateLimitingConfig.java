package com.app.heartbound.config;

import com.app.heartbound.config.security.RateLimitingFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
public class RateLimitingConfig {

    private final RateLimitingFilter rateLimitingFilter;

    @Autowired
    public RateLimitingConfig(RateLimitingFilter rateLimitingFilter) {
        this.rateLimitingFilter = rateLimitingFilter;
    }

    /**
     * Periodically cleans up the rate limiting request tracker
     * to prevent memory leaks from IPs that no longer make requests
     */
    @Scheduled(fixedRateString = "${rate.limit.cleanup-interval-ms:300000}")
    public void cleanupRateLimitTracker() {
        rateLimitingFilter.cleanupRequestTracker();
    }
} 