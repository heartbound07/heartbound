package com.app.heartbound.dto.pairing;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * PublicPairingDTO
 * 
 * Public-facing DTO for displaying pairing information without sensitive user data.
 * Used for endpoints like /pairings/active where all users can see basic pairing info.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicPairingDTO {
    private Long id;
    private String user1Id;
    private String user2Id;
    private String discordChannelName;
    private LocalDateTime matchedAt;
    private int messageCount;
    private int user1MessageCount;
    private int user2MessageCount;
    private int voiceTimeMinutes;
    private int wordCount;
    private int emojiCount;
    private int activeDays;
    private double compatibilityScore;
    private String breakupInitiatorId;
    private String breakupReason;
    private LocalDateTime breakupTimestamp;
    private boolean mutualBreakup;
    private boolean active;
    private boolean blacklisted;
} 