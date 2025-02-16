package com.app.heartbound.controllers;

import com.app.heartbound.dto.UserDTO;
import com.app.heartbound.services.AuthService;
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

    /**
     * Endpoint to log a user in by generating a JWT token from the provided user data.
     * Expects a JSON payload with user details.
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody UserDTO userDTO) {
        String token = authService.generateTokenForUser(userDTO);
        return ResponseEntity.ok(Map.of("token", token));
    }

    /**
     * Endpoint to log out a user.
     * Since JWT tokens are stateless, this endpoint instructs the client to simply remove the token.
     */
    @PostMapping("/logout")
    public ResponseEntity<?> logout() {
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    /**
     * Endpoint to validate a provided JWT token.
     * Expects a "token" query parameter.
     */
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
