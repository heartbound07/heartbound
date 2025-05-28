package com.app.heartbound.entities;

import com.app.heartbound.enums.Rank;
import com.app.heartbound.enums.Region;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * MatchQueueUser Entity
 * 
 * Represents a user in the matchmaking queue with their preferences.
 * Used for persistent queue management in the matchmaking system.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "match_queue_users",
       indexes = {
           @Index(name = "idx_queue_user_id", columnList = "user_id"),
           @Index(name = "idx_queue_in_queue", columnList = "in_queue"),
           @Index(name = "idx_queue_queued_at", columnList = "queued_at"),
           @Index(name = "idx_queue_region", columnList = "region"),
           @Index(name = "idx_queue_rank", columnList = "rank"),
           @Index(name = "idx_queue_active_users", columnList = "in_queue, queued_at")
       })
public class MatchQueueUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private String userId;

    @Column(name = "age", nullable = false)
    private int age;

    @Enumerated(EnumType.STRING)
    @Column(name = "region", nullable = false)
    private Region region;

    @Enumerated(EnumType.STRING)
    @Column(name = "rank", nullable = false)
    private Rank rank;

    @Column(name = "queued_at", nullable = false)
    private LocalDateTime queuedAt;

    @Builder.Default
    @Column(name = "in_queue", nullable = false)
    private boolean inQueue = true;

    @PrePersist
    protected void onCreate() {
        if (queuedAt == null) {
            queuedAt = LocalDateTime.now();
        }
    }
} 