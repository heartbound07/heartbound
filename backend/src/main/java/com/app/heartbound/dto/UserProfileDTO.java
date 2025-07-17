package com.app.heartbound.dto;

import com.app.heartbound.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;
import java.util.UUID;

/**
 * Data transfer object for user profile information.
 * Contains minimal user data needed for UI display.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserProfileDTO {
    private String id;
    private String username;
    private String avatar;
    private String displayName;
    private String pronouns;
    private String about;
    private String bannerColor;
    private String bannerUrl;
    private boolean banned;
    
    // Add roles - you may choose to expose this in profiles if needed
    private Set<Role> roles;
    
    // User's credit balance
    private Integer credits;
    
    // Add level and experience
    private Integer level;
    private Integer experience;
    
    // Add XP required for next level
    private Integer xpForNextLevel;
    
    // Add message count field
    private Long messageCount;
    
    // Add fish caught count
    private Integer fishCaughtCount;

    // Add time-based message count fields
    private Integer messagesToday;
    private Integer messagesThisWeek;
    private Integer messagesThisTwoWeeks;
    
    // Add voice activity fields
    private Integer voiceRank;
    private Integer voiceTimeMinutesToday;
    private Integer voiceTimeMinutesThisWeek;
    private Integer voiceTimeMinutesThisTwoWeeks;
    
    // Add total voice time field for lifetime statistics
    private Integer voiceTimeMinutesTotal;
    
    // Update the fields for equipped items to match the specific categories
    private UUID equippedUserColorId;
    private UUID equippedListingId;
    private UUID equippedAccentId;

    // Add this field for single equipped badge
    private UUID equippedBadgeId;
    
    // Add this field for the equipped badge's thumbnail URL
    private String badgeUrl;
    
    // Add this field for the equipped badge's name
    private String badgeName;
    
    // Add resolved nameplate color for frontend preview
    private String nameplateColor;
    
    // Daily claim system fields
    private Integer dailyStreak;
    private java.time.LocalDateTime lastDailyClaim;

    // Add role selection fields
    private String selectedAgeRoleId;
    private String selectedGenderRoleId;
    private String selectedRankRoleId;
    private String selectedRegionRoleId;
    
    // Add fishing limit cooldown field
    private java.time.LocalDateTime fishingLimitCooldownUntil;
}
