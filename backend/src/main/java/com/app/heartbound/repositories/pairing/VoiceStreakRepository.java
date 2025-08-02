package com.app.heartbound.repositories.pairing;

import com.app.heartbound.entities.VoiceStreak;
import com.app.heartbound.entities.Pairing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface VoiceStreakRepository extends JpaRepository<VoiceStreak, Long> {

    // Find streak by pairing and date
    Optional<VoiceStreak> findByPairingAndStreakDate(Pairing pairing, LocalDate streakDate);

    // Find all streaks for a pairing, ordered by date descending
    @Query("SELECT vs FROM VoiceStreak vs WHERE vs.pairing.id = :pairingId AND vs.active = true ORDER BY vs.streakDate DESC")
    List<VoiceStreak> findByPairingId(@Param("pairingId") Long pairingId);

    // Find current/latest streak for pairing
    @Query("SELECT vs FROM VoiceStreak vs WHERE vs.pairing.id = :pairingId AND vs.active = true ORDER BY vs.streakDate DESC LIMIT 1")
    Optional<VoiceStreak> findLatestStreakByPairingId(@Param("pairingId") Long pairingId);

    // Find streaks within date range for pairing
    @Query("SELECT vs FROM VoiceStreak vs WHERE vs.pairing.id = :pairingId AND vs.streakDate BETWEEN :startDate AND :endDate AND vs.active = true ORDER BY vs.streakDate")
    List<VoiceStreak> findStreaksInDateRange(@Param("pairingId") Long pairingId, @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    // Get current streak count for pairing
    @Query("SELECT COALESCE(MAX(vs.streakCount), 0) FROM VoiceStreak vs WHERE vs.pairing.id = :pairingId AND vs.active = true")
    int getCurrentStreakCount(@Param("pairingId") Long pairingId);

    // Get highest streak count for pairing
    @Query("SELECT COALESCE(MAX(vs.streakCount), 0) FROM VoiceStreak vs WHERE vs.pairing.id = :pairingId")
    int getHighestStreakCount(@Param("pairingId") Long pairingId);

    // Find streaks that meet minimum activity threshold
    @Query("SELECT vs FROM VoiceStreak vs WHERE vs.pairing.id = :pairingId AND vs.voiceMinutes >= 30 AND vs.active = true ORDER BY vs.streakDate DESC")
    List<VoiceStreak> findValidStreaksByPairingId(@Param("pairingId") Long pairingId);

    // Find today's streak for pairing
    @Query("SELECT vs FROM VoiceStreak vs WHERE vs.pairing.id = :pairingId AND vs.streakDate = :today AND vs.active = true")
    Optional<VoiceStreak> findTodayStreakByPairingId(@Param("pairingId") Long pairingId, @Param("today") LocalDate today);

    // Find yesterday's streak for pairing
    @Query("SELECT vs FROM VoiceStreak vs WHERE vs.pairing.id = :pairingId AND vs.streakDate = :yesterday AND vs.active = true")
    Optional<VoiceStreak> findYesterdayStreakByPairingId(@Param("pairingId") Long pairingId, @Param("yesterday") LocalDate yesterday);

    // Check if pairing has activity on specific date
    boolean existsByPairingAndStreakDateAndActiveTrue(Pairing pairing, LocalDate streakDate);

    // Get total voice minutes for pairing across all streaks
    @Query("SELECT COALESCE(SUM(vs.voiceMinutes), 0) FROM VoiceStreak vs WHERE vs.pairing.id = :pairingId AND vs.active = true")
    int getTotalVoiceMinutesByPairingId(@Param("pairingId") Long pairingId);

    // Find streak milestones (streaks at specific counts: 3, 7, 14, 30 days)
    @Query("SELECT vs FROM VoiceStreak vs WHERE vs.pairing.id = :pairingId AND vs.streakCount IN (3, 7, 14, 30) AND vs.active = true ORDER BY vs.streakCount DESC")
    List<VoiceStreak> findStreakMilestonesByPairingId(@Param("pairingId") Long pairingId);

    // Find top streak performers (leaderboard)
    @Query("SELECT vs.pairing.id, MAX(vs.streakCount) FROM VoiceStreak vs WHERE vs.pairing.active = true AND vs.active = true GROUP BY vs.pairing.id ORDER BY MAX(vs.streakCount) DESC")
    List<Object[]> findTopStreakPerformers();

    // Count active streak days for pairing
    @Query("SELECT COUNT(vs) FROM VoiceStreak vs WHERE vs.pairing.id = :pairingId AND vs.voiceMinutes >= 30 AND vs.active = true")
    long countActiveStreakDaysByPairingId(@Param("pairingId") Long pairingId);

    // Find broken streaks (gaps in consecutive days)
    @Query("SELECT vs FROM VoiceStreak vs WHERE vs.pairing.id = :pairingId AND vs.active = false ORDER BY vs.streakDate DESC")
    List<VoiceStreak> findBrokenStreaksByPairingId(@Param("pairingId") Long pairingId);
    
    // Batch get current streak counts for multiple pairings (for leaderboard optimization)
    @Query("SELECT vs.pairing.id, COALESCE(MAX(vs.streakCount), 0) FROM VoiceStreak vs WHERE vs.pairing.id IN :pairingIds AND vs.active = true GROUP BY vs.pairing.id")
    List<Object[]> getCurrentStreakCountsForPairings(@Param("pairingIds") List<Long> pairingIds);
} 