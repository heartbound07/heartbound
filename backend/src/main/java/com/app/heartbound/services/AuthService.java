package com.app.heartbound.services;

import com.app.heartbound.config.security.JWTTokenProvider;
import com.app.heartbound.dto.UserDTO;
import com.app.heartbound.dto.oauth.OAuthTokenResponse;
import com.app.heartbound.enums.Role;
import com.app.heartbound.entities.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Set;

@Service
public class AuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);
    private final JWTTokenProvider jwtTokenProvider;
    private final UserService userService;

    public AuthService(JWTTokenProvider jwtTokenProvider, UserService userService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userService = userService;
    }

    /**
     * Generates a JWT token for the authenticated user, including their roles.
     *
     * @param userDTO - Data transfer object containing user details.
     * @return A JWT token string.
     */
    public String generateTokenForUser(UserDTO userDTO) {
        logger.info("Generating JWT token for user with id: {}", userDTO.getId());
        
        // Get user's roles from the database if available
        Set<Role> roles = userDTO.getRoles();
        if (roles == null || roles.isEmpty()) {
            User user = userService.getUserById(userDTO.getId());
            if (user != null && user.getRoles() != null && !user.getRoles().isEmpty()) {
                roles = user.getRoles();
            } else {
                // Default to USER role if no roles found
                roles = Collections.singleton(Role.USER);
            }
        }
        
        String token = jwtTokenProvider.generateToken(
                userDTO.getId(),
                userDTO.getUsername(),
                userDTO.getEmail(),
                userDTO.getAvatar(),
                roles
        );
        logger.info("JWT token generated successfully for user: {}", userDTO.getId());
        return token;
    }
    
    /**The project was not built due to "release 23 is not found in the system". Fix the problem, then try refreshing this project and building it since it may be inconsistent
     * Validates the provided JWT token.
     *
     * @param token - The JWT token to validate.
     * @return true if the token is valid, false otherwise.
     */
    public boolean validateToken(String token) {
        logger.info("Validating JWT token.");
        boolean isValid = jwtTokenProvider.validateToken(token);
        if (isValid) {
            logger.info("JWT token validated successfully.");
        } else {
            logger.warn("JWT token validation failed.");
        }
        return isValid;
    }
    
    /**
     * Extracts the user ID from the given JWT token.
     *
     * @param token - The JWT token.
     * @return The user ID stored in the token.
     */
    public String getUserIdFromToken(String token) {
        logger.info("Extracting user id from JWT token.");
        String userId = jwtTokenProvider.getUserIdFromJWT(token);
        logger.info("Extracted user id: {}", userId);
        return userId;
    }

    public OAuthTokenResponse refreshToken(String refreshToken) {
        // Validate and decode the refresh token
        String userId = jwtTokenProvider.getUserIdFromRefreshToken(refreshToken);
        
        // Fetch the user to get their current roles
        User user = userService.getUserById(userId);
        Set<Role> roles = user != null && user.getRoles() != null ? 
                user.getRoles() : Collections.singleton(Role.USER);
        
        // Generate new tokens with the user's current roles
        String newAccessToken = jwtTokenProvider.generateToken(
                userId, 
                user != null ? user.getUsername() : "username", 
                user != null ? user.getEmail() : "email@example.com", 
                user != null ? user.getAvatar() : "avatar.png", 
                roles
        );
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(userId, roles);
        
        return OAuthTokenResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .tokenType("bearer")
                .expiresIn((int) (jwtTokenProvider.getTokenExpiryInMs() / 1000))
                .scope("identify email")
                .build();
    }
}
