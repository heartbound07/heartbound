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
@Tag(name = "Riot OAuth", description = "Endpoints for Riot Games OAuth2 integration")
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
        summary = "Initiate Riot OAuth flow",
        description = "Generates the Riot Games authorization URL and returns it to the client for redirection, or provides a dev link.",
        security = { @SecurityRequirement(name = "bearerAuth") },
        responses = {
            @ApiResponse(responseCode = "200", description = "Returns JSON with 'url' or 'linkEndpoint'"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
        }
    )
    public ResponseEntity<?> authorize(Authentication authentication) {
        // Get the current user ID from authentication
        String userId = SecurityUtils.getCurrentUserId(authentication);
        if (userId == null) {
            logger.error("User ID not found in authentication context for Riot authorize");
            // It's crucial to return an error response here, not proceed.
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Authentication required"));
        }

        boolean isDevMode = riotOAuthService.isDevMode();
        logger.info("[RiotOAuthController] Authorize request received for user ID: {}. Dev Mode: {}", userId, isDevMode); // Log entry and dev mode status

        // Check if development mode is enabled
        if (isDevMode) {
            // In dev mode, provide the endpoint for manual linking
            Map<String, String> response = new HashMap<>();
            response.put("linkEndpoint", "/api/oauth2/riot/dev-link");
            logger.info("[RiotOAuthController] Returning dev mode link endpoint for user {}: {}", userId, response.get("linkEndpoint")); // Log dev response
            return ResponseEntity.ok(response);
        } else {
            try {
                logger.debug("[RiotOAuthController] Generating production Riot authorization URI for user {}", userId); // Log before generation
                // Generate the Riot authorization URI
                URI authorizationUri = riotOAuthService.generateAuthorizationUri(userId);
                logger.debug("[RiotOAuthController] Generated Riot authorization URI: {}", authorizationUri); // Log generated URI
                String authorizationUrl = authorizationUri.toString();
                logger.debug("[RiotOAuthController] Generated Riot authorization URL string: {}", authorizationUrl); // Log URL string

                // Return the URL in a JSON object with 200 OK status
                Map<String, String> responseBody = new HashMap<>();
                responseBody.put("url", authorizationUrl);

                logger.info("[RiotOAuthController] Returning production Riot OAuth authorization URL for user {}: {}", userId, authorizationUrl); // Log success response
                return ResponseEntity.ok(responseBody);
            } catch (Exception e) {
                // Log the error and return an internal server error response
                // Use the specific userId in the log message for better tracking
                logger.error("[RiotOAuthController] Error generating Riot authorization URL for user ID {}: {}", userId, e.getMessage(), e); // Log exception
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
        summary = "Handle Riot OAuth callback",
        description = "Processes the callback from Riot Games after user authorization",
        responses = {
            @ApiResponse(responseCode = "302", description = "Redirects back to the frontend application"),
            @ApiResponse(responseCode = "400", description = "Bad request (e.g., invalid state, missing code)"),
            @ApiResponse(responseCode = "401", description = "Unauthorized (if callback security fails)"),
            @ApiResponse(responseCode = "500", description = "Internal server error during processing")
        }
    )
    public ResponseEntity<?> handleCallback(@RequestParam("code") String code, @RequestParam("state") String state, Authentication authentication) {
        logger.info("Received Riot OAuth callback with code and state");
        
        // Ensure the user is authenticated for this callback endpoint
        String userId = SecurityUtils.getCurrentUserId(authentication);
        if (userId == null) {
            logger.error("Authentication required for Riot callback, but none found.");
            // Redirect to a frontend error page or login page
            return ResponseEntity.status(HttpStatus.FOUND).location(URI.create("/auth-error?error=unauthorized_callback")).build();
        }
        
        try {
            // Process the callback (exchange code, get user info, link account)
            User linkedUser = riotOAuthService.processOAuthCallback(code, state);
            logger.info("Successfully processed Riot callback for user ID: {}", linkedUser.getId());
            
            // Redirect back to the frontend profile page (or a success page)
            // TODO: Make the redirect URL configurable
            String frontendRedirectUrl = "http://localhost:3000/dashboard/profile?riot_link_status=success"; 
            return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(frontendRedirectUrl)).build();
            
        } catch (AccountLinkingException e) {
            logger.error("Riot account linking error during callback: {}", e.getMessage(), e);
            // Redirect to frontend with specific error
            String errorRedirectUrl = "http://localhost:3000/dashboard/profile?riot_link_status=error&message=" + e.getMessage();
            return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(errorRedirectUrl)).build();
        } catch (IllegalArgumentException e) {
            logger.error("Invalid state or code during Riot callback: {}", e.getMessage(), e);
            // Redirect to frontend with specific error
            String errorRedirectUrl = "http://localhost:3000/dashboard/profile?riot_link_status=error&message=invalid_request";
            return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(errorRedirectUrl)).build();
        } catch (Exception e) {
            logger.error("Unexpected error during Riot callback processing: {}", e.getMessage(), e);
            // Redirect to a generic error page on the frontend
            String errorRedirectUrl = "http://localhost:3000/dashboard/profile?riot_link_status=error&message=internal_error";
            return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(errorRedirectUrl)).build();
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
            @ApiResponse(responseCode = "200", description = "Account successfully unlinked, returns updated user info"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "User not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
        }
    )
    public ResponseEntity<?> unlinkAccount(Authentication authentication) {
        // Get the current user ID from authentication
        String userId = SecurityUtils.getCurrentUserId(authentication);
        if (userId == null) {
            logger.error("User ID not found in authentication context during unlink");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Authentication required"));
        }
        
        logger.debug("Attempting to unlink Riot account for user ID: {}", userId);
        
        try {
            // Unlink the Riot account using the service layer method
            User updatedUser = riotOAuthService.unlinkRiotAccount(userId);
            
            // Convert the updated User entity to UserDTO for the response
            // Assuming userService has a method to map User -> UserDTO
            UserDTO userDTO = userService.mapUserToDTO(updatedUser); 
            
            // Return success response with the updated user DTO
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Riot account successfully unlinked");
            response.put("user", userDTO); // Send back the updated user object
            
            logger.info("Successfully unlinked Riot account for user ID: {}", userId);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            // Catch potential exceptions like ResourceNotFoundException from userService
            logger.error("Error during Riot account unlinking for user ID {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to unlink Riot account: " + e.getMessage()));
        }
    }
}
