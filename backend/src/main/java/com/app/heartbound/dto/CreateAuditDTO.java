package com.app.heartbound.dto;

import com.app.heartbound.enums.AuditSeverity;
import com.app.heartbound.enums.AuditCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Data Transfer Object for creating new audit entries")
public class CreateAuditDTO {

    @Schema(description = "The user ID who performed the action", example = "1234567890", required = true)
    @NotBlank(message = "User ID is required")
    @Size(max = 50, message = "User ID cannot exceed 50 characters")
    private String userId;

    @Schema(description = "The action that was performed", example = "USER_UPDATED", required = true)
    @NotBlank(message = "Action is required")
    @Size(max = 100, message = "Action cannot exceed 100 characters")
    private String action;

    @Schema(description = "The type of entity affected", example = "User")
    @Size(max = 100, message = "Entity type cannot exceed 100 characters")
    private String entityType;

    @Schema(description = "The ID of the entity affected", example = "1234567890")
    @Size(max = 100, message = "Entity ID cannot exceed 100 characters")
    private String entityId;

    @Schema(description = "Human-readable description of the action", example = "User profile updated")
    @Size(max = 1000, message = "Description cannot exceed 1000 characters")
    private String description;

    @Schema(description = "The IP address from which the action was performed", example = "192.168.1.1")
    @Size(max = 45, message = "IP address cannot exceed 45 characters")
    private String ipAddress;

    @Schema(description = "The user agent string of the client", example = "Mozilla/5.0...")
    @Size(max = 500, message = "User agent cannot exceed 500 characters")
    private String userAgent;

    @Schema(description = "The session ID associated with the action", example = "sess_abc123")
    @Size(max = 100, message = "Session ID cannot exceed 100 characters")
    private String sessionId;

    @Schema(description = "Additional details about the action in JSON format")
    private String details;

    @Schema(description = "The severity level of the audit entry", example = "INFO")
    private AuditSeverity severity;

    @Schema(description = "The category of the audit entry", example = "USER_MANAGEMENT")
    private AuditCategory category;

    @Schema(description = "The source system or component that generated this audit entry", example = "WEB_API")
    @Size(max = 50, message = "Source cannot exceed 50 characters")
    private String source;
} 