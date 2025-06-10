package com.app.heartbound.dto.pairing;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * VoiceStreakDTO
 * 
 * Data Transfer Object for VoiceStreak entity responses.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VoiceStreakDTO {
    private Long id;
    private Long pairingId;
    private LocalDate streakDate;
    private int voiceMinutes;
    private int streakCount;
    private boolean active;
    private boolean isToday;
    private boolean isYesterday;
    private boolean meetsMinimumActivity;
    private int streakXPReward;
    private String streakTier;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
} 