package com.app.heartbound.services.riot;

import com.app.heartbound.dto.riot.RiotAccountInfoDTO;
import com.app.heartbound.dto.riot.RiotTokenResponseDTO;
import com.app.heartbound.entities.User;
import com.app.heartbound.exceptions.AccountLinkingException;
import com.app.heartbound.services.UserService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service to handle Riot Games OAuth2 authentication and account linking.
 * Supports both production OAuth flow and development mode with API keys.
 */
@Service
public class RiotOAuthService {
    private static final Logger logger = LoggerFactory.getLogger(RiotOAuthService.class);
    
    // API Endpoints
    private static final String RIOT_AUTH_URL = "https://auth.riotgames.com/authorize";
    private static final String RIOT_TOKEN_URL = "https://auth.riotgames.com/token";
    private static final String RIOT_USERINFO_URL = "https://americas.api.riotgames.com/riot/account/v1/accounts/me";
    
    // Development mode endpoints
    private static final String RIOT_SUMMONER_BY_NAME_URL = "https://%s.api.riotgames.com/lol/summoner/v4/summoners/by-name/%s";
    private static final String RIOT_ACCOUNT_BY_PUUID_URL = "https://americas.api.riotgames.com/riot/account/v1/accounts/by-puuid/%s";
    
    // Store state parameters temporarily to prevent CSRF attacks
    private final Map<String, String> stateStore = new ConcurrentHashMap<>();
    
    @Value("${riot.client.id:dev-mode}")
    private String clientId;
    
    @Value("${riot.client.secret:dev-mode}")
    private String clientSecret;
    
    @Value("${riot.redirect.uri}")
    private String redirectUri;
    
    @Value("${riot.api.key}")
    private String apiKey;
    
    @Value("${riot.dev.mode:true}")
    private boolean devMode;
    
    private final UserService userService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    @Autowired
    public RiotOAuthService(UserService userService, RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.userService = userService;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }
    
    /**
     * Checks if the service is running in development mode
     * @return true if in development mode, false otherwise
     */
    public boolean isDevMode() {
        return devMode;
    }
    
    /**
     * Generates an authorization URI for Riot OAuth
     * @param userId User ID to associate with the OAuth request
     * @return URI to redirect the user to
     */
    public URI generateAuthorizationUri(String userId) {
        if (devMode) {
            throw new IllegalStateException("Cannot generate authorization URI in development mode");
        }
        
        // Generate a secure state parameter to prevent CSRF attacks
        String state = generateSecureState();
        // Store the state parameter with the user ID
        stateStore.put(state, userId);
        
        // Build the authorization URL
        return UriComponentsBuilder.fromHttpUrl(RIOT_AUTH_URL)
            .queryParam("client_id", clientId)
            .queryParam("redirect_uri", redirectUri)
            .queryParam("response_type", "code")
            .queryParam("scope", "openid")
            .queryParam("state", state)
            .build()
            .toUri();
    }
    
    /**
     * Validates the state parameter from the OAuth callback
     * @param state State parameter from callback
     * @return Associated user ID if valid, null otherwise
     */
    public String validateState(String state) {
        return stateStore.remove(state);
    }
    
    /**
     * Exchanges the authorization code for OAuth tokens
     * @param code Authorization code from Riot
     * @return Token response DTO containing access token and other info
     */
    public RiotTokenResponseDTO exchangeCodeForTokens(String code) {
        if (devMode) {
            throw new IllegalStateException("Cannot exchange code for tokens in development mode");
        }
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth(clientId, clientSecret);
        
        MultiValueMap<String, String> requestBody = new LinkedMultiValueMap<>();
        requestBody.add("grant_type", "authorization_code");
        requestBody.add("code", code);
        requestBody.add("redirect_uri", redirectUri);
        
        HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(requestBody, headers);
        
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    RIOT_TOKEN_URL, HttpMethod.POST, requestEntity, String.class);
            
            if (response.getBody() == null) {
                throw new RuntimeException("Empty response body from token endpoint");
            }
            
            return objectMapper.readValue(response.getBody(), RiotTokenResponseDTO.class);
        } catch (JsonProcessingException e) {
            logger.error("Error parsing token response: {}", e.getMessage());
            throw new RuntimeException("Failed to parse token response", e);
        } catch (Exception e) {
            logger.error("Error exchanging code for tokens: {}", e.getMessage());
            throw new RuntimeException("Failed to exchange authorization code for tokens", e);
        }
    }
    
    /**
     * Gets Riot account information using the access token (Production mode only)
     * @param accessToken OAuth access token
     * @return Account information DTO
     */
    public RiotAccountInfoDTO getRiotAccountInfo(String accessToken) {
        if (devMode) {
            throw new IllegalStateException("Cannot get account info with access token in development mode");
        }
        
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
     * Gets Riot account information using the API key in development mode
     * 
     * This is a two-step process:
     * 1. First, we get the summoner by name to get the PUUID
     * 2. Then, we use the PUUID to get the full account info including game name and tag line
     * 
     * @param gameName In-game name
     * @param tagLine Region tag (e.g., NA1)
     * @param region Region code (e.g., na1, euw1)
     * @return Account information DTO
     */
    public RiotAccountInfoDTO getRiotAccountInfoDev(String gameName, String tagLine, String region) {
        if (!devMode) {
            throw new IllegalStateException("Development mode account info retrieval is not allowed in production mode");
        }
        
        try {
            // First, look up the summoner by name to get the PUUID
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Riot-Token", apiKey);
            
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            // Format: https://{region}.api.riotgames.com/lol/summoner/v4/summoners/by-name/{summonerName}
            String summonerUrl = String.format(RIOT_SUMMONER_BY_NAME_URL, region.toLowerCase(), gameName);
            
            ResponseEntity<String> summonerResponse = restTemplate.exchange(
                    summonerUrl, HttpMethod.GET, entity, String.class);
            
            if (summonerResponse.getBody() == null) {
                throw new RuntimeException("Summoner info response body is null");
            }
            
            // Parse to get PUUID
            Map<String, Object> summonerMap = objectMapper.readValue(summonerResponse.getBody(), Map.class);
            String puuid = (String) summonerMap.get("puuid");
            
            if (puuid == null || puuid.isEmpty()) {
                throw new RuntimeException("Could not find summoner with name: " + gameName);
            }
            
            // Create response manually since we already have the info
            RiotAccountInfoDTO accountInfo = new RiotAccountInfoDTO();
            accountInfo.setPuuid(puuid);
            accountInfo.setGameName(gameName);
            accountInfo.setTagLine(tagLine);
            
            return accountInfo;
        } catch (JsonProcessingException e) {
            logger.error("Error parsing Riot account info in dev mode: {}", e.getMessage());
            throw new RuntimeException("Failed to parse Riot account info", e);
        } catch (Exception e) {
            logger.error("Error retrieving Riot account info in dev mode: {}", e.getMessage());
            throw new RuntimeException("Failed to retrieve Riot account info: " + e.getMessage(), e);
        }
    }
    
    /**
     * Links a Riot account to a user
     * @param userId User ID to link account to
     * @param accountInfo Riot account information
     * @return Updated user entity
     * @throws AccountLinkingException if the account is already linked to another user
     */
    public User linkRiotAccount(String userId, RiotAccountInfoDTO accountInfo) throws AccountLinkingException {
        return userService.linkRiotAccount(userId, accountInfo);
    }
    
    /**
     * Unlinks a Riot account from a user
     * @param userId User ID to unlink account from
     * @return Updated user entity
     */
    public User unlinkRiotAccount(String userId) {
        return userService.unlinkRiotAccount(userId);
    }
    
    /**
     * Processes the complete OAuth flow
     * @param code Authorization code from Riot
     * @param state State parameter from callback
     * @return Updated user entity
     * @throws AccountLinkingException if the account is already linked to another user
     */
    public User processOAuthCallback(String code, String state) throws AccountLinkingException {
        if (devMode) {
            throw new IllegalStateException("Cannot process OAuth callback in development mode");
        }
        
        // Validate the state parameter to prevent CSRF attacks
        String userId = validateState(state);
        if (userId == null) {
            throw new IllegalArgumentException("Invalid state parameter");
        }
        
        // Exchange the authorization code for tokens
        RiotTokenResponseDTO tokenResponse = exchangeCodeForTokens(code);
        
        // Get user information using the access token
        RiotAccountInfoDTO accountInfo = getRiotAccountInfo(tokenResponse.getAccessToken());
        
        // Link the Riot account to the user
        return linkRiotAccount(userId, accountInfo);
    }
    
    /**
     * Generates a secure random state parameter for CSRF protection
     * @return Base64 encoded random string
     */
    private String generateSecureState() {
        SecureRandom secureRandom = new SecureRandom();
        byte[] stateBytes = new byte[32];
        secureRandom.nextBytes(stateBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(stateBytes);
    }
}
