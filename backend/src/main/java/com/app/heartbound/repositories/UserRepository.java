package com.app.heartbound.repositories;

import com.app.heartbound.enums.Role;
import com.app.heartbound.entities.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
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
    
    // Find a user by Riot PUUID (for account linking)
    Optional<User> findByRiotPuuid(String riotPuuid);
}
