package com.app.heartbound.dto;

import com.app.heartbound.enums.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Data Transfer Object that represents a user.")
public class UserDTO {

    @Schema(description = "The external (OAuth) user identifier", example = "1234567890")
    private String id;

    @Schema(description = "The username of the user", example = "john_doe")
    private String username;

    @Schema(description = "The discriminator for the user", example = "0001")
    private String discriminator;

    @Schema(description = "The avatar URL or identifier", example = "avatar1234.png")
    private String avatar;

    @Schema(description = "The email address of the user", example = "johndoe@example.com")
    private String email;
    
    @Schema(description = "The roles assigned to the user", example = "[\"USER\", \"MONARCH\"]")
    private Set<Role> roles;
    
    @Schema(description = "The number of credits the user has", example = "100")
    private Integer credits = 0;
    
    // --- New Riot Account Fields ---
    @Schema(description = "The Riot Games Player Universally Unique ID", example = "aBc123dE-45fG-678h-9IjK-lMnOpQrStUv")
    private String riotPuuid;
    
    @Schema(description = "The Riot Games in-game name", example = "RiotPlayer")
    private String riotGameName;
    
    @Schema(description = "The Riot Games tag line (part after #)", example = "NA1")
    private String riotTagLine;
    // --- End Riot Account Fields ---
}
