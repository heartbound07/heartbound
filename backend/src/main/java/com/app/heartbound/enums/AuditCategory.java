package com.app.heartbound.enums;

/**
 * Audit categories for organizing audit entries by functional area.
 * Used to group and filter audit logs by the type of system activity.
 */
public enum AuditCategory {
    /**
     * General system operations and events
     */
    SYSTEM,
    
    /**
     * User management operations (create, update, delete users)
     */
    USER_MANAGEMENT,
    
    /**
     * Authentication events (login, logout, token refresh)
     */
    AUTHENTICATION,
    
    /**
     * Authorization and permission checks
     */
    AUTHORIZATION,
    
    /**
     * Data access and modification events
     */
    DATA_ACCESS,
    
    /**
     * System configuration changes
     */
    CONFIGURATION,
    
    /**
     * Financial transactions and operations
     */
    FINANCIAL,
    
    /**
     * Security-related events and alerts
     */
    SECURITY
} 