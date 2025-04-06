package com.app.heartbound.controllers.oauth;

import java.math.BigInteger;
import java.security.SecureRandom;
import jakarta.servlet.http.HttpSession;
import java.util.Base64;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Set;

import com.app.heartbound.dto.UserDTO;
import com.app.heartbound.dto.oauth.OAuthTokenResponse;
import com.app.heartbound.services.oauth.OAuthService;
import com.app.heartbound.services.UserService;
import com.app.heartbound.services.oauth.DiscordCodeStore;
import com.app.heartbound.config.security.JWTTokenProvider;
import com.app.heartbound.entities.User;
import com.app.heartbound.enums.Role;
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
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.view.RedirectView;

@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
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

    // Session key for storing the state originated from the frontend
    private static final String SESSION_FRONTEND_STATE_KEY = "FRONTEND_DISCORD_OAUTH_STATE";
    private final SecureRandom secureRandom = new SecureRandom(); // For generating single-use codes

    @Autowired
    private OAuthService oauthService;  // Handles token exchange and user info retrieval

    @Autowired
    private UserService userService; // Inject UserService to persist user details

    @Autowired
    private JWTTokenProvider jwtTokenProvider;

    @Autowired
    private DiscordCodeStore discordCodeStore; // Inject the code store

    @Operation(summary = "Authorize Discord User", description = "Generates a CSRF token and redirects the user to Discord for authorization")
    @ApiResponses({
        @ApiResponse(responseCode = "302", description = "Redirects to Discord authorization endpoint")
    })
    @GetMapping("/auth/discord/authorize")
    public RedirectView authorize(HttpSession session) {
        // Generate a secure random state token for CSRF protection.
        // This state is primarily validated by the frontend upon callback.
        String frontendState = new BigInteger(130, secureRandom).toString(32);
        session.setAttribute(SESSION_FRONTEND_STATE_KEY, frontendState); // Store for validation on callback
        logger.debug("Generated and stored frontend state in session: {}", frontendState);

        // Construct the redirect URL
        // Use the frontendState in the redirect to Discord
        String redirectUrl = String.format(
                "%s?client_id=%s&redirect_uri=%s&response_type=code&scope=%s&state=%s&prompt=consent", // Added prompt=consent
                DISCORD_AUTH_URL,
                discordClientId,
                discordRedirectUri,
                discordScopes.replace(" ", "%20"),
                frontendState); // Pass the frontend-originated state to Discord

        logger.debug("Redirecting to Discord OAuth URL: {}", redirectUrl);
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
            @RequestParam(name = "state", required = false) String incomingState, // Renamed for clarity
            @Parameter(description = "Error response from Discord", required = false)
            @RequestParam(name = "error", required = false) String error,
            HttpSession session) {

        if (error != null) {
            logger.error("Discord OAuth error received: {}", error);
            return new RedirectView("/login?error=Discord+authorization+failed");
        }

        if (code == null || incomingState == null) {
            logger.error("Missing required OAuth parameters: code or state is null.");
            return new RedirectView("/login?error=Missing+code+or+state");
        }

        // Validate the incoming state against the one stored in the session (originated from frontend)
        String expectedState = (String) session.getAttribute(SESSION_FRONTEND_STATE_KEY);
        logger.debug("Validating incoming state '{}' against expected state '{}'", incomingState, expectedState);
        if (expectedState == null || !incomingState.equals(expectedState)) {
            logger.error("Invalid state parameter. Expected '{}' but received '{}'. Possible CSRF detected.", expectedState, incomingState);
            return new RedirectView("/login?error=Invalid+state");
        }
        session.removeAttribute(SESSION_FRONTEND_STATE_KEY); // Remove state after validation
        logger.debug("State validation successful. Removed state from session.");

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
        logger.info("Exchanging Discord code for tokens...");
        try {
            tokenResponse = restTemplate.postForObject(DISCORD_TOKEN_URL, requestEntity, OAuthTokenResponse.class);
            if (tokenResponse == null || tokenResponse.getAccessToken() == null) {
                logger.error("Token exchange failed: received null token response");
                return new RedirectView("/login?error=Token+exchange+failed");
            }
            logger.info("Token exchange successful. Access token acquired.");
        } catch (Exception e) {
            logger.error("Token exchange failed: {}", e.getMessage());
            return new RedirectView("/login?error=Token+exchange+failed");
        }

        UserDTO userDTO;
        logger.info("Fetching user info from Discord...");
        try {
            userDTO = oauthService.getUserInfo(tokenResponse.getAccessToken());
            logger.info("User details retrieved successfully: {}", userDTO);
        } catch (Exception e) {
            logger.error("Failed to retrieve user details: {}", e.getMessage());
            return new RedirectView("/login?error=User+information+retrieval+failed");
        }

        // Find or create user in our database
        logger.info("Creating or updating user in database...");
        User user = userService.createOrUpdateUser(userDTO);
        logger.info("User processed successfully. User ID: {}", user.getId());

        // --- Start Secure Code Exchange ---
        // 1. Generate a secure, single-use code
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        String singleUseCode = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        // 2. Store the code temporarily, associated with the user ID
        discordCodeStore.storeCode(singleUseCode, user.getId());
        logger.info("Generated and stored single-use code for user ID: {}", user.getId());

        // 3. URL-encode the single-use code and the original frontend state
        String encodedSingleUseCode = URLEncoder.encode(singleUseCode, StandardCharsets.UTF_8);
        String encodedFrontendState = URLEncoder.encode(incomingState, StandardCharsets.UTF_8); // Use the validated incoming state

        // Build the redirect URL to include both tokens as query parameters.
        // Redirect to frontend with the single-use code and the original state
        String frontendRedirectUrl = String.format(
                "http://localhost:3000/auth/discord/callback?code=%s&state=%s",
                encodedSingleUseCode,
                encodedFrontendState // Pass the validated frontend state back
        );
        // --- End Secure Code Exchange ---

        String discordAvatarUrl = userDTO.getAvatar();
        if (user != null && discordAvatarUrl != null && discordAvatarUrl.contains("cdn.discordapp.com")) {
            // Always update the cached Discord avatar URL, even if not using it right now
            user.setDiscordAvatarUrl(discordAvatarUrl);
            userService.updateUser(user);
            logger.debug("Updated cached Discord avatar URL during OAuth flow");
        }

        logger.info("Redirecting to frontend callback URL: {}", frontendRedirectUrl);
        return new RedirectView(frontendRedirectUrl);
    }
}
