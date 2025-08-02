package com.app.heartbound.dto.pairing;

import com.app.heartbound.dto.PublicUserProfileDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Optimized DTO for pairing leaderboard with embedded user profiles")
public class PairingLeaderboardDTO {

    @Schema(description = "Unique identifier of the pairing", example = "1")
    private Long id;

    @Schema(description = "First user's ID", example = "1234567890")
    private String user1Id;

    @Schema(description = "Second user's ID", example = "0987654321")
    private String user2Id;

    @Schema(description = "Embedded profile data for first user")
    private PublicUserProfileDTO user1Profile;

    @Schema(description = "Embedded profile data for second user")
    private PublicUserProfileDTO user2Profile;

    @Schema(description = "Discord channel name for this pairing", example = "heartbound-pair-123")
    private String discordChannelName;

    @Schema(description = "When the pairing was created", example = "2024-01-15T10:30:00")
    private LocalDateTime matchedAt;

    @Schema(description = "Total messages sent by both users", example = "1250")
    private int messageCount;

    @Schema(description = "Messages sent by first user", example = "620")
    private int user1MessageCount;

    @Schema(description = "Messages sent by second user", example = "630")
    private int user2MessageCount;

    @Schema(description = "Total voice time in minutes", example = "540")
    private int voiceTimeMinutes;

    @Schema(description = "Total word count across all messages", example = "15750")
    private int wordCount;

    @Schema(description = "Total emoji count", example = "234")
    private int emojiCount;

    @Schema(description = "Number of active days", example = "15")
    private int activeDays;

    @Schema(description = "Compatibility score between users", example = "85")
    private double compatibilityScore;

    @Schema(description = "Current level of the pairing", example = "5")
    private int currentLevel;

    @Schema(description = "Total XP earned by the pairing", example = "2500")
    private int totalXP;

    @Schema(description = "Current voice streak count", example = "7")
    private int currentStreak;

    @Schema(description = "Whether this pairing is currently active", example = "true")
    private boolean active;
} 