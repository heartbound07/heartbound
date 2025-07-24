package com.app.heartbound.repositories;

import com.app.heartbound.entities.DailyVoiceActivityStat;
import com.app.heartbound.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface DailyVoiceActivityStatRepository extends JpaRepository<DailyVoiceActivityStat, Long> {

    Optional<DailyVoiceActivityStat> findByUserAndDate(User user, LocalDate date);

    List<DailyVoiceActivityStat> findByUserAndDateBetweenOrderByDateAsc(User user, LocalDate startDate, LocalDate endDate);

    @Modifying
    @Transactional
    @Query(value = "INSERT INTO daily_voice_activity_stats (user_id, date, voice_minutes, created_at, updated_at) " +
                   "VALUES (:userId, :date, :minutes, NOW(), NOW()) " +
                   "ON CONFLICT (user_id, date) " +
                   "DO UPDATE SET " +
                   "voice_minutes = daily_voice_activity_stats.voice_minutes + :minutes, " +
                   "updated_at = NOW()",
           nativeQuery = true)
    void incrementVoiceMinutes(@Param("userId") String userId, @Param("date") LocalDate date, @Param("minutes") int minutes);

    @Query("SELECT dvs FROM DailyVoiceActivityStat dvs WHERE dvs.user = :user AND dvs.date >= :fromDate ORDER BY dvs.date ASC")
    List<DailyVoiceActivityStat> findRecentActivityByUser(@Param("user") User user, @Param("fromDate") LocalDate fromDate);

    /**
     * Deletes all daily voice activity stats for a given user.
     * This is crucial for ensuring data integrity when a user is deleted.
     *
     * @param userId The ID of the user whose voice stats are to be deleted.
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM DailyVoiceActivityStat dvs WHERE dvs.user.id = :userId")
    void deleteByUserId(@Param("userId") String userId);
} 