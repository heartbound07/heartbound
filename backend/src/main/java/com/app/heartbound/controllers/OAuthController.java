package com.app.heartbound.controllers;

import java.math.BigInteger;
import java.security.SecureRandom;
import javax.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;

@RestController
public class DiscordAuthController {

    @Value("${discord.client-id}")
    private String discordClientId;

    @Value("${discord.redirect-uri}")
    private String discordRedirectUri;

    @Value("${discord.scopes}")
    private String discordScopes;

    // Discord's OAuth2 endpoint URL.
    private static final String DISCORD_AUTH_URL = "https://discord.com/api/oauth2/authorize";

    // The session key where the CSRF state token will be stored.
    private static final String SESSION_STATE = "DISCORD_OAUTH_STATE";

    @GetMapping("/auth/discord/authorize")
    public RedirectView authorize(HttpSession session) {
        // Generate a secure random state token for CSRF protection.
        String state = new BigInteger(130, new SecureRandom()).toString(32);
        session.setAttribute(SESSION_STATE, state);

        // Construct the redirect URL.
        String redirectUrl = String.format("%s?client_id=%s&redirect_uri=%s&response_type=code&scope=%s&state=%s",
                DISCORD_AUTH_URL,
                discordClientId,
                discordRedirectUri,
                discordScopes.replace(" ", "%20"),
                state);

        return new RedirectView(redirectUrl);
    }
}
