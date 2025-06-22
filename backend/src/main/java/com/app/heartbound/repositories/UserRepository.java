package com.app.heartbound.repositories;

import com.app.heartbound.enums.Role;
import com.app.heartbound.entities.User;
import com.app.heartbound.entities.Shop;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, String> {
    
    // Find users with a specific role
    @Query("SELECT u FROM User u JOIN u.roles r WHERE r = :role")
    List<User> findByRole(Role role);
    
    // Check if a user has a specific role
    @Query("SELECT COUNT(u) > 0 FROM User u JOIN u.roles r WHERE u.id = :userId AND r = :role")
    boolean hasRole(String userId, Role role);

    // Find users by username or email containing a search term
    Page<User> findByUsernameContainingOrEmailContaining(String username, String email, Pageable pageable);

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
}
