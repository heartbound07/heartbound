package com.app.heartbound.config;

import com.app.heartbound.services.discord.LeaderboardCommandListener;
import com.app.heartbound.services.discord.ChatActivityListener;
import com.app.heartbound.services.discord.CreditsCommandListener;
import com.app.heartbound.services.discord.WelcomeListener;
import com.app.heartbound.services.discord.WelcomeCommandListener;
import com.app.heartbound.services.discord.ShopCommandListener;
import com.app.heartbound.services.discord.InventoryCommandListener;
import com.app.heartbound.services.discord.FishCommandListener;
import com.app.heartbound.services.discord.StatsCommandListener;
import com.app.heartbound.services.discord.BreakupCommandListener;
import com.app.heartbound.services.discord.DiscordMessageListenerService;
import com.app.heartbound.services.discord.DiscordVoiceTimeTrackerService;
import jakarta.annotation.PreDestroy;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import java.util.concurrent.TimeUnit;

@Configuration
public class DiscordConfig {

    private static final Logger logger = LoggerFactory.getLogger(DiscordConfig.class);

    // Inject the token from application.properties
    @Value("${discord.token}")
    private String discordToken;

    private JDA jdaInstance;
    
    @Autowired
    private LeaderboardCommandListener leaderboardCommandListener;

    @Autowired
    private ChatActivityListener chatActivityListener;

    @Autowired
    private CreditsCommandListener creditsCommandListener;

    @Autowired
    private WelcomeListener welcomeListener;

    @Autowired
    private WelcomeCommandListener welcomeCommandListener;

    @Lazy
    @Autowired
    private ShopCommandListener shopCommandListener;

    @Autowired
    private InventoryCommandListener inventoryCommandListener;

    @Autowired
    private FishCommandListener fishCommandListener;

    @Lazy
    @Autowired
    private StatsCommandListener statsCommandListener;

    @Lazy
    @Autowired
    private BreakupCommandListener breakupCommandListener;

    @Autowired
    private DiscordMessageListenerService discordMessageListenerService;

    @Autowired
    private DiscordVoiceTimeTrackerService discordVoiceTimeTrackerService;

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
            jdaInstance = JDABuilder.createDefault(discordToken)
                    .enableIntents(
                            GatewayIntent.GUILD_MEMBERS,      // Required for adding users to the server
                            GatewayIntent.GUILD_VOICE_STATES, // Needed for voice channel creation
                            GatewayIntent.MESSAGE_CONTENT,    // Enabled in portal/properties
                            GatewayIntent.GUILD_MESSAGES      // Required for receiving messages
                    )
                    // Enable necessary caches
                    .enableCache(
                            CacheFlag.VOICE_STATE,         // Needed for voice operations
                            CacheFlag.MEMBER_OVERRIDES,    // Might be needed for category permissions
                            CacheFlag.ROLE_TAGS            // Often useful with members/permissions
                    )
                    // Disable unnecessary caches
                    .disableCache(
                            CacheFlag.ACTIVITY,
                            CacheFlag.EMOJI,
                            CacheFlag.STICKER,
                            CacheFlag.CLIENT_STATUS,
                            CacheFlag.ONLINE_STATUS,
                            CacheFlag.SCHEDULED_EVENTS
                    )
                    // Register all listeners EXCEPT shopCommandListener and statsCommandListener (we'll register them manually)
                    .addEventListeners(leaderboardCommandListener, chatActivityListener, 
                                      creditsCommandListener, welcomeListener, welcomeCommandListener,
                                      inventoryCommandListener, fishCommandListener,
                                      discordMessageListenerService, discordVoiceTimeTrackerService)
                    .build();

            // Waits until JDA is fully connected and ready
            jdaInstance.awaitReady();
            logger.info("JDA instance built and ready!");
            
            // Register shop command listener manually
            shopCommandListener.registerWithJDA(jdaInstance);
            
            // Register stats command listener manually
            statsCommandListener.registerWithJDA(jdaInstance);
            
            // Register breakup command listener manually
            breakupCommandListener.registerWithJDA(jdaInstance);
            
            // Register slash commands
            registerSlashCommands();
            
            return jdaInstance;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("JDA initialization was interrupted.", e);
            throw new RuntimeException("JDA initialization interrupted", e);
        } catch (Exception e) { // Catch other potential exceptions
            logger.error("Failed to build JDA instance. Check token and intents.", e);
            throw new RuntimeException("Failed to initialize JDA", e);
        }
    }
    
    /**
     * Register all slash commands used by the application
     */
    private void registerSlashCommands() {
        try {
            logger.debug("[SLASH COMMAND DEBUG] Registering slash commands with Discord API");
            
            // This will overwrite all existing global commands with the new definitions
            jdaInstance.updateCommands()
                .addCommands(
                    Commands.slash("leaderboard", "Displays the user leaderboard by level or credits")
                        .addOptions(
                            new OptionData(OptionType.STRING, "type", "Sort leaderboard by 'levels' or 'credits'", false)
                                .addChoice("levels", "levels")
                                .addChoice("credits", "credits")
                        ),
                    Commands.slash("credits", "Check your current credit balance"),
                    Commands.slash("welcome", "Sends the verification welcome message")
                        .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR)),
                    Commands.slash("shop", "Displays items currently available in the shop"),
                    Commands.slash("inventory", "Displays the items you currently own"),
                    Commands.slash("fish", "Go fishing for a chance to win or lose credits"),
                    Commands.slash("stats", "View your current pairing statistics"),
                    Commands.slash("breakup", "End your current match/pairing")
                )
                .queue(
                    cmds -> {
                        logger.info("Successfully registered {} slash commands", cmds.size());
                        cmds.forEach(command -> 
                            logger.debug("[SLASH COMMAND DEBUG] Registered command: /{} - {}", 
                                       command.getName(), command.getDescription())
                        );
                    },
                    error -> logger.error("Failed to register slash commands: {}", error.getMessage())
                );
        } catch (Exception e) {
            logger.error("Error registering slash commands", e);
        }
    }

    // Graceful shutdown when the Spring application context closes
    @PreDestroy
    public void shutdown() {
        if (jdaInstance != null) {
            logger.info("Shutting down JDA instance...");
            
            try {
                // First, unregister all event listeners to prevent events during shutdown
                // Note: the ShopCommandListener will handle its own unregistration
                jdaInstance.shutdownNow();
                
                // Wait for JDA to fully shut down with a timeout
                boolean shutdownComplete = jdaInstance.awaitShutdown(10, TimeUnit.SECONDS);
                
                if (shutdownComplete) {
                    logger.info("JDA instance shut down successfully.");
                } else {
                    logger.warn("JDA instance shutdown timed out after 10 seconds.");
                }
            } catch (Exception e) {
                logger.error("Error during JDA shutdown", e);
            }
        }
    }
}