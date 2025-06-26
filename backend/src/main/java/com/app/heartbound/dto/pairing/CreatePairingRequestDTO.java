package com.app.heartbound.dto.pairing;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO for creating pairings or group channels
 * Supports both individual pairings and group channels (10 users)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreatePairingRequestDTO {
    
    // Legacy fields for individual pairings (maintained for backward compatibility)
    @NotBlank(message = "User1 ID is required")
    private String user1Id;
    
    @NotBlank(message = "User2 ID is required") 
    private String user2Id;
    
    // Discord IDs for legacy pairing
    private String user1DiscordId;
    private String user2DiscordId;
    
    // Discord channel information
    private Long discordChannelId;
    private String discordChannelName;
    
    @Min(value = 0, message = "Compatibility score must be non-negative")
    @Max(value = 100, message = "Compatibility score must not exceed 100")
    private int compatibilityScore;
    
    // Legacy user information for individual pairings
    private Integer user1Age;
    private String user1Gender;
    private String user1Region;
    private String user1Rank;
    private Integer user2Age;
    private String user2Gender;
    private String user2Region;
    private String user2Rank;
    
    // 🆕 NEW: Group channel fields
    @NotEmpty(message = "Group user IDs cannot be empty")
    private List<String> groupUserIds;
    
    @NotEmpty(message = "Group Discord IDs cannot be empty")
    private List<String> groupDiscordIds;
    
    private boolean isGroupChannel = false;
    
    private String groupRegion; // All users must have same region
    private String groupChannelType = "MIXED_GROUP"; // MIXED_GROUP, PAIR, etc.
    
    // Group member details (for tracking)
    private List<GroupMemberInfo> groupMembers;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GroupMemberInfo {
        private String userId;
        private String discordId;
        private Integer age;
        private String gender;
        private String region;
        private String rank;
    }
} 