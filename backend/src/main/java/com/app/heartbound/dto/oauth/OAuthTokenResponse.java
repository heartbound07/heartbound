package com.app.heartbound.dto.oauth;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "OAuth token response containing access token, refresh token, expiration time, and scope details.")
public class OAuthTokenResponse {

    @JsonProperty("access_token")
    @Schema(description = "Access token issued by the OAuth provider", example = "abcd1234")
    private String accessToken;

    @JsonProperty("token_type")
    @Schema(description = "Token type (e.g., bearer)", example = "bearer")
    private String tokenType;

    @JsonProperty("expires_in")
    @Schema(description = "Expiration duration of the access token in seconds", example = "3600")
    private int expiresIn;

    @JsonProperty("refresh_token")
    @Schema(description = "Refresh token used to obtain new access tokens", example = "refresh1234")
    private String refreshToken;

    @Schema(description = "Scope of the access token", example = "identify email")
    private String scope;
}
