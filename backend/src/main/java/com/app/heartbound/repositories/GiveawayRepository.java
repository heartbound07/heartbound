package com.app.heartbound.repositories;

import com.app.heartbound.entities.Giveaway;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GiveawayRepository extends JpaRepository<Giveaway, UUID> {
    
    /**
     * Find active giveaways that haven't expired yet
     */
    @Query("SELECT g FROM Giveaway g WHERE g.status = 'ACTIVE' AND g.endDate > :now")
    List<Giveaway> findActiveGiveaways(@Param("now") LocalDateTime now);
    
    /**
     * Find expired giveaways that are still marked as active
     */
    @Query("SELECT g FROM Giveaway g WHERE g.status = 'ACTIVE' AND g.endDate <= :now")
    List<Giveaway> findExpiredActiveGiveaways(@Param("now") LocalDateTime now);
    
    /**
     * Find giveaway by Discord message ID
     */
    Optional<Giveaway> findByMessageId(String messageId);
    
    /**
     * Find giveaways by host user ID
     */
    List<Giveaway> findByHostUserIdOrderByCreatedAtDesc(String hostUserId);
    
    /**
     * Find giveaways in a specific channel
     */
    List<Giveaway> findByChannelIdOrderByCreatedAtDesc(String channelId);
    
    /**
     * Count active giveaways by host
     */
    @Query("SELECT COUNT(g) FROM Giveaway g WHERE g.hostUserId = :hostUserId AND g.status = 'ACTIVE'")
    long countActiveGiveawaysByHost(@Param("hostUserId") String hostUserId);
} 