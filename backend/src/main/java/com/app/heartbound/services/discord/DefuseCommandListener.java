package com.app.heartbound.services.discord;

import com.app.heartbound.entities.User;
import com.app.heartbound.services.UserService;
import com.app.heartbound.config.CacheConfig;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Nonnull;
import java.awt.Color;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.Set;
import java.util.HashSet;
import java.util.Random;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;

@Component
public class DefuseCommandListener extends ListenerAdapter {
    
    private static final Logger logger = LoggerFactory.getLogger(DefuseCommandListener.class);
    private static final Color EMBED_COLOR = new Color(88, 101, 242); // Discord Blurple
    private static final Color SUCCESS_COLOR = new Color(40, 167, 69); // Bootstrap success green
    private static final Color FAILURE_COLOR = new Color(220, 53, 69); // Bootstrap danger red
    private static final Color WARNING_COLOR = new Color(255, 193, 7); // Bootstrap warning yellow
    
    private final UserService userService;
    private final CacheConfig cacheConfig;
    private final Random random = new Random();
    
    // Wire colors and their emojis
    private static final List<String> WIRE_COLORS = Arrays.asList("red", "blue", "yellow", "green", "pink");
    private static final List<String> WIRE_EMOJIS = Arrays.asList("🔴", "🔵", "🟡", "🟢", "🩷");
    
    // Store active games to prevent duplicates and manage state
    private final ConcurrentHashMap<String, DefuseGame> activeGames = new ConcurrentHashMap<>();
    
    public DefuseCommandListener(UserService userService, CacheConfig cacheConfig) {
        this.userService = userService;
        this.cacheConfig = cacheConfig;
        logger.info("DefuseCommandListener initialized");
    }
    
