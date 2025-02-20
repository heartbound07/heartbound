package com.app.heartbound.controllers.oauth;

import java.math.BigInteger;
import java.security.SecureRandom;
import jakarta.servlet.http.HttpSession;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import com.app.heartbound.dto.UserDTO;
import com.app.heartbound.dto.oauth.OAuthRefreshRequest;
import com.app.heartbound.dto.oauth.OAuthTokenResponse;
import com.app.heartbound.services.oauth.OAuthService;
import com.app.heartbound.services.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.view.RedirectView;

@RestController
public class OAuthController {

    private static final Logger logger = LoggerFactory.getLogger(OAuthController.class);

    @Value("${discord.client-id}")
    private String discordClientId;

    @Value("${discord.client-secret}")
    private String discordClientSecret;

    @Value("${discord.redirect-uri}")
    private String discordRedirectUri;

    @Value("${discord.scopes}")
    private String discordScopes;

    // Discord endpoints
    private static final String DISCORD_AUTH_URL = "https://discord.com/api/oauth2/authorize";
    private static final String DISCORD_TOKEN_URL = "https://discord.com/api/oauth2/token";

    // Session key for storing CSRF state
    private static final String SESSION_STATE = "DISCORD_OAUTH_STATE";

    @Autowired
    private OAuthService oauthService;  // Handles token exchange and user info retrieval

    @Autowired
    private UserService userService; // Inject UserService to persist user details

    @Operation(summary = "Authorize Discord User", description = "Generates a CSRF token and redirects the user to Discord for authorization")
    @ApiResponses({
        @ApiResponse(responseCode = "302", description = "Redirects to Discord authorization endpoint")
    })
    @GetMapping("/auth/discord/authorize")
    public RedirectView authorize(HttpSession session) {
        // Generate a secure random state token for CSRF protection.
        String state = new BigInteger(130, new SecureRandom()).toString(32);
        session.setAttribute(SESSION_STATE, state);

        // Construct the redirect URL
        String redirectUrl = String.format("%s?client_id=%s&redirect_uri=%s&response_type=code&scope=%s&state=%s",
                DISCORD_AUTH_URL,
                discordClientId,
                discordRedirectUri,
                discordScopes.replace(" ", "%20"),
                state);

        logger.info("Redirecting to Discord OAuth authorization with URL: {}", redirectUrl);
        return new RedirectView(redirectUrl);
    }

    @Operation(summary = "Discord OAuth Callback", description = "Handles the callback from Discord, exchanges code for a token, retrieves user info, and persists the user")
    @ApiResponses({
        @ApiResponse(responseCode = "302", description = "Redirects to dashboard on successful login"),
        @ApiResponse(responseCode = "400", description = "Returns error if required parameters are missing or invalid")
    })
    @GetMapping("/oauth2/callback/discord")
    public RedirectView callback(
            @Parameter(description = "Authorization code returned from Discord", required = false)
            @RequestParam(name = "code", required = false) String code,
            @Parameter(description = "CSRF state token", required = false)
            @RequestParam(name = "state", required = false) String state,
            @Parameter(description = "Error response from Discord", required = false)
            @RequestParam(name = "error", required = false) String error,
            HttpSession session) {

        // Handle error returned from Discord
        if (error != null) {
            logger.error("Discord OAuth error received: {}", error);
            return new RedirectView("/login?error=Discord+authorization+failed");
        }

        // Validate required parameters
        if (code == null || state == null) {
            logger.error("Missing required OAuth parameters: code or state is null.");
            return new RedirectView("/login?error=Missing+code+or+state");
        }

        // Verify state token to prevent CSRF
        String sessionState = (String) session.getAttribute(SESSION_STATE);
        if (sessionState == null || !state.equals(sessionState)) {
            logger.error("Invalid state parameter. Possible CSRF detected. Received state: {}, expected: {}", state, sessionState);
            return new RedirectView("/login?error=Invalid+state");
        }
        // Remove state after validation
        session.removeAttribute(SESSION_STATE);

        // Prepare token exchange request
        RestTemplate restTemplate = new RestTemplate();
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("client_id", discordClientId);
        body.add("client_secret", discordClientSecret);
        body.add("grant_type", "authorization_code");
        body.add("code", code);
        body.add("redirect_uri", discordRedirectUri);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(body, headers);

        OAuthTokenResponse tokenResponse;
        try {
            tokenResponse = restTemplate.postForObject(DISCORD_TOKEN_URL, requestEntity, OAuthTokenResponse.class);
            logger.info("Token exchange successful. Access token acquired.");
        } catch (Exception e) {
            logger.error("Token exchange failed: {}", e.getMessage());
            return new RedirectView("/login?error=Token+exchange+failed");
        }

        // Retrieve user details using the access token
        UserDTO userDTO;
        try {
            userDTO = oauthService.getUserInfo(tokenResponse.getAccessToken());
            logger.info("User details retrieved successfully: {}", userDTO);
        } catch (Exception e) {
            logger.error("Failed to retrieve user details: {}", e.getMessage());
            return new RedirectView("/login?error=User+information+retrieval+failed");
        }

        // Persist the user details locally using UserService
        userService.createOrUpdateUser(userDTO);
        logger.info("User information persisted successfully for user: {}", userDTO.getUsername());

        // After persisting the user details
        String tokenParam = URLEncoder.encode(tokenResponse.getAccessToken(), StandardCharsets.UTF_8);
        String frontendRedirectUrl = String.format("http://localhost:3000/auth/discord/callback?accessToken=%s", tokenParam);
        return new RedirectView(frontendRedirectUrl);
    }

    @Operation(summary = "Refresh Discord OAuth Token", description = "Refreshes the Discord access token using a provided refresh token")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Returns new access token"),
        @ApiResponse(responseCode = "400", description = "Token refresh failed")
    })
    @PostMapping("/oauth2/refresh/discord")
    public ResponseEntity<OAuthTokenResponse> refreshToken(@RequestBody OAuthRefreshRequest refreshRequest) {
        try {
            OAuthTokenResponse tokenResponse = oauthService.refreshAccessToken(refreshRequest);
            logger.info("Token refresh successful. New access token acquired.");
            return ResponseEntity.ok(tokenResponse);
        } catch (Exception e) {
            logger.error("Token refresh failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }
    }
}
