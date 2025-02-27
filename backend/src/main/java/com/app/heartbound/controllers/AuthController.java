package com.app.heartbound.controllers;

import com.app.heartbound.dto.UserDTO;
import com.app.heartbound.dto.oauth.OAuthRefreshRequest;
import com.app.heartbound.dto.oauth.OAuthTokenResponse;
import com.app.heartbound.services.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;

    public AuthController(AuthService authService) {
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
}
