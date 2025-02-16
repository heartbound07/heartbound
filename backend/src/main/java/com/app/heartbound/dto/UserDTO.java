package com.app.heartbound.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
}
