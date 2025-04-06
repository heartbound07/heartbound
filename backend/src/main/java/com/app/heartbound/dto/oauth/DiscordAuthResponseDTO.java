package com.app.heartbound.dto.oauth;

import com.app.heartbound.dto.UserDTO;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DiscordAuthResponseDTO {
    private String accessToken;
    private String refreshToken;
    private String tokenType = "bearer"; // Default to bearer
    private long expiresIn; // In seconds
    private String scope;
    private UserDTO user;
} 