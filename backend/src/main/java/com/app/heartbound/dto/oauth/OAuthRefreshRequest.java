package com.app.heartbound.dto.oauth;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Request payload for refreshing the OAuth access token. Note: Refresh token is not used in the single JWT implementation.")
public class OAuthRefreshRequest {

    @Schema(description = "The refresh token", example = "efgh5678")
    private String refreshToken;
}
