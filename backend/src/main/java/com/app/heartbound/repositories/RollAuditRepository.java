package com.app.heartbound.repositories;

import com.app.heartbound.entities.RollAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * RollAuditRepository
 * 
 * Data access layer for roll audit operations.
 * Provides methods for querying roll history and statistical analysis.
 */
@Repository
public interface RollAuditRepository extends JpaRepository<RollAudit, UUID> {
    
    /**
     * Find all roll audits for a specific user
     */
    List<RollAudit> findByUserIdOrderByRollTimestampDesc(String userId);
    
    /**
     * Find all roll audits for a specific case
     */
    List<RollAudit> findByCaseIdOrderByRollTimestampDesc(UUID caseId);
    
    /**
     * Find roll audits within a time range
     */
    List<RollAudit> findByRollTimestampBetweenOrderByRollTimestampDesc(
        LocalDateTime startTime, LocalDateTime endTime);
    
    /**
     * Find roll audits by verification status
     */
    List<RollAudit> findByVerificationStatusOrderByRollTimestampDesc(
        RollAudit.VerificationStatus status);
    
    /**
     * Find recent roll audits for a user
     */
    List<RollAudit> findTop10ByUserIdOrderByRollTimestampDesc(String userId);
    
    /**
     * Count rolls for a user in a time period
     */
    @Query("SELECT COUNT(r) FROM RollAudit r WHERE r.userId = :userId " +
           "AND r.rollTimestamp BETWEEN :startTime AND :endTime")
    long countUserRollsInPeriod(@Param("userId") String userId,
                               @Param("startTime") LocalDateTime startTime,
                               @Param("endTime") LocalDateTime endTime);
    
    /**
     * Get statistical data for a case
     */
    @Query("SELECT r.wonItemId, COUNT(r) FROM RollAudit r WHERE r.caseId = :caseId " +
           "GROUP BY r.wonItemId")
    List<Object[]> getItemWinStatsByCaseId(@Param("caseId") UUID caseId);
    
    /**
     * Get average roll value for a case
     */
    @Query("SELECT AVG(r.rollValue) FROM RollAudit r WHERE r.caseId = :caseId")
    Double getAverageRollValueByCaseId(@Param("caseId") UUID caseId);
    
    /**
     * Find audits with anomaly flags
     */
    List<RollAudit> findByAnomalyFlagsIsNotNullOrderByRollTimestampDesc();
    
    /**
     * Get roll distribution for statistical analysis
     */
    @Query("SELECT r.rollValue, COUNT(r) FROM RollAudit r " +
           "WHERE r.rollTimestamp BETWEEN :startTime AND :endTime " +
           "GROUP BY r.rollValue ORDER BY r.rollValue")
    List<Object[]> getRollDistribution(@Param("startTime") LocalDateTime startTime,
                                      @Param("endTime") LocalDateTime endTime);
    
    /**
     * Get user roll frequency for anomaly detection
     */
    @Query("SELECT r.userId, COUNT(r) FROM RollAudit r " +
           "WHERE r.rollTimestamp BETWEEN :startTime AND :endTime " +
           "GROUP BY r.userId HAVING COUNT(r) > :threshold " +
           "ORDER BY COUNT(r) DESC")
    List<Object[]> getHighFrequencyUsers(@Param("startTime") LocalDateTime startTime,
                                        @Param("endTime") LocalDateTime endTime,
                                        @Param("threshold") long threshold);
    
    /**
     * Find rolls with suspicious patterns
     */
    @Query("SELECT r FROM RollAudit r WHERE r.userId = :userId " +
           "AND r.rollTimestamp BETWEEN :startTime AND :endTime " +
           "ORDER BY r.rollTimestamp")
    List<RollAudit> getUserRollsInTimeframe(@Param("userId") String userId,
                                           @Param("startTime") LocalDateTime startTime,
                                           @Param("endTime") LocalDateTime endTime);
    
    /**
     * Get total rolls count
     */
    @Query("SELECT COUNT(r) FROM RollAudit r")
    long getTotalRollsCount();
    
    /**
     * Get rolls count by time period
     */
    @Query("SELECT COUNT(r) FROM RollAudit r WHERE r.rollTimestamp >= :since")
    long getRollsCountSince(@Param("since") LocalDateTime since);
    
    /**
     * Find cases with unusual win rates
     */
    @Query("SELECT r.caseId, r.caseName, r.wonItemId, r.wonItemName, " +
           "COUNT(r) as winCount, r.dropRate " +
           "FROM RollAudit r " +
           "WHERE r.rollTimestamp BETWEEN :startTime AND :endTime " +
           "GROUP BY r.caseId, r.caseName, r.wonItemId, r.wonItemName, r.dropRate " +
           "HAVING (COUNT(r) * 100.0 / (SELECT COUNT(ra2) FROM RollAudit ra2 " +
           "WHERE ra2.caseId = r.caseId AND ra2.rollTimestamp BETWEEN :startTime AND :endTime)) " +
           "NOT BETWEEN (r.dropRate - :tolerance) AND (r.dropRate + :tolerance)")
    List<Object[]> getAnomalousWinRates(@Param("startTime") LocalDateTime startTime,
                                       @Param("endTime") LocalDateTime endTime,
                                       @Param("tolerance") double tolerance);
} 