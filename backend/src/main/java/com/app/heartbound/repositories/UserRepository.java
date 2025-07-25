package com.app.heartbound.repositories;

import com.app.heartbound.enums.Role;
import com.app.heartbound.entities.User;
import com.app.heartbound.entities.Shop;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.app.heartbound.dto.LeaderboardEntryDTO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, String> {
    
    // Find users with a specific role
    @Query("SELECT u FROM User u JOIN u.roles r WHERE r = :role")
    List<User> findByRole(Role role);
    
    // Check if a user has a specific role
    @Query("SELECT COUNT(u) > 0 FROM User u JOIN u.roles r WHERE u.id = :userId AND r = :role")
    boolean hasRole(String userId, Role role);

    // Find users by username containing a search term (email no longer available)
    Page<User> findByUsernameContaining(String username, Pageable pageable);

    // Add this method to your existing UserRepository interface
    List<User> findByInventoryContaining(Shop item);

    // **OPTIMIZATION: Batch operations for QueueService performance**
    
    /**
     * Fetch multiple users by IDs in a single query to avoid N+1 problem
     * Used by QueueService.getQueueUserDetails() for efficient batch loading
     */
    @Query("SELECT u FROM User u WHERE u.id IN :userIds")
    List<User> findByIdIn(@Param("userIds") Set<String> userIds);

    /**
     * Get user profiles as a map for efficient lookup by QueueService
     * Returns key-value pairs for fast access in queue detail calculations
     */
    @Query("SELECT u.id, u.username, u.avatar FROM User u WHERE u.id IN :userIds")
    List<Object[]> findUserProfilesByIds(@Param("userIds") Set<String> userIds);
    
    /**
     * Finds a paginated list of users for the leaderboard, mapped to a lightweight DTO.
     * This query selects only the necessary fields for the leaderboard.
     *
     * @param pageable specifies the limit (e.g., top 100 users)
     * @return a page of LeaderboardEntryDTOs
     */
    @Query("SELECT new com.app.heartbound.dto.LeaderboardEntryDTO(" +
           "u.id, u.username, u.displayName, u.avatar, u.credits, u.level, u.experience, u.voiceTimeMinutesTotal, u.messageCount, u.banned, u.fishCaughtCount" +
           ") FROM User u WHERE u.banned = false OR u.banned IS NULL")
    Page<LeaderboardEntryDTO> findLeaderboardEntries(Pageable pageable);

    /**
     * Find user with eagerly loaded inventory collections for Discord commands
     * Prevents LazyInitializationException when used outside web transactions
     */
    @Query("SELECT DISTINCT u FROM User u " +
           "LEFT JOIN FETCH u.itemInstances ii " +
           "LEFT JOIN FETCH ii.baseItem " +
           "WHERE u.id = :userId")
    Optional<User> findByIdWithInventory(@Param("userId") String userId);

    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.credits = u.credits + :credits, u.experience = u.experience + :xp WHERE u.id = :userId")
    void incrementCreditsAndXp(@Param("userId") String userId, @Param("credits") int credits, @Param("xp") int xp);

    List<User> findByPrisonReleaseAtIsNotNull();

    // Pessimistic locking for purchase transactions
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :userId")
    Optional<User> findByIdWithLock(@Param("userId") String userId, LockModeType lockMode);

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.inventory LEFT JOIN FETCH u.inventoryItems WHERE u.id = :id")
    Optional<User> findByIdWithInventories(@Param("id") String id);

    @Query("SELECT u FROM User u WHERE u.username = :username")
    Optional<User> findByUsername(@Param("username") String username);
    // Custom query to find users by equipped badge ID
    List<User> findByEquippedBadgeId(UUID equippedBadgeId);
    
    Optional<User> findByEmail(String email);

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.itemInstances WHERE u.id = :id")
    Optional<User> findByIdWithItemInstances(@Param("id") String id);
}