    @Override
    public void onSlashCommandInteraction(@Nonnull SlashCommandInteractionEvent event) {
        if (!event.getName().equals("defuse")) {
            return; // Not our command
        }
        
        String challengerId = event.getUser().getId();
        logger.info("User {} requested /defuse", challengerId);
        
        // Get command options
        OptionMapping userOption = event.getOption("user");
        OptionMapping betOption = event.getOption("bet");
        
        if (userOption == null || betOption == null) {
            event.reply("Both user and bet amount are required!").setEphemeral(true).queue();
            return;
        }
        
        String challengedUserId = userOption.getAsUser().getId();
        int betAmount = betOption.getAsInt();
        
        // Prevent self-challenge
        if (challengerId.equals(challengedUserId)) {
            event.reply("You cannot challenge yourself to Defuse!").setEphemeral(true).queue();
            return;
        }
        
        // Validate bet amount
        if (betAmount <= 0) {
            event.reply("Bet amount must be greater than 0!").setEphemeral(true).queue();
            return;
        }
        
        try {
            // Check if either user is already in an active game
            String gameKey1 = generateGameKey(challengerId, challengedUserId);
            String gameKey2 = generateGameKey(challengedUserId, challengerId);
            
            logger.debug("Checking for duplicate games: gameKey1={}, gameKey2={}", gameKey1, gameKey2);
            logger.debug("gameKey1 exists: {}, gameKey2 exists: {}", activeGames.containsKey(gameKey1), activeGames.containsKey(gameKey2));
            
            if (activeGames.containsKey(gameKey1) || activeGames.containsKey(gameKey2)) {
                logger.debug("Duplicate game detected, rejecting challenge");
                event.reply("One of you is already in an active Defuse game!").setEphemeral(true).queue();
                return;
            }
            
            // Fetch both users from the database
            User challenger = userService.getUserById(challengerId);
            User challenged = userService.getUserById(challengedUserId);
            
            if (challenger == null) {
                event.reply("You are currently not signed up with the bot!").setEphemeral(true).queue();
                return;
            }
            
            if (challenged == null) {
                event.reply(String.format("<@%s> is currently not signed up with the bot!", challengedUserId)).setEphemeral(true).queue();
                return;
            }
            
            // Get current credits (handle null case)
            Integer challengerCredits = challenger.getCredits();
            Integer challengedCredits = challenged.getCredits();
            int challengerBalance = (challengerCredits == null) ? 0 : challengerCredits;
            int challengedBalance = (challengedCredits == null) ? 0 : challengedCredits;
            
            // Validate sufficient credits for both users
            if (challengerBalance < betAmount) {
                event.reply(String.format("<@%s> does not have enough credits!", challengerId)).setEphemeral(true).queue();
                return;
            }
            
            if (challengedBalance < betAmount) {
                event.reply(String.format("<@%s> does not have enough credits!", challengedUserId)).setEphemeral(true).queue();
                return;
            }
            
            // Create game state
            DefuseGame game = new DefuseGame(challengerId, challengedUserId, betAmount);
            activeGames.put(gameKey1, game);
            
            logger.debug("Created new Defuse game: gameKey={}, challenger={}, challenged={}, bet={}", 
                        gameKey1, challengerId, challengedUserId, betAmount);
            logger.debug("Active games after creation: {}", activeGames.keySet());
            
            // Get challenger display name for footer
            String challengerName = getUserDisplayName(event, challengerId);
            
            // Create initial request embed
            EmbedBuilder initialEmbed = new EmbedBuilder()
                .setColor(EMBED_COLOR)
                .setDescription(String.format("<@%s> Do you accept <@%s>'s challenge for defuse?", challengedUserId, challengerId))
                .setFooter(String.format("%s wants to bet 🪙+%d credits", challengerName, betAmount));
            
            // Create accept/reject buttons
            Button acceptButton = Button.success("defuse_accept_" + gameKey1, "✅");
            Button rejectButton = Button.danger("defuse_reject_" + gameKey1, "❌");
            
            event.reply("")
                .addEmbeds(initialEmbed.build())
                .addComponents(ActionRow.of(acceptButton, rejectButton))
                .queue(
                    success -> {
                        // Set up timeout for the challenge
                        CompletableFuture.delayedExecutor(60, TimeUnit.SECONDS).execute(() -> {
                            DefuseGame timeoutGame = activeGames.get(gameKey1);
                            if (timeoutGame != null && timeoutGame.getState() == DefuseGame.GameState.PENDING) {
                                // Game timed out
                                activeGames.remove(gameKey1);
                                
                                logger.debug("Defuse challenge timed out for game: {}, removed from active games", gameKey1);
                                logger.debug("Active games after timeout: {}", activeGames.keySet());
                                
                                EmbedBuilder timeoutEmbed = new EmbedBuilder()
                                    .setColor(WARNING_COLOR)
                                    .setTitle("Expired!");
                                
                                event.getHook().editOriginalEmbeds(timeoutEmbed.build())
                                    .setComponents() // Remove buttons
                                    .queue();
                                
                                logger.debug("Defuse challenge timed out for game: {}", gameKey1);
                            } else {
                                logger.debug("Timeout check: game still active or already processed for gameKey: {}", gameKey1);
                            }
                        });
                    },
                    error -> logger.error("Failed to send Defuse challenge: {}", error.getMessage())
                );
            
        } catch (Exception e) {
            logger.error("Error processing /defuse command for user {}", challengerId, e);
            
            EmbedBuilder errorEmbed = new EmbedBuilder()
                .setColor(FAILURE_COLOR)
                .setTitle("❌ Error")
                .setDescription("An error occurred while processing your Defuse challenge.");
            
            event.reply("").addEmbeds(errorEmbed.build()).setEphemeral(true).queue();
        }
    }
    
    @Override
    public void onButtonInteraction(@Nonnull ButtonInteractionEvent event) {
        String buttonId = event.getComponentId();
        
        if (!buttonId.startsWith("defuse_")) {
            return; // Not our button
        }
        
        String userId = event.getUser().getId();
        
        try {
            if (buttonId.startsWith("defuse_accept_") || buttonId.startsWith("defuse_reject_")) {
                handleChallengeResponse(event, buttonId, userId);
            } else if (buttonId.startsWith("defuse_wire_")) {
                handleWireCut(event, buttonId, userId);
            }
        } catch (Exception e) {
            logger.error("Error handling Defuse button interaction: {}", e.getMessage(), e);
            event.reply("An error occurred while processing your action.").setEphemeral(true).queue();
        }
    }
    
