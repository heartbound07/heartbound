package com.app.heartbound.dto;

import com.app.heartbound.enums.AuditSeverity;
import com.app.heartbound.enums.AuditCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Data Transfer Object that represents an audit entry.")
public class AuditDTO {

    @Schema(description = "The unique identifier of the audit entry", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID id;

    @Schema(description = "The timestamp when the audit entry was created", example = "2024-01-15T10:30:00")
    private LocalDateTime timestamp;

    @Schema(description = "The user ID who performed the action", example = "1234567890")
    private String userId;

    @Schema(description = "The action that was performed", example = "USER_UPDATED")
    private String action;

    @Schema(description = "The type of entity affected", example = "User")
    private String entityType;

    @Schema(description = "The ID of the entity affected", example = "1234567890")
    private String entityId;

    @Schema(description = "Human-readable description of the action", example = "User profile updated")
    private String description;

    @Schema(description = "The IP address from which the action was performed", example = "192.168.1.1")
    private String ipAddress;

    @Schema(description = "The user agent string of the client", example = "Mozilla/5.0...")
    private String userAgent;

    @Schema(description = "The session ID associated with the action", example = "sess_abc123")
    private String sessionId;

    @Schema(description = "Additional details about the action in JSON format")
    private String details;

    @Schema(description = "The severity level of the audit entry", example = "INFO")
    private AuditSeverity severity;

    @Schema(description = "The category of the audit entry", example = "USER_MANAGEMENT")
    private AuditCategory category;

    @Schema(description = "The source system or component that generated this audit entry", example = "WEB_API")
    private String source;
} 