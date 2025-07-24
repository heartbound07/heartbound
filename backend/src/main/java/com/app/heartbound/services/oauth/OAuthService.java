package com.app.heartbound.services.oauth;

import com.app.heartbound.dto.UserDTO;
import com.app.heartbound.entities.User;
import com.app.heartbound.enums.Role;
import com.app.heartbound.services.UserService;
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

    private final UserService userService;

    public OAuthService(UserService userService) {
        this.userService = userService;
    }

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

            // Check if user is a MONARCH. This is crucial for determining avatar permissions.
            User user = userService.getUserById(userDTO.getId());
            boolean isMonarch = (user != null && user.getRoles() != null && user.getRoles().contains(Role.MONARCH));
            if (user != null) {
                logger.debug("Checked database for user {}. Is Monarch: {}", userDTO.getId(), isMonarch);
            } else {
                logger.debug("User {} not found in database yet. Assuming not a Monarch.", userDTO.getId());
            }

            // Build avatar URL using Discord's CDN endpoints.
            String avatarUrl;
            String avatarHash = userDTO.getAvatar(); // This is just the hash, not a full URL yet.

            if (avatarHash != null && !avatarHash.isEmpty()) {
                // Custom avatar exists. Check if it's animated and if the user has permission.
                String extension = ".png"; // Default to PNG for security
                if (avatarHash.startsWith("a_")) { // Avatar is animated
                    if (isMonarch) {
                        extension = ".gif"; // User is a MONARCH, so allow GIF
                        logger.debug("User {} is a MONARCH with an animated avatar. Using .gif extension.", userDTO.getId());
                    } else {
                        // User has an animated avatar but is not a Monarch. Force PNG.
                        logger.info("User {} has an animated avatar but is not a MONARCH. Forcing .png extension as per premium feature rules.", userDTO.getId());
                    }
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
                    // New default avatar for users with no discriminator (username is 'pomelo')
                    // The formula is: (user_id >> 22) % 6
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
