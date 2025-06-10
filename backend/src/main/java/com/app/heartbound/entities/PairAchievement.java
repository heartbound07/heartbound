package com.app.heartbound.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * PairAchievement Entity
 * 
 * Tracks completed achievements for each pairing.
 * Links a specific pairing to a specific achievement with completion metadata.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "pair_achievements",
       uniqueConstraints = @UniqueConstraint(columnNames = {"pairing_id", "achievement_id"}),
       indexes = {
           @Index(name = "idx_pair_achievement_pairing", columnList = "pairing_id"),
           @Index(name = "idx_pair_achievement_achievement", columnList = "achievement_id"),
           @Index(name = "idx_pair_achievement_unlocked", columnList = "unlocked_at"),
           @Index(name = "idx_pair_achievement_pair_unlocked", columnList = "pairing_id, unlocked_at")
       })
public class PairAchievement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "pairing_id", nullable = false)
    private Pairing pairing;

    @ManyToOne
    @JoinColumn(name = "achievement_id", nullable = false)
    private Achievement achievement;

    @Column(name = "unlocked_at", nullable = false)
    private LocalDateTime unlockedAt;

    @Column(name = "progress_value")
    private Integer progressValue; // The actual value when achievement was unlocked (e.g., 1500 messages)

    @Column(name = "xp_awarded", nullable = false)
    private int xpAwarded; // XP actually awarded (may differ from achievement reward due to bonuses)

    @Column(name = "notified", nullable = false)
    private boolean notified = false; // Whether users have been notified about this achievement

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (unlockedAt == null) {
            unlockedAt = LocalDateTime.now();
        }
    }

    /**
     * Check if this achievement was recently unlocked (within last 24 hours)
     */
    public boolean isRecentlyUnlocked() {
        return unlockedAt.isAfter(LocalDateTime.now().minusDays(1));
    }

    /**
     * Get a display-friendly unlock time
     */
    public String getUnlockTimeDisplay() {
        LocalDateTime now = LocalDateTime.now();
        long minutesAgo = java.time.Duration.between(unlockedAt, now).toMinutes();
        
        if (minutesAgo < 60) {
            return minutesAgo + " minutes ago";
        } else if (minutesAgo < 1440) { // 24 hours
            return (minutesAgo / 60) + " hours ago";
        } else {
            return (minutesAgo / 1440) + " days ago";
        }
    }
} 