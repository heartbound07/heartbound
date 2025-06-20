package com.app.heartbound.repositories;

import com.app.heartbound.entities.DailyMessageStat;
import com.app.heartbound.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface DailyMessageStatRepository extends JpaRepository<DailyMessageStat, Long> {
    
    /**
     * Find a daily message stat for a specific user and date
     */
    Optional<DailyMessageStat> findByUserAndDate(User user, LocalDate date);
    
    /**
     * Find daily message stats for a user within a date range, ordered by date
     */
    @Query("SELECT dms FROM DailyMessageStat dms WHERE dms.user = :user AND dms.date BETWEEN :startDate AND :endDate ORDER BY dms.date ASC")
    List<DailyMessageStat> findByUserAndDateBetweenOrderByDateAsc(
            @Param("user") User user, 
            @Param("startDate") LocalDate startDate, 
            @Param("endDate") LocalDate endDate);
    
    /**
     * Increment message count for a user on a specific date
     * If the record doesn't exist, it will be created with count 1
     */
    @Modifying
    @Query(value = """
        INSERT INTO daily_message_stats (user_id, date, message_count, created_at, updated_at)
        VALUES (:userId, :date, 1, NOW(), NOW())
        ON DUPLICATE KEY UPDATE 
        message_count = message_count + 1,
        updated_at = NOW()
        """, nativeQuery = true)
    int incrementMessageCount(@Param("userId") String userId, @Param("date") LocalDate date);
    
    /**
     * Get the last N days of activity for a user
     */
    @Query("SELECT dms FROM DailyMessageStat dms WHERE dms.user = :user AND dms.date >= :fromDate ORDER BY dms.date ASC")
    List<DailyMessageStat> findRecentActivityByUser(@Param("user") User user, @Param("fromDate") LocalDate fromDate);
} 