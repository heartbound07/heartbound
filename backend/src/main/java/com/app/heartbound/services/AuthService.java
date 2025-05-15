package com.app.heartbound.services;

import com.app.heartbound.config.security.JWTTokenProvider;
import com.app.heartbound.dto.UserDTO;
import com.app.heartbound.dto.oauth.OAuthTokenResponse;
import com.app.heartbound.enums.Role;
import com.app.heartbound.entities.User;
import com.app.heartbound.exceptions.AuthenticationException;
import com.app.heartbound.exceptions.InvalidTokenException;
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
     * Generates a JWT access token for a user based on the provided UserDTO.
     * User roles and credits are sourced first from the UserDTO. If not present,
     * they are fetched from the database via UserService. If still unavailable after
     * checking the database, roles default to USER and credits default to 0.
     *
     * @param userDTO - Data transfer object containing user details. Must contain at least user ID.
     *                  Username, email, and avatar are taken from this DTO for the token.
     * @return A JWT access token string.
     */
    public String generateTokenForUser(UserDTO userDTO) {
        logger.info("Generating JWT token for user with id: {}", userDTO.getId());
        
        User userEntity = null;
        boolean needsRolesFromDb = (userDTO.getRoles() == null || userDTO.getRoles().isEmpty());
        boolean needsCreditsFromDb = (userDTO.getCredits() == null);

        if (needsRolesFromDb || needsCreditsFromDb) {
            // Fetch user from DB only once if either roles or credits (or both) are missing from DTO
            userEntity = userService.getUserById(userDTO.getId());
        }

        Set<Role> roles = userDTO.getRoles();
        if (needsRolesFromDb) {
            if (userEntity != null && userEntity.getRoles() != null && !userEntity.getRoles().isEmpty()) {
                roles = userEntity.getRoles();
            } else {
                // Default to USER role if not in DTO and not found in DB user or user has no roles
                roles = Collections.singleton(Role.USER);
            }
        }
        // Ensure roles are at least USER if it was an empty set from DTO and DB check didn't override
        if (roles == null || roles.isEmpty()) {
            roles = Collections.singleton(Role.USER);
        }
        
        Integer credits = userDTO.getCredits();
        if (needsCreditsFromDb) {
            credits = (userEntity != null && userEntity.getCredits() != null) ? userEntity.getCredits() : 0;
        }
        // Ensure credits are at least 0 if it was null from DTO and DB check didn't provide a value
        if (credits == null) {
            credits = 0;
        }
        
        String token = jwtTokenProvider.generateToken(
                userDTO.getId(),
                userDTO.getUsername(),
                userDTO.getEmail(),
                userDTO.getAvatar(),
                roles,
                credits
        );
        
        logger.info("JWT token generated successfully for user: {}", userDTO.getUsername());
        return token;
    }
    
    /**
     * Validates the provided JWT token.
     *
     * @param token - The JWT token to validate.
     * @return true if the token is valid
     * @throws InvalidTokenException if the token is invalid
     */
    public boolean validateToken(String token) {
        logger.info("Validating JWT token.");
        try {
            boolean isValid = jwtTokenProvider.validateToken(token);
            logger.info("JWT token validated successfully.");
            return isValid;
        } catch (InvalidTokenException e) {
            logger.warn("JWT token validation failed: {}", e.getMessage());
            throw e; // Re-throw the exception to be handled by the controller
        }
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

    /**
     * Refreshes the token pair using the provided refresh token.
     *
     * @param refreshToken the refresh token
     * @return a new token pair
     * @throws InvalidTokenException if the refresh token is invalid
     * @throws AuthenticationException if the user is not found
     */
    public OAuthTokenResponse refreshToken(String refreshToken) {
        try {
            // Check if the token has been used before
            if (jwtTokenProvider.isRefreshTokenUsed(refreshToken)) {
                logger.error("Attempted reuse of refresh token");
                throw new InvalidTokenException("Token has already been used");
            }
            
            // Validate and decode the refresh token
            String userId = jwtTokenProvider.getUserIdFromRefreshToken(refreshToken);
            
            // Fetch the user to get their current roles and credits
            User user = userService.getUserById(userId);
            if (user == null) {
                logger.error("User not found for refresh token with userId: {}", userId);
                throw new AuthenticationException("User not found");
            }
            
            // Mark the old token as used to prevent reuse
            jwtTokenProvider.invalidateRefreshToken(refreshToken);
            
            // Get user's roles and credits
            Set<Role> roles = user.getRoles() != null ? 
                    user.getRoles() : Collections.singleton(Role.USER);
            Integer credits = user.getCredits();
            
            // Generate new tokens with the user's current roles and credits
            String newAccessToken = jwtTokenProvider.generateToken(
                    userId, 
                    user.getUsername(), 
                    user.getEmail(), 
                    user.getAvatar(), 
                    roles,
                    credits
            );
            String newRefreshToken = jwtTokenProvider.generateRefreshToken(userId, roles);
            
            return OAuthTokenResponse.builder()
                    .accessToken(newAccessToken)
                    .refreshToken(newRefreshToken)
                    .tokenType("bearer")
                    .expiresIn((int) (jwtTokenProvider.getTokenExpiryInMs() / 1000))
                    .scope("identify email")
                    .build();
        } catch (InvalidTokenException e) {
            logger.error("Invalid refresh token: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("Error refreshing token: {}", e.getMessage());
            throw new InvalidTokenException("Failed to refresh token: " + e.getMessage(), e);
        }
    }
}
