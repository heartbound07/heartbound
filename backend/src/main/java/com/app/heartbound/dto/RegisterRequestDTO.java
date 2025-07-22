package com.app.heartbound.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

@Data
@Schema(description = "Request payload for user registration.")
public class RegisterRequestDTO {

    @Schema(description = "The desired username for the user", example = "new_user")
    @NotEmpty(message = "Username cannot be empty")
    @Size(min = 3, max = 32, message = "Username must be between 3 and 32 characters")
    private String username;

    @Schema(description = "The desired password for the user", example = "securePassword123!")
    @NotEmpty(message = "Password cannot be empty")
    @Size(min = 8, message = "Password must be at least 8 characters long")
    private String password;

    @Schema(description = "The email address for the user", example = "new_user@example.com")
    @NotEmpty(message = "Email cannot be empty")
    @Email(message = "Email should be valid")
    private String email;
} 