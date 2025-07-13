package com.app.heartbound.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * PendingRoleSelection Entity
 * 
 * Stores role selections for Discord users who haven't registered on the website yet.
 * When they register, these selections are automatically applied to their User profile.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "pending_role_selections",
       indexes = {
           @Index(name = "idx_pending_user_id", columnList = "discord_user_id"),
           @Index(name = "idx_pending_updated_at", columnList = "updated_at")
       })
public class PendingRoleSelection {
    
    @Id
    private String discordUserId; // Discord user ID as primary key
    
    private String selectedAgeRoleId;
    private String selectedGenderRoleId;
    private String selectedRankRoleId;
    private String selectedRegionRoleId;
    
    @UpdateTimestamp
    private LocalDateTime updatedAt;
    
    /**
     * Check if user has selected a role in a specific category
     */
    public boolean hasRoleInCategory(String category) {
        return switch (category.toLowerCase()) {
            case "age" -> selectedAgeRoleId != null && !selectedAgeRoleId.isBlank();
            case "gender" -> selectedGenderRoleId != null && !selectedGenderRoleId.isBlank();
            case "rank" -> selectedRankRoleId != null && !selectedRankRoleId.isBlank();
            case "region" -> selectedRegionRoleId != null && !selectedRegionRoleId.isBlank();
            default -> false;
        };
    }
    
    /**
     * Get the selected role ID for a specific category
     */
    public String getRoleIdForCategory(String category) {
        return switch (category.toLowerCase()) {
            case "age" -> selectedAgeRoleId;
            case "gender" -> selectedGenderRoleId;
            case "rank" -> selectedRankRoleId;
            case "region" -> selectedRegionRoleId;
            default -> null;
        };
    }
    
    /**
     * Set the selected role ID for a specific category
     */
    public void setRoleIdForCategory(String category, String roleId) {
        switch (category.toLowerCase()) {
            case "age" -> this.selectedAgeRoleId = roleId;
            case "gender" -> this.selectedGenderRoleId = roleId;
            case "rank" -> this.selectedRankRoleId = roleId;
            case "region" -> this.selectedRegionRoleId = roleId;
        }
    }
    
    /**
     * Check if user has selected all required roles
     */
    public boolean hasAllRequiredRoles() {
        return selectedAgeRoleId != null && !selectedAgeRoleId.isBlank() &&
               selectedGenderRoleId != null && !selectedGenderRoleId.isBlank() &&
               selectedRankRoleId != null && !selectedRankRoleId.isBlank() &&
               selectedRegionRoleId != null && !selectedRegionRoleId.isBlank();
    }
} 