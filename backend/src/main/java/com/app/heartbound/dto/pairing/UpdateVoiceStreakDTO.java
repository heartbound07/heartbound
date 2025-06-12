package com.app.heartbound.dto.pairing;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for admin-only voice streak updates
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateVoiceStreakDTO {
    
    /**
     * Date of the streak (ISO format)
     */
    private String streakDate;
    
    /**
     * Voice minutes for this streak
     */
    private Integer voiceMinutes;
    
    /**
     * Override streak count (optional)
     */
    private Integer streakCount;
    
    /**
     * Whether this streak is active (optional)
     */
    private Boolean active;
} 