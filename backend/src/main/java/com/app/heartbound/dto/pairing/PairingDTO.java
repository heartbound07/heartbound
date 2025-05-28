package com.app.heartbound.dto.pairing;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * PairingDTO
 * 
 * Data Transfer Object for Pairing entity responses.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PairingDTO {
    private Long id;
    private String user1Id;
    private String user2Id;
    private Long discordChannelId;
    private LocalDateTime matchedAt;
    private int messageCount;
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
} 