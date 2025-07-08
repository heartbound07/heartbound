package com.app.heartbound.controllers;

import com.app.heartbound.config.security.RateLimited;
import com.app.heartbound.dto.AuditDTO;
import com.app.heartbound.dto.CreateAuditDTO;
import com.app.heartbound.enums.AuditSeverity;
import com.app.heartbound.enums.AuditCategory;
import com.app.heartbound.enums.RateLimitKeyType;
import com.app.heartbound.services.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/audit")
@Tag(name = "Audit Management", description = "API for managing audit logs")
public class AuditController {

    private static final Logger logger = LoggerFactory.getLogger(AuditController.class);
    
    private final AuditService auditService;
    
    @Autowired
    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }
    
    /**
     * Get paginated audit entries with optional filtering
     * 
     * @param page the page number (0-based)
     * @param size the page size
     * @param userId filter by user ID (optional)
     * @param action filter by action (optional)
     * @param entityType filter by entity type (optional)
     * @param severity filter by severity (optional)
     * @param category filter by category (optional)
     * @param startDate filter by start date (optional)
     * @param endDate filter by end date (optional)
     * @return paginated audit entries
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get audit entries", description = "Retrieve paginated audit entries with optional filtering")
    public ResponseEntity<Page<AuditDTO>> getAuditEntries(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) int size,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) AuditSeverity severity,
            @RequestParam(required = false) AuditCategory category,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            Authentication authentication) {
        
        logger.debug("Admin {} requesting audit entries - page: {}, size: {}", 
                    authentication.getName(), page, size);
        
        try {
            Page<AuditDTO> auditEntries = auditService.getAuditEntries(
                page, size, userId, action, entityType, severity, category, startDate, endDate);
            
            // Debug logging to understand the response structure
            logger.info("Audit entries response: totalElements={}, totalPages={}, size={}, number={}, first={}, last={}, contentSize={}", 
                        auditEntries.getTotalElements(), auditEntries.getTotalPages(), 
                        auditEntries.getSize(), auditEntries.getNumber(), 
                        auditEntries.isFirst(), auditEntries.isLast(), 
                        auditEntries.getContent().size());
            
            // Log first few entries to check sorting
            if (!auditEntries.getContent().isEmpty()) {
                logger.info("First entry timestamp: {}", auditEntries.getContent().get(0).getTimestamp());
                if (auditEntries.getContent().size() > 1) {
                    logger.info("Second entry timestamp: {}", auditEntries.getContent().get(1).getTimestamp());
                }
            }
            
            return ResponseEntity.ok(auditEntries);
        } catch (Exception e) {
            logger.error("Error retrieving audit entries for admin {}: {}", 
                        authentication.getName(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Get a single audit entry by ID
     * 
     * @param id the audit entry ID
     * @return the audit entry
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get audit entry by ID", description = "Retrieve a single audit entry by its ID")
    public ResponseEntity<AuditDTO> getAuditById(@PathVariable UUID id, Authentication authentication) {
        logger.debug("Admin {} requesting audit entry with ID: {}", authentication.getName(), id);
        
        try {
            AuditDTO auditEntry = auditService.getAuditById(id);
            return ResponseEntity.ok(auditEntry);
        } catch (Exception e) {
            logger.error("Error retrieving audit entry {} for admin {}: {}", 
                        id, authentication.getName(), e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }
    
    /**
     * Create a new audit entry
     * 
     * @param createAuditDTO the audit data to create
     * @return the created audit entry
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @RateLimited(
        requestsPerMinute = 30,
        requestsPerHour = 200,
        keyType = RateLimitKeyType.USER,
        keyPrefix = "audit_create"
    )
    @Operation(summary = "Create audit entry", description = "Create a new audit entry")
    public ResponseEntity<AuditDTO> createAuditEntry(
            @RequestBody @Valid CreateAuditDTO createAuditDTO,
            Authentication authentication) {
        
        logger.debug("Admin {} creating new audit entry for action: {}", 
                    authentication.getName(), createAuditDTO.getAction());
        
        try {
            AuditDTO createdAudit = auditService.createAuditEntry(createAuditDTO);
            return ResponseEntity.status(HttpStatus.CREATED).body(createdAudit);
        } catch (Exception e) {
            logger.error("Error creating audit entry for admin {}: {}", 
                        authentication.getName(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Get audit entries for a specific user
     * 
     * @param userId the user ID
     * @param page the page number (0-based)
     * @param size the page size
     * @return paginated audit entries for the user
     */
    @GetMapping("/user/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get audit entries by user", description = "Retrieve audit entries for a specific user")
    public ResponseEntity<Page<AuditDTO>> getAuditEntriesByUser(
            @PathVariable String userId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) int size,
            Authentication authentication) {
        
        logger.debug("Admin {} requesting audit entries for user: {}", 
                    authentication.getName(), userId);
        
        try {
            Page<AuditDTO> auditEntries = auditService.getAuditEntriesByUser(userId, page, size);
            return ResponseEntity.ok(auditEntries);
        } catch (Exception e) {
            logger.error("Error retrieving audit entries for user {} by admin {}: {}", 
                        userId, authentication.getName(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Get high severity audit entries
     * 
     * @param page the page number (0-based)
     * @param size the page size
     * @return paginated high severity audit entries
     */
    @GetMapping("/high-severity")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get high severity audit entries", description = "Retrieve recent high severity audit entries")
    public ResponseEntity<Page<AuditDTO>> getHighSeverityAuditEntries(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) int size,
            Authentication authentication) {
        
        logger.debug("Admin {} requesting high severity audit entries", authentication.getName());
        
        try {
            Page<AuditDTO> auditEntries = auditService.getHighSeverityAuditEntries(page, size);
            return ResponseEntity.ok(auditEntries);
        } catch (Exception e) {
            logger.error("Error retrieving high severity audit entries for admin {}: {}", 
                        authentication.getName(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Get audit statistics
     * 
     * @return audit statistics
     */
    @GetMapping("/statistics")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get audit statistics", description = "Retrieve audit entry statistics")
    public ResponseEntity<AuditService.AuditStatisticsDTO> getAuditStatistics(Authentication authentication) {
        logger.debug("Admin {} requesting audit statistics", authentication.getName());
        
        try {
            AuditService.AuditStatisticsDTO statistics = auditService.getAuditStatistics();
            return ResponseEntity.ok(statistics);
        } catch (Exception e) {
            logger.error("Error retrieving audit statistics for admin {}: {}", 
                        authentication.getName(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Delete old audit entries for data retention
     * 
     * @param request map containing the cutoff date
     * @return number of deleted entries
     */
    @DeleteMapping("/cleanup")
    @PreAuthorize("hasRole('ADMIN')")
    @RateLimited(
        requestsPerMinute = 2,
        requestsPerHour = 10,
        keyType = RateLimitKeyType.USER,
        keyPrefix = "audit_cleanup"
    )
    @Operation(summary = "Cleanup old audit entries", description = "Delete audit entries older than specified date")
    public ResponseEntity<Map<String, Object>> cleanupOldAuditEntries(
            @RequestBody Map<String, String> request,
            Authentication authentication) {
        
        logger.info("Admin {} requesting cleanup of old audit entries", authentication.getName());
        
        try {
            String cutoffDateStr = request.get("cutoffDate");
            if (cutoffDateStr == null || cutoffDateStr.trim().isEmpty()) {
                return ResponseEntity.badRequest().build();
            }
            
            LocalDateTime cutoffDate = LocalDateTime.parse(cutoffDateStr);
            
            // Safety check: don't allow deletion of entries newer than 30 days
            LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
            if (cutoffDate.isAfter(thirtyDaysAgo)) {
                logger.warn("Admin {} attempted to delete audit entries newer than 30 days. Request denied.", 
                           authentication.getName());
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Cannot delete audit entries newer than 30 days"));
            }
            
            int deletedCount = auditService.deleteOldAuditEntries(cutoffDate);
            
            logger.info("Admin {} deleted {} old audit entries", authentication.getName(), deletedCount);
            
            return ResponseEntity.ok(Map.of(
                "message", "Successfully deleted old audit entries",
                "deletedCount", deletedCount,
                "cutoffDate", cutoffDate.toString()
            ));
            
        } catch (Exception e) {
            logger.error("Error cleaning up audit entries for admin {}: {}", 
                        authentication.getName(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to cleanup audit entries"));
        }
    }
} 