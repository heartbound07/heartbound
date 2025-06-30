package com.app.heartbound.repositories;

import com.app.heartbound.entities.Giveaway;
import com.app.heartbound.entities.GiveawayEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface GiveawayEntryRepository extends JpaRepository<GiveawayEntry, UUID> {
    
    /**
     * Find all entries for a specific giveaway
     */
    List<GiveawayEntry> findByGiveawayOrderByEntryDateAsc(Giveaway giveaway);
    
    /**
     * Find all entries by a specific user for a specific giveaway
     */
    List<GiveawayEntry> findByGiveawayAndUserIdOrderByEntryNumberAsc(Giveaway giveaway, String userId);
    
    /**
     * Count entries by a specific user for a specific giveaway
     */
    long countByGiveawayAndUserId(Giveaway giveaway, String userId);
    
    /**
     * Count total entries for a specific giveaway
     */
    long countByGiveaway(Giveaway giveaway);
    
    /**
     * Find entry by giveaway ID and user ID and entry number
     */
    @Query("SELECT e FROM GiveawayEntry e WHERE e.giveaway.id = :giveawayId AND e.userId = :userId AND e.entryNumber = :entryNumber")
    GiveawayEntry findByGiveawayIdAndUserIdAndEntryNumber(@Param("giveawayId") UUID giveawayId, 
                                                         @Param("userId") String userId, 
                                                         @Param("entryNumber") Integer entryNumber);
    
    /**
     * Get the highest entry number for a user in a specific giveaway
     */
    @Query("SELECT COALESCE(MAX(e.entryNumber), 0) FROM GiveawayEntry e WHERE e.giveaway = :giveaway AND e.userId = :userId")
    int getMaxEntryNumberForUser(@Param("giveaway") Giveaway giveaway, @Param("userId") String userId);
} 