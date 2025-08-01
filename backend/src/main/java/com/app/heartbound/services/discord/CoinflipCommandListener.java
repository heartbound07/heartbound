package com.app.heartbound.services.discord;

import com.app.heartbound.entities.User;
import com.app.heartbound.services.UserService;
import com.app.heartbound.services.SecureRandomService;
import com.app.heartbound.services.AuditService;
import com.app.heartbound.dto.CreateAuditDTO;
import com.app.heartbound.enums.AuditSeverity;
import com.app.heartbound.enums.AuditCategory;
import com.app.heartbound.config.CacheConfig;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import java.awt.Color;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Component
public class CoinflipCommandListener extends ListenerAdapter {
    
    private static final Logger logger = LoggerFactory.getLogger(CoinflipCommandListener.class);
    private static final Color EMBED_COLOR = new Color(88, 101, 242); // Discord Blurple
    private static final Color SUCCESS_COLOR = new Color(40, 167, 69); // Bootstrap success green
    private static final Color FAILURE_COLOR = new Color(220, 53, 69); // Bootstrap danger red
    
    private final UserService userService;
    private final CacheConfig cacheConfig;
    private final SecureRandomService secureRandomService;
    private final AuditService auditService;
    private final TermsOfServiceService termsOfServiceService;
    
    @Value("${discord.main.guild.id}")
    private String mainGuildId;

    public CoinflipCommandListener(UserService userService, CacheConfig cacheConfig, SecureRandomService secureRandomService, AuditService auditService, TermsOfServiceService termsOfServiceService) {
        this.userService = userService;
        this.cacheConfig = cacheConfig;
        this.secureRandomService = secureRandomService;
        this.auditService = auditService;
        this.termsOfServiceService = termsOfServiceService;
        logger.info("CoinflipCommandListener initialized with secure random and audit service");
    }
    