    private void handleChallengeResponse(ButtonInteractionEvent event, String buttonId, String userId) {
        String gameKey = buttonId.substring(buttonId.indexOf("_", 7) + 1);
        
        logger.debug("Handling challenge response: buttonId={}, userId={}, gameKey={}", buttonId, userId, gameKey);
        logger.debug("Active games: {}", activeGames.keySet());
        
        DefuseGame game = activeGames.get(gameKey);
        
        if (game == null) {
            logger.warn("Game not found for challenge response: gameKey={}, active games: {}", gameKey, activeGames.keySet());
            event.reply("This game is no longer active.").setEphemeral(true).queue();
            return;
        }
        
        // Only the challenged user can accept/reject
        if (!userId.equals(game.getChallengedUserId())) {
            event.reply("You cannot respond to this challenge.").setEphemeral(true).queue();
            return;
        }
        
        if (buttonId.startsWith("defuse_accept_")) {
            // Accept the challenge
            game.setState(DefuseGame.GameState.ACTIVE);
            
            logger.debug("Challenge accepted: gameKey={}, game state set to ACTIVE", gameKey);
            
            // Start the wire cutting phase
            startWireCuttingPhase(event, gameKey, game);
            
        } else if (buttonId.startsWith("defuse_reject_")) {
            // Reject the challenge
            activeGames.remove(gameKey);
            
            logger.debug("Challenge rejected: gameKey={}, removed from active games", gameKey);
            logger.debug("Active games after rejection: {}", activeGames.keySet());
            
            EmbedBuilder rejectEmbed = new EmbedBuilder()
                .setColor(FAILURE_COLOR)
                .setDescription(String.format("<@%s> **has rejected** <@%s> **'s request.**", 
                    game.getChallengedUserId(), game.getChallengerUserId()));
            
            event.editMessageEmbeds(rejectEmbed.build())
                .setComponents() // Remove buttons
                .queue();
        }
    }
    
    private void startWireCuttingPhase(ButtonInteractionEvent event, String gameKey, DefuseGame game) {
        // Store display names before starting async sequence
        String challengerName = getUserDisplayName(event, game.getChallengerUserId());
        String challengedName = getUserDisplayName(event, game.getChallengedUserId());
        game.setChallengerDisplayName(challengerName);
        game.setChallengedDisplayName(challengedName);
        
        logger.debug("Starting wire cutting phase for gameKey: {}", gameKey);
        logger.debug("Bomb wire is: {}", game.getBombWire());
        
        updateWireCuttingEmbed(event, game);
    }
    
    private void updateWireCuttingEmbed(ButtonInteractionEvent event, DefuseGame game) {
        String currentPlayerId = game.getCurrentPlayerId();
        String currentPlayerName = currentPlayerId.equals(game.getChallengerUserId()) ? 
            game.getChallengerDisplayName() : game.getChallengedDisplayName();
        
        EmbedBuilder wireEmbed = new EmbedBuilder()
            .setColor(EMBED_COLOR)
            .setTitle("The bomb is ticking! 💣")
            .setDescription(String.format("Cut the right wire to stay alive!\nOne wire will explode. The rest are safe.\n\n<@%s>\n```Choose a wire below.```", currentPlayerId))
            .setFooter("You have 5 seconds to cut a wire.");
        
        // Create wire buttons for remaining wires
        List<Button> wireButtons = new ArrayList<>();
        String gameKey = generateGameKey(game.getChallengerUserId(), game.getChallengedUserId());
        
        for (int i = 0; i < WIRE_COLORS.size(); i++) {
            String wire = WIRE_COLORS.get(i);
            String emoji = WIRE_EMOJIS.get(i);
            
            if (!game.getCutWires().contains(wire)) {
                Button wireButton = Button.secondary("defuse_wire_" + wire + "_" + gameKey, emoji);
                wireButtons.add(wireButton);
            }
        }
        
        // Set up wire cutting timeout
        CompletableFuture.delayedExecutor(5, TimeUnit.SECONDS).execute(() -> {
            DefuseGame timeoutGame = activeGames.get(gameKey);
            if (timeoutGame != null && timeoutGame.getState() == DefuseGame.GameState.ACTIVE && 
                timeoutGame.getCurrentPlayerId().equals(currentPlayerId)) {
                // Player timed out, they lose
                handleWireTimeout(event, gameKey, timeoutGame, currentPlayerId);
            }
        });
        
        event.editMessageEmbeds(wireEmbed.build())
            .setComponents(ActionRow.of(wireButtons))
            .queue();
    }
    
    private void handleWireTimeout(ButtonInteractionEvent event, String gameKey, DefuseGame game, String timedOutPlayerId) {
        logger.debug("Wire cutting timed out for player: {} in game: {}", timedOutPlayerId, gameKey);
        
        // Remove game from active games
        activeGames.remove(gameKey);
        
        String winnerId = timedOutPlayerId.equals(game.getChallengerUserId()) ? 
            game.getChallengedUserId() : game.getChallengerUserId();
        String winnerName = winnerId.equals(game.getChallengerUserId()) ? 
            game.getChallengerDisplayName() : game.getChallengedDisplayName();
        
        EmbedBuilder timeoutEmbed = new EmbedBuilder()
            .setColor(FAILURE_COLOR)
            .setTitle("💥 BOOM!")
            .setDescription(String.format("<@%s> ran out of time!\n\n**The bomb exploded!**💣", timedOutPlayerId));
        
        event.getHook().editOriginalEmbeds(timeoutEmbed.build())
            .setComponents() // Remove buttons
            .queue();
        
        // Brief pause then show winner
        CompletableFuture.delayedExecutor(2, TimeUnit.SECONDS).execute(() -> {
            showGameResult(event, gameKey, game, winnerId, "timeout");
        });
    }
    
