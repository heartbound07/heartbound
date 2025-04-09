package com.app.heartbound.dto;

import com.app.heartbound.enums.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

/**
 * Data transfer object for user profile information.
 * Contains minimal user data needed for UI display.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Data transfer object for user profile information")
public class UserProfileDTO {
    @Schema(description = "The user's unique identifier")
    private String id;
    
    @Schema(description = "The username of the user")
    private String username;
    
    @Schema(description = "The avatar URL or identifier")
    private String avatar;
    
    @Schema(description = "The display name chosen by the user")
    private String displayName;
    
    @Schema(description = "The user's preferred pronouns")
    private String pronouns;
    
    @Schema(description = "The user's about me text")
    private String about;
    
    @Schema(description = "The user's banner color preference")
    private String bannerColor;
    
    @Schema(description = "The user's custom banner URL")
    private String bannerUrl;
    
    @Schema(description = "The roles assigned to the user")
    private Set<Role> roles;
    
    @Schema(description = "User's credit balance")
    private Integer credits;
    
    // --- New Riot Account Fields ---
    @Schema(description = "The Riot Games in-game name", example = "RiotPlayer")
    private String riotGameName;
    
    @Schema(description = "The Riot Games tag line (part after #)", example = "NA1")
    private String riotTagLine;
    
    @Schema(description = "Whether the user has linked their Riot account")
    private boolean riotAccountLinked;
    // --- End Riot Account Fields ---
}
