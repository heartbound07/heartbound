package com.app.heartbound.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * BlacklistEntry Entity
 * 
 * Represents a blacklisted pair of users who should not be matched again.
 * Typically created after a pairing breakup to prevent re-pairing.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "blacklist_entries",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user1_id", "user2_id"}),
       indexes = {
           @Index(name = "idx_blacklist_user1", columnList = "user1_id"),
           @Index(name = "idx_blacklist_user2", columnList = "user2_id"),
           @Index(name = "idx_blacklist_pair", columnList = "user1_id, user2_id"),
           @Index(name = "idx_blacklist_created", columnList = "created_at")
       })
public class BlacklistEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user1_id", nullable = false)
    private String user1Id;

    @Column(name = "user2_id", nullable = false)
    private String user2Id;

    @Column(name = "reason")
    private String reason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    // Helper method to check if this entry matches a pair (order-independent)
    public boolean matchesPair(String userId1, String userId2) {
        return (user1Id.equals(userId1) && user2Id.equals(userId2)) ||
               (user1Id.equals(userId2) && user2Id.equals(userId1));
    }

    // Static helper method to create a blacklist entry with consistent ordering
    public static BlacklistEntry create(String userId1, String userId2, String reason) {
        // Ensure consistent ordering (lower ID first) to prevent duplicate entries
        String firstUser = userId1.compareTo(userId2) < 0 ? userId1 : userId2;
        String secondUser = userId1.compareTo(userId2) < 0 ? userId2 : userId1;
        
        return BlacklistEntry.builder()
                .user1Id(firstUser)
                .user2Id(secondUser)
                .reason(reason)
                .build();
    }
} 