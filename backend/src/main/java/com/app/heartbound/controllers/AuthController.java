package com.app.heartbound.controllers;

import com.app.heartbound.dto.UserDTO;
import com.app.heartbound.dto.oauth.OAuthRefreshRequest;
import com.app.heartbound.dto.oauth.OAuthTokenResponse;
import com.app.heartbound.dto.oauth.DiscordCodeExchangeRequest;
import com.app.heartbound.dto.oauth.DiscordAuthResponseDTO;
import com.app.heartbound.entities.User;
import com.app.heartbound.services.AuthService;
import com.app.heartbound.services.UserService;
import com.app.heartbound.services.oauth.DiscordCodeStore;
import com.app.heartbound.config.security.JWTTokenProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;
import java.util.Collections;
import com.app.heartbound.enums.Role;

@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;
    private final DiscordCodeStore discordCodeStore;
    private final UserService userService;
    private final JWTTokenProvider jwtTokenProvider;

    public AuthController(AuthService authService, DiscordCodeStore discordCodeStore, UserService userService, JWTTokenProvider jwtTokenProvider) {
        this.discordCodeStore = discordCodeStore;
        this.userService = userService;
        this.jwtTokenProvider = jwtTokenProvider;
        this.authService = authService;
    }

    @Operation(summary = "Log in a user", description = "Generates a JWT token for the given user details")
    @ApiResponses({
        @ApiResponse(responseCode = "200",
                     description = "JWT token generated successfully",
                     content = @Content(schema = @Schema(implementation = Map.class))),
        @ApiResponse(responseCode = "400", description = "Invalid user details provided", content = @Content)
    })
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody UserDTO userDTO) {
        logger.info("Login attempt for user: {}", userDTO.getUsername());
        String token = authService.generateTokenForUser(userDTO);
        logger.info("JWT token generated successfully for user: {}", userDTO.getUsername());
        return ResponseEntity.ok(Map.of("token", token));
    }

    @Operation(summary = "Log out the current user", description = "Instructs the client to remove the stored JWT token")
    @ApiResponses({
        @ApiResponse(responseCode = "200",
                     description = "Logged out successfully",
                     content = @Content(schema = @Schema(implementation = Map.class)))
    })
    @PostMapping("/logout")
    public ResponseEntity<?> logout() {
        logger.info("Logout request received.");
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    @Operation(summary = "Validate JWT token", description = "Validates the provided JWT token and returns the associated user information if valid")
    @ApiResponses({
        @ApiResponse(responseCode = "200",
                     description = "Token is valid",
                     content = @Content(schema = @Schema(implementation = Map.class))),
        @ApiResponse(responseCode = "400",
                     description = "Token is invalid",
                     content = @Content)
    })
    @GetMapping("/validate")
    public ResponseEntity<?> validateToken(@RequestParam("token") String token) {
        logger.info("Validating provided JWT token.");
        if (authService.validateToken(token)) {
            String userId = authService.getUserIdFromToken(token);
            logger.info("Token validation successful for userId: {}", userId);
            return ResponseEntity.ok(Map.of("valid", true, "userId", userId));
        } else {
            logger.warn("Token validation failed.");
            return ResponseEntity.badRequest().body(Map.of("valid", false));
        }
    }

    @Operation(summary = "Refresh an expired access token")
    @ApiResponse(responseCode = "200", description = "New token pair issued")
    @ApiResponse(responseCode = "401", description = "Invalid or expired refresh token")
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestBody OAuthRefreshRequest refreshRequest) {
        try {
            OAuthTokenResponse tokenResponse = authService.refreshToken(refreshRequest.getRefreshToken());
            return ResponseEntity.ok(tokenResponse);
        } catch (Exception e) {
            logger.error("Refresh token failed: {}", e.getMessage());
            return ResponseEntity.status(401).body("Invalid refresh token");
        }
    }

    @Operation(summary = "Exchange Discord single-use code for JWT tokens",
               description = "Exchanges a short-lived code received after Discord OAuth for application JWT tokens.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Tokens and user info successfully generated",
                     content = @Content(schema = @Schema(implementation = DiscordAuthResponseDTO.class))),
        @ApiResponse(responseCode = "400", description = "Invalid or expired code provided",
                     content = @Content(examples = @ExampleObject(value = "{\"error\": \"Invalid or expired code\"}")))
    })
    @PostMapping("/discord/exchange-code")
    public ResponseEntity<?> exchangeDiscordCode(@RequestBody DiscordCodeExchangeRequest request) {
        // Log the raw request body code
        logger.info("Received request to exchange Discord code. Raw code from request: [{}]", request.getCode());

        if (request.getCode() == null || request.getCode().trim().isEmpty()) {
             logger.warn("Exchange code request received with empty or null code.");
             return ResponseEntity.status(400).body(Map.of("error", "Code parameter is missing"));
        }

        // Validate and consume the code using the store
        String userId = discordCodeStore.consumeCode(request.getCode());
        // Log the result immediately after consumption attempt
        logger.info("Result from consuming code [{}]: userId = {}", request.getCode(), (userId != null ? userId : "null/invalid"));

        if (userId == null) {
            logger.warn("Failed to consume code '{}'. It might be invalid, expired, or already used.", request.getCode());
            return ResponseEntity.status(400).body(Map.of("error", "Invalid or expired code"));
        }

        logger.info("Code validated successfully for user ID: {}. Generating tokens...", userId);

        try {
            // Fetch the full user details needed for token generation
            User user = userService.getUserById(userId);
            if (user == null) {
                logger.error("User not found for ID '{}' retrieved from code store.", userId);
                return ResponseEntity.status(400).body(Map.of("error", "User associated with code not found"));
            }
            logger.debug("Fetched user details for token generation: {}", user); // Log user details

            // Generate tokens using the AuthService or JWTTokenProvider directly
            // Reusing logic similar to AuthService.refreshToken or parts of OAuthController callback
            Set<Role> roles = user.getRoles() != null ? user.getRoles() : Collections.singleton(Role.USER);
            String accessToken = jwtTokenProvider.generateToken(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getAvatar(), // Use the avatar from the User entity
                roles,
                user.getCredits()
            );
            String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId(), roles);

            // Prepare the UserDTO for the response
            UserDTO userDTO = userService.mapUserToDTO(user); // Assuming a mapping function exists

            // Combine into the final response DTO
            DiscordAuthResponseDTO response = new DiscordAuthResponseDTO(
                accessToken,
                refreshToken,
                "bearer", // Token type
                jwtTokenProvider.getTokenExpiryInMs() / 1000, // Correct method name: getTokenExpiryInMs
                "read write", // Define appropriate scope or get from config/user
                userDTO);

            // Log before returning success
            logger.info("Successfully generated tokens and prepared response for user ID: {}", userId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error generating tokens after code exchange for user ID {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", "Internal server error during token generation"));
        }
    }
}
