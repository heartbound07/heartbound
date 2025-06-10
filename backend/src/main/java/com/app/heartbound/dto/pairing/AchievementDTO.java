package com.app.heartbound.dto.pairing;

import com.app.heartbound.enums.AchievementType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * AchievementDTO
 * 
 * Data Transfer Object for Achievement entity responses.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AchievementDTO {
    private Long id;
    private String achievementKey;
    private String name;
    private String description;
    private AchievementType achievementType;
    private int xpReward;
    private int requirementValue;
    private String requirementDescription;
    private String iconUrl;
    private String badgeColor;
    private String rarity;
    private String tier;
    private boolean active;
    private boolean hidden;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
} 