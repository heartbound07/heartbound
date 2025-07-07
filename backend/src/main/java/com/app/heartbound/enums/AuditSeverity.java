package com.app.heartbound.enums;

/**
 * Audit severity levels for categorizing the importance of audit events.
 * Used to prioritize and filter audit entries based on their security impact.
 */
public enum AuditSeverity {
    /**
     * Low priority informational events
     */
    LOW,
    
    /**
     * General informational events (default)
     */
    INFO,
    
    /**
     * Warning events that may require attention
     */
    WARNING,
    
    /**
     * High priority events that require immediate attention
     */
    HIGH,
    
    /**
     * Critical security events requiring urgent action
     */
    CRITICAL
} 