    private void handleWireCut(ButtonInteractionEvent event, String buttonId, String userId) {
        logger.debug("Handling wire cut: buttonId={}, userId={}", buttonId, userId);
        
        String[] parts = buttonId.split("_");
        if (parts.length < 4) {
            logger.error("Invalid button ID format: {}", buttonId);
            event.reply("Invalid button format.").setEphemeral(true).queue();
            return;
        }
        
        String wire = parts[2]; // wire color
        // GameKey is everything after "defuse_wire_{wire}_", since it can contain underscores
        StringBuilder gameKeyBuilder = new StringBuilder();
        for (int i = 3; i < parts.length; i++) {
            if (i > 3) gameKeyBuilder.append("_");
            gameKeyBuilder.append(parts[i]);
        }
        String gameKey = gameKeyBuilder.toString();
        
        logger.debug("Parsed wire: {}, gameKey: {}", wire, gameKey);
        logger.debug("Active games: {}", activeGames.keySet());
        
        DefuseGame game = activeGames.get(gameKey);
        
        if (game == null) {
            logger.warn("Game not found for gameKey: {}, active games: {}", gameKey, activeGames.keySet());
            event.reply("This game is no longer active.").setEphemeral(true).queue();
            return;
        }
        
        if (game.getState() != DefuseGame.GameState.ACTIVE) {
            event.reply("This game is not in an active state.").setEphemeral(true).queue();
            return;
        }
        
        // Check if user is part of this game
        if (!userId.equals(game.getChallengerUserId()) && !userId.equals(game.getChallengedUserId())) {
            event.reply("This is not your game!").setEphemeral(true).queue();
            return;
        }
        
        // Check if it's the user's turn
        if (!userId.equals(game.getCurrentPlayerId())) {
            event.reply("It's not your turn!").setEphemeral(true).queue();
            return;
        }
        
        // Check if wire has already been cut
        if (game.getCutWires().contains(wire)) {
            event.reply("This wire has already been cut!").setEphemeral(true).queue();
            return;
        }
        
        // Cut the wire
        game.cutWire(wire);
        
        logger.debug("Wire {} cut by user {} in game {}", wire, userId, gameKey);
        logger.debug("Cut wires: {}, Bomb wire: {}", game.getCutWires(), game.getBombWire());
        
        // Show wire cut result
        showWireCutResult(event, gameKey, game, wire, userId);
    }
    
    private void showWireCutResult(ButtonInteractionEvent event, String gameKey, DefuseGame game, String wire, String userId) {
        String playerName = userId.equals(game.getChallengerUserId()) ? 
            game.getChallengerDisplayName() : game.getChallengedDisplayName();
        
        // Get wire emoji
        String wireEmoji = WIRE_EMOJIS.get(WIRE_COLORS.indexOf(wire));
        String wireColor = wire.substring(0, 1).toUpperCase() + wire.substring(1);
        
        EmbedBuilder cutEmbed = new EmbedBuilder()
            .setColor(EMBED_COLOR)
            .setTitle("💥 Wire Cut!")
            .setDescription(String.format("<@%s> cut the ```%s Wire...```", userId, wireColor));
        
        event.editMessageEmbeds(cutEmbed.build())
            .setComponents() // Remove buttons temporarily
            .queue();
        
        // Brief pause before showing result
        CompletableFuture.delayedExecutor(1500, TimeUnit.MILLISECONDS).execute(() -> {
            if (wire.equals(game.getBombWire())) {
                // Player cut the bomb wire - they lose!
                EmbedBuilder bombEmbed = new EmbedBuilder()
                    .setColor(FAILURE_COLOR)
                    .setTitle("💥 Wire Cut!")
                    .setDescription(String.format("<@%s> cut the ```%s Wire...```\n\n**💥 It was the bomb!**", userId, wireColor));
                
                event.getHook().editOriginalEmbeds(bombEmbed.build())
                    .setComponents() // Remove buttons
                    .queue();
                
                // Remove game from active games
                activeGames.remove(gameKey);
                
                // Determine winner (the other player)
                String winnerId = userId.equals(game.getChallengerUserId()) ? 
                    game.getChallengedUserId() : game.getChallengerUserId();
                
                // Brief pause then show winner
                CompletableFuture.delayedExecutor(2, TimeUnit.SECONDS).execute(() -> {
                    showGameResult(event, gameKey, game, winnerId, "bomb");
                });
                
            } else {
                // Wire was safe - continue game
                EmbedBuilder safeEmbed = new EmbedBuilder()
                    .setColor(SUCCESS_COLOR)
                    .setTitle("💥 Wire Cut!")
                    .setDescription(String.format("<@%s> cut the ```%s Wire...```\n\n**✅ It was safe!**", userId, wireColor));
                
                event.getHook().editOriginalEmbeds(safeEmbed.build())
                    .setComponents() // Remove buttons
                    .queue();
                
                // Switch to next player
                game.switchPlayer();
                
                // Brief pause then continue with next player
                CompletableFuture.delayedExecutor(2, TimeUnit.SECONDS).execute(() -> {
                    updateWireCuttingEmbed(event, game);
                });
            }
        });
    }
    
