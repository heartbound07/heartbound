package com.app.heartbound.services;

import com.app.heartbound.dto.AuditDTO;
import com.app.heartbound.dto.CreateAuditDTO;
import com.app.heartbound.entities.Audit;
import com.app.heartbound.enums.AuditSeverity;
import com.app.heartbound.enums.AuditCategory;
import com.app.heartbound.repositories.AuditRepository;
import com.app.heartbound.exceptions.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AuditService {

    private static final Logger logger = LoggerFactory.getLogger(AuditService.class);
    
    private final AuditRepository auditRepository;
    
    @Autowired
    public AuditService(AuditRepository auditRepository) {
        this.auditRepository = auditRepository;
    }
    
    /**
     * Creates a new audit entry
     * 
     * @param createAuditDTO the audit data to create
     * @return the created audit entry as DTO
     */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public AuditDTO createAuditEntry(CreateAuditDTO createAuditDTO) {
        logger.debug("Creating new audit entry for user: {}, action: {}", 
            createAuditDTO.getUserId(), createAuditDTO.getAction());
        
        return createAuditEntryInternal(createAuditDTO);
    }
    
    /**
     * Creates a new audit entry for internal system operations (Discord bot, scheduled tasks, etc.)
     * This method bypasses security requirements and should only be used by trusted internal services.
     * 
     * @param createAuditDTO the audit data to create
     * @return the created audit entry as DTO
     */
    @Transactional
    public AuditDTO createSystemAuditEntry(CreateAuditDTO createAuditDTO) {
        logger.debug("Creating system audit entry for user: {}, action: {}", 
            createAuditDTO.getUserId(), createAuditDTO.getAction());
        
        return createAuditEntryInternal(createAuditDTO);
    }
    
    /**
     * Internal method to create audit entries, shared by both public and system methods
     * 
     * @param createAuditDTO the audit data to create
     * @return the created audit entry as DTO
     */
    private AuditDTO createAuditEntryInternal(CreateAuditDTO createAuditDTO) {
        Audit audit = new Audit();
        audit.setUserId(createAuditDTO.getUserId());
        audit.setAction(createAuditDTO.getAction());
        audit.setEntityType(createAuditDTO.getEntityType());
        audit.setEntityId(createAuditDTO.getEntityId());
        audit.setDescription(createAuditDTO.getDescription());
        audit.setIpAddress(createAuditDTO.getIpAddress());
        audit.setUserAgent(createAuditDTO.getUserAgent());
        audit.setSessionId(createAuditDTO.getSessionId());
        audit.setDetails(createAuditDTO.getDetails());
        audit.setSeverity(createAuditDTO.getSeverity() != null ? createAuditDTO.getSeverity() : AuditSeverity.INFO);
        audit.setCategory(createAuditDTO.getCategory() != null ? createAuditDTO.getCategory() : AuditCategory.SYSTEM);
        audit.setSource(createAuditDTO.getSource());
        
        Audit savedAudit = auditRepository.save(audit);
        logger.info("Created audit entry with ID: {} for action: {}", savedAudit.getId(), savedAudit.getAction());
        
        return mapToDTO(savedAudit);
    }
    
    /**
     * Retrieves a single audit entry by ID
     * 
     * @param id the audit entry ID
     * @return the audit entry as DTO
     */
    @PreAuthorize("hasRole('ADMIN')")
    public AuditDTO getAuditById(UUID id) {
        logger.debug("Retrieving audit entry with ID: {}", id);
        
        Audit audit = auditRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Audit entry not found with ID: " + id));
        
        return mapToDTO(audit);
    }
    
    /**
     * Retrieves audit entries with pagination and filtering
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
    @PreAuthorize("hasRole('ADMIN')")
    public Page<AuditDTO> getAuditEntries(int page, int size, String userId, String action, 
            String entityType, AuditSeverity severity, AuditCategory category,
            LocalDateTime startDate, LocalDateTime endDate) {
        
        logger.debug("Retrieving audit entries - page: {}, size: {}, userId: {}, action: {}, " +
                    "entityType: {}, severity: {}, category: {}", 
                    page, size, userId, action, entityType, severity, category);
        
        // Create pageable with explicit sorting by timestamp descending
        Pageable pageable = PageRequest.of(page, size, 
            Sort.by(Sort.Direction.DESC, "timestamp"));
        
        Page<Audit> auditPage;
        
        // Use the comprehensive filter query if any filters are provided
        if (userId != null || action != null || entityType != null || 
            severity != null || category != null || startDate != null || endDate != null) {
            
            auditPage = auditRepository.findWithFilters(
                userId, action, entityType, severity, category, startDate, endDate, pageable);
        } else {
            // No filters - get all audit entries with explicit timestamp descending order
            auditPage = auditRepository.findAll(pageable);
        }
        
        return auditPage.map(this::mapToDTO);
    }
    
    /**
     * Retrieves audit entries for a specific user
     * 
     * @param userId the user ID
     * @param page the page number (0-based)
     * @param size the page size
     * @return paginated audit entries for the user
     */
    @PreAuthorize("hasRole('ADMIN')")
    public Page<AuditDTO> getAuditEntriesByUser(String userId, int page, int size) {
        logger.debug("Retrieving audit entries for user: {} - page: {}, size: {}", userId, page, size);
        
        // Create pageable with explicit sorting by timestamp descending
        Pageable pageable = PageRequest.of(page, size, 
            Sort.by(Sort.Direction.DESC, "timestamp"));
        Page<Audit> auditPage = auditRepository.findByUserIdOrderByTimestampDesc(userId, pageable);
        
        return auditPage.map(this::mapToDTO);
    }
    
    /**
     * Retrieves audit entries within a date range
     * 
     * @param startDate the start date
     * @param endDate the end date
     * @param page the page number (0-based)
     * @param size the page size
     * @return paginated audit entries within the date range
     */
    @PreAuthorize("hasRole('ADMIN')")
    public Page<AuditDTO> getAuditEntriesByDateRange(LocalDateTime startDate, LocalDateTime endDate, 
            int page, int size) {
        logger.debug("Retrieving audit entries from {} to {} - page: {}, size: {}", 
                    startDate, endDate, page, size);
        
        // Create pageable with explicit sorting by timestamp descending
        Pageable pageable = PageRequest.of(page, size, 
            Sort.by(Sort.Direction.DESC, "timestamp"));
        Page<Audit> auditPage = auditRepository.findByTimestampBetweenOrderByTimestampDesc(
            startDate, endDate, pageable);
        
        return auditPage.map(this::mapToDTO);
    }
    
    /**
     * Retrieves recent high severity audit entries
     * 
     * @param page the page number (0-based)
     * @param size the page size
     * @return paginated high severity audit entries
     */
    @PreAuthorize("hasRole('ADMIN')")
    public Page<AuditDTO> getHighSeverityAuditEntries(int page, int size) {
        logger.debug("Retrieving high severity audit entries - page: {}, size: {}", page, size);
        
        // Create pageable with explicit sorting by timestamp descending
        Pageable pageable = PageRequest.of(page, size, 
            Sort.by(Sort.Direction.DESC, "timestamp"));
        Page<Audit> auditPage = auditRepository.findRecentHighSeverityEntries(pageable);
        
        return auditPage.map(this::mapToDTO);
    }
    
    /**
     * Deletes audit entries older than the specified cutoff date
     * This is used for data retention policies
     * 
     * @param cutoffDate the cutoff date - entries older than this will be deleted
     * @return the number of deleted entries
     */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public int deleteOldAuditEntries(LocalDateTime cutoffDate) {
        logger.info("Deleting audit entries older than: {}", cutoffDate);
        
        int deletedCount = auditRepository.deleteByTimestampBefore(cutoffDate);
        
        logger.info("Deleted {} old audit entries", deletedCount);
        return deletedCount;
    }
    
    /**
     * Gets count statistics for audit entries
     * 
     * @return statistics about audit entries
     */
    @PreAuthorize("hasRole('ADMIN')")
    public AuditStatisticsDTO getAuditStatistics() {
        logger.debug("Calculating audit statistics");
        
        long totalEntries = auditRepository.count();
        
        // Calculate entries from last 24 hours
        LocalDateTime last24Hours = LocalDateTime.now().minusDays(1);
        long entriesLast24Hours = auditRepository.countByTimestampBetween(last24Hours, LocalDateTime.now());
        
        // Calculate entries from last 7 days
        LocalDateTime last7Days = LocalDateTime.now().minusDays(7);
        long entriesLast7Days = auditRepository.countByTimestampBetween(last7Days, LocalDateTime.now());
        
        // Calculate entries from last 30 days
        LocalDateTime last30Days = LocalDateTime.now().minusDays(30);
        long entriesLast30Days = auditRepository.countByTimestampBetween(last30Days, LocalDateTime.now());
        
        return AuditStatisticsDTO.builder()
            .totalEntries(totalEntries)
            .entriesLast24Hours(entriesLast24Hours)
            .entriesLast7Days(entriesLast7Days)
            .entriesLast30Days(entriesLast30Days)
            .build();
    }
    
    /**
     * Maps an Audit entity to AuditDTO
     * 
     * @param audit the audit entity
     * @return the audit DTO
     */
    private AuditDTO mapToDTO(Audit audit) {
        return AuditDTO.builder()
            .id(audit.getId())
            .timestamp(audit.getTimestamp())
            .userId(audit.getUserId())
            .action(audit.getAction())
            .entityType(audit.getEntityType())
            .entityId(audit.getEntityId())
            .description(audit.getDescription())
            .ipAddress(audit.getIpAddress())
            .userAgent(audit.getUserAgent())
            .sessionId(audit.getSessionId())
            .details(audit.getDetails())
            .severity(audit.getSeverity())
            .category(audit.getCategory())
            .source(audit.getSource())
            .build();
    }
    
    /**
     * Statistics DTO for audit entries
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class AuditStatisticsDTO {
        private long totalEntries;
        private long entriesLast24Hours;
        private long entriesLast7Days;
        private long entriesLast30Days;
    }
} 