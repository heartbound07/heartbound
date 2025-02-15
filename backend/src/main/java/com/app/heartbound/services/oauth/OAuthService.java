package com.app.heartbound.services.oauth;

import com.app.heartbound.dto.oauth.OAuthRefreshRequest;
import com.app.heartbound.dto.oauth.OAuthTokenResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

@Service
public class OAuthService {

    @Value("${discord.client-id}")
    private String discordClientId;

    @Value("${discord.client-secret}")
    private String discordClientSecret;

    @Value("${discord.redirect-uri}")
    private String discordRedirectUri;

    private static final String DISCORD_TOKEN_URL = "https://discord.com/api/oauth2/token";

    public OAuthTokenResponse refreshAccessToken(OAuthRefreshRequest refreshRequest) {
        RestTemplate restTemplate = new RestTemplate();

        // Build the request body for token refresh
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("client_id", discordClientId);
        body.add("client_secret", discordClientSecret);
        body.add("grant_type", "refresh_token");
        body.add("refresh_token", refreshRequest.getRefreshToken());
        body.add("redirect_uri", discordRedirectUri);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(body, headers);

        OAuthTokenResponse tokenResponse = restTemplate.postForObject(DISCORD_TOKEN_URL, requestEntity, OAuthTokenResponse.class);
        
        if (tokenResponse == null) {
            throw new RuntimeException("Token refresh failed; received null response from Discord");
        }
        
        return tokenResponse;
    }
}