    @Transactional
    private void showGameResult(ButtonInteractionEvent event, String gameKey, DefuseGame game, String winnerId, String endReason) {
        try {
            logger.debug("Showing game result: gameKey={}, winnerId={}, endReason={}", gameKey, winnerId, endReason);
            
            // Process credit transfer
            User challenger = userService.getUserById(game.getChallengerUserId());
            User challenged = userService.getUserById(game.getChallengedUserId());
            
            if (challenger == null || challenged == null) {
                logger.error("User not found during Defuse result processing");
                return;
            }
            
            String loserId = winnerId.equals(game.getChallengerUserId()) ? 
                game.getChallengedUserId() : game.getChallengerUserId();
            
            User winnerUser = winnerId.equals(game.getChallengerUserId()) ? challenger : challenged;
            User loserUser = winnerId.equals(game.getChallengerUserId()) ? challenged : challenger;
            
            // Update credits
            int winnerCredits = (winnerUser.getCredits() == null) ? 0 : winnerUser.getCredits();
            int loserCredits = (loserUser.getCredits() == null) ? 0 : loserUser.getCredits();
            
            winnerUser.setCredits(winnerCredits + game.getBetAmount());
            loserUser.setCredits(loserCredits - game.getBetAmount());
            
            // Save both users
            userService.updateUser(winnerUser);
            userService.updateUser(loserUser);
            
            // Invalidate caches
            cacheConfig.invalidateUserProfileCache(winnerId);
            cacheConfig.invalidateUserProfileCache(loserId);
            
            // Get winner display name
            String winnerName = winnerId.equals(game.getChallengerUserId()) ? 
                game.getChallengerDisplayName() : game.getChallengedDisplayName();
            
            // Create result embed
            EmbedBuilder resultEmbed = new EmbedBuilder()
                .setColor(SUCCESS_COLOR)
                .setTitle(String.format("%s you won! 🪙+%d", winnerName, game.getBetAmount()));
            
            event.getHook().editOriginalEmbeds(resultEmbed.build())
                .setComponents() // Remove all components
                .queue();
            
            logger.info("Defuse game completed: {} won {} credits from {} (reason: {})", 
                winnerId, game.getBetAmount(), loserId, endReason);
            
        } catch (Exception e) {
            logger.error("Error processing Defuse game result: {}", e.getMessage(), e);
            
            EmbedBuilder errorEmbed = new EmbedBuilder()
                .setColor(FAILURE_COLOR)
                .setTitle("❌ Error")
                .setDescription("An error occurred while processing the game result.");
            
            event.getHook().editOriginalEmbeds(errorEmbed.build())
                .setComponents() // Remove all components
                .queue();
        }
    }
    
    private String generateGameKey(String user1, String user2) {
        // Create a consistent key regardless of order
        String gameKey = user1.compareTo(user2) < 0 ? user1 + "_" + user2 : user2 + "_" + user1;
        logger.debug("Generated game key: {} for users {} and {}", gameKey, user1, user2);
        return gameKey;
    }
    
