package com.app.heartbound.services.discord;

import com.app.heartbound.entities.BlackjackGame;
import com.app.heartbound.entities.BlackjackHand;
import com.app.heartbound.entities.Card;
import com.app.heartbound.entities.User;
import com.app.heartbound.services.UserService;
import com.app.heartbound.services.SecureRandomService;
import com.app.heartbound.services.AuditService;
import com.app.heartbound.dto.CreateAuditDTO;
import com.app.heartbound.enums.AuditSeverity;
import com.app.heartbound.enums.AuditCategory;
import com.app.heartbound.entities.DiscordBotSettings;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;
import java.awt.Color;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;

@Component
public class BlackjackCommandListener extends ListenerAdapter {
    
    private static final Logger logger = LoggerFactory.getLogger(BlackjackCommandListener.class);
    private static final Color EMBED_COLOR = new Color(88, 101, 242); // Discord Blurple
    private static final Color WIN_COLOR = new Color(40, 167, 69); // Green
    private static final Color LOSE_COLOR = new Color(220, 53, 69); // Red
    private static final Color PUSH_COLOR = new Color(255, 193, 7); // Yellow
    
    // Track active games - userId -> BlackjackGame
    private final ConcurrentHashMap<String, BlackjackGame> activeGames = new ConcurrentHashMap<>();
    
    private final UserService userService;
    private final SecureRandomService secureRandomService;
    private final AuditService auditService;
    private final DiscordBotSettingsService discordBotSettingsService;
    private final TermsOfServiceService termsOfServiceService;
    
    @Value("${discord.main.guild.id}")
    private String mainGuildId;

    public BlackjackCommandListener(UserService userService, SecureRandomService secureRandomService, AuditService auditService, DiscordBotSettingsService discordBotSettingsService, TermsOfServiceService termsOfServiceService) {
        this.userService = userService;
        this.secureRandomService = secureRandomService;
        this.auditService = auditService;
        this.discordBotSettingsService = discordBotSettingsService;
        this.termsOfServiceService = termsOfServiceService;
        logger.info("BlackjackCommandListener initialized with secure random and audit service");
    }
    
