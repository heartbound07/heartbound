package com.app.heartbound.entities;

import com.app.heartbound.enums.AchievementType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Achievement Entity
 * 
 * Defines the available achievements in the system, their requirements,
 * XP rewards, and metadata. This acts as a template/definition for achievements.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "achievements",
       indexes = {
           @Index(name = "idx_achievement_type", columnList = "achievement_type"),
           @Index(name = "idx_achievement_active", columnList = "active"),
           @Index(name = "idx_achievement_key", columnList = "achievement_key", unique = true)
       })
public class Achievement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "achievement_key", nullable = false, unique = true, length = 100)
    private String achievementKey; // Unique identifier (e.g., "MESSAGE_MILESTONE_1000")

    @Column(name = "name", nullable = false, length = 200)
    private String name; // Display name (e.g., "Chatterbox")

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description; // Description (e.g., "Send 1000 messages together")

    @Enumerated(EnumType.STRING)
    @Column(name = "achievement_type", nullable = false)
    private AchievementType achievementType; // MESSAGE_MILESTONE, WEEKLY_ACTIVITY, VOICE_STREAK, etc.

    @Column(name = "xp_reward", nullable = false)
    private int xpReward; // XP awarded when achievement is completed

    @Column(name = "requirement_value", nullable = false)
    private int requirementValue; // Numeric requirement (e.g., 1000 for 1000 messages)

    @Column(name = "requirement_description", length = 500)
    private String requirementDescription; // Human-readable requirement description

    @Column(name = "icon_url")
    private String iconUrl; // Optional icon URL for the achievement

    @Column(name = "badge_color", length = 20)
    private String badgeColor; // Color for achievement badge (e.g., "gold", "silver", "bronze")

    @Builder.Default
    @Column(name = "rarity", length = 20)
    private String rarity = "common"; // Achievement rarity (common, rare, epic, legendary)

    @Builder.Default
    @Column(name = "active", nullable = false)
    private boolean active = true; // Whether this achievement is currently active

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
     * Check if this achievement should be hidden until unlocked
     */
    public boolean isHidden() {
        return rarity.equals("legendary") || achievementType == AchievementType.SPECIAL;
    }

    /**
     * Get the display tier based on requirement value for milestone achievements
     */
    public String getTier() {
        if (achievementType == AchievementType.MESSAGE_MILESTONE) {
            if (requirementValue >= 10000) return "Diamond";
            if (requirementValue >= 5000) return "Gold";
            if (requirementValue >= 1000) return "Silver";
            return "Bronze";
        }
        return "Standard";
    }
} 