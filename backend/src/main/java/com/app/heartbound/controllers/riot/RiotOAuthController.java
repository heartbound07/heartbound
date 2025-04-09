package com.app.heartbound.controllers.riot;

import com.app.heartbound.dto.UserDTO;
import com.app.heartbound.dto.riot.RiotAccountInfoDTO;
import com.app.heartbound.dto.riot.RiotTokenResponseDTO;
import com.app.heartbound.entities.User;
import com.app.heartbound.exceptions.AccountLinkingException;
import com.app.heartbound.services.UserService;
import com.app.heartbound.utils.SecurityUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/oauth2/riot")
@Tag(name = "Riot OAuth", description = "Endpoints for Riot Games account authentication and linking")
public class RiotOAuthController {
    
    private static final Logger logger = LoggerFactory.getLogger(RiotOAuthController.class);
    private static final String RIOT_AUTH_URL = "https://auth.riotgames.com/authorize";
    private static final String RIOT_TOKEN_URL = "https://auth.riotgames.com/token";
    private static final String RIOT_USERINFO_URL = "https://americas.api.riotgames.com/riot/account/v1/accounts/me";
    
    // Store state parameters temporarily to prevent CSRF attacks
    private final Map<String, String> stateStore = new ConcurrentHashMap<>();
    
    @Value("${riot.client.id}")
    private String clientId;
    
    @Value("${riot.client.secret}")
    private String clientSecret;
    
    @Value("${riot.redirect.uri}")
    private String redirectUri;
    
    private final UserService userService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    @Autowired
    public RiotOAuthController(UserService userService, RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.userService = userService;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }
    
    /**
     * Initiates the Riot OAuth flow by redirecting to Riot's authorization page
     */
    @GetMapping("/authorize")
    @Operation(
        summary = "Initiate Riot OAuth2 flow",
        description = "Redirects the user to Riot's authorization page to begin the OAuth2 flow",
        responses = {
            @ApiResponse(responseCode = "302", description = "Redirects to Riot authorization page")
        },
        security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<Void> authorize(Authentication authentication) {
        // Get the current user ID from authentication
        String userId = SecurityUtils.getCurrentUserId(authentication);
        if (userId == null) {
            logger.error("User ID not found in authentication context");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        // Generate a secure state parameter to prevent CSRF attacks
        String state = generateSecureState();
        // Store the state parameter with the user ID
        stateStore.put(state, userId);
        
        // Build the authorization URL
        URI authorizationUri = UriComponentsBuilder.fromHttpUrl(RIOT_AUTH_URL)
            .queryParam("client_id", clientId)
            .queryParam("redirect_uri", redirectUri)
            .queryParam("response_type", "code")
            .queryParam("scope", "openid")
            .queryParam("state", state)
            .build()
            .toUri();
        
        logger.info("Redirecting user {} to Riot authorization", userId);
        
        // Return a 302 redirect to Riot's authorization page
        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(authorizationUri);
        return new ResponseEntity<>(headers, HttpStatus.FOUND);
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
        
        // Validate the state parameter to prevent CSRF attacks
        String userId = stateStore.remove(state);
        if (userId == null) {
            logger.error("Invalid state parameter in callback");
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid state parameter"));
        }
        
        try {
            // Exchange the authorization code for tokens
            RiotTokenResponseDTO tokenResponse = exchangeCodeForTokens(code);
            
            // Get user information using the access token
            RiotAccountInfoDTO accountInfo = getRiotAccountInfo(tokenResponse.getAccessToken());
            
            // Link the Riot account to the user
            User updatedUser = userService.linkRiotAccount(userId, accountInfo);
            
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
            logger.error("Error during Riot OAuth callback: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to process Riot authentication"));
        }
    }
    
    /**
     * Unlinks a Riot account from the current user
     */
    @DeleteMapping("/unlink")
    @Operation(
        summary = "Unlink Riot account",
        description = "Removes the Riot account association from the current user's profile",
        responses = {
            @ApiResponse(responseCode = "200", description = "Account successfully unlinked"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
        },
        security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<?> unlinkAccount(Authentication authentication) {
        String userId = SecurityUtils.getCurrentUserId(authentication);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        User updatedUser = userService.unlinkRiotAccount(userId);
        UserDTO userDTO = userService.mapUserToDTO(updatedUser);
        
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Riot account successfully unlinked");
        response.put("user", userDTO);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Exchanges an authorization code for access and refresh tokens
     */
    private RiotTokenResponseDTO exchangeCodeForTokens(String code) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        
        // Base64 encode client ID and secret for Basic Auth
        String credentials = clientId + ":" + clientSecret;
        String encodedCredentials = Base64.getEncoder().encodeToString(credentials.getBytes());
        headers.set("Authorization", "Basic " + encodedCredentials);
        
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "authorization_code");
        formData.add("code", code);
        formData.add("redirect_uri", redirectUri);
        
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(formData, headers);
        
        try {
            ResponseEntity<RiotTokenResponseDTO> response = restTemplate.postForEntity(
                    RIOT_TOKEN_URL, request, RiotTokenResponseDTO.class);
            
            if (response.getBody() == null) {
                throw new RuntimeException("Token response body is null");
            }
            
            return response.getBody();
        } catch (Exception e) {
            logger.error("Error exchanging code for tokens: {}", e.getMessage());
            throw new RuntimeException("Failed to exchange authorization code for tokens", e);
        }
    }
    
    /**
     * Gets Riot account information using the access token
     */
    private RiotAccountInfoDTO getRiotAccountInfo(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        
        HttpEntity<String> entity = new HttpEntity<>(headers);
        
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    RIOT_USERINFO_URL, HttpMethod.GET, entity, String.class);
            
            if (response.getBody() == null) {
                throw new RuntimeException("Account info response body is null");
            }
            
            // Parse the response manually to handle Riot's API format
            Map<String, Object> responseMap = objectMapper.readValue(response.getBody(), Map.class);
            
            RiotAccountInfoDTO accountInfo = new RiotAccountInfoDTO();
            accountInfo.setPuuid((String) responseMap.get("puuid"));
            accountInfo.setGameName((String) responseMap.get("gameName"));
            accountInfo.setTagLine((String) responseMap.get("tagLine"));
            
            return accountInfo;
        } catch (JsonProcessingException e) {
            logger.error("Error parsing Riot account info: {}", e.getMessage());
            throw new RuntimeException("Failed to parse Riot account info", e);
        } catch (Exception e) {
            logger.error("Error retrieving Riot account info: {}", e.getMessage());
            throw new RuntimeException("Failed to retrieve Riot account info", e);
        }
    }
    
    /**
     * Generates a secure random state parameter for CSRF protection
     */
    private String generateSecureState() {
        SecureRandom secureRandom = new SecureRandom();
        byte[] stateBytes = new byte[32];
        secureRandom.nextBytes(stateBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(stateBytes);
    }
}
