package com.app.heartbound.enums;

/**
 * Enumeration for different types of rate limiting keys
 */
public enum RateLimitKeyType {
    /**
     * Rate limit based on client IP address
     */
    IP,
    
    /**
     * Rate limit based on authenticated user ID
     */
    USER,
    
    /**
     * Rate limit based on combination of user ID and IP
     */
    USER_IP
} 