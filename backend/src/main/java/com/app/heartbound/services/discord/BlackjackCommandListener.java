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
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.util.concurrent.ConcurrentHashMap;

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
    
    @Autowired
    public BlackjackCommandListener(UserService userService, SecureRandomService secureRandomService, AuditService auditService) {
        this.userService = userService;
        this.secureRandomService = secureRandomService;
        this.auditService = auditService;
        logger.info("BlackjackCommandListener initialized with secure random and audit service");
    }
    
    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("blackjack")) {
            return; // Not our command
        }
        
        String userId = event.getUser().getId();
        logger.info("User {} requested /blackjack", userId);
        
        // Check for active game first
        if (activeGames.containsKey(userId)) {
            // User has an active game, show current state
            event.deferReply().queue();
            
            try {
                BlackjackGame existingGame = activeGames.get(userId);
                
                if (existingGame.isGameEnded()) {
                    // Game ended but wasn't cleaned up properly, remove it and start fresh
                    activeGames.remove(userId);
                    // Continue to create new game below
                } else {
                    // Show current game state
                    MessageEmbed embed = buildGameEmbed(existingGame, event.getUser().getEffectiveName(), event.getUser().getEffectiveAvatarUrl(), false);
                    
                    Button hitButton = Button.success("blackjack_hit:" + userId, "Hit");
                    Button stayButton = Button.danger("blackjack_stay:" + userId, "Stay");
                    
                    event.getHook().sendMessageEmbeds(embed)
                            .addActionRow(hitButton, stayButton)
                            .queue();
                    return;
                }
            } catch (Exception e) {
                logger.error("Error showing existing game for user {}", userId, e);
                // Remove the corrupted game and continue to create new game
                activeGames.remove(userId);
            }
        }
        
        // Acknowledge the interaction immediately (if not already done above)
        if (!activeGames.containsKey(userId)) {
            event.deferReply().queue();
        }
        
        try {
            // Get bet amount from command option
            int betAmount = event.getOption("bet").getAsInt();
            
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
            
            if (currentCredits < betAmount) {
                event.getHook().sendMessage("You don't have enough credits! You have " + currentCredits + " credits but tried to bet " + betAmount + ".")
                        .setEphemeral(true)
                        .queue();
                return;
            }
            
            // Deduct the bet amount from user's credits
            user.setCredits(currentCredits - betAmount);
            userService.updateUser(user);
            
            // Create new game with secure random
            BlackjackGame game = new BlackjackGame(userId, betAmount, secureRandomService);
            activeGames.put(userId, game);
            
            // Check for immediate blackjack
            if (game.getPlayerHand().isBlackjack()) {
                // Player has blackjack, complete the game immediately
                game.playerStand(); // This will trigger dealer play
                handleGameEnd(event.getHook(), game, user, true, event.getUser().getEffectiveName(), event.getUser().getEffectiveAvatarUrl());
            } else {
                // Send initial game embed with buttons
                MessageEmbed embed = buildGameEmbed(game, event.getUser().getEffectiveName(), event.getUser().getEffectiveAvatarUrl(), false);
                
                Button hitButton = Button.success("blackjack_hit:" + userId, "Hit");
                Button stayButton = Button.danger("blackjack_stay:" + userId, "Stay");
                
                event.getHook().sendMessageEmbeds(embed)
                        .addActionRow(hitButton, stayButton)
                        .queue();
            }
            
        } catch (Exception e) {
            logger.error("Error processing /blackjack command for user {}", userId, e);
            
            // Remove the game from active games if it was added
            activeGames.remove(userId);
            
            event.getHook().sendMessage("An error occurred while starting the blackjack game.")
                    .setEphemeral(true)
                    .queue();
        }
    }
    
    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
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
        
        // Player hits
        Card dealtCard = game.playerHit();
        logger.debug("User {} hit and received: {}", game.getUserId(), dealtCard);
        
        // Update the embed
        MessageEmbed embed = buildGameEmbed(game, event.getUser().getEffectiveName(), event.getUser().getEffectiveAvatarUrl(), false);
        
        if (game.isGameEnded()) {
            // Player busted
            handleGameEnd(event.getHook(), game, user, false, event.getUser().getEffectiveName(), event.getUser().getEffectiveAvatarUrl());
        } else {
            // Game continues, keep buttons
            Button hitButton = Button.success("blackjack_hit:" + game.getUserId(), "Hit");
            Button stayButton = Button.danger("blackjack_stay:" + game.getUserId(), "Stay");
            
            event.getHook().editOriginalEmbeds(embed)
                    .setActionRow(hitButton, stayButton)
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
        
        // Player stands, start dealer reveal sequence
        game.setDealerTurn(true); // Mark that it's dealer's turn
        logger.debug("User {} stood, starting dealer reveal sequence", game.getUserId());
        
        // First, reveal dealer's hidden card
        MessageEmbed embed = buildGameEmbed(game, event.getUser().getEffectiveName(), event.getUser().getEffectiveAvatarUrl(), false);
        event.getHook().editOriginalEmbeds(embed)
                .setComponents() // Remove buttons since player's turn is over
                .queue();
        
        // Start the dramatic dealer sequence after a short delay
        java.util.concurrent.ScheduledExecutorService scheduler = java.util.concurrent.Executors.newScheduledThreadPool(1);
        
        scheduler.schedule(() -> {
            playDealerHandWithDelay(event, game, user, scheduler, 0);
        }, 2, java.util.concurrent.TimeUnit.SECONDS); // 2 second delay before dealer starts hitting
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
        BlackjackGame.GameResult result = game.getResult();
        
        // Update user credits
        Integer currentCredits = user.getCredits();
        int creditChange = 0;
        
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
                creditChange = 0; // Bet already deducted, no return
                break;
        }
        
        int newCredits = (currentCredits == null ? 0 : currentCredits) + creditChange;
        user.setCredits(newCredits);
        userService.updateUser(user);
        
        // Create audit entry for blackjack game result
        try {
            String action = "";
            String description = "";
            int netChange = 0;
            
            switch (result) {
                case PLAYER_BLACKJACK:
                    action = "BLACKJACK_WIN";
                    netChange = payout;
                    description = String.format("Won %d credits with blackjack (bet: %d, payout: 1.5x)", 
                        payout, game.getBetAmount());
                    break;
                case PLAYER_WIN:
                    action = "BLACKJACK_WIN";
                    netChange = payout;
                    description = String.format("Won %d credits in blackjack (bet: %d, payout: 1x)", 
                        payout, game.getBetAmount());
                    break;
                case PUSH:
                    action = "BLACKJACK_PUSH";
                    netChange = 0;
                    description = String.format("Blackjack push - bet returned (bet: %d)", game.getBetAmount());
                    break;
                case DEALER_WIN:
                    action = "BLACKJACK_LOSS";
                    netChange = -game.getBetAmount();
                    description = String.format("Lost %d credits in blackjack (bet: %d)", 
                        game.getBetAmount(), game.getBetAmount());
                    break;
            }
            
            CreateAuditDTO auditEntry = CreateAuditDTO.builder()
                .userId(game.getUserId())
                .action(action)
                .entityType("USER_CREDITS")
                .entityId(game.getUserId())
                .description(description)
                .severity(game.getBetAmount() > 1000 ? AuditSeverity.WARNING : AuditSeverity.INFO)
                .category(AuditCategory.FINANCIAL)
                .details(String.format("{\"game\":\"blackjack\",\"bet\":%d,\"result\":\"%s\",\"playerHand\":\"%s\",\"dealerHand\":\"%s\",\"netChange\":%d,\"newBalance\":%d}", 
                    game.getBetAmount(), result.name(), game.getPlayerHand().getValue(), game.getDealerHand().getValue(), netChange, newCredits))
                .source("DISCORD_BOT")
                .build();
            
            auditService.createSystemAuditEntry(auditEntry);
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
    
    private MessageEmbed buildGameEmbed(BlackjackGame game, String userName, String userAvatarUrl, boolean gameEnded) {
        EmbedBuilder embed = new EmbedBuilder();
        
        // Set author with bet information
        embed.setAuthor(userName + ", you have bet 🪙 " + game.getBetAmount() + " credits.", null, userAvatarUrl);
        
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
        
        // Player hand field
        BlackjackHand playerHand = game.getPlayerHand();
        String playerTitle = userName + " [" + playerHand.getValue() + "]";
        String playerCards = playerHand.getCardsUnicode();
        
        embed.addField(playerTitle, playerCards, true);
        
        return embed.build();
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
} 