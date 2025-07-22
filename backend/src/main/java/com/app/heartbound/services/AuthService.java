package com.app.heartbound.services;

import com.app.heartbound.config.security.JWTTokenProvider;
import com.app.heartbound.dto.LoginRequestDTO;
import com.app.heartbound.dto.UserDTO;
import com.app.heartbound.dto.oauth.OAuthTokenResponse;
import com.app.heartbound.dto.RegisterRequestDTO;
import com.app.heartbound.enums.Role;
import com.app.heartbound.entities.User;
import com.app.heartbound.exceptions.AuthenticationException;
import com.app.heartbound.exceptions.InvalidTokenException;
import com.app.heartbound.exceptions.UserNotFoundException;
import com.app.heartbound.exceptions.UserAlreadyExistsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Set;

@Service
public class AuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);
    private final JWTTokenProvider jwtTokenProvider;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;

    public AuthService(JWTTokenProvider jwtTokenProvider, UserService userService, PasswordEncoder passwordEncoder) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Authenticates a user with username and password, then generates JWTs.
     * This replaces the insecure direct token generation from a UserDTO.
     *
     * @param loginRequest DTO containing username and password.
     * @return An OAuthTokenResponse containing the access and refresh tokens.
     * @throws UserNotFoundException if the user does not exist.
     * @throws AuthenticationException if the password is incorrect.
     */
    public OAuthTokenResponse login(LoginRequestDTO loginRequest) {
        logger.info("Login attempt for user: {}", loginRequest.getUsername());

        // Step 1: Fetch user from the database (source of truth)
        User user = userService.findByUsername(loginRequest.getUsername())
                .orElseThrow(() -> {
                    logger.warn("Login failed: User not found with username '{}'", loginRequest.getUsername());
                    return new UserNotFoundException("User not found with username: " + loginRequest.getUsername());
                });

        // Step 2: Securely verify the password
        if (!passwordEncoder.matches(loginRequest.getPassword(), user.getPassword())) {
            logger.warn("Login failed: Invalid password for user '{}'", loginRequest.getUsername());
            throw new AuthenticationException("Invalid username or password");
        }

        logger.info("User '{}' authenticated successfully. Generating tokens...", user.getUsername());

        // Step 3: Generate tokens using trusted data from the User entity
        Set<Role> roles = user.getRoles() != null && !user.getRoles().isEmpty()
                ? user.getRoles()
                : Collections.singleton(Role.USER);

        String accessToken = jwtTokenProvider.generateToken(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getAvatar(),
                roles,
                user.getCredits()
        );

        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId(), roles);

        logger.info("Tokens generated successfully for user: {}", user.getUsername());

        return OAuthTokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("bearer")
                .expiresIn((int) (jwtTokenProvider.getTokenExpiryInMs() / 1000))
                .scope("read write") // Adjust scope as needed
                .build();
    }
    
    /**
     * Registers a new user, hashes their password, and saves them to the database.
     *
     * @param registerRequest DTO containing username, password, and email.
     * @return The newly created User entity.
     * @throws UserAlreadyExistsException if the username is already taken.
     */
    public User register(RegisterRequestDTO registerRequest) {
        logger.info("Registration attempt for username: {}", registerRequest.getUsername());

        // Step 1: Check if username already exists
        if (userService.findByUsername(registerRequest.getUsername()).isPresent()) {
            logger.warn("Registration failed: Username '{}' is already taken.", registerRequest.getUsername());
            throw new UserAlreadyExistsException("Username is already taken: " + registerRequest.getUsername());
        }

        // Step 2: Hash the password
        String hashedPassword = passwordEncoder.encode(registerRequest.getPassword());

        // Step 3: Create the user via UserService
        User newUser = userService.createUser(registerRequest, hashedPassword);

        logger.info("User '{}' registered successfully.", newUser.getUsername());
        return newUser;
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
