package com.app.heartbound.controllers.riot;

import com.app.heartbound.dto.UserDTO;
import com.app.heartbound.dto.riot.RiotAccountInfoDTO;
import com.app.heartbound.entities.User;
import com.app.heartbound.exceptions.AccountLinkingException;
import com.app.heartbound.services.UserService;
import com.app.heartbound.services.riot.RiotOAuthService;
import com.app.heartbound.utils.SecurityUtils;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/oauth2/riot")
@Tag(name = "Riot OAuth", description = "Endpoints for Riot Games account authentication and linking")
public class RiotOAuthController {
    
    private static final Logger logger = LoggerFactory.getLogger(RiotOAuthController.class);
    
    private final UserService userService;
    private final RiotOAuthService riotOAuthService;
    
    @Value("${riot.dev.mode:true}")
    private boolean devMode;
    
    @Autowired
    public RiotOAuthController(UserService userService, RiotOAuthService riotOAuthService) {
        this.userService = userService;
        this.riotOAuthService = riotOAuthService;
    }
    
    /**
     * Initiates the Riot OAuth flow or a development mode flow
     */
    @GetMapping("/authorize")
    @Operation(
        summary = "Initiate Riot OAuth2 flow",
        description = "Redirects to Riot authentication page or returns development mode info",
        responses = {
            @ApiResponse(responseCode = "302", description = "Redirect to Riot auth page (production)"),
            @ApiResponse(responseCode = "200", description = "Dev mode information (development)")
        },
        security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<?> authorize(Authentication authentication) {
        // Get the current user ID from authentication
        String userId = SecurityUtils.getCurrentUserId(authentication);
        if (userId == null) {
            logger.error("User ID not found in authentication context");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        if (devMode) {
            // In development mode, return a response with instructions instead of redirecting
            Map<String, Object> response = new HashMap<>();
            response.put("mode", "development");
            response.put("message", "Development mode active. Use the /dev-link endpoint to link a Riot account.");
            response.put("linkEndpoint", "/api/oauth2/riot/dev-link");
            return ResponseEntity.ok(response);
        } else {
            try {
                // Generate authorization URL and redirect to Riot
                URI authorizationUri = riotOAuthService.generateAuthorizationUri(userId);
                
                // Return a 302 redirect to Riot's authorization page
                HttpHeaders headers = new HttpHeaders();
                headers.setLocation(authorizationUri);
                return new ResponseEntity<>(headers, HttpStatus.FOUND);
            } catch (Exception e) {
                logger.error("Error generating authorization URL: {}", e.getMessage(), e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("error", "Failed to initiate Riot authentication"));
            }
        }
    }
    
    /**
     * Development-only endpoint to manually link a Riot account
     */
    @PostMapping("/dev-link")
    @Operation(
        summary = "Link Riot account (Development mode only)",
        description = "Allows direct linking of Riot account in development environment",
        responses = {
            @ApiResponse(responseCode = "200", description = "Account successfully linked"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "409", description = "Account linking conflict")
        },
        security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<?> devLinkAccount(
            @RequestParam("gameName") String gameName,
            @RequestParam("tagLine") String tagLine,
            @RequestParam("region") String region,
            Authentication authentication) {
        
        if (!devMode) {
            logger.error("Dev-link endpoint called while not in development mode");
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "This endpoint is only available in development mode"));
        }
        
        // Get the current user ID from authentication
        String userId = SecurityUtils.getCurrentUserId(authentication);
        if (userId == null) {
            logger.error("User ID not found in authentication context");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        try {
            // Use the Riot API to get account info using the API key
            RiotAccountInfoDTO accountInfo = riotOAuthService.getRiotAccountInfoDev(gameName, tagLine, region);
            
            // Link the Riot account to the user
            User updatedUser = riotOAuthService.linkRiotAccount(userId, accountInfo);
            
            // Convert to DTO for response
            UserDTO userDTO = userService.mapUserToDTO(updatedUser);
            
            // Return success response
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Riot account successfully linked");
            response.put("user", userDTO);
            
            return ResponseEntity.ok(response);
        } catch (AccountLinkingException e) {
            logger.error("Account linking error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error during development account linking: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to link Riot account: " + e.getMessage()));
        }
    }
    
    /**
     * Handles the callback from Riot after user authorization
     */
    @GetMapping("/callback")
    @Operation(
        summary = "Handle Riot OAuth2 callback",
        description = "Processes the callback from Riot, exchanges code for tokens, and links the Riot account",
        responses = {
            @ApiResponse(responseCode = "200", description = "Account successfully linked"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "409", description = "Account linking conflict")
        }
    )
    public ResponseEntity<?> callback(
            @RequestParam("code") String code,
            @RequestParam("state") String state) {
        
        if (devMode) {
            logger.error("OAuth callback endpoint called in development mode");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "OAuth callbacks are not supported in development mode"));
        }
        
        try {
            // Process the complete OAuth flow
            User updatedUser = riotOAuthService.processOAuthCallback(code, state);
            
            // Convert to DTO for response
            UserDTO userDTO = userService.mapUserToDTO(updatedUser);
            
            // Return success response
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Riot account successfully linked");
            response.put("user", userDTO);
            
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid state parameter in callback: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid state parameter"));
        } catch (AccountLinkingException e) {
            logger.error("Account linking error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error during Riot OAuth callback: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to process Riot authentication"));
        }
    }
    
    /**
     * Unlinks a Riot account from the user's profile
     */
    @DeleteMapping("/unlink")
    @Operation(
        summary = "Unlink Riot account",
        description = "Remove the linked Riot account from the user's profile",
        security = { @SecurityRequirement(name = "bearerAuth") },
        responses = {
            @ApiResponse(responseCode = "200", description = "Account successfully unlinked"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "User not found")
        }
    )
    public ResponseEntity<?> unlinkAccount(Authentication authentication) {
        // Get the current user ID from authentication
        String userId = SecurityUtils.getCurrentUserId(authentication);
        if (userId == null) {
            logger.error("User ID not found in authentication context");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        try {
            // Unlink the Riot account
            User updatedUser = riotOAuthService.unlinkRiotAccount(userId);
            
            // Convert to DTO for response
            UserDTO userDTO = userService.mapUserToDTO(updatedUser);
            
            // Return success response
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Riot account successfully unlinked");
            response.put("user", userDTO);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error during Riot account unlinking: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to unlink Riot account: " + e.getMessage()));
        }
    }
}
