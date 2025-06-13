package com.app.heartbound.repositories.pairing;

import com.app.heartbound.entities.Pairing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PairingRepository extends JpaRepository<Pairing, Long> {

    // Find active pairing for a user
    @Query("SELECT p FROM Pairing p WHERE (p.user1Id = :userId OR p.user2Id = :userId) AND p.active = true")
    Optional<Pairing> findActivePairingByUserId(@Param("userId") String userId);

    // Find all pairings involving a user (active and inactive)
    @Query("SELECT p FROM Pairing p WHERE (p.user1Id = :userId OR p.user2Id = :userId)")
    List<Pairing> findAllPairingsByUserId(@Param("userId") String userId);

    // Find all active pairings
    List<Pairing> findByActiveTrue();

    // Find all inactive pairings
    List<Pairing> findByActiveFalse();

    // Check if two users have ever been paired
    @Query("SELECT p FROM Pairing p WHERE " +
           "(p.user1Id = :user1Id AND p.user2Id = :user2Id) OR " +
           "(p.user1Id = :user2Id AND p.user2Id = :user1Id)")
    List<Pairing> findPairingsBetweenUsers(@Param("user1Id") String user1Id, @Param("user2Id") String user2Id);

    // Find pairing by Discord channel ID
    Optional<Pairing> findByDiscordChannelId(Long discordChannelId);
    
    // Find active pairings ordered by level (highest first), then by total XP (highest first)
    @Query("SELECT p FROM Pairing p LEFT JOIN PairLevel pl ON pl.pairing.id = p.id " +
           "WHERE p.active = true " +
           "ORDER BY COALESCE(pl.currentLevel, 1) DESC, COALESCE(pl.totalXP, 0) DESC, p.matchedAt ASC")
    List<Pairing> findActivePairingsOrderedByLevel();
    
    // Count pairings created after a specific date/time (for admin statistics)
    @Query("SELECT COUNT(p) FROM Pairing p WHERE p.matchedAt >= :afterDate")
    int countByMatchedAtAfter(@Param("afterDate") java.time.LocalDateTime afterDate);
} 