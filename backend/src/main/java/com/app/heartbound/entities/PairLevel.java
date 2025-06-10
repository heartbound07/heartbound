package com.app.heartbound.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * PairLevel Entity
 * 
 * Tracks XP and level progression for each pairing in the system.
 * Each pairing has a single level that represents the combined progress of both users.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "pair_levels",
       indexes = {
           @Index(name = "idx_pair_level_pairing_id", columnList = "pairing_id"),
           @Index(name = "idx_pair_level_current_level", columnList = "current_level"),
           @Index(name = "idx_pair_level_total_xp", columnList = "total_xp")
       })
public class PairLevel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "pairing_id", nullable = false, unique = true)
    private Pairing pairing;

    @Builder.Default
    @Column(name = "current_level", nullable = false)
    private int currentLevel = 1;

    @Builder.Default
    @Column(name = "total_xp", nullable = false)
    private int totalXP = 0;

    @Builder.Default
    @Column(name = "current_level_xp", nullable = false)
    private int currentLevelXP = 0;

    @Builder.Default
    @Column(name = "next_level_xp", nullable = false)
    private int nextLevelXP = 100; // XP required for next level

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
     * Calculate XP needed for next level
     */
    public int getXPNeededForNextLevel() {
        return nextLevelXP - currentLevelXP;
    }

    /**
     * Calculate level progress percentage (0-100)
     */
    public double getLevelProgressPercentage() {
        if (nextLevelXP == 0) return 100.0;
        return ((double) currentLevelXP / nextLevelXP) * 100.0;
    }

    /**
     * Check if ready to level up
     */
    public boolean isReadyToLevelUp() {
        return currentLevelXP >= nextLevelXP;
    }

    /**
     * Calculate next level XP requirement using exponential growth
     * Base: 100 XP for level 2, increases by 25% each level
     */
    public static int calculateNextLevelXP(int level) {
        if (level <= 1) return 100;
        return (int) Math.ceil(100 * Math.pow(1.25, level - 1));
    }

    /**
     * Level up the pair level
     */
    public void levelUp() {
        currentLevel++;
        currentLevelXP = 0;
        nextLevelXP = calculateNextLevelXP(currentLevel);
    }
} 