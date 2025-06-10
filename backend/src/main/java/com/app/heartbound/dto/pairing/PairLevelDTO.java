package com.app.heartbound.dto.pairing;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * PairLevelDTO
 * 
 * Data Transfer Object for PairLevel entity responses.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PairLevelDTO {
    private Long id;
    private Long pairingId;
    private int currentLevel;
    private int totalXP;
    private int currentLevelXP;
    private int nextLevelXP;
    private int xpNeededForNextLevel;
    private double levelProgressPercentage;
    private boolean readyToLevelUp;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
} 