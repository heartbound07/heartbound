package com.app.heartbound.dto.pairing;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for admin-only achievement management (unlock/lock)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManageAchievementDTO {
    
    /**
     * ID of the achievement to manage
     */
    private Long achievementId;
    
    /**
     * Action to perform: "unlock" or "lock"
     */
    private String action;
    
    /**
     * Custom XP amount to award (optional, overrides default)
     */
    private Integer customXP;
} 