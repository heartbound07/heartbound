package com.app.heartbound.dto.riot;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class RiotTokenResponseDTO {
    @JsonProperty("access_token")
    private String accessToken;

    @JsonProperty("refresh_token")
    private String refreshToken; // Store if needed for future API calls

    @JsonProperty("id_token")
    private String idToken; // Useful, often contains PUUID

    @JsonProperty("token_type")
    private String tokenType;

    @JsonProperty("expires_in")
    private int expiresIn;

    private String scope;
}