    /**
     * Get a user's display name, handling special characters and fallbacks
     * @param event The slash command interaction event to get guild context
     * @param userId The Discord user ID
     * @return The user's effective name (display name if available, otherwise username)
     */
    private String getUserDisplayName(SlashCommandInteractionEvent event, String userId) {
        try {
            logger.debug("Attempting to retrieve display name for user ID: {}", userId);
            
            // Try to get the user from the guild first (for nickname support)
            Guild guild = event.getGuild();
            if (guild != null) {
                logger.debug("Guild found: {}, attempting to get member for user: {}", guild.getName(), userId);
                Member member = guild.getMemberById(userId);
                if (member != null) {
                    logger.debug("Member found in guild for user: {}", userId);
                    // getEffectiveName() returns nickname > global name > username in that priority
                    String effectiveName = member.getEffectiveName();
                    logger.debug("Member effective name: '{}'", effectiveName);
                    if (effectiveName != null && !effectiveName.trim().isEmpty()) {
                        // Handle special characters by escaping Discord markdown
                        String sanitized = sanitizeDisplayName(effectiveName);
                        logger.debug("Successfully retrieved member name: '{}' -> '{}'", effectiveName, sanitized);
                        return sanitized;
                    }
                } else {
                    logger.debug("Member not found in guild for user: {}", userId);
                }
            } else {
                logger.debug("No guild context available for user: {}", userId);
            }
            
            // Fallback to getting user directly from JDA
            logger.debug("Attempting direct user lookup for user: {}", userId);
            net.dv8tion.jda.api.entities.User user = event.getJDA().getUserById(userId);
            if (user != null) {
                logger.debug("User found via direct lookup for user: {}", userId);
                // getEffectiveName() returns global name if available, otherwise username
                String effectiveName = user.getEffectiveName();
                logger.debug("User effective name: '{}'", effectiveName);
                if (effectiveName != null && !effectiveName.trim().isEmpty()) {
                    String sanitized = sanitizeDisplayName(effectiveName);
                    logger.debug("Successfully retrieved user effective name: '{}' -> '{}'", effectiveName, sanitized);
                    return sanitized;
                }
                
                // Final fallback to username
                String username = user.getName();
                logger.debug("User username: '{}'", username);
                if (username != null && !username.trim().isEmpty()) {
                    String sanitized = sanitizeDisplayName(username);
                    logger.debug("Successfully retrieved username: '{}' -> '{}'", username, sanitized);
                    return sanitized;
                }
            } else {
                logger.debug("User not found via direct lookup for user: {}", userId);
            }
            
            // Try getting user info from the interaction itself if it's one of the participants
            logger.debug("Attempting to get user from interaction context for user: {}", userId);
            net.dv8tion.jda.api.entities.User interactionUser = event.getUser();
            if (interactionUser != null && userId.equals(interactionUser.getId())) {
                logger.debug("User matches interaction user: {}", userId);
                String effectiveName = interactionUser.getEffectiveName();
                logger.debug("Interaction user effective name: '{}'", effectiveName);
                if (effectiveName != null && !effectiveName.trim().isEmpty()) {
                    String sanitized = sanitizeDisplayName(effectiveName);
                    logger.debug("Successfully retrieved interaction user name: '{}' -> '{}'", effectiveName, sanitized);
                    return sanitized;
                }
                
                String username = interactionUser.getName();
                logger.debug("Interaction user username: '{}'", username);
                if (username != null && !username.trim().isEmpty()) {
                    String sanitized = sanitizeDisplayName(username);
                    logger.debug("Successfully retrieved interaction username: '{}' -> '{}'", username, sanitized);
                    return sanitized;
                }
            }
            
            // Try using retrieveUserById as last resort (this makes an API call)
            logger.debug("Attempting API retrieval for user: {}", userId);
            try {
                net.dv8tion.jda.api.entities.User retrievedUser = event.getJDA().retrieveUserById(userId).complete();
                if (retrievedUser != null) {
                    logger.debug("User retrieved via API for user: {}", userId);
                    String effectiveName = retrievedUser.getEffectiveName();
                    logger.debug("API user effective name: '{}'", effectiveName);
                    if (effectiveName != null && !effectiveName.trim().isEmpty()) {
                        String sanitized = sanitizeDisplayName(effectiveName);
                        logger.debug("Successfully retrieved API user effective name: '{}' -> '{}'", effectiveName, sanitized);
                        return sanitized;
                    }
                    
                    String username = retrievedUser.getName();
                    logger.debug("API user username: '{}'", username);
                    if (username != null && !username.trim().isEmpty()) {
                        String sanitized = sanitizeDisplayName(username);
                        logger.debug("Successfully retrieved API username: '{}' -> '{}'", username, sanitized);
                        return sanitized;
                    }
                }
            } catch (Exception apiException) {
                logger.debug("API retrieval failed for user {}: {}", userId, apiException.getMessage());
            }
            
            // Ultimate fallback if all else fails
            logger.warn("Could not retrieve display name for user ID: {} - all lookup methods failed", userId);
            return "Unknown User";
            
        } catch (Exception e) {
            logger.error("Error retrieving display name for user ID {}: {}", userId, e.getMessage(), e);
            return "Unknown User";
        }
    }
    
