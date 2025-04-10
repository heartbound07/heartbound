package com.app.heartbound.repositories.lfg;

import com.app.heartbound.entities.LFGParty;
import com.app.heartbound.enums.TrackingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LFGPartyRepository extends JpaRepository<LFGParty, UUID>, JpaSpecificationExecutor<LFGParty> {
    Optional<LFGParty> findByUserId(String userId);
    
    /**
     * Find active parties that need tracking based on their status, expiration date, and tracking status
     * 
     * @param statuses List of party statuses to include (e.g., "open", "full")
     * @param expiresAfter Only include parties that expire after this time
     * @param trackingStatuses List of tracking statuses to include
     * @return List of matching LFG parties
     */
    List<LFGParty> findByStatusInAndExpiresAtAfterAndTrackingStatusIn(
            Collection<String> statuses,
            Instant expiresAfter,
            Collection<TrackingStatus> trackingStatuses);
}
