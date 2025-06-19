package com.app.heartbound.dto;

import com.app.heartbound.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;
import java.util.UUID;
import java.util.Map;

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
    
    // Add roles - you may choose to expose this in profiles if needed
    private Set<Role> roles;
    
    // User's credit balance
    private Integer credits;
    
    // Add level and experience
    private Integer level;
    private Integer experience;
    
    // Add message count field
    private Long messageCount;
    
    // Update the fields for equipped items to match the specific categories
    private UUID equippedUserColorId;
    private UUID equippedListingId;
    private UUID equippedAccentId;

    // Add this field for equipped badges
    private Set<UUID> equippedBadgeIds;
    
    // Add this field to map badge IDs to their thumbnail URLs
    private Map<String, String> badgeUrls;
    
    // Add this field to map badge IDs to their names
    private Map<String, String> badgeNames;
}
