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
            // This logic is now simplified to remove the database call from the auth flow.
            // The check for premium avatars (e.g., for MONARCH role) should be handled
            // by a separate, post-authentication user profile sync if needed.
            String avatarUrl;
            String avatarHash = userDTO.getAvatar(); // This is just the hash from Discord.

            if (avatarHash != null && !avatarHash.isEmpty()) {
                // Default to .png for stability. Animated avatars can be handled by a post-login sync.
                String extension = ".png";
                if (avatarHash.startsWith("a_")) {
                    logger.info("User {} has an animated avatar. Defaulting to .png during auth flow for stability.", userDTO.getId());
                }
                
                avatarUrl = String.format("https://cdn.discordapp.com/avatars/%s/%s%s?size=1024", userDTO.getId(), avatarHash, extension);
                logger.debug("Constructed custom avatar URL: {}", avatarUrl);
            } else {
                // No custom avatar, use a default one based on discriminator or user ID
                if (userDTO.getDiscriminator() != null && !"0".equals(userDTO.getDiscriminator())) {
                    // Legacy default avatar based on discriminator
                    int defaultAvatar = Integer.parseInt(userDTO.getDiscriminator()) % 5;
                    avatarUrl = "https://cdn.discordapp.com/embed/avatars/" + defaultAvatar + ".png";
                    logger.debug("Constructed legacy default avatar URL for discriminator {}: {}", userDTO.getDiscriminator(), avatarUrl);
                } else {
                    // New default avatar for users with no discriminator
                    try {
                        long userIdLong = Long.parseLong(userDTO.getId());
                        long defaultAvatarIndex = (userIdLong >> 22) % 6;
                        avatarUrl = "https://cdn.discordapp.com/embed/avatars/" + defaultAvatarIndex + ".png";
                        logger.debug("Constructed new default avatar URL for user {}: {}", userDTO.getId(), avatarUrl);
                    } catch (NumberFormatException e) {
                        logger.error("Could not parse user ID '{}' to long for default avatar calculation. Falling back.", userDTO.getId(), e);
                        avatarUrl = "https://cdn.discordapp.com/embed/avatars/0.png"; // Fallback
                    }
                }
            }
            userDTO.setAvatar(avatarUrl);
        } else {
            logger.error("Failed to retrieve user information; response was null.");
        }
        return userDTO;
    }
}
