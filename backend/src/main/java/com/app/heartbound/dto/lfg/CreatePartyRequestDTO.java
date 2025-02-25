package com.app.heartbound.dto.lfg;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * CreatePartyRequestDTO
 *
 * DTO for creating a new LFG party.
 *
 * Expected JSON payload:
 * {
 *   "game": "Valorant",
 *   "title": "Need a duo partner",
 *   "description": "Looking for someone to join my party for ranked games and fun times.",
 *   "requirements": {
 *     "rank": "Diamond",
 *     "region": "NA",
 *     "voiceChat": true
 *   },
 *   "expiresIn": 30,
 *   "maxPlayers": 5
 * }
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreatePartyRequestDTO {

    @NotBlank(message = "Game is required")
    private String game;

    @NotBlank(message = "Title is required")
    private String title;

    @NotBlank(message = "Description is required")
    private String description;

    @NotNull(message = "Party requirements must be provided")
    @Valid
    private PartyRequirementsDTO requirements;

    @Positive(message = "Expires in must be a positive number")
    private int expiresIn;

    @Positive(message = "Max players must be a positive number")
    private int maxPlayers;

    /**
     * PartyRequirementsDTO
     *
     * Nested DTO to encapsulate party requirements.
     * Note: The accepted languages field has been removed per updated requirements.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PartyRequirementsDTO {

        @NotBlank(message = "Rank is required")
        private String rank;

        @NotBlank(message = "Region is required")
        private String region;

        @NotNull(message = "Voice chat flag is required")
        private Boolean voiceChat;
    }
}
