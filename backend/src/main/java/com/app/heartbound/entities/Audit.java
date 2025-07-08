package com.app.heartbound.entities;

import com.app.heartbound.enums.AuditSeverity;
import com.app.heartbound.enums.AuditCategory;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Audit Entity
 * 
 * Tracks all system activities for security auditing and compliance.
 * Provides complete audit trail for administrative actions and system events.
 */
@Data
@Entity
@Table(name = "audits", indexes = {
    @Index(name = "idx_audit_user_id", columnList = "user_id"),
    @Index(name = "idx_audit_timestamp", columnList = "timestamp"),
    @Index(name = "idx_audit_action", columnList = "action"),
    @Index(name = "idx_audit_entity_type", columnList = "entity_type"),
    @Index(name = "idx_audit_entity_id", columnList = "entity_id")
})
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Audit {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @CreationTimestamp
    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;
    
    @Column(name = "user_id", nullable = false, columnDefinition = "TEXT")
    private String userId;
    
    @Column(name = "action", nullable = false, columnDefinition = "TEXT")
    private String action;
    
    @Column(name = "entity_type", columnDefinition = "TEXT")
    private String entityType;
    
    @Column(name = "entity_id", columnDefinition = "TEXT")
    private String entityId;
    
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    @Column(name = "ip_address", columnDefinition = "TEXT")
    private String ipAddress;
    
    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;
    
    @Column(name = "session_id", columnDefinition = "TEXT")
    private String sessionId;
    
    @Column(name = "details", columnDefinition = "TEXT")
    private String details;
    
    @Column(name = "severity", length = 20)
    @Enumerated(EnumType.STRING)
    private AuditSeverity severity = AuditSeverity.INFO;
    
    @Column(name = "category", length = 50)
    @Enumerated(EnumType.STRING)
    private AuditCategory category = AuditCategory.SYSTEM;
    
    @Column(name = "source", columnDefinition = "TEXT")
    private String source;
} 