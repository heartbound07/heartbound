package com.app.heartbound.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * VoiceStreak Entity
 * 
 * Tracks daily voice activity streaks for pairings.
 * Records consecutive days where the pair spent time in voice channels together.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "voice_streaks",
       indexes = {
           @Index(name = "idx_voice_streak_pairing", columnList = "pairing_id"),
           @Index(name = "idx_voice_streak_date", columnList = "streak_date"),
           @Index(name = "idx_voice_streak_pairing_date", columnList = "pairing_id, streak_date", unique = true),
           @Index(name = "idx_voice_streak_active", columnList = "active")
       })
public class VoiceStreak {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "pairing_id", nullable = false)
    private Pairing pairing;

    @Column(name = "streak_date", nullable = false)
    private LocalDate streakDate; // Date of the voice activity

    @Column(name = "voice_minutes", nullable = false)
    private int voiceMinutes; // Minutes spent in voice on this date

    @Column(name = "streak_count", nullable = false)
    private int streakCount; // Current streak count at this date

    @Builder.Default
    @Column(name = "active", nullable = false)
    private boolean active = true; // Whether this streak record is active

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Check if this streak record is from today
     */
    public boolean isToday() {
        return streakDate.equals(LocalDate.now());
    }

    /**
     * Check if this streak record is from yesterday
     */
    public boolean isYesterday() {
        return streakDate.equals(LocalDate.now().minusDays(1));
    }

    /**
     * Check if this streak entry meets the minimum activity threshold
     */
    public boolean meetsMinimumActivity() {
        return voiceMinutes >= 30; // Minimum 30 minutes for streak to count
    }

    /**
     * Get the XP reward for this streak milestone
     */
    public int getStreakXPReward() {
        if (streakCount >= 30) return 500; // 1 month streak
        if (streakCount >= 14) return 200; // 2 week streak
        if (streakCount >= 7) return 100;  // 1 week streak
        if (streakCount >= 3) return 50;   // 3 day streak
        return 0;
    }

    /**
     * Get streak tier based on count
     */
    public String getStreakTier() {
        if (streakCount >= 30) return "Legendary";
        if (streakCount >= 14) return "Epic";
        if (streakCount >= 7) return "Rare";
        if (streakCount >= 3) return "Common";
        return "None";
    }
} 