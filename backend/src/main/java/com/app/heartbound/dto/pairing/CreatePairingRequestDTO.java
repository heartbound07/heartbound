package com.app.heartbound.dto.pairing;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * CreatePairingRequestDTO
 * 
 * DTO for creating a new pairing.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreatePairingRequestDTO {

    @NotBlank(message = "User1 ID is required")
    private String user1Id;

    @NotBlank(message = "User2 ID is required")
    private String user2Id;

    // Discord user IDs for channel creation (optional - can be null if users don't have Discord linked)
    private String user1DiscordId;
    private String user2DiscordId;

    // Discord channel info (set after channel creation)
    private Long discordChannelId;
    private String discordChannelName;

    @Min(value = 0, message = "Compatibility score must be non-negative")
    @Max(value = 100, message = "Compatibility score must not exceed 100")
    private int compatibilityScore;

    private Integer user1Age;
    private String user1Gender;
    private String user1Region;
    private String user1Rank;
    private Integer user2Age;
    private String user2Gender;
    private String user2Region;
    private String user2Rank;
} 