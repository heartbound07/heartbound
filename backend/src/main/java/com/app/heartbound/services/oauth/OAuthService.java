package com.app.heartbound.services.oauth;

import com.app.heartbound.dto.UserDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
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

    // Removed refresh token functionality since only a singular JWT token is used.

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
            // Build avatar URL using Discord's CDN endpoints.
            String avatarUrl;
            if (userDTO.getAvatar() != null && !userDTO.getAvatar().isEmpty()) {
                // Custom avatar exists
                avatarUrl = "https://cdn.discordapp.com/avatars/" + userDTO.getId() + "/" + userDTO.getAvatar() + ".png?size=128";
            } else if (userDTO.getDiscriminator() != null) {
                // Use default avatar based on discriminator
                int defaultAvatar = Integer.parseInt(userDTO.getDiscriminator()) % 5;
                avatarUrl = "https://cdn.discordapp.com/embed/avatars/" + defaultAvatar + ".png";
            } else {
                // Fallback to a hardcoded default (should ideally never occur)
                avatarUrl = "/images/default-avatar.png";
            }
            userDTO.setAvatar(avatarUrl);
        } else {
            logger.error("Failed to retrieve user information; response was null.");
        }
        return userDTO;
    }
}
