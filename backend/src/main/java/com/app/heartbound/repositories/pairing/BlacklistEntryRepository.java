package com.app.heartbound.repositories.pairing;

import com.app.heartbound.entities.BlacklistEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BlacklistEntryRepository extends JpaRepository<BlacklistEntry, Long> {

    // Check if a pair is blacklisted (order-independent)
    @Query("SELECT b FROM BlacklistEntry b WHERE " +
           "(b.user1Id = :user1Id AND b.user2Id = :user2Id) OR " +
           "(b.user1Id = :user2Id AND b.user2Id = :user1Id)")
    Optional<BlacklistEntry> findByUserPair(@Param("user1Id") String user1Id, @Param("user2Id") String user2Id);

    // Check if a pair exists (returns boolean for performance)
    @Query("SELECT COUNT(b) > 0 FROM BlacklistEntry b WHERE " +
           "(b.user1Id = :user1Id AND b.user2Id = :user2Id) OR " +
           "(b.user1Id = :user2Id AND b.user2Id = :user1Id)")
    boolean existsByUserPair(@Param("user1Id") String user1Id, @Param("user2Id") String user2Id);
} 