    /**
     * Get a user's display name, handling special characters and fallbacks
     * @param event The button interaction event to get guild context
     * @param userId The Discord user ID
     * @return The user's effective name (display name if available, otherwise username)
     */
    private String getUserDisplayName(ButtonInteractionEvent event, String userId) {
        try {
            logger.debug("Attempting to retrieve display name for user ID: {}", userId);
            
            // Try to get the user from the guild first (for nickname support)
            Guild guild = event.getGuild();
            if (guild != null) {
                logger.debug("Guild found: {}, attempting to get member for user: {}", guild.getName(), userId);
                Member member = guild.getMemberById(userId);
                if (member != null) {
                    logger.debug("Member found in guild for user: {}", userId);
                    // getEffectiveName() returns nickname > global name > username in that priority
                    String effectiveName = member.getEffectiveName();
                    logger.debug("Member effective name: '{}'", effectiveName);
                    if (effectiveName != null && !effectiveName.trim().isEmpty()) {
                        // Handle special characters by escaping Discord markdown
                        String sanitized = sanitizeDisplayName(effectiveName);
                        logger.debug("Successfully retrieved member name: '{}' -> '{}'", effectiveName, sanitized);
                        return sanitized;
                    }
                } else {
                    logger.debug("Member not found in guild for user: {}", userId);
                }
            } else {
                logger.debug("No guild context available for user: {}", userId);
            }
            
            // Fallback to getting user directly from JDA
            logger.debug("Attempting direct user lookup for user: {}", userId);
            net.dv8tion.jda.api.entities.User user = event.getJDA().getUserById(userId);
            if (user != null) {
                logger.debug("User found via direct lookup for user: {}", userId);
                // getEffectiveName() returns global name if available, otherwise username
                String effectiveName = user.getEffectiveName();
                logger.debug("User effective name: '{}'", effectiveName);
                if (effectiveName != null && !effectiveName.trim().isEmpty()) {
                    String sanitized = sanitizeDisplayName(effectiveName);
                    logger.debug("Successfully retrieved user effective name: '{}' -> '{}'", effectiveName, sanitized);
                    return sanitized;
                }
                
                // Final fallback to username
                String username = user.getName();
                logger.debug("User username: '{}'", username);
                if (username != null && !username.trim().isEmpty()) {
                    String sanitized = sanitizeDisplayName(username);
                    logger.debug("Successfully retrieved username: '{}' -> '{}'", username, sanitized);
                    return sanitized;
                }
            } else {
                logger.debug("User not found via direct lookup for user: {}", userId);
            }
            
            // Try getting user info from the interaction itself if it's one of the participants
            logger.debug("Attempting to get user from interaction context for user: {}", userId);
            net.dv8tion.jda.api.entities.User interactionUser = event.getUser();
            if (interactionUser != null && userId.equals(interactionUser.getId())) {
                logger.debug("User matches interaction user: {}", userId);
                String effectiveName = interactionUser.getEffectiveName();
                logger.debug("Interaction user effective name: '{}'", effectiveName);
                if (effectiveName != null && !effectiveName.trim().isEmpty()) {
                    String sanitized = sanitizeDisplayName(effectiveName);
                    logger.debug("Successfully retrieved interaction user name: '{}' -> '{}'", effectiveName, sanitized);
                    return sanitized;
                }
                
                String username = interactionUser.getName();
                logger.debug("Interaction user username: '{}'", username);
                if (username != null && !username.trim().isEmpty()) {
                    String sanitized = sanitizeDisplayName(username);
                    logger.debug("Successfully retrieved interaction username: '{}' -> '{}'", username, sanitized);
                    return sanitized;
                }
            }
            
            // Try using retrieveUserById as last resort (this makes an API call)
            logger.debug("Attempting API retrieval for user: {}", userId);
            try {
                net.dv8tion.jda.api.entities.User retrievedUser = event.getJDA().retrieveUserById(userId).complete();
                if (retrievedUser != null) {
                    logger.debug("User retrieved via API for user: {}", userId);
                    String effectiveName = retrievedUser.getEffectiveName();
                    logger.debug("API user effective name: '{}'", effectiveName);
                    if (effectiveName != null && !effectiveName.trim().isEmpty()) {
                        String sanitized = sanitizeDisplayName(effectiveName);
                        logger.debug("Successfully retrieved API user effective name: '{}' -> '{}'", effectiveName, sanitized);
                        return sanitized;
                    }
                    
                    String username = retrievedUser.getName();
                    logger.debug("API user username: '{}'", username);
                    if (username != null && !username.trim().isEmpty()) {
                        String sanitized = sanitizeDisplayName(username);
                        logger.debug("Successfully retrieved API username: '{}' -> '{}'", username, sanitized);
                        return sanitized;
                    }
                }
            } catch (Exception apiException) {
                logger.debug("API retrieval failed for user {}: {}", userId, apiException.getMessage());
            }
            
            // Ultimate fallback if all else fails
            logger.warn("Could not retrieve display name for user ID: {} - all lookup methods failed", userId);
            return "Unknown User";
            
        } catch (Exception e) {
            logger.error("Error retrieving display name for user ID {}: {}", userId, e.getMessage(), e);
            return "Unknown User";
        }
    }
    
