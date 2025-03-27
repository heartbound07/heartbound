package com.app.heartbound.dto.lfg;

import com.app.heartbound.enums.Rank;
import com.app.heartbound.enums.Region;
import com.app.heartbound.dto.lfg.CharacterCount;
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
 *   "maxPlayers": 5,
 *   "matchType": "casual",
 *   "gameMode": "unrated",
 *   "teamSize": "duo",
 *   "voicePreference": "discord",
 *   "ageRestriction": "any"
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
    @CharacterCount(min = 1, max = 50, message = "Title must be between 1 and 50 characters")
    private String title;

    @NotBlank(message = "Description is required")
    @CharacterCount(min = 1, max = 100, message = "Description must be between 1 and 100 characters")
    private String description;

    @NotNull(message = "Party requirements must be provided")
    @Valid
    private PartyRequirementsDTO requirements;

    @Positive(message = "Expires in must be a positive number")
    private int expiresIn;

    @Positive(message = "Max players must be a positive number")
    private int maxPlayers;

    @NotBlank(message = "Match type is required")
    private String matchType;

    @NotBlank(message = "Game mode is required")
    private String gameMode;

    @NotBlank(message = "Team size is required")
    private String teamSize;

    @NotBlank(message = "Voice preference is required")
    private String voicePreference;

    @NotBlank(message = "Age restriction is required")
    private String ageRestriction;

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

        @NotNull(message = "Rank is required")
        private Rank rank;

        @NotNull(message = "Region is required")
        private Region region;

        @NotNull(message = "Invite only flag is required")
        private Boolean inviteOnly;
    }
}
