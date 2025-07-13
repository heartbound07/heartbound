package com.app.heartbound.repositories;

import com.app.heartbound.entities.PendingRoleSelection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PendingRoleSelectionRepository extends JpaRepository<PendingRoleSelection, String> {
    
    /**
     * Find pending role selection by Discord user ID
     */
    Optional<PendingRoleSelection> findByDiscordUserId(String discordUserId);
    
    /**
     * Check if a pending role selection exists for a Discord user
     */
    boolean existsByDiscordUserId(String discordUserId);
    
    /**
     * Find all pending role selections updated before a specific time
     * Useful for cleanup of old pending selections
     */
    List<PendingRoleSelection> findByUpdatedAtBefore(LocalDateTime cutoffTime);
    
    /**
     * Count pending role selections updated before a specific time
     */
    long countByUpdatedAtBefore(LocalDateTime cutoffTime);
} 