    @Override
    public void onSlashCommandInteraction(@Nonnull SlashCommandInteractionEvent event) {
        if (!event.getName().equals("blackjack")) {
            return; // Not our command
        }
        
        // Guild restriction check
        Guild guild = event.getGuild();
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

            // Handle the blackjack command logic
            handleBlackjackCommand(event);
        });
    }

    /**
     * Handles the main blackjack command logic after ToS agreement
     */
    private void handleBlackjackCommand(@Nonnull SlashCommandInteractionEvent event) {
        String userId = event.getUser().getId();
        logger.info("User {} requested /blackjack", userId);
        
        // Check for active game first
        if (activeGames.containsKey(userId)) {
            try {
                BlackjackGame existingGame = activeGames.get(userId);
                
                if (existingGame == null || existingGame.isGameEnded()) {
                    // Game ended but wasn't cleaned up properly, remove it and start fresh
                    activeGames.remove(userId);
                    // Continue to create new game below
                } else {
                    // Show current game state
                    MessageEmbed embed = buildGameEmbed(existingGame, event.getUser().getEffectiveName(), event.getUser().getEffectiveAvatarUrl(), false);
                    
                    // Build action buttons based on game state
                    List<Button> buttons = buildActionButtons(existingGame);
                    
                    event.getHook().sendMessageEmbeds(embed)
                            .addActionRow(buttons)
                            .queue();
                    return;
                }
            } catch (Exception e) {
                logger.error("Error showing existing game for user {}", userId, e);
                // Remove the corrupted game and continue to create new game
                activeGames.remove(userId);
            }
        }
        
        try {
            // Get bet amount from command option
            OptionMapping betOption = event.getOption("bet");
            if (betOption == null) {
                event.getHook().sendMessage("Bet amount is a required option.").setEphemeral(true).queue();
                return;
            }
            int betAmount = betOption.getAsInt();
            
            // Validate bet amount
            if (betAmount <= 0) {
                event.getHook().sendMessage("Bet amount must be greater than 0.").setEphemeral(true).queue();
                return;
            }
            
            // Fetch the user from the database
            User user = userService.getUserById(userId);
            
            if (user == null) {
                logger.warn("User {} not found in database when using /blackjack", userId);
                event.getHook().sendMessage("Could not find your account. Please log in to the web application first.")
                        .setEphemeral(true)
                        .queue();
                return;
            }
            
            // Get current credits (handle null case)
            Integer credits = user.getCredits();
            int currentCredits = (credits == null) ? 0 : credits;
            
            logger.info("User {} current credits: {}, bet amount: {}", userId, currentCredits, betAmount);
            
            // Validate sufficient credits before deduction
            if (currentCredits < betAmount) {
                event.getHook().sendMessage("You don't have enough credits! You have " + currentCredits + " credits but tried to bet " + betAmount + ".")
                        .setEphemeral(true)
                        .queue();
                return;
            }
            
            // Fetch Discord bot settings to get role multipliers
            DiscordBotSettings settings = discordBotSettingsService.getDiscordBotSettings();

            // Get user's highest role multiplier
            Member member = event.getMember();
            double roleMultiplier = userService.getUserHighestMultiplier(member, settings);
            
            // Create new game with secure random and role multiplier
            BlackjackGame game = new BlackjackGame(userId, betAmount, roleMultiplier, secureRandomService);
            
            // Atomically add game to prevent duplicate games for same user
            BlackjackGame existingGame = activeGames.putIfAbsent(userId, game);
            if (existingGame != null) {
                // Race condition: another game was created concurrently
                event.getHook().sendMessage("You already have an active blackjack game!")
                        .setEphemeral(true)
                        .queue();
                return;
            }
            
            // Only deduct credits after successful game creation and insertion
            boolean betDeducted = userService.deductCreditsIfSufficient(userId, betAmount);
            if (!betDeducted) {
                // Remove the game since credit deduction failed
                activeGames.remove(userId);
                event.getHook().sendMessage("Credit deduction failed. Please try again.")
                        .setEphemeral(true)
                        .queue();
                return;
            }
            
            // Check for immediate blackjack
            if (game.getPlayerHand().isBlackjack()) {
                // Player has blackjack, complete the game immediately
                game.playerStand(); // This will trigger dealer play
                handleGameEnd(event.getHook(), game, user, true, event.getUser().getEffectiveName(), event.getUser().getEffectiveAvatarUrl());
            } else {
                // Send initial game embed with buttons
                MessageEmbed embed = buildGameEmbed(game, event.getUser().getEffectiveName(), event.getUser().getEffectiveAvatarUrl(), false);
                
                // Build action buttons based on game state
                List<Button> buttons = buildActionButtons(game);
                
                event.getHook().sendMessageEmbeds(embed)
                        .addActionRow(buttons)
                        .queue();
            }
            
        } catch (Exception e) {
            logger.error("Error processing /blackjack command for user {}", userId, e);
            
            // Clean up any partial state - remove game and refund credits if necessary
            BlackjackGame failedGame = activeGames.remove(userId);
            if (failedGame != null) {
                // Refund the bet since game creation/initialization failed
                try {
                    userService.updateCreditsAtomic(userId, failedGame.getBetAmount());
                    logger.info("Refunded {} credits to user {} due to game creation failure", failedGame.getBetAmount(), userId);
                } catch (Exception refundException) {
                    logger.error("Failed to refund credits to user {} after game creation failure: {}", userId, refundException.getMessage());
                }
            }
            
            event.getHook().sendMessage("An error occurred while starting the blackjack game. Please try again.")
                    .setEphemeral(true)
                    .queue();
        }
    }
    
    @Override
    public void onButtonInteraction(@Nonnull ButtonInteractionEvent event) {
        String componentId = event.getComponentId();
        
        if (!componentId.startsWith("blackjack_")) {
            return; // Not our button
        }
        
        // Parse the user ID from the component ID
        String[] parts = componentId.split(":");
        if (parts.length != 2) {
            logger.warn("Invalid button component ID: {}", componentId);
            return;
        }
        
        String action = parts[0];
        String gameUserId = parts[1];
        String interactingUserId = event.getUser().getId();
        
        // Verify user
        if (!interactingUserId.equals(gameUserId)) {
            event.reply("This is not your game!")
                    .setEphemeral(true)
                    .queue();
            return;
        }
        
        // Get the active game
        BlackjackGame game = activeGames.get(gameUserId);
        if (game == null) {
            event.reply("No active game found. Please start a new game with `/blackjack`.")
                    .setEphemeral(true)
                    .queue();
            return;
        }
        
        // Acknowledge the button interaction
        event.deferEdit().queue();
        
        try {
            // Get user for credit updates
            User user = userService.getUserById(gameUserId);
            if (user == null) {
                logger.error("User {} not found when processing button interaction", gameUserId);
                event.getHook().editOriginal("Error: User not found.")
                        .setComponents() // Remove buttons
                        .queue();
                activeGames.remove(gameUserId);
                return;
            }
            
            if (action.equals("blackjack_hit")) {
                handleHit(event, game, user);
            } else if (action.equals("blackjack_stay")) {
                handleStay(event, game, user);
            } else if (action.equals("blackjack_double_down")) {
                handleDoubleDown(event, game, user);
            } else if (action.equals("blackjack_split")) {
                handleSplit(event, game, user);
            }
            
        } catch (Exception e) {
            logger.error("Error processing blackjack button interaction for user {}", gameUserId, e);
            event.getHook().editOriginal("An error occurred while processing your action.")
                    .setComponents() // Remove buttons
                    .queue();
            activeGames.remove(gameUserId);
        }
    }
    
    private void handleHit(ButtonInteractionEvent event, BlackjackGame game, User user) {
        if (game.isGameEnded()) {
            event.getHook().editOriginal("Game has already ended.")
                    .setComponents()
                    .queue();
            activeGames.remove(game.getUserId());
            return;
        }
        
        if (!game.canActiveHandAct()) {
            event.getHook().editOriginal("Cannot hit in current game state.")
                    .setComponents()
                    .queue();
            return;
        }
        
        // Player hits
        Card dealtCard = game.playerHit();
        logger.debug("User {} hit and received: {}", game.getUserId(), dealtCard);
        
        // Update the embed
        MessageEmbed embed = buildGameEmbed(game, event.getUser().getEffectiveName(), event.getUser().getEffectiveAvatarUrl(), false);
        
        if (game.isGameEnded()) {
            // Game ended (bust or split completion)
            handleGameEnd(event.getHook(), game, user, false, event.getUser().getEffectiveName(), event.getUser().getEffectiveAvatarUrl());
        } else {
            // Game continues, update buttons based on current state
            List<Button> buttons = buildActionButtons(game);
            
            event.getHook().editOriginalEmbeds(embed)
                    .setActionRow(buttons)
                    .queue();
        }
    }
    
    private void handleStay(ButtonInteractionEvent event, BlackjackGame game, User user) {
        if (game.isGameEnded()) {
            event.getHook().editOriginal("Game has already ended.")
                    .setComponents()
                    .queue();
            activeGames.remove(game.getUserId());
            return;
        }

        // Player stands on the current hand. This will transition the game state internally.
        game.playerStand();
        logger.debug("User {} stood. isGameEnded: {}, isSplit: {}, isPlayingFirstHand: {}",
                game.getUserId(), game.isGameEnded(), game.isSplit(), game.isPlayingFirstHand());

        // After playerStand(), if the game has ended, it's the dealer's turn.
        // If it hasn't, it's a split game, and we proceed to the second hand.
        if (game.isGameEnded()) {
            // This occurs after standing on a single hand or the second hand of a split.
            // The player's turn is over; start the dealer's reveal sequence.
            logger.info("User {}'s turn is over. Starting dealer reveal sequence.", game.getUserId());

            // First, reveal the dealer's hidden card and remove action buttons.
            MessageEmbed embed = buildGameEmbed(game, event.getUser().getEffectiveName(), event.getUser().getEffectiveAvatarUrl(), false);
            event.getHook().editOriginalEmbeds(embed)
                    .setComponents() // Remove buttons as the player's turn is complete.
                    .queue();

            // Start the dramatic dealer play sequence after a short delay.
            java.util.concurrent.ScheduledExecutorService scheduler = java.util.concurrent.Executors.newScheduledThreadPool(1);

            scheduler.schedule(() -> {
                playDealerHandWithDelay(event, game, user, scheduler, 0);
            }, 2, java.util.concurrent.TimeUnit.SECONDS); // 2-second delay before the dealer starts hitting.
        } else {
            // This case is only reachable in a split game after the first hand is played.
            logger.info("User {} finished their first split hand and is now playing the second hand.", game.getUserId());

            // Update the embed to show the new game state (e.g., the second hand is now active).
            MessageEmbed embed = buildGameEmbed(game, event.getUser().getEffectiveName(), event.getUser().getEffectiveAvatarUrl(), false);

            // Provide new action buttons for the second hand.
            List<Button> buttons = buildActionButtons(game);

            event.getHook().editOriginalEmbeds(embed)
                    .setActionRow(buttons)
                    .queue();
        }
    }
    
    private void handleDoubleDown(ButtonInteractionEvent event, BlackjackGame game, User user) {
        if (game.isGameEnded()) {
            event.getHook().editOriginal("Game has already ended.")
                    .setComponents()
                    .queue();
            activeGames.remove(game.getUserId());
            return;
        }
        
        if (!game.canDoubleDown()) {
            event.getHook().editOriginal("Double down is not available.")
                    .setComponents()
                    .queue();
            return;
        }
        
        // Check if user has sufficient credits for the additional bet
        int additionalBet = game.getBetAmount();
        Integer credits = user.getCredits();
        int currentCredits = (credits == null) ? 0 : credits;
        
        if (currentCredits < additionalBet) {
            event.getHook().sendMessage("You don't have enough credits to double down! You need " + additionalBet + " more credits.")
                    .setEphemeral(true)
                    .queue();
            return;
        }
        
        // Deduct additional credits for double down
        boolean creditDeducted = userService.deductCreditsIfSufficient(game.getUserId(), additionalBet);
        if (!creditDeducted) {
            event.getHook().sendMessage("Credit deduction failed for double down.")
                    .setEphemeral(true)
                    .queue();
            return;
        }
        
        try {
            // Execute double down
            Card dealtCard = game.doubleDown();
            logger.debug("User {} doubled down and received: {}", game.getUserId(), dealtCard);
            
            // Update the embed to show the player's new card and final hand total
            MessageEmbed embed = buildGameEmbed(game, event.getUser().getEffectiveName(), event.getUser().getEffectiveAvatarUrl(), false);
            
            if (game.isGameEnded()) {
                // Check if player busted after double down
                if (game.getPlayerHand().isBusted()) {
                    // Player busted, end game immediately without dealer play
                    logger.info("User {} doubled down and busted. Ending game immediately.", game.getUserId());
                    handleGameEnd(event.getHook(), game, user, false, event.getUser().getEffectiveName(), event.getUser().getEffectiveAvatarUrl());
                } else {
                    // Player didn't bust; start the dealer's reveal sequence
                    logger.info("User {} doubled down. Starting dealer reveal sequence.", game.getUserId());
                    
                    // First, update the embed and remove action buttons
                    event.getHook().editOriginalEmbeds(embed)
                            .setComponents() // Remove buttons as the player's turn is complete
                            .queue();
                    
                    // Start the dramatic dealer play sequence after a short delay
                    ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
                    
                    scheduler.schedule(() -> {
                        playDealerHandWithDelay(event, game, user, scheduler, 0);
                    }, 2, TimeUnit.SECONDS); // 2-second delay before the dealer starts hitting
                }
            } else {
                // This shouldn't happen as double down auto-stands, but handle it gracefully
                event.getHook().editOriginalEmbeds(embed)
                        .setComponents()
                        .queue();
            }
            
        } catch (Exception e) {
            logger.error("Error during double down for user {}", game.getUserId(), e);
            // Refund the additional bet on error
            try {
                userService.updateCreditsAtomic(game.getUserId(), additionalBet);
                logger.info("Refunded {} credits to user {} due to double down failure", additionalBet, game.getUserId());
            } catch (Exception refundException) {
                logger.error("Failed to refund double down credits to user {}: {}", game.getUserId(), refundException.getMessage());
            }
            
            event.getHook().editOriginal("An error occurred during double down.")
                    .setComponents()
                    .queue();
            activeGames.remove(game.getUserId());
        }
    }
    
    private void handleSplit(ButtonInteractionEvent event, BlackjackGame game, User user) {
        if (game.isGameEnded()) {
            event.getHook().editOriginal("Game has already ended.")
                    .setComponents()
                    .queue();
            activeGames.remove(game.getUserId());
            return;
        }
        
        if (!game.canSplit()) {
            event.getHook().editOriginal("Split is not available.")
                    .setComponents()
                    .queue();
            return;
        }
        
        // Check if user has sufficient credits for the additional bet
        int additionalBet = game.getBetAmount();
        Integer credits = user.getCredits();
        int currentCredits = (credits == null) ? 0 : credits;
        
        if (currentCredits < additionalBet) {
            event.getHook().sendMessage("You don't have enough credits to split! You need " + additionalBet + " more credits.")
                    .setEphemeral(true)
                    .queue();
            return;
        }
        
        // Deduct additional credits for split
        boolean creditDeducted = userService.deductCreditsIfSufficient(game.getUserId(), additionalBet);
        if (!creditDeducted) {
            event.getHook().sendMessage("Credit deduction failed for split.")
                    .setEphemeral(true)
                    .queue();
            return;
        }
        
        try {
            // Execute split
            game.split();
            logger.debug("User {} split their hand", game.getUserId());
            
            // Update the embed to show split hands
            MessageEmbed embed = buildGameEmbed(game, event.getUser().getEffectiveName(), event.getUser().getEffectiveAvatarUrl(), false);
            
            if (game.isGameEnded()) {
                // Split aces case - game ends immediately
                handleGameEnd(event.getHook(), game, user, false, event.getUser().getEffectiveName(), event.getUser().getEffectiveAvatarUrl());
            } else {
                // Build action buttons for the active hand
                List<Button> buttons = buildActionButtons(game);
                
                event.getHook().editOriginalEmbeds(embed)
                        .setActionRow(buttons)
                        .queue();
            }
            
        } catch (Exception e) {
            logger.error("Error during split for user {}", game.getUserId(), e);
            // Refund the additional bet on error
            try {
                userService.updateCreditsAtomic(game.getUserId(), additionalBet);
                logger.info("Refunded {} credits to user {} due to split failure", additionalBet, game.getUserId());
            } catch (Exception refundException) {
                logger.error("Failed to refund split credits to user {}: {}", game.getUserId(), refundException.getMessage());
            }
            
            event.getHook().editOriginal("An error occurred during split.")
                    .setComponents()
                    .queue();
            activeGames.remove(game.getUserId());
        }
    }
    
    private void playDealerHandWithDelay(ButtonInteractionEvent event, BlackjackGame game, User user, 
                                        java.util.concurrent.ScheduledExecutorService scheduler, int hitCount) {
        try {
            BlackjackHand dealerHand = game.getDealerHand();
            
            // Check if dealer needs to hit (less than 17)
            if (dealerHand.getValue() < 17) {
                // Dealer hits
                Card newCard = game.getDeck().dealCard();
                dealerHand.addCard(newCard);
                
                logger.debug("Dealer hit #{}: {}, new total: {}", hitCount + 1, newCard, dealerHand.getValue());
                
                // Update the embed to show the new card
                MessageEmbed embed = buildGameEmbed(game, event.getUser().getEffectiveName(), event.getUser().getEffectiveAvatarUrl(), false);
                event.getHook().editOriginalEmbeds(embed).queue();
                
                // Check if dealer busted
                if (dealerHand.isBusted()) {
                    // Dealer busted, end game immediately
                    game.setGameEnded(true);
                    handleGameEnd(event.getHook(), game, user, false, event.getUser().getEffectiveName(), event.getUser().getEffectiveAvatarUrl());
                    scheduler.shutdown();
                } else {
                    // Continue dealer hitting after delay
                    scheduler.schedule(() -> {
                        playDealerHandWithDelay(event, game, user, scheduler, hitCount + 1);
                    }, 2500, java.util.concurrent.TimeUnit.MILLISECONDS); // 2.5 second delay between dealer hits
                }
            } else {
                // Dealer stands (17 or higher), end game properly
                // CRITICAL FIX: Ensure game ends through proper game logic to maintain consistency
                game.setGameEnded(true);
                handleGameEnd(event.getHook(), game, user, false, event.getUser().getEffectiveName(), event.getUser().getEffectiveAvatarUrl());
                scheduler.shutdown();
            }
        } catch (Exception e) {
            logger.error("Error during dealer play sequence for user {}", game.getUserId(), e);
            // Fallback: complete the game immediately using standard game logic
            try {
                // Ensure game state is consistent before calling playerStand
                if (!game.isGameEnded()) {
                    game.playerStand();
                }
                handleGameEnd(event.getHook(), game, user, false, event.getUser().getEffectiveName(), event.getUser().getEffectiveAvatarUrl());
            } catch (Exception fallbackException) {
                logger.error("Fallback game end also failed for user {}", game.getUserId(), fallbackException);
                // Force end the game
                game.setGameEnded(true);
                handleGameEnd(event.getHook(), game, user, false, event.getUser().getEffectiveName(), event.getUser().getEffectiveAvatarUrl());
            }
            scheduler.shutdown();
        }
    }
    
    private void handleGameEnd(Object hook, BlackjackGame game, User user, boolean isInitialBlackjack, String discordUserName, String discordAvatarUrl) {
        // Calculate payout
        int payout = game.calculatePayout();
        
        // Atomically update user credits
        int creditChange = 0;
        
        if (game.isSplit()) {
            // Handle split game results
            BlackjackGame.GameResult[] results = game.getSplitResults();
            BlackjackGame.GameResult firstResult = results[0];
            BlackjackGame.GameResult secondResult = results[1];
            
            // Calculate credit change for split games
            int originalBetPerHand = game.getBetAmount() / 2;
            
            // Calculate payout for each hand
            for (BlackjackGame.GameResult result : results) {
                switch (result) {
                    case PLAYER_BLACKJACK:
                    case PLAYER_WIN:
                        creditChange += originalBetPerHand + Math.abs(payout / 2); // Return bet + winnings
                        break;
                    case PUSH:
                        creditChange += originalBetPerHand; // Return bet only
                        break;
                    case DEALER_WIN:
                        // Bet was already deducted, so no change here
                        break;
                    case IN_PROGRESS:
                    default:
                        logger.warn("handleGameEnd called with unexpected split game result '{}' for user {}", result, user.getId());
                        break;
                }
            }
            
            // The total bet was already deducted. This call adds back any returns and winnings.
            if (creditChange > 0) {
                userService.updateCreditsAtomic(user.getId(), creditChange);
            }
            
            // Fetch updated user for accurate balance
            User updatedUser = userService.getUserById(user.getId());
            int newCredits = (updatedUser != null && updatedUser.getCredits() != null) ? updatedUser.getCredits() : 0;
            
            // Create audit entries for split game results
            try {
                createSplitAuditEntries(game, user.getId(), firstResult, secondResult, payout, newCredits);
            } catch (Exception e) {
                logger.error("Failed to create audit entries for split blackjack game for user {}: {}", game.getUserId(), e.getMessage());
            }
            
            // Build final embed for split game
            MessageEmbed embed = buildSplitGameEndEmbed(game, discordUserName, discordAvatarUrl, firstResult, secondResult, payout, newCredits);
            
            // Send final message without buttons
            if (hook instanceof net.dv8tion.jda.api.interactions.InteractionHook) {
                ((net.dv8tion.jda.api.interactions.InteractionHook) hook).editOriginalEmbeds(embed)
                        .setComponents() // Remove buttons
                        .queue();
            }
            
            // Remove game from active games
            activeGames.remove(game.getUserId());
            
            logger.info("Split blackjack game ended for user {}: hand1={}, hand2={}, credit change={}, new credits={}", 
                    game.getUserId(), firstResult, secondResult, creditChange, newCredits);
        } else {
            // Handle single hand game (existing logic)
            BlackjackGame.GameResult result = game.getResult();
            
            switch (result) {
                case PLAYER_BLACKJACK:
                    creditChange = game.getBetAmount() + payout; // Return bet + 1.5x bet
                    break;
                case PLAYER_WIN:
                    creditChange = game.getBetAmount() + payout; // Return bet + 1x bet
                    break;
                case PUSH:
                    creditChange = game.getBetAmount(); // Return bet only
                    break;
                case DEALER_WIN:
                    // Bet was already deducted, so no change here. The initial bet is lost.
                    creditChange = 0;
                    break;
                case IN_PROGRESS:
                default:
                    logger.warn("handleGameEnd called with unexpected game result '{}' for user {}", result, user.getId());
                    activeGames.remove(game.getUserId());
                    return; // Exit early
            }
            
            // The initial bet was already deducted. This call adds back the bet and any winnings.
            if (creditChange > 0) {
                userService.updateCreditsAtomic(user.getId(), creditChange);
            }
            
            // Fetch updated user for accurate balance in logs and embeds
            User updatedUser = userService.getUserById(user.getId());
            int newCredits = (updatedUser != null && updatedUser.getCredits() != null) ? updatedUser.getCredits() : 0;
            
            // Create audit entry for blackjack game result
            try {
                createSingleGameAuditEntry(game, user.getId(), result, payout, newCredits);
            } catch (Exception e) {
                logger.error("Failed to create audit entry for blackjack game result for user {}: {}", game.getUserId(), e.getMessage());
            }
            
            // Build final embed - use Discord avatar and username from event
            MessageEmbed embed = buildGameEndEmbed(game, discordUserName, discordAvatarUrl, result, payout, isInitialBlackjack, newCredits);
            
            // Send final message without buttons
            if (hook instanceof net.dv8tion.jda.api.interactions.InteractionHook) {
                ((net.dv8tion.jda.api.interactions.InteractionHook) hook).editOriginalEmbeds(embed)
                        .setComponents() // Remove buttons
                        .queue();
            }
            
            // Remove game from active games
            activeGames.remove(game.getUserId());
            
            logger.info("Blackjack game ended for user {}: result={}, credit change={}, new credits={}", 
                    game.getUserId(), result, creditChange, newCredits);
        }
    }
    
    private MessageEmbed buildGameEmbed(BlackjackGame game, String userName, String userAvatarUrl, boolean gameEnded) {
        EmbedBuilder embed = new EmbedBuilder();
        
        // Set author with bet information
        String betInfo = game.isSplit() ? 
            userName + ", you have bet 🪙 " + (game.getBetAmount() / 2) + " credits per hand (🪙 " + game.getBetAmount() + " total)." :
            userName + ", you have bet 🪙 " + game.getBetAmount() + " credits.";
        if (game.isDoubledDown()) {
            betInfo += " (Doubled Down)";
        }
        embed.setAuthor(betInfo, null, userAvatarUrl);
        
        embed.setColor(EMBED_COLOR);
        
        // Dealer hand field
        BlackjackHand dealerHand = game.getDealerHand();
        String dealerTitle;
        String dealerCards;
        
        if (gameEnded || game.isDealerTurn()) {
            // Show all dealer cards
            dealerTitle = "Dealer [" + dealerHand.getValue() + "]";
            dealerCards = dealerHand.getCardsUnicode();
        } else {
            // Hide dealer's first card
            dealerTitle = "Dealer [" + dealerHand.getSecondCardValue() + " + ?]";
            dealerCards = dealerHand.getCardsUnicode(true);
        }
        
        embed.addField(dealerTitle, dealerCards, true);
        
        if (game.isSplit()) {
            // Show both hands for split
            BlackjackHand firstHand = game.getPlayerHand();
            BlackjackHand secondHand = game.getSecondHand();
            
            // Determine which hand is active
            boolean isFirstHandActive = game.isPlayingFirstHand() && !game.isFirstHandCompleted();
            boolean isSecondHandActive = !game.isPlayingFirstHand() && !gameEnded;
            
            // First hand
            String firstHandTitle = (isFirstHandActive ? "▶ " : "") + userName + " Hand 1 [" + firstHand.getValue() + "]";
            if (firstHand.isBusted()) {
                firstHandTitle += " (BUST)";
            }
            String firstHandCards = firstHand.getCardsUnicode();
            embed.addField(firstHandTitle, firstHandCards, true);
            
            // Second hand
            String secondHandTitle = (isSecondHandActive ? "▶ " : "") + userName + " Hand 2 [" + secondHand.getValue() + "]";
            if (secondHand.isBusted()) {
                secondHandTitle += " (BUST)";
            }
            String secondHandCards = secondHand.getCardsUnicode();
            embed.addField(secondHandTitle, secondHandCards, true);
        } else {
            // Single hand display
            BlackjackHand playerHand = game.getPlayerHand();
            String playerTitle = userName + " [" + playerHand.getValue() + "]";
            if (playerHand.isBusted()) {
                playerTitle += " (BUST)";
            }
            String playerCards = playerHand.getCardsUnicode();
            
            embed.addField(playerTitle, playerCards, true);
        }
        
        return embed.build();
    }

    private List<Button> buildActionButtons(BlackjackGame game) {
        List<Button> buttons = new java.util.ArrayList<>();

        if (game.canDoubleDown()) {
            Button doubleDownButton = Button.success("blackjack_double_down:" + game.getUserId(), "Double Down");
            buttons.add(doubleDownButton);
        }
        if (game.canSplit()) {
            Button splitButton = Button.danger("blackjack_split:" + game.getUserId(), "Split");
            buttons.add(splitButton);
        }
        if (!game.isGameEnded()) {
            Button hitButton = Button.success("blackjack_hit:" + game.getUserId(), "Hit");
            Button stayButton = Button.danger("blackjack_stay:" + game.getUserId(), "Stay");
            buttons.add(hitButton);
            buttons.add(stayButton);
        }
        return buttons;
    }
    
    private MessageEmbed buildGameEndEmbed(BlackjackGame game, String userName, String userAvatarUrl, 
                                          BlackjackGame.GameResult result, int payout, boolean isInitialBlackjack, int currentCredits) {
        EmbedBuilder embed = new EmbedBuilder();
        
        // Set color based on result
        Color embedColor;
        String resultText;
        
        switch (result) {
            case PLAYER_WIN:
                embedColor = WIN_COLOR;
                resultText = "🎉 You win! ";
                break;
            case PLAYER_BLACKJACK:
                embedColor = WIN_COLOR;
                resultText = isInitialBlackjack ? "🎊 BLACKJACK! Natural 21! " : "🎉 You win with 21! ";
                break;
            case DEALER_WIN:
                embedColor = LOSE_COLOR;
                resultText = game.getPlayerHand().isBusted() ? "💥 You busted! " : "😞 Dealer wins! ";
                break;
            case PUSH:
                embedColor = PUSH_COLOR;
                resultText = "🤝 Tie! ";
                break;
            default:
                embedColor = EMBED_COLOR;
                resultText = "Game ended. ";
                break;
        }
        
        // Add payout info to result text
        if (payout > 0) {
            resultText += "🪙+" + payout;
        } else if (payout == 0 && result == BlackjackGame.GameResult.PUSH) {
            resultText += "Bet returned";
        }
        
        // Set author with result message
        embed.setAuthor(resultText, null, userAvatarUrl);
        embed.setColor(embedColor);
        
        // Show final hands
        BlackjackHand dealerHand = game.getDealerHand();
        String dealerTitle = "Dealer [" + dealerHand.getValue() + "]";
        embed.addField(dealerTitle, dealerHand.getCardsUnicode(), true);
        
        BlackjackHand playerHand = game.getPlayerHand();
        String playerTitle = userName + " [" + playerHand.getValue() + "]";
        embed.addField(playerTitle, playerHand.getCardsUnicode(), true);
        
        // Add footer with current credits
        embed.setFooter(userName + ", you now have " + currentCredits + " credits", null);
        
        return embed.build();
    }

    /**
     * Create audit entries for split game results.
     */
    private void createSplitAuditEntries(BlackjackGame game, String userId, BlackjackGame.GameResult firstResult, 
                                       BlackjackGame.GameResult secondResult, int totalPayout, int newCredits) {
        int originalBetPerHand = game.getBetAmount() / 2;
        
        // Create audit entry for first hand
        createHandAuditEntry(game, userId, firstResult, originalBetPerHand, 1, newCredits, game.getPlayerHand(), game.getDealerHand());
        
        // Create audit entry for second hand
        createHandAuditEntry(game, userId, secondResult, originalBetPerHand, 2, newCredits, game.getSecondHand(), game.getDealerHand());
    }
    
    /**
     * Create audit entry for a single hand (used for split games).
     */
    private void createHandAuditEntry(BlackjackGame game, String userId, BlackjackGame.GameResult result, 
                                    int betAmount, int handNumber, int newCredits, BlackjackHand hand, BlackjackHand dealerHand) {
        String action = "";
        String description = "";
        int netChange = 0;
        
        switch (result) {
            case PLAYER_BLACKJACK:
                action = "BLACKJACK_SPLIT_WIN";
                netChange = (int) (betAmount * 1.5 * game.getRoleMultiplier());
                description = String.format("Won %d credits with split hand %d blackjack (bet: %d, payout: 1.5x, multiplier: %.2fx)", 
                    netChange, handNumber, betAmount, game.getRoleMultiplier());
                break;
            case PLAYER_WIN:
                action = "BLACKJACK_SPLIT_WIN";
                netChange = betAmount;
                description = String.format("Won %d credits with split hand %d (bet: %d, payout: 1x)", 
                    netChange, handNumber, betAmount);
                break;
            case PUSH:
                action = "BLACKJACK_SPLIT_PUSH";
                netChange = 0;
                description = String.format("Split hand %d push - bet returned (bet: %d)", handNumber, betAmount);
                break;
            case DEALER_WIN:
                action = "BLACKJACK_SPLIT_LOSS";
                netChange = -betAmount;
                description = String.format("Lost %d credits with split hand %d (bet: %d)", 
                    betAmount, handNumber, betAmount);
                break;
            case IN_PROGRESS:
            default:
                logger.warn("createHandAuditEntry called with unexpected game result '{}' for user {}", result, userId);
                return; // Do not create an audit for an unexpected state
        }
        
        CreateAuditDTO auditEntry = CreateAuditDTO.builder()
            .userId(userId)
            .action(action)
            .entityType("USER_CREDITS")
            .entityId(userId)
            .description(description)
            .severity(betAmount > 1000 ? AuditSeverity.WARNING : AuditSeverity.INFO)
            .category(AuditCategory.FINANCIAL)
            .details(String.format("{\"game\":\"blackjack_split\",\"hand\":%d,\"bet\":%d,\"result\":\"%s\",\"roleMultiplier\":%.2f,\"handValue\":\"%s\",\"dealerHand\":\"%s\",\"netChange\":%d,\"newBalance\":%d,\"splitAces\":%b}", 
                handNumber, betAmount, result.name(), game.getRoleMultiplier(), hand.getValue(), dealerHand.getValue(), netChange, newCredits, game.isSplitAces()))
            .source("DISCORD_BOT")
            .build();
        
        auditService.createSystemAuditEntry(auditEntry);
    }
    
    /**
     * Create audit entry for single hand games (updated from original method).
     */
    private void createSingleGameAuditEntry(BlackjackGame game, String userId, BlackjackGame.GameResult result, 
                                          int payout, int newCredits) {
        String action = "";
        String description = "";
        int netChange = 0;
        int baseBet = game.isDoubledDown() ? game.getBetAmount() / 2 : game.getBetAmount();
        
        switch (result) {
            case PLAYER_BLACKJACK:
                action = "BLACKJACK_WIN";
                netChange = payout;
                description = String.format("Won %d credits with blackjack (bet: %d, payout: 1.5x, multiplier: %.2fx%s)", 
                    payout, baseBet, game.getRoleMultiplier(), game.isDoubledDown() ? ", doubled down" : "");
                break;
            case PLAYER_WIN:
                action = "BLACKJACK_WIN";
                netChange = payout;
                description = String.format("Won %d credits in blackjack (bet: %d, payout: 1x%s)", 
                    payout, baseBet, game.isDoubledDown() ? ", doubled down" : "");
                break;
            case PUSH:
                action = "BLACKJACK_PUSH";
                netChange = 0;
                description = String.format("Blackjack push - bet returned (bet: %d%s)", baseBet, game.isDoubledDown() ? ", doubled down" : "");
                break;
            case DEALER_WIN:
                action = "BLACKJACK_LOSS";
                netChange = -baseBet;
                description = String.format("Lost %d credits in blackjack (bet: %d%s)", 
                    baseBet, baseBet, game.isDoubledDown() ? ", doubled down" : "");
                break;
            case IN_PROGRESS:
            default:
                 logger.warn("createSingleGameAuditEntry called with unexpected game result '{}' for user {}", result, userId);
                 return; // Do not create an audit for an unexpected state
        }
        
        CreateAuditDTO auditEntry = CreateAuditDTO.builder()
            .userId(userId)
            .action(action)
            .entityType("USER_CREDITS")
            .entityId(userId)
            .description(description)
            .severity(baseBet > 1000 ? AuditSeverity.WARNING : AuditSeverity.INFO)
            .category(AuditCategory.FINANCIAL)
            .details(String.format("{\"game\":\"blackjack\",\"bet\":%d,\"result\":\"%s\",\"roleMultiplier\":%.2f,\"playerHand\":\"%s\",\"dealerHand\":\"%s\",\"netChange\":%d,\"newBalance\":%d,\"doubledDown\":%b}", 
                baseBet, result.name(), game.getRoleMultiplier(), game.getPlayerHand().getValue(), game.getDealerHand().getValue(), netChange, newCredits, game.isDoubledDown()))
            .source("DISCORD_BOT")
            .build();
        
        auditService.createSystemAuditEntry(auditEntry);
    }
    
    /**
     * Build the final embed for split game results.
     */
    private MessageEmbed buildSplitGameEndEmbed(BlackjackGame game, String userName, String userAvatarUrl, 
                                              BlackjackGame.GameResult firstResult, BlackjackGame.GameResult secondResult, 
                                              int totalPayout, int currentCredits) {
        EmbedBuilder embed = new EmbedBuilder();
        
        // Determine overall result color and text
        Color embedColor = EMBED_COLOR;
        String resultText = "Split Results: ";
        
        int wins = 0;
        int losses = 0;
        int pushes = 0;
        
        BlackjackGame.GameResult[] results = {firstResult, secondResult};
        for (BlackjackGame.GameResult result : results) {
            switch (result) {
                case PLAYER_WIN:
                case PLAYER_BLACKJACK:
                    wins++;
                    break;
                case DEALER_WIN:
                    losses++;
                    break;
                case PUSH:
                    pushes++;
                    break;
            }
        }
        
        if (wins == 2) {
            embedColor = WIN_COLOR;
            resultText += "🎉 Both hands won! ";
        } else if (losses == 2) {
            embedColor = LOSE_COLOR;
            resultText += "😞 Both hands lost! ";
        } else if (pushes == 2) {
            embedColor = PUSH_COLOR;
            resultText += "🤝 Both hands pushed! ";
        } else if (wins == 1 && losses == 1) {
            embedColor = PUSH_COLOR;
            resultText += "⚖️ One win, one loss! ";
        } else if (wins == 1 && pushes == 1) {
            embedColor = WIN_COLOR;
            resultText += "🎯 One win, one push! ";
        } else if (losses == 1 && pushes == 1) {
            embedColor = LOSE_COLOR;
            resultText += "📉 One loss, one push! ";
        }
        
        // Add payout info
        if (totalPayout > 0) {
            resultText += "🪙+" + totalPayout;
        } else if (totalPayout == 0) {
            resultText += "Even";
        } else {
            resultText += "🪙" + totalPayout;
        }
        
        embed.setAuthor(resultText, null, userAvatarUrl);
        embed.setColor(embedColor);
        
        // Show final hands
        BlackjackHand dealerHand = game.getDealerHand();
        String dealerTitle = "Dealer [" + dealerHand.getValue() + "]";
        if (dealerHand.isBusted()) {
            dealerTitle += " (BUST)";
        }
        embed.addField(dealerTitle, dealerHand.getCardsUnicode(), true);
        
        // First hand
        BlackjackHand firstHand = game.getPlayerHand();
        String firstHandTitle = userName + " Hand 1 [" + firstHand.getValue() + "] - " + getResultEmoji(firstResult);
        if (firstHand.isBusted()) {
            firstHandTitle += " (BUST)";
        }
        embed.addField(firstHandTitle, firstHand.getCardsUnicode(), true);
        
        // Second hand
        BlackjackHand secondHand = game.getSecondHand();
        String secondHandTitle = userName + " Hand 2 [" + secondHand.getValue() + "] - " + getResultEmoji(secondResult);
        if (secondHand.isBusted()) {
            secondHandTitle += " (BUST)";
        }
        embed.addField(secondHandTitle, secondHand.getCardsUnicode(), true);
        
        // Add footer with current credits
        embed.setFooter(userName + ", you now have " + currentCredits + " credits", null);
        
        return embed.build();
    }
    
    /**
     * Get result emoji for individual hand results.
     */
    private String getResultEmoji(BlackjackGame.GameResult result) {
        switch (result) {
            case PLAYER_WIN:
                return "🎉 WIN";
            case PLAYER_BLACKJACK:
                return "🎊 BLACKJACK";
            case DEALER_WIN:
                return "😞 LOSS";
            case PUSH:
                return "🤝 PUSH";
            default:
                return "❓";
        }
    }
} 