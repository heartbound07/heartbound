package com.app.heartbound.dto.pairing;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * PairingDTO
 * 
 * DTO for pairing information - supports both individual pairings and group channels
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PairingDTO {
    private Long id;
    
    // Legacy pairing fields (maintained for backward compatibility)
    private String user1Id;
    private String user2Id;
    private Long discordChannelId;
    private String discordChannelName;
    private String discordLeaderboardMessageId;
    private LocalDateTime matchedAt;
    private int messageCount;
    private int user1MessageCount;
    private int user2MessageCount;
    private int voiceTimeMinutes;
    private int wordCount;
    private int emojiCount;
    private int activeDays;
    private int compatibilityScore;
    private String breakupInitiatorId;
    private String breakupReason;
    private LocalDateTime breakupTimestamp;
    private boolean mutualBreakup;
    private boolean active;
    private boolean blacklisted;
    
    // Legacy user information
    private Integer user1Age;
    private String user1Gender;
    private String user1Region;
    private String user1Rank;
    private Integer user2Age;
    private String user2Gender;
    private String user2Region;
    private String user2Rank;
    
    // 🆕 NEW: Group channel fields
    private boolean isGroupChannel = false;
    private String groupChannelType; // MIXED_GROUP, PAIR, etc.
    private String groupRegion;
    private List<String> groupUserIds;
    private List<String> groupDiscordIds;
    private List<GroupMemberDTO> groupMembers;
    private int totalGroupMembers = 0;
    private int maleCount = 0;
    private int femaleCount = 0;
    private LocalDateTime groupCreatedAt;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GroupMemberDTO {
        private String userId;
        private String discordId;
        private Integer age;
        private String gender;
        private String region;
        private String rank;
        private String displayName;
        private String avatar;
        private int messageCount = 0;
        private boolean active = true;
    }
    
    // Helper methods
    public boolean isIndividualPairing() {
        return !isGroupChannel;
    }
    
    public boolean involvesUser(String userId) {
        if (isGroupChannel) {
            return groupUserIds != null && groupUserIds.contains(userId);
        } else {
            return userId.equals(user1Id) || userId.equals(user2Id);
        }
    }
    
    public String getOtherUserId(String userId) {
        if (isGroupChannel) {
            return null; // Not applicable for groups
        }
        
        if (userId.equals(user1Id)) {
            return user2Id;
        } else if (userId.equals(user2Id)) {
            return user1Id;
        }
        return null;
    }
} 