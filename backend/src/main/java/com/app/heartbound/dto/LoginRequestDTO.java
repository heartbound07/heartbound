package com.app.heartbound.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Request payload for username and password login.")
public class LoginRequestDTO {

    @Schema(description = "The username of the user", example = "john_doe")
    private String username;

    @Schema(description = "The password of the user", example = "password123")
    private String password;
} 