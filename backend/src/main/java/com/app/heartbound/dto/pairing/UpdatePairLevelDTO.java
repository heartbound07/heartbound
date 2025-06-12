package com.app.heartbound.dto.pairing;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for admin-only pair level and XP updates
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdatePairLevelDTO {
    
    /**
     * Set the current level directly (optional)
     */
    private Integer currentLevel;
    
    /**
     * Set the total XP directly (optional)
     */
    private Integer totalXP;
    
    /**
     * Add or subtract XP (can be negative) (optional)
     */
    private Integer xpIncrement;
} 