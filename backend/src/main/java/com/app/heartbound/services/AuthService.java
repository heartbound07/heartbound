package com.app.heartbound.services;

import com.app.heartbound.config.security.JWTTokenProvider;
import com.app.heartbound.dto.UserDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);
    private final JWTTokenProvider jwtTokenProvider;

    public AuthService(JWTTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    /**
     * Generates a JWT token for the authenticated user.
     *
     * @param userDTO - Data transfer object containing user details.
     * @return A JWT token string.
     */
    public String generateTokenForUser(UserDTO userDTO) {
        logger.info("Generating JWT token for user with id: {}", userDTO.getId());
        String token = jwtTokenProvider.generateToken(
                userDTO.getId(),
                userDTO.getUsername(),
                userDTO.getEmail()
        );
        logger.info("JWT token generated successfully for user id: {}", userDTO.getId());
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
}
