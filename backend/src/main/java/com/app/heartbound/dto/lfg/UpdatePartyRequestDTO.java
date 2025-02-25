package com.app.heartbound.dto.lfg;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdatePartyRequestDTO {

    private String game;
    private String title;
    private String description;
    
    @Valid
    private PartyRequirementsDTO requirements;
    
    // Using wrapper types for optional fields
    private Integer expiresIn;
    private Integer maxPlayers;

    /**
     * PartyRequirementsDTO
     *
     * Nested DTO to encapsulate party requirements.
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
