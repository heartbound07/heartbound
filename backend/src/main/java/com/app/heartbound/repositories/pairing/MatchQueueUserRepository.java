package com.app.heartbound.repositories.pairing;

import com.app.heartbound.entities.MatchQueueUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface MatchQueueUserRepository extends JpaRepository<MatchQueueUser, Long> {

    // Find user in queue by user ID
    Optional<MatchQueueUser> findByUserId(String userId);

    // Find all users currently in queue
    List<MatchQueueUser> findByInQueueTrue();

    // Find all users no longer in queue
    List<MatchQueueUser> findByInQueueFalse();

    // Check if user is in queue
    boolean existsByUserIdAndInQueueTrue(String userId);

    // **OPTIMIZATION: Batch operations to reduce N+1 queries**
    
    /**
     * Get detailed queue information with user profiles in a single query
     * This eliminates N+1 query problem when fetching user details
     */
    @Query("SELECT mqu FROM MatchQueueUser mqu " +
           "LEFT JOIN FETCH User u ON mqu.userId = u.id " +
           "WHERE mqu.inQueue = true " +
           "ORDER BY mqu.queuedAt ASC")
    List<MatchQueueUser> findActiveQueueUsersWithUserProfiles();

    /**
     * Get queue statistics aggregated in database to reduce multiple queries
     * Returns count by region, rank, gender, and age ranges in single query
     */
    @Query("SELECT " +
           "mqu.region as region, " +
           "mqu.rank as rank, " +
           "mqu.gender as gender, " +
           "CASE " +
           "  WHEN mqu.age BETWEEN 18 AND 21 THEN '18-21' " +
           "  WHEN mqu.age BETWEEN 22 AND 25 THEN '22-25' " +
           "  WHEN mqu.age BETWEEN 26 AND 30 THEN '26-30' " +
           "  WHEN mqu.age BETWEEN 31 AND 35 THEN '31-35' " +
           "  ELSE '36+' " +
           "END as ageRange, " +
           "COUNT(*) as count " +
           "FROM MatchQueueUser mqu " +
           "WHERE mqu.inQueue = true " +
           "GROUP BY mqu.region, mqu.rank, mqu.gender, " +
           "CASE " +
           "  WHEN mqu.age BETWEEN 18 AND 21 THEN '18-21' " +
           "  WHEN mqu.age BETWEEN 22 AND 25 THEN '22-25' " +
           "  WHEN mqu.age BETWEEN 26 AND 30 THEN '26-30' " +
           "  WHEN mqu.age BETWEEN 31 AND 35 THEN '31-35' " +
           "  ELSE '36+' " +
           "END")
    List<Object[]> getQueueStatisticsAggregated();

    /**
     * Get queue size and average wait time in single optimized query
     * Using simpler approach to avoid Hibernate EXTRACT issues
     */
    @Query("SELECT COUNT(*) FROM MatchQueueUser mqu WHERE mqu.inQueue = true")
    int getActiveQueueSize();

    /**
     * Get all active queue times for wait time calculation
     */
    @Query("SELECT mqu.queuedAt FROM MatchQueueUser mqu WHERE mqu.inQueue = true")
    List<LocalDateTime> getActiveQueueTimes();

    /**
     * Find multiple users by their user IDs for batch operations
     * Used by QueueService to remove matched users from queue efficiently
     */
    List<MatchQueueUser> findByUserIdIn(List<String> userIds);

    /**
     * Count users queued after specific date for performance tracking
     */
    @Query("SELECT COUNT(*) FROM MatchQueueUser mqu WHERE mqu.queuedAt >= :afterDate AND mqu.inQueue = true")
    int countActiveUsersQueuedAfter(@Param("afterDate") LocalDateTime afterDate);

    /**
     * Get queue size at different time intervals for history tracking
     */
    @Query("SELECT " +
           "EXTRACT(HOUR FROM mqu.queuedAt) as hour, " +
           "COUNT(*) as count " +
           "FROM MatchQueueUser mqu " +
           "WHERE mqu.queuedAt >= :since AND mqu.inQueue = true " +
           "GROUP BY EXTRACT(HOUR FROM mqu.queuedAt) " +
           "ORDER BY hour")
    List<Object[]> getQueueSizeHistory(@Param("since") LocalDateTime since);

    /**
     * Optimized method to get only user IDs for lightweight operations
     */
    @Query("SELECT mqu.userId FROM MatchQueueUser mqu WHERE mqu.inQueue = true ORDER BY mqu.queuedAt ASC")
    List<String> findActiveQueueUserIds();

    /**
     * Count total users in queue - optimized for cache warming
     */
    @Query("SELECT COUNT(*) FROM MatchQueueUser mqu WHERE mqu.inQueue = true")
    int countActiveQueueUsers();
} 