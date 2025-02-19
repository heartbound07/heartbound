package com.app.heartbound.services.oauth;

import com.app.heartbound.dto.UserDTO;
import com.app.heartbound.dto.oauth.OAuthRefreshRequest;
import com.app.heartbound.dto.oauth.OAuthTokenResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

@Service
public class OAuthService {

    private static final Logger logger = LoggerFactory.getLogger(OAuthService.class);

    @Value("${discord.client-id}")
    private String discordClientId;

    @Value("${discord.client-secret}")
    private String discordClientSecret;

    @Value("${discord.redirect-uri}")
    private String discordRedirectUri;

    private static final String DISCORD_TOKEN_URL = "https://discord.com/api/oauth2/token";

    public OAuthTokenResponse refreshAccessToken(OAuthRefreshRequest refreshRequest) {
        logger.info("Attempting to refresh access token using provided refresh token.");
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
            logger.error("Token refresh failed; received null response from Discord.");
            throw new RuntimeException("Token refresh failed; received null response from Discord");
        }
        
        logger.info("Token refresh successful. New access token acquired.");
        return tokenResponse;
    }
    
    // New method to retrieve user details using the access token
    public UserDTO getUserInfo(String accessToken) {
        logger.info("Retrieving user information from Discord API using provided access token.");
        RestTemplate restTemplate = new RestTemplate();
        
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        HttpEntity<String> entity = new HttpEntity<>(headers);
        final String DISCORD_USER_URL = "https://discord.com/api/users/@me";
        
        UserDTO userDTO = restTemplate.exchange(DISCORD_USER_URL, HttpMethod.GET, entity, UserDTO.class).getBody();
        if (userDTO != null) {
            logger.info("User information retrieved successfully for user id: {}", userDTO.getId());
        } else {
            logger.error("Failed to retrieve user information; response was null.");
        }
        return userDTO;
    }
}
