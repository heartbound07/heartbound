package com.app.heartbound.repositories;

import com.app.heartbound.entities.DailyVoiceActivityStat;
import com.app.heartbound.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface DailyVoiceActivityStatRepository extends JpaRepository<DailyVoiceActivityStat, Long> {
    
    /**
     * Find daily voice activity stats for a user between given dates, ordered by date ascending
     */
    List<DailyVoiceActivityStat> findByUserAndDateBetweenOrderByDateAsc(User user, LocalDate startDate, LocalDate endDate);
    
    /**
     * Increment the voice minutes for a user on a specific date.
     * If no record exists for that user and date, create one.
     * This is an upsert operation optimized for concurrent access (PostgreSQL syntax).
     */
    @Modifying
    @Query(value = """
        INSERT INTO daily_voice_activity_stats (user_id, date, voice_minutes) 
        VALUES (:userId, :date, :voiceMinutes)
        ON CONFLICT (user_id, date) 
        DO UPDATE SET voice_minutes = daily_voice_activity_stats.voice_minutes + :voiceMinutes
        """, nativeQuery = true)
    void incrementVoiceMinutes(@Param("userId") String userId, @Param("date") LocalDate date, @Param("voiceMinutes") int voiceMinutes);
} 