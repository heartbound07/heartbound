package com.app.heartbound.controllers.oauth;

import java.math.BigInteger;
import java.security.SecureRandom;
import jakarta.servlet.http.HttpSession;
import java.util.Base64;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import com.app.heartbound.dto.UserDTO;
import com.app.heartbound.dto.oauth.OAuthTokenResponse;
import com.app.heartbound.services.oauth.OAuthService;
import com.app.heartbound.services.UserService;
import com.app.heartbound.services.oauth.DiscordCodeStore;
import com.app.heartbound.entities.User;
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

import com.app.heartbound.services.discord.DiscordChannelService;

@CrossOrigin(origins = "http://", allowCredentials = "true")
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

    @Value("${frontend.base.url}")
    private String frontendBaseUrl;

    // Discord endpoints
    private static final String DISCORD_TOKEN_URL = "https://discord.com/api/oauth2/token";

    // Session key for storing the state originated from the frontend
    private static final String SESSION_FRONTEND_STATE_KEY = "FRONTEND_DISCORD_OAUTH_STATE";
    private final SecureRandom secureRandom = new SecureRandom(); // For generating single-use codes

    @Autowired
    private OAuthService oauthService;  // Handles token exchange and user info retrieval

    @Autowired
    private UserService userService; // Inject UserService to persist user details

    @Autowired
    private DiscordCodeStore discordCodeStore; // Inject the code store

    @Autowired
    private DiscordChannelService discordChannelService; // Add this autowired dependency

    @Operation(summary = "Initiate Discord OAuth flow", description = "Redirects the user to Discord for authorization.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "302", description = "Redirecting to Discord")
    })
    @GetMapping("/auth/discord/authorize")
    public RedirectView authorizeWithDiscord(
            @Parameter(description = "Optional state parameter provided by the frontend for CSRF protection")
            @RequestParam(name = "state", required = false) String frontendState, // Accept state from frontend
            HttpSession session) {

        String stateToUse;
        if (frontendState != null && !frontendState.trim().isEmpty()) {
            // Use the state provided by the frontend
            stateToUse = frontendState;
            logger.debug("Using state provided by frontend: [{}]", stateToUse);
        } else {
            // Fallback: Generate a new state if frontend didn't provide one (should not happen ideally)
            logger.warn("No state provided by frontend, generating a new one (fallback).");
            byte[] randomBytes = new byte[16];
            secureRandom.nextBytes(randomBytes);
            stateToUse = new BigInteger(1, randomBytes).toString(16);
        }

        // Store the state (either frontend-provided or generated) in the session for later validation
        session.setAttribute(SESSION_FRONTEND_STATE_KEY, stateToUse);
        logger.debug("Stored state in session for validation: [{}]", stateToUse);

        // Construct the Discord authorization URL using the state we decided to use
        String discordAuthUrl = String.format(
                "https://discord.com/api/oauth2/authorize?client_id=%s&redirect_uri=%s&response_type=code&scope=%s&state=%s&prompt=consent",
                discordClientId,
                URLEncoder.encode(discordRedirectUri, StandardCharsets.UTF_8),
                URLEncoder.encode(discordScopes, StandardCharsets.UTF_8),
                URLEncoder.encode(stateToUse, StandardCharsets.UTF_8) // Use the stateToUse
        );

        logger.debug("Redirecting to Discord OAuth URL: {}", discordAuthUrl);
        logger.info("Redirecting to Discord OAuth authorization with state: [{}]", stateToUse); // Log the state being used
        return new RedirectView(discordAuthUrl);
    }

    @Operation(summary = "Discord OAuth Callback", description = "Handles the callback from Discord, exchanges code for a token, retrieves user info, and persists the user")
    @ApiResponses({
        @ApiResponse(responseCode = "302", description = "Redirects to dashboard on successful login"),
        @ApiResponse(responseCode = "400", description = "Returns error if required parameters are missing or invalid")
    })
    @GetMapping("/oauth2/callback/discord")
    public RedirectView callbackFromDiscord(@RequestParam("code") String discordCode,
                                            @RequestParam("state") String incomingState,
                                            HttpSession session) {

        logger.info("Received callback from Discord. Code: [{}], State: [{}]", discordCode, incomingState);

        // --- State Validation ---
        // Retrieve the state stored in the session during the initial authorize redirect
        // NOTE: Ensure authorizeWithDiscord correctly stores state in the session using SESSION_FRONTEND_STATE_KEY
        String sessionState = (String) session.getAttribute(SESSION_FRONTEND_STATE_KEY);
        logger.debug("Retrieved state from session: [{}]", sessionState);

        if (sessionState == null) {
            logger.warn("No state found in session. Potential session issue or direct access.");
            // Redirect to frontend login with a state error
            return new RedirectView(frontendBaseUrl + "/login?error=Session+state+missing");
        }

        if (!incomingState.equals(sessionState)) {
            logger.error("State mismatch! Session state: [{}], Incoming state: [{}]. Potential CSRF attack.", sessionState, incomingState);
            // Clear the potentially compromised state from the session
            session.removeAttribute(SESSION_FRONTEND_STATE_KEY);
            // Redirect to frontend login with a state mismatch error
            return new RedirectView(frontendBaseUrl + "/login?error=State+mismatch");
        }

        // State is valid, clear it from the session as it's single-use for this flow step
        session.removeAttribute(SESSION_FRONTEND_STATE_KEY);
        logger.info("State validation successful for state: [{}]", incomingState);
        // --- End State Validation ---


        // Exchange Discord code for tokens
        logger.info("Exchanging Discord code [{}] for tokens...", discordCode);
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        map.add("client_id", discordClientId);
        map.add("client_secret", discordClientSecret);
        map.add("grant_type", "authorization_code");
        map.add("code", discordCode); // Use the code received from Discord
        map.add("redirect_uri", discordRedirectUri);

        HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(map, headers);
        OAuthTokenResponse tokenResponse;

        try {
            tokenResponse = restTemplate.postForObject(DISCORD_TOKEN_URL, requestEntity, OAuthTokenResponse.class);
            if (tokenResponse == null || tokenResponse.getAccessToken() == null) {
                logger.error("Token exchange failed: received null token response from Discord");
                return new RedirectView(frontendBaseUrl + "/login?error=Discord+token+exchange+failed");
            }
            logger.info("Discord token exchange successful.");
        } catch (Exception e) {
            logger.error("Discord token exchange failed: {}", e.getMessage(), e); // Log stack trace
            return new RedirectView(frontendBaseUrl + "/login?error=Discord+token+exchange+exception");
        }

        UserDTO userDTO;
        logger.info("Fetching user info from Discord...");
        try {
            userDTO = oauthService.getUserInfo(tokenResponse.getAccessToken());
            logger.info("User details retrieved successfully from Discord: {}", userDTO);
        } catch (Exception e) {
            logger.error("Failed to retrieve user details from Discord: {}", e.getMessage(), e); // Log stack trace
            return new RedirectView(frontendBaseUrl + "/login?error=Discord+user+info+retrieval+failed");
        }

        // Attempt to add the user to the Discord server
        logger.info("Attempting to add user to Discord server...");
        boolean addedToGuild = discordChannelService.addUserToGuild(
            userDTO.getId(),
            tokenResponse.getAccessToken()
        );
        if (addedToGuild) {
            logger.info("User {} successfully added/verified in Discord server", userDTO.getId());
        } else {
            logger.warn("Could not add user {} to Discord server. User may need to join manually.", userDTO.getId());
            // Note: We continue the authentication flow even if this fails
        }

        // Find or create user in our database
        logger.info("Creating or updating user in database...");
        User user = userService.createOrUpdateUser(userDTO);
        if (user == null || user.getId() == null) {
             logger.error("Failed to create or update user for Discord ID: {}", userDTO.getId());
             return new RedirectView(frontendBaseUrl + "/login?error=User+processing+failed");
        }
        logger.info("User processed successfully. User ID: {}", user.getId());

        // --- Start Secure Code Exchange ---
        // 1. Generate a secure, single-use code
        String singleUseCode;
        try {
            byte[] randomBytes = new byte[32];
            secureRandom.nextBytes(randomBytes); // Generate random bytes
            singleUseCode = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
            logger.debug("Generated single-use code: [{}]", singleUseCode); // Log the generated code
        } catch (Exception e) {
            logger.error("Failed to generate secure single-use code", e);
            return new RedirectView(frontendBaseUrl + "/login?error=Internal+server+error+(code+gen)");
        }


        // 2. Store the code temporarily, associated with the user ID
        try {
            discordCodeStore.storeCode(singleUseCode, user.getId());
            // Log confirmation AFTER storing
            logger.info("Stored single-use code [{}] for user ID: {}", singleUseCode, user.getId());
        } catch (Exception e) {
            logger.error("Failed to store single-use code [{}] for user ID {}: {}", singleUseCode, user.getId(), e.getMessage(), e);
            return new RedirectView(frontendBaseUrl + "/login?error=Internal+server+error+(code+store)");
        }

        // 3. URL-encode the single-use code and the original frontend state received from Discord
        String encodedSingleUseCode;
        String encodedFrontendState;
        try {
             encodedSingleUseCode = URLEncoder.encode(singleUseCode, StandardCharsets.UTF_8);
             // Use the validated incoming state received from Discord (and validated against session)
             encodedFrontendState = URLEncoder.encode(incomingState, StandardCharsets.UTF_8);
             logger.debug("Encoded single-use code: [{}], Encoded state: [{}]", encodedSingleUseCode, encodedFrontendState);
        } catch (Exception e) {
             logger.error("Failed to URL encode parameters for frontend redirect", e);
             return new RedirectView(frontendBaseUrl + "/login?error=Internal+server+error+(encoding)");
        }


        // Build the redirect URL to the frontend callback
        String frontendRedirectUrl = String.format(
                "%s/auth/discord/callback?code=%s&state=%s",
                frontendBaseUrl,
                encodedSingleUseCode,
                encodedFrontendState 
        );
        // --- End Secure Code Exchange ---

        // Ensure the user's avatar in the application reflects their Discord avatar
        // userDTO.getAvatar() contains the full Discord CDN URL from oauthService.getUserInfo()
        String discordAvatarFromDTO = userDTO.getAvatar(); 

        if (discordAvatarFromDTO != null && !discordAvatarFromDTO.isEmpty()) {
            boolean changed = false;
            String currentPrimaryAvatar = user.getAvatar();
            String currentDiscordAvatarCache = user.getDiscordAvatarUrl();

            // Logic to update the primary avatar:
            // Only update if the current primary avatar is null, empty, or the "USE_DISCORD_AVATAR" marker,
            // AND it's different from the new Discord avatar.
            if ((currentPrimaryAvatar == null || currentPrimaryAvatar.isEmpty() || "USE_DISCORD_AVATAR".equals(currentPrimaryAvatar))
                && !discordAvatarFromDTO.equals(currentPrimaryAvatar)) {
                user.setAvatar(discordAvatarFromDTO);
                logger.debug("Updating primary avatar for user {} from '{}' to '{}' (was null, empty, or USE_DISCORD_AVATAR).", user.getId(), currentPrimaryAvatar, discordAvatarFromDTO);
                changed = true;
            } else if (currentPrimaryAvatar != null && !currentPrimaryAvatar.isEmpty() && !"USE_DISCORD_AVATAR".equals(currentPrimaryAvatar)) {
                // User has a custom avatar, ensure it's not accidentally overwritten by a non-changed check below.
                // Log that we are preserving the custom avatar.
                if (!discordAvatarFromDTO.equals(currentPrimaryAvatar)) {
                     logger.debug("User {} has a custom primary avatar '{}'. Preserving it over Discord avatar '{}'.", user.getId(), currentPrimaryAvatar, discordAvatarFromDTO);
                }
            }

            // Always update the specific discordAvatarUrl field if it's different and a valid Discord CDN URL.
            // This ensures the cached Discord avatar is always up-to-date.
            if (discordAvatarFromDTO.contains("cdn.discordapp.com") && !discordAvatarFromDTO.equals(currentDiscordAvatarCache)) {
                user.setDiscordAvatarUrl(discordAvatarFromDTO);
                logger.debug("Updating cached discordAvatarUrl for user {} from '{}' to '{}'.", user.getId(), currentDiscordAvatarCache, discordAvatarFromDTO);
                changed = true;
            }

            if (changed) {
                userService.updateUser(user); // Persist the changes
                logger.info("User avatar data updated (if necessary) from Discord for user {} during OAuth callback.", user.getId());
            } else {
                logger.debug("User avatar data for user {} required no updates based on Discord data or custom avatar was preserved.", user.getId());
            }
        }


        logger.info(">>> CONSTRUCTED REDIRECT URL TO FRONTEND: {}", frontendRedirectUrl); // CRITICAL LOG
        return new RedirectView(frontendRedirectUrl);
    }
}