    @Override
    public void onSlashCommandInteraction(@Nonnull SlashCommandInteractionEvent event) {
        if (!event.getName().equals("coinflip")) {
            return; // Not our command
        }
        
        // Guild restriction check
        final Guild guild = event.getGuild();
        if (guild == null || !guild.getId().equals(mainGuildId)) {
            event.reply("This command can only be used in the main Heartbound server.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        // Require Terms of Service agreement before proceeding
        termsOfServiceService.requireAgreement(event, user -> {
            // Defer reply to prevent timeout
            event.deferReply().queue();

            // Handle the coinflip command logic
            handleCoinflipCommand(event);
        });
    }

    /**
     * Handles the main coinflip command logic after ToS agreement
     */
    private void handleCoinflipCommand(@Nonnull SlashCommandInteractionEvent event) {
        String userId = event.getUser().getId();
        logger.info("User {} requested /coinflip", userId);
        
        // Get command options
        OptionMapping guessOption = event.getOption("guess");
        OptionMapping betOption = event.getOption("bet");
        
        if (guessOption == null || betOption == null) {
            event.getHook().editOriginal("Both guess and bet amount are required!").queue();
            return;
        }
        
        String userGuess = guessOption.getAsString().toLowerCase();
        int betAmount = betOption.getAsInt();
        
        // Validate guess
        if (!userGuess.equals("heads") && !userGuess.equals("tails")) {
            event.getHook().editOriginal("Invalid guess! Please choose either 'heads' or 'tails'.").queue();
            return;
        }
        
        // Validate bet amount
        if (betAmount <= 0) {
            event.getHook().editOriginal("Bet amount must be greater than 0!").queue();
            return;
        }
        
        try {
            // Fetch the user from the database
            User user = userService.getUserById(userId);
            
            if (user == null) {
                logger.warn("User {} not found in database when using /coinflip", userId);
                event.getHook().editOriginal("Could not find your account. Please log in to the web application first.").queue();
                return;
            }
            
            // Get current credits (handle null case)
            Integer credits = user.getCredits();
            int currentCredits = (credits == null) ? 0 : credits;
            
            logger.debug("User {} current credits: {}, bet amount: {}", userId, currentCredits, betAmount);
            
            // Validate sufficient credits
            if (currentCredits < betAmount) {
                EmbedBuilder errorEmbed = new EmbedBuilder()
                    .setColor(FAILURE_COLOR)
                    .setTitle("❌ Insufficient Credits")
                    .setDescription(String.format("You need **🪙%d credits** but only have **🪙%d credits**!", betAmount, currentCredits));
                
                event.getHook().editOriginalEmbeds(errorEmbed.build()).queue();
                return;
            }
            
            // Send initial "flipping" embed
            EmbedBuilder initialEmbed = new EmbedBuilder()
                .setColor(EMBED_COLOR)
                .setTitle(String.format("%s, you chose %s", event.getUser().getEffectiveName(), userGuess))
                .setDescription("🪙 flipping the coin...");
            
            event.getHook().editOriginalEmbeds(initialEmbed.build()).queue();
            
            // Perform the coin flip after 3.5 seconds delay
            CompletableFuture.delayedExecutor(3500, TimeUnit.MILLISECONDS).execute(() -> {
                try {
                    boolean coinResult = secureRandomService.getSecureInt(2) == 1; // 0 = heads, 1 = tails
                    String coinResultString = coinResult ? "tails" : "heads";
                    boolean userWon = userGuess.equals(coinResultString);
                    
                    // Calculate credit change
                    int creditChange = userWon ? betAmount : -betAmount;
                    
                    // Atomically update user's credits
                    boolean success = userService.updateCreditsAtomic(userId, creditChange);
                    
                    if (!success) {
                        logger.error("Failed to atomically update credits for user {} in coinflip. Amount: {}", userId, creditChange);
                        event.getHook().editOriginal("An error occurred while updating your credits. Please contact support.").queue();
                        return;
                    }

                    // Fetch the updated user to get the new balance for the embed
                    User updatedUser = userService.getUserById(userId);
                    int newCredits = (updatedUser != null && updatedUser.getCredits() != null) ? updatedUser.getCredits() : 0;
                    
                    EmbedBuilder resultEmbed = new EmbedBuilder();
                    
                    if (userWon) {
                        // User won
                        // Create audit entry for win
                        try {
                            CreateAuditDTO auditEntry = CreateAuditDTO.builder()
                                .userId(userId)
                                .action("COINFLIP_WIN")
                                .entityType("USER_CREDITS")
                                .entityId(userId)
                                .description(String.format("Won %d credits in coinflip (bet: %d, guess: %s, result: %s)", 
                                    betAmount, betAmount, userGuess, coinResultString))
                                .severity(betAmount > 1000 ? AuditSeverity.WARNING : AuditSeverity.INFO)
                                .category(AuditCategory.FINANCIAL)
                                .details(String.format("{\"game\":\"coinflip\",\"bet\":%d,\"guess\":\"%s\",\"result\":\"%s\",\"won\":%d,\"newBalance\":%d}", 
                                    betAmount, userGuess, coinResultString, betAmount, newCredits))
                                .source("DISCORD_BOT")
                                .build();
                            
                            auditService.createSystemAuditEntry(auditEntry);
                        } catch (Exception e) {
                            logger.error("Failed to create audit entry for coinflip win by user {}: {}", userId, e.getMessage());
                        }
                        
                        resultEmbed
                            .setColor(SUCCESS_COLOR)
                            .setTitle(String.format("🎉 You got it right, it was %s!", coinResultString))
                            .setDescription(String.format("You have earned **🪙 %d credits**!", betAmount))
                            .setFooter(event.getUser().getEffectiveName() + ", you now have " + newCredits + " credits", null);
                        
                        logger.info("User {} won coinflip: +{} credits. New balance: {}", userId, betAmount, newCredits);
                    } else {
                        // User lost
                        // Create audit entry for loss
                        try {
                            CreateAuditDTO auditEntry = CreateAuditDTO.builder()
                                .userId(userId)
                                .action("COINFLIP_LOSS")
                                .entityType("USER_CREDITS")
                                .entityId(userId)
                                .description(String.format("Lost %d credits in coinflip (bet: %d, guess: %s, result: %s)", 
                                    betAmount, betAmount, userGuess, coinResultString))
                                .severity(betAmount > 1000 ? AuditSeverity.WARNING : AuditSeverity.INFO)
                                .category(AuditCategory.FINANCIAL)
                                .details(String.format("{\"game\":\"coinflip\",\"bet\":%d,\"guess\":\"%s\",\"result\":\"%s\",\"lost\":%d,\"newBalance\":%d}", 
                                    betAmount, userGuess, coinResultString, betAmount, newCredits))
                                .source("DISCORD_BOT")
                                .build();
                            
                            auditService.createSystemAuditEntry(auditEntry);
                        } catch (Exception e) {
                            logger.error("Failed to create audit entry for coinflip loss by user {}: {}", userId, e.getMessage());
                        }
                        
                        resultEmbed
                            .setColor(FAILURE_COLOR)
                            .setTitle(String.format("💸 Whoops, it was %s.", coinResultString))
                            .setDescription(String.format("You lost **🪙 %d credits**!", betAmount))
                            .setFooter(event.getUser().getEffectiveName() + ", you now have " + newCredits + " credits", null);
                        
                        logger.info("User {} lost coinflip: -{} credits. New balance: {}", userId, betAmount, newCredits);
                    }
                    
                    // Invalidate user profile cache to ensure fresh data
                    cacheConfig.invalidateUserProfileCache(userId);
                    
                    // Update the embed with the result
                    event.getHook().editOriginalEmbeds(resultEmbed.build()).queue(
                        v -> logger.debug("Coinflip result sent to user {}", userId),
                        error -> logger.error("Failed to send coinflip result to user {}: {}", userId, error.getMessage())
                    );
                    
                } catch (Exception e) {
                    logger.error("Error processing coinflip result for user {}", userId, e);
                    
                    EmbedBuilder errorEmbed = new EmbedBuilder()
                        .setColor(FAILURE_COLOR)
                        .setTitle("❌ Error")
                        .setDescription("An error occurred while processing your coinflip. Please try again.");
                    
                    event.getHook().editOriginalEmbeds(errorEmbed.build()).queue();
                }
            });
            
        } catch (Exception e) {
            logger.error("Error processing /coinflip command for user {}", userId, e);
            
            EmbedBuilder errorEmbed = new EmbedBuilder()
                .setColor(FAILURE_COLOR)
                .setTitle("❌ Error")
                .setDescription("An error occurred while processing your coinflip.");
            
            event.getHook().editOriginalEmbeds(errorEmbed.build()).queue();
        }
    }
} 