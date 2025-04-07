package com.app.heartbound.dto.oauth;

import com.app.heartbound.dto.UserDTO;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthSuccessResponseDTO {
    @JsonProperty("access_token")
    private String accessToken;

    @JsonProperty("refresh_token")
    private String refreshToken;

    @JsonProperty("token_type")
    private String tokenType = "bearer"; // Default to bearer

    @JsonProperty("expires_in")
    private long expiresIn; // In seconds

    @JsonProperty("scope")
    private String scope;

    @JsonProperty("user")
    private UserDTO user;
} 