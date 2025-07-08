package com.app.heartbound.repositories;

import com.app.heartbound.entities.Audit;
import com.app.heartbound.enums.AuditSeverity;
import com.app.heartbound.enums.AuditCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.UUID;

@Repository
public interface AuditRepository extends JpaRepository<Audit, UUID> {
    
    /**
     * Find all audit entries with explicit timestamp descending order
     */
    @Query("SELECT a FROM Audit a ORDER BY a.timestamp DESC")
    Page<Audit> findAllOrderByTimestampDesc(Pageable pageable);
    
    /**
     * Find audit entries by user ID with pagination
     */
    Page<Audit> findByUserIdOrderByTimestampDesc(String userId, Pageable pageable);
    
    /**
     * Find audit entries by action with pagination
     */
    @Query("SELECT a FROM Audit a WHERE LOWER(a.action) LIKE LOWER(CONCAT('%', :action, '%')) ORDER BY a.timestamp DESC")
    Page<Audit> findByActionContainingIgnoreCaseOrderByTimestampDesc(@Param("action") String action, Pageable pageable);
    
    /*
     * Find audit entries by entity type with pagination
     */
    Page<Audit> findByEntityTypeOrderByTimestampDesc(String entityType, Pageable pageable);
    
    /**
     * Find audit entries by severity with pagination
     */
    Page<Audit> findBySeverityOrderByTimestampDesc(AuditSeverity severity, Pageable pageable);
    
    /**
     * Find audit entries by category with pagination
     */
    Page<Audit> findByCategoryOrderByTimestampDesc(AuditCategory category, Pageable pageable);
    
    /**
     * Find audit entries within a date range with pagination
     */
    @Query("SELECT a FROM Audit a WHERE a.timestamp BETWEEN :startDate AND :endDate ORDER BY a.timestamp DESC")
    Page<Audit> findByTimestampBetweenOrderByTimestampDesc(
        @Param("startDate") LocalDateTime startDate, 
        @Param("endDate") LocalDateTime endDate, 
        Pageable pageable
    );
    
    /**
     * Find audit entries by user and date range with pagination
     */
    @Query("SELECT a FROM Audit a WHERE a.userId = :userId AND a.timestamp BETWEEN :startDate AND :endDate ORDER BY a.timestamp DESC")
    Page<Audit> findByUserIdAndTimestampBetweenOrderByTimestampDesc(
        @Param("userId") String userId,
        @Param("startDate") LocalDateTime startDate, 
        @Param("endDate") LocalDateTime endDate, 
        Pageable pageable
    );
    
    /**
     * Search audit entries with multiple filters
     */
    @Query("SELECT a FROM Audit a WHERE " +
           "(:userId IS NULL OR a.userId = :userId) AND " +
           "(:action IS NULL OR LOWER(a.action) LIKE LOWER(CONCAT('%', :action, '%'))) AND " +
           "(:entityType IS NULL OR a.entityType = :entityType) AND " +
           "(:severity IS NULL OR a.severity = :severity) AND " +
           "(:category IS NULL OR a.category = :category) AND " +
           "(:startDate IS NULL OR a.timestamp >= :startDate) AND " +
           "(:endDate IS NULL OR a.timestamp <= :endDate) " +
           "ORDER BY a.timestamp DESC")
    Page<Audit> findWithFilters(
        @Param("userId") String userId,
        @Param("action") String action,
        @Param("entityType") String entityType,
        @Param("severity") AuditSeverity severity,
        @Param("category") AuditCategory category,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate,
        Pageable pageable
    );
    
    /**
     * Count audit entries by user
     */
    long countByUserId(String userId);
    
    /**
     * Count audit entries by action
     */
    long countByAction(String action);
    
    /**
     * Count audit entries within date range
     */
    @Query("SELECT COUNT(a) FROM Audit a WHERE a.timestamp BETWEEN :startDate AND :endDate")
    long countByTimestampBetween(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);
    
    /**
     * Delete audit entries older than specified date (for data retention)
     * This is used for bulk cleanup of old audit logs
     */
    @Modifying
    @Query("DELETE FROM Audit a WHERE a.timestamp < :cutoffDate")
    int deleteByTimestampBefore(@Param("cutoffDate") LocalDateTime cutoffDate);
    
    /**
     * Find audit entries by entity ID (for tracking specific entity changes)
     */
    Page<Audit> findByEntityIdOrderByTimestampDesc(String entityId, Pageable pageable);
    
    /**
     * Find recent high severity audit entries
     */
    @Query("SELECT a FROM Audit a WHERE a.severity IN ('HIGH', 'CRITICAL') ORDER BY a.timestamp DESC")
    Page<Audit> findRecentHighSeverityEntries(Pageable pageable);
} 