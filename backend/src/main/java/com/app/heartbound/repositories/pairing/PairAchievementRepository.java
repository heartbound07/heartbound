package com.app.heartbound.repositories.pairing;

import com.app.heartbound.entities.PairAchievement;
import com.app.heartbound.entities.Pairing;
import com.app.heartbound.entities.Achievement;
import com.app.heartbound.enums.AchievementType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PairAchievementRepository extends JpaRepository<PairAchievement, Long> {

    // Find all achievements for a pairing
    List<PairAchievement> findByPairing(Pairing pairing);

    // Find achievements by pairing ID
    @Query("SELECT pa FROM PairAchievement pa WHERE pa.pairing.id = :pairingId ORDER BY pa.unlockedAt DESC")
    List<PairAchievement> findByPairingId(@Param("pairingId") Long pairingId);

    // Check if pairing has specific achievement
    boolean existsByPairingAndAchievement(Pairing pairing, Achievement achievement);

    // Find specific achievement for pairing
    Optional<PairAchievement> findByPairingAndAchievement(Pairing pairing, Achievement achievement);

    // Find recent achievements for pairing
    @Query("SELECT pa FROM PairAchievement pa WHERE pa.pairing.id = :pairingId AND pa.unlockedAt >= :since ORDER BY pa.unlockedAt DESC")
    List<PairAchievement> findRecentAchievements(@Param("pairingId") Long pairingId, @Param("since") LocalDateTime since);

    // Find achievements by type for pairing
    @Query("SELECT pa FROM PairAchievement pa WHERE pa.pairing.id = :pairingId AND pa.achievement.achievementType = :type ORDER BY pa.unlockedAt DESC")
    List<PairAchievement> findByPairingIdAndAchievementType(@Param("pairingId") Long pairingId, @Param("type") AchievementType type);

    // Find unnotified achievements
    @Query("SELECT pa FROM PairAchievement pa WHERE pa.notified = false ORDER BY pa.unlockedAt ASC")
    List<PairAchievement> findUnnotifiedAchievements();

    // Find unnotified achievements for specific pairing
    @Query("SELECT pa FROM PairAchievement pa WHERE pa.pairing.id = :pairingId AND pa.notified = false ORDER BY pa.unlockedAt ASC")
    List<PairAchievement> findUnnotifiedAchievementsByPairingId(@Param("pairingId") Long pairingId);

    // Count achievements by pairing
    @Query("SELECT COUNT(pa) FROM PairAchievement pa WHERE pa.pairing.id = :pairingId")
    long countByPairingId(@Param("pairingId") Long pairingId);

    // Count achievements by type for pairing
    @Query("SELECT COUNT(pa) FROM PairAchievement pa WHERE pa.pairing.id = :pairingId AND pa.achievement.achievementType = :type")
    long countByPairingIdAndAchievementType(@Param("pairingId") Long pairingId, @Param("type") AchievementType type);

    // Get total XP earned from achievements for pairing
    @Query("SELECT COALESCE(SUM(pa.xpAwarded), 0) FROM PairAchievement pa WHERE pa.pairing.id = :pairingId")
    int getTotalXPFromAchievements(@Param("pairingId") Long pairingId);

    // Find top achievement earners
    @Query("SELECT pa.pairing.id, COUNT(pa), SUM(pa.xpAwarded) FROM PairAchievement pa WHERE pa.pairing.active = true GROUP BY pa.pairing.id ORDER BY COUNT(pa) DESC, SUM(pa.xpAwarded) DESC")
    List<Object[]> findTopAchievementEarners();

    // Find achievements unlocked in date range
    @Query("SELECT pa FROM PairAchievement pa WHERE pa.unlockedAt BETWEEN :startDate AND :endDate ORDER BY pa.unlockedAt DESC")
    List<PairAchievement> findAchievementsInDateRange(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    // Find achievements by rarity for pairing
    @Query("SELECT pa FROM PairAchievement pa WHERE pa.pairing.id = :pairingId AND pa.achievement.rarity = :rarity ORDER BY pa.unlockedAt DESC")
    List<PairAchievement> findByPairingIdAndRarity(@Param("pairingId") Long pairingId, @Param("rarity") String rarity);
} 