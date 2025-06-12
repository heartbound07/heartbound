package com.app.heartbound.dto.pairing;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for admin-only voice streak creation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateVoiceStreakDTO {
    
    /**
     * Date of the streak (ISO format)
     */
    private String streakDate;
    
    /**
     * Voice minutes for this streak
     */
    private Integer voiceMinutes;
    
    /**
     * Streak count for this entry
     */
    private Integer streakCount;
    
    /**
     * Whether this streak is active
     */
    private Boolean active;
} 