package com.app.heartbound.repositories.pairing;

import com.app.heartbound.entities.PairLevel;
import com.app.heartbound.entities.Pairing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PairLevelRepository extends JpaRepository<PairLevel, Long> {

    // Find pair level by pairing
    Optional<PairLevel> findByPairing(Pairing pairing);

    // Find pair level by pairing ID
    @Query("SELECT pl FROM PairLevel pl WHERE pl.pairing.id = :pairingId")
    Optional<PairLevel> findByPairingId(@Param("pairingId") Long pairingId);

    // Find all pair levels for active pairings
    @Query("SELECT pl FROM PairLevel pl WHERE pl.pairing.active = true")
    List<PairLevel> findByActivePairings();

    // Find top level pairs (leaderboard)
    @Query("SELECT pl FROM PairLevel pl WHERE pl.pairing.active = true ORDER BY pl.currentLevel DESC, pl.totalXP DESC")
    List<PairLevel> findTopLevelPairs();

    // Find pairs by level range
    @Query("SELECT pl FROM PairLevel pl WHERE pl.currentLevel BETWEEN :minLevel AND :maxLevel ORDER BY pl.currentLevel DESC, pl.totalXP DESC")
    List<PairLevel> findPairsByLevelRange(@Param("minLevel") int minLevel, @Param("maxLevel") int maxLevel);

    // Get total XP leaderboard
    @Query("SELECT pl FROM PairLevel pl WHERE pl.pairing.active = true ORDER BY pl.totalXP DESC")
    List<PairLevel> findByTotalXPDesc();

    // Count pairs at each level
    @Query("SELECT pl.currentLevel, COUNT(pl) FROM PairLevel pl WHERE pl.pairing.active = true GROUP BY pl.currentLevel ORDER BY pl.currentLevel")
    List<Object[]> countPairsByLevel();

    // Find pairs ready to level up
    @Query("SELECT pl FROM PairLevel pl WHERE pl.currentLevelXP >= pl.nextLevelXP")
    List<PairLevel> findPairsReadyToLevelUp();

    // Get average level of active pairs
    @Query("SELECT AVG(pl.currentLevel) FROM PairLevel pl WHERE pl.pairing.active = true")
    Double getAverageLevelOfActivePairs();

    // Check if pairing exists in pair levels
    boolean existsByPairing(Pairing pairing);
    
    // Batch fetch pair levels by multiple pairing IDs (for leaderboard optimization)
    @Query("SELECT pl FROM PairLevel pl WHERE pl.pairing.id IN :pairingIds")
    List<PairLevel> findByPairingIds(@Param("pairingIds") List<Long> pairingIds);
} 