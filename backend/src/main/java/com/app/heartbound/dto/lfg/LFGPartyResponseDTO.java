package com.app.heartbound.dto.lfg;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LFGPartyResponseDTO {

    private UUID id;
    private String userId;
    private String game;
    private String title;
    private String description;
    private PartyRequirementsDTO requirements;
    private int expiresIn;
    private int maxPlayers;
    private String status;
    private Instant createdAt;
    private Instant expiresAt;
    private Set<String> participants;
    
    // New fields for additional group information
    private String matchType;
    private String gameMode;
    private String teamSize;
    private String voicePreference;
    private String ageRestriction;

    /**
     * PartyRequirementsDTO
     *
     * Nested DTO to encapsulate party requirements in the response.
     * Contains:
     * - rank: Minimum required rank.
     * - region: Preferred region.
     * - voiceChat: Flag indicating if voice chat is required.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PartyRequirementsDTO {
        private String rank;
        private String region;
        private Boolean voiceChat;
    }
}
