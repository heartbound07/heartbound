package com.app.heartbound.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Pairing Entity
 * 
 * Represents both individual pairings and group channels.
 * Supports backward compatibility with existing pairing system.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "pairings",
       indexes = {
           @Index(name = "idx_user1_id", columnList = "user1_id"),
           @Index(name = "idx_user2_id", columnList = "user2_id"),
           @Index(name = "idx_active", columnList = "active"),
           @Index(name = "idx_user1_user2", columnList = "user1_id, user2_id"),
           @Index(name = "idx_discord_channel", columnList = "discord_channel_id"),
           @Index(name = "idx_group_channel", columnList = "is_group_channel"),
           @Index(name = "idx_group_region", columnList = "group_region")
       })
public class Pairing {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    // Legacy fields for individual pairings (maintained for backward compatibility)
    @Column(name = "user1_id", nullable = false)
    private String user1Id;
    
    @Column(name = "user2_id", nullable = false)
    private String user2Id;

    // Discord channel information
    @Column(name = "discord_channel_id")
    private Long discordChannelId;
    
    @Column(name = "discord_channel_name")
    private String discordChannelName;
    
    // Discord leaderboard integration
    @Column(name = "discord_leaderboard_message_id")
    private String discordLeaderboardMessageId;
    
    @Column(name = "matched_at", nullable = false)
    private LocalDateTime matchedAt;
    
    // Activity metrics (maintained for both individual and group tracking)
    @Builder.Default
    @Column(name = "message_count", nullable = false)
    private int messageCount = 0;
    
    @Builder.Default
    @Column(name = "user1_message_count", nullable = false)
    private int user1MessageCount = 0;
    
    @Builder.Default
    @Column(name = "user2_message_count", nullable = false)
    private int user2MessageCount = 0;
    
    @Builder.Default
    @Column(name = "voice_time_minutes", nullable = false)
    private int voiceTimeMinutes = 0;
    
    @Column(name = "current_voice_session_start")
    private LocalDateTime currentVoiceSessionStart;
    
    @Builder.Default
    @Column(name = "word_count", nullable = false)
    private int wordCount = 0;
    
    @Builder.Default
    @Column(name = "emoji_count", nullable = false)
    private int emojiCount = 0;
    
    @Builder.Default
    @Column(name = "active_days", nullable = false)
    private int activeDays = 0;
    
    @Column(name = "compatibility_score", nullable = false)
    private int compatibilityScore;
    
    // Breakup information
    @Column(name = "breakup_initiator_id")
    private String breakupInitiatorId;
    
    @Column(name = "breakup_reason", columnDefinition = "TEXT")
    private String breakupReason;
    
    @Column(name = "breakup_timestamp")
    private LocalDateTime breakupTimestamp;
    
    @Builder.Default
    @Column(name = "mutual_breakup", nullable = false)
    private boolean mutualBreakup = false;
    
    @Builder.Default
    @Column(name = "active", nullable = false)
    private boolean active = true;
    
    @Builder.Default
    @Column(name = "blacklisted", nullable = false)
    private boolean blacklisted = false;
    
    // Legacy user information for individual pairings
    @Column(name = "user1_age")
    private Integer user1Age;
    
    @Column(name = "user1_gender")
    private String user1Gender;
    
    @Column(name = "user1_region")
    private String user1Region;
    
    @Column(name = "user1_rank")
    private String user1Rank;
    
    @Column(name = "user2_age")
    private Integer user2Age;
    
    @Column(name = "user2_gender")
    private String user2Gender;
    
    @Column(name = "user2_region")
    private String user2Region;
    
    @Column(name = "user2_rank")
    private String user2Rank;
    
    // 🆕 NEW: Group channel fields
    @Builder.Default
    @Column(name = "is_group_channel", nullable = false)
    private boolean isGroupChannel = false;
    
    @Column(name = "group_channel_type")
    private String groupChannelType; // MIXED_GROUP, PAIR, etc.
    
    @Column(name = "group_region")
    private String groupRegion;
    
    @Column(name = "group_user_ids", columnDefinition = "TEXT")
    private String groupUserIds; // JSON array of user IDs
    
    @Column(name = "group_discord_ids", columnDefinition = "TEXT")
    private String groupDiscordIds; // JSON array of Discord IDs
    
    @Column(name = "group_members_data", columnDefinition = "TEXT")
    private String groupMembersData; // JSON array of member details
    
    @Builder.Default
    @Column(name = "total_group_members", nullable = false)
    private int totalGroupMembers = 0;
    
    @Builder.Default
    @Column(name = "male_count", nullable = false)
    private int maleCount = 0;
    
    @Builder.Default
    @Column(name = "female_count", nullable = false)
    private int femaleCount = 0;
    
    @Column(name = "group_created_at")
    private LocalDateTime groupCreatedAt;

    // Helper methods
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
    
    public boolean isIndividualPairing() {
        return !isGroupChannel;
    }
} 