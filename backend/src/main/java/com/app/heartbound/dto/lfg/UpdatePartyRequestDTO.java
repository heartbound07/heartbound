package com.app.heartbound.dto.lfg;

import com.app.heartbound.enums.Rank;
import com.app.heartbound.enums.Region;
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
    
    @CharacterCount(min = 1, max = 50, message = "Title must be between 1 and 50 characters")
    private String title;
    
    @CharacterCount(min = 1, max = 100, message = "Description must be between 1 and 100 characters")
    private String description;
    
    @Valid
    private PartyRequirementsDTO requirements;
    
    // Using wrapper types for optional fields
    private Integer expiresIn;
    private Integer maxPlayers;
    
    // New optional fields for additional group creation values
    private String matchType;
    private String gameMode;
    private String teamSize;
    private String voicePreference;
    private String ageRestriction;

    /**
     * PartyRequirementsDTO
     *
     * Nested DTO to encapsulate party requirements.
     * Contains:
     * - rank: Minimum required rank.
     * - region: Preferred region.
     * - inviteOnly: Flag indicating if the party is invite only.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PartyRequirementsDTO {
        private Rank rank;
        private Region region;
        private Boolean inviteOnly;
    }
}
