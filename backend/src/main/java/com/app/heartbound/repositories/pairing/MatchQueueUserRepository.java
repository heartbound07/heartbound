package com.app.heartbound.repositories.pairing;

import com.app.heartbound.entities.MatchQueueUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MatchQueueUserRepository extends JpaRepository<MatchQueueUser, Long> {

    // Find user in queue by user ID
    Optional<MatchQueueUser> findByUserId(String userId);

    // Find all users currently in queue
    List<MatchQueueUser> findByInQueueTrue();

    // Find all users no longer in queue
    List<MatchQueueUser> findByInQueueFalse();

    // Check if user is in queue
    boolean existsByUserIdAndInQueueTrue(String userId);
} 