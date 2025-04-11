package com.app.heartbound.config;

import jakarta.annotation.PreDestroy;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DiscordConfig {

    private static final Logger logger = LoggerFactory.getLogger(DiscordConfig.class);

    // Inject the token from application.properties
    @Value("${discord.token}")
    private String discordToken;

    private JDA jdaInstance;

    @Bean
    public JDA jda() {
        if (discordToken == null || discordToken.isBlank() || discordToken.equals("${DISCORD_BOT_TOKEN}")) {
            logger.error("Discord token is missing, invalid, or not replaced in properties. JDA bean cannot be created.");
            // Fail fast if the token isn't configured correctly
            throw new IllegalStateException("Discord token is missing or invalid. Check .env and application.properties.");
        }

        try {
            logger.info("Attempting to build JDA instance manually...");
            // Build JDA with necessary intents based on previous configuration and usage
            // Ref: application.properties line 47
            // Ref: DiscordChannelService.java lines 36-79 (implies GUILD_VOICE_STATES)
            jdaInstance = JDABuilder.createDefault(discordToken)
                    .enableIntents(
                            GatewayIntent.GUILD_MEMBERS,      // Required for adding users to the server
                            GatewayIntent.GUILD_VOICE_STATES, // Needed for voice channel creation
                            GatewayIntent.MESSAGE_CONTENT     // Enabled in portal/properties
                            // Add any other intents your bot might need in the future
                    )
                    // Enable necessary caches
                    .enableCache(
                            CacheFlag.VOICE_STATE,         // Needed for voice operations
                            CacheFlag.MEMBER_OVERRIDES,    // Might be needed for category permissions
                            CacheFlag.ROLE_TAGS            // Often useful with members/permissions
                            // Add CacheFlag.CHANNEL_STRUCTURE if issues persist, but it increases memory usage
                    )
                    // Disable unnecessary caches
                    .disableCache(
                            CacheFlag.ACTIVITY,
                            CacheFlag.EMOJI,
                            CacheFlag.STICKER,
                            CacheFlag.CLIENT_STATUS,
                            CacheFlag.ONLINE_STATUS,
                            CacheFlag.SCHEDULED_EVENTS
                            // CacheFlag.FORUM_TAGS // If not using forums
                    )
                    .build();

            // Waits until JDA is fully connected and ready. Consider adding a timeout.
            jdaInstance.awaitReady();
            logger.info("JDA instance built and ready!");
            return jdaInstance;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("JDA initialization was interrupted.", e);
            throw new RuntimeException("JDA initialization interrupted", e);
        } catch (Exception e) { // Catch other potential exceptions (e.g., LoginException for invalid token)
            logger.error("Failed to build JDA instance. Check token and intents.", e);
            // Throwing ensures the application context fails to load if JDA fails
            throw new RuntimeException("Failed to initialize JDA", e);
        }
    }

    // Graceful shutdown when the Spring application context closes
    @PreDestroy
    public void shutdown() {
        if (jdaInstance != null) {
            logger.info("Shutting down JDA instance...");
            // shutdownNow() is generally preferred in a Spring context for faster cleanup
            jdaInstance.shutdownNow();
            logger.info("JDA instance shut down.");
        }
    }
} 