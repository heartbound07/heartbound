package com.app.heartbound.dto.pairing;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * PairAchievementDTO
 * 
 * Data Transfer Object for PairAchievement entity responses.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PairAchievementDTO {
    private Long id;
    private Long pairingId;
    private AchievementDTO achievement;
    private LocalDateTime unlockedAt;
    private Integer progressValue;
    private int xpAwarded;
    private boolean notified;
    private boolean recentlyUnlocked;
    private String unlockTimeDisplay;
    private LocalDateTime createdAt;
} 