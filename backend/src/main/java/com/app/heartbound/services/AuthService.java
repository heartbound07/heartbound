package com.app.heartbound.services;

import com.app.heartbound.config.security.JWTTokenProvider;
import com.app.heartbound.dto.UserDTO;

import org.springframework.stereotype.Service;

@Service
public class AuthService {

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
        return jwtTokenProvider.generateToken(userDTO.getId());
    }
    
    /**The project was not built due to "release 23 is not found in the system". Fix the problem, then try refreshing this project and building it since it may be inconsistent
     * Validates the provided JWT token.
     *
     * @param token - The JWT token to validate.
     * @return true if the token is valid, false otherwise.
     */
    public boolean validateToken(String token) {
        return jwtTokenProvider.validateToken(token);
    }
    
    /**
     * Extracts the user ID from the given JWT token.
     *
     * @param token - The JWT token.
     * @return The user ID stored in the token.
     */
    public String getUserIdFromToken(String token) {
        return jwtTokenProvider.getUserIdFromJWT(token);
    }
}
