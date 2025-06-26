package com.app.heartbound.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Pairing Entity
 * 
 * Represents a pairing between two users in the "Don't Catch Feelings Challenge".
 * Tracks their compatibility, activity metrics, and breakup information.
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
           @Index(name = "idx_discord_channel", columnList = "discord_channel_id")
       })
public class Pairing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user1_id", nullable = false)
    private String user1Id;

    @Column(name = "user2_id", nullable = false)
    private String user2Id;

    // Note: Discord snowflake IDs are typically 64-bit, but using Long as specified
    // Consider String if integration issues arise with Discord API
    @Column(name = "discord_channel_id")
    private Long discordChannelId;

    @Column(name = "discord_channel_name")
    private String discordChannelName;

    // Discord leaderboard message ID for persistent tracking
    @Column(name = "discord_leaderboard_message_id")
    private String discordLeaderboardMessageId;

    @Column(name = "matched_at", nullable = false)
    private LocalDateTime matchedAt;

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

    // Helper method to check if a user is part of this pairing
    public boolean involvesUser(String userId) {
        return user1Id.equals(userId) || user2Id.equals(userId);
    }

    // Helper method to get the other user in the pairing
    public String getOtherUserId(String userId) {
        if (user1Id.equals(userId)) {
            return user2Id;
        } else if (user2Id.equals(userId)) {
            return user1Id;
        }
        throw new IllegalArgumentException("User " + userId + " is not part of this pairing");
    }
} 