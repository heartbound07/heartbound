package com.app.heartbound.config.security;

import com.app.heartbound.enums.RateLimitKeyType;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to configure endpoint-specific rate limiting.
 * Supports both user-based and IP-based rate limiting with configurable limits.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimited {
    
    /**
     * Maximum number of requests allowed per minute
     */
    int requestsPerMinute() default 60;
    
    /**
     * Maximum number of requests allowed per hour
     */
    int requestsPerHour() default 1000;
    
    /**
     * Type of key to use for rate limiting
     */
    RateLimitKeyType keyType() default RateLimitKeyType.IP;
    
    /**
     * Optional custom key prefix for this endpoint
     */
    String keyPrefix() default "";
    
    /**
     * Enable/disable this rate limit
     */
    boolean enabled() default true;
    
    /**
     * Burst capacity (initial tokens available)
     * If 0, defaults to requestsPerMinute * 1.2
     */
    int burstCapacity() default 0;
} 