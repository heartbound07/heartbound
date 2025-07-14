package com.app.heartbound.repositories;

import com.app.heartbound.entities.PendingPrison;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for the PendingPrison entity.
 */
@Repository
public interface PendingPrisonRepository extends JpaRepository<PendingPrison, String> {

    /**
     * Find all pending prisons updated before a specific time.
     * Useful for cleanup of old pending records.
     *
     * @param cutoffTime The cutoff time.
     * @return A list of old PendingPrison records.
     */
    List<PendingPrison> findByUpdatedAtBefore(LocalDateTime cutoffTime);

    /**
     * Find all pending prisons that have a release date set.
     * Useful for reconciling releases on startup.
     *
     * @return A list of PendingPrison records with a non-null prisonReleaseAt.
     */
    List<PendingPrison> findByPrisonReleaseAtIsNotNull();
} 