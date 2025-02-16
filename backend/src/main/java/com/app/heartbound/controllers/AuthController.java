package com.app.heartbound.controllers;

import com.app.heartbound.dto.UserDTO;
import com.app.heartbound.services.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

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
        String token = authService.generateTokenForUser(userDTO);
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
        if (authService.validateToken(token)) {
            String userId = authService.getUserIdFromToken(token);
            return ResponseEntity.ok(Map.of("valid", true, "userId", userId));
        } else {
            return ResponseEntity.badRequest().body(Map.of("valid", false));
        }
    }
}
