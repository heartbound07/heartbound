package com.app.heartbound.dto.oauth;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Request payload for refreshing the OAuth access token.")
public class OAuthRefreshRequest {

    @Schema(description = "Refresh token used to obtain a new access token", example = "refresh1234")
    private String refreshToken;
    
    public String getRefreshToken() {
        return refreshToken;
    }
    
    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }
}