    /**
     * Sanitize display name to handle special characters and Discord markdown
     * @param name The raw display name
     * @return Sanitized name safe for embed titles
     */
    private String sanitizeDisplayName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "Unknown User";
        }
        
        // Escape Discord markdown characters that could interfere with embed formatting
        // This prevents issues with names containing *, _, `, ~, |, \
        String sanitized = name
            .replace("\\", "\\\\")  // Escape backslashes first
            .replace("*", "\\*")    // Escape asterisks (bold/italic)
            .replace("_", "\\_")    // Escape underscores (italic/underline)
            .replace("`", "\\`")    // Escape backticks (code)
            .replace("~", "\\~")    // Escape tildes (strikethrough)
            .replace("|", "\\|");   // Escape pipes (spoiler)
        
        // Trim whitespace and ensure it's not empty after sanitization
        sanitized = sanitized.trim();
        if (sanitized.isEmpty()) {
            return "Unknown User";
        }
        
        // Limit length to prevent overly long embed titles (Discord limit is 256 chars)
        if (sanitized.length() > 50) {
            sanitized = sanitized.substring(0, 47) + "...";
        }
        
        return sanitized;
    }
    
    // Inner class to represent game state
    private static class DefuseGame {
        public enum GameState {
            PENDING, ACTIVE, COMPLETED
        }
        
        private final String challengerUserId;
        private final String challengedUserId;
        private final int betAmount;
        private GameState state;
        private String currentPlayerId; // whose turn it is
        private String bombWire; // which wire is the bomb
        private Set<String> cutWires; // which wires have been cut
        private String challengerDisplayName;
        private String challengedDisplayName;
        
        public DefuseGame(String challengerUserId, String challengedUserId, int betAmount) {
            this.challengerUserId = challengerUserId;
            this.challengedUserId = challengedUserId;
            this.betAmount = betAmount;
            this.state = GameState.PENDING;
            this.currentPlayerId = challengerUserId; // Challenger goes first
            this.cutWires = new HashSet<>();
            
            // Randomly select bomb wire
            Random random = new Random();
            this.bombWire = WIRE_COLORS.get(random.nextInt(WIRE_COLORS.size()));
        }
        
        public void cutWire(String wire) {
            this.cutWires.add(wire);
        }
        
        public void switchPlayer() {
            this.currentPlayerId = this.currentPlayerId.equals(challengerUserId) ? 
                challengedUserId : challengerUserId;
        }
        
        // Getters and setters
        public String getChallengerUserId() { return challengerUserId; }
        public String getChallengedUserId() { return challengedUserId; }
        public int getBetAmount() { return betAmount; }
        public GameState getState() { return state; }
        public void setState(GameState state) { this.state = state; }
        public String getCurrentPlayerId() { return currentPlayerId; }
        public String getBombWire() { return bombWire; }
        public Set<String> getCutWires() { return cutWires; }
        public String getChallengerDisplayName() { return challengerDisplayName; }
        public String getChallengedDisplayName() { return challengedDisplayName; }
        public void setChallengerDisplayName(String challengerDisplayName) { this.challengerDisplayName = challengerDisplayName; }
        public void setChallengedDisplayName(String challengedDisplayName) { this.challengedDisplayName = challengedDisplayName; }
    }
} 