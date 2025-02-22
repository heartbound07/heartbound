package com.app.heartbound.dto.oauth;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Request payload for refreshing the OAuth access token. Note: Refresh token is not used in the single JWT implementation.")
public class OAuthRefreshRequest {
    // This class is retained for backward compatibility.
    // In the single JWT implementation, no refresh token is required.
}
