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

@Component
public class RpsCommandListener extends ListenerAdapter {
    
    private static final Logger logger = LoggerFactory.getLogger(RpsCommandListener.class);
    private static final Color EMBED_COLOR = new Color(88, 101, 242); // Discord Blurple
    private static final Color SUCCESS_COLOR = new Color(40, 167, 69); // Bootstrap success green
    private static final Color FAILURE_COLOR = new Color(220, 53, 69); // Bootstrap danger red
    private static final Color WARNING_COLOR = new Color(255, 193, 7); // Bootstrap warning yellow
    
    private final UserService userService;
    private final CacheConfig cacheConfig;
    
    // Store active games to prevent duplicates and manage state
    private final ConcurrentHashMap<String, RpsGame> activeGames = new ConcurrentHashMap<>();
    
    public RpsCommandListener(UserService userService, CacheConfig cacheConfig) {
        this.userService = userService;
        this.cacheConfig = cacheConfig;
        logger.info("RpsCommandListener initialized");
    }
    
    @Override
    public void onSlashCommandInteraction(@Nonnull SlashCommandInteractionEvent event) {
        if (!event.getName().equals("rps")) {
            return; // Not our command
        }
        
        String challengerId = event.getUser().getId();
        logger.info("User {} requested /rps", challengerId);
        
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
            event.reply("You cannot challenge yourself to Rock Paper Scissors!").setEphemeral(true).queue();
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
                event.reply("One of you is already in an active Rock Paper Scissors game!").setEphemeral(true).queue();
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
            RpsGame game = new RpsGame(challengerId, challengedUserId, betAmount);
            activeGames.put(gameKey1, game);
            
            logger.debug("Created new RPS game: gameKey={}, challenger={}, challenged={}, bet={}", 
                        gameKey1, challengerId, challengedUserId, betAmount);
            logger.debug("Active games after creation: {}", activeGames.keySet());
            
            // Create initial request embed
            EmbedBuilder initialEmbed = new EmbedBuilder()
                .setColor(EMBED_COLOR)
                .setDescription(String.format("<@%s> Do you accept <@%s>'s Rock Paper Scissors challenge?", challengedUserId, challengerId));
            
            // Create accept/reject buttons
            Button acceptButton = Button.success("rps_accept_" + gameKey1, "✅");
            Button rejectButton = Button.danger("rps_reject_" + gameKey1, "❌");
            
            event.reply("")
                .addEmbeds(initialEmbed.build())
                .addComponents(ActionRow.of(acceptButton, rejectButton))
                .queue(
                    success -> {
                        // Set up timeout for the challenge
                        CompletableFuture.delayedExecutor(60, TimeUnit.SECONDS).execute(() -> {
                            RpsGame timeoutGame = activeGames.get(gameKey1);
                            if (timeoutGame != null && timeoutGame.getState() == RpsGame.GameState.PENDING) {
                                // Game timed out
                                activeGames.remove(gameKey1);
                                
                                logger.debug("RPS challenge timed out for game: {}, removed from active games", gameKey1);
                                logger.debug("Active games after timeout: {}", activeGames.keySet());
                                
                                EmbedBuilder timeoutEmbed = new EmbedBuilder()
                                    .setColor(WARNING_COLOR)
                                    .setTitle("Expired!");
                                
                                event.getHook().editOriginalEmbeds(timeoutEmbed.build())
                                    .setComponents() // Remove buttons
                                    .queue();
                                
                                logger.debug("RPS challenge timed out for game: {}", gameKey1);
                            } else {
                                logger.debug("Timeout check: game still active or already processed for gameKey: {}", gameKey1);
                            }
                        });
                    },
                    error -> logger.error("Failed to send RPS challenge: {}", error.getMessage())
                );
            
        } catch (Exception e) {
            logger.error("Error processing /rps command for user {}", challengerId, e);
            
            EmbedBuilder errorEmbed = new EmbedBuilder()
                .setColor(FAILURE_COLOR)
                .setTitle("❌ Error")
                .setDescription("An error occurred while processing your Rock Paper Scissors challenge.");
            
            event.reply("").addEmbeds(errorEmbed.build()).setEphemeral(true).queue();
        }
    }
    
    @Override
    public void onButtonInteraction(@Nonnull ButtonInteractionEvent event) {
        String buttonId = event.getComponentId();
        
        if (!buttonId.startsWith("rps_")) {
            return; // Not our button
        }
        
        String userId = event.getUser().getId();
        
        try {
            if (buttonId.startsWith("rps_accept_") || buttonId.startsWith("rps_reject_")) {
                handleChallengeResponse(event, buttonId, userId);
            } else if (buttonId.startsWith("rps_move_")) {
                handleMoveSelection(event, buttonId, userId);
            }
        } catch (Exception e) {
            logger.error("Error handling RPS button interaction: {}", e.getMessage(), e);
            event.reply("An error occurred while processing your action.").setEphemeral(true).queue();
        }
    }
    
    private void handleChallengeResponse(ButtonInteractionEvent event, String buttonId, String userId) {
        String gameKey = buttonId.substring(buttonId.indexOf("_", 4) + 1);
        
        logger.debug("Handling challenge response: buttonId={}, userId={}, gameKey={}", buttonId, userId, gameKey);
        logger.debug("Active games: {}", activeGames.keySet());
        
        RpsGame game = activeGames.get(gameKey);
        
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
        
        if (buttonId.startsWith("rps_accept_")) {
            // Accept the challenge
            game.setState(RpsGame.GameState.ACTIVE);
            
            logger.debug("Challenge accepted: gameKey={}, game state set to ACTIVE", gameKey);
            
            EmbedBuilder gameEmbed = new EmbedBuilder()
                .setColor(EMBED_COLOR)
                .setTitle("Rock Paper Scissors...")
                .setDescription(String.format("<@%s> - ```Hasn't selected a move```\n<@%s> - ```Hasn't selected a move```", 
                    game.getChallengerUserId(), game.getChallengedUserId()))
                .setFooter(String.format("Winner will win 🪙+%d!", game.getBetAmount()));
            
            // Create move selection buttons
            Button rockButton = Button.secondary("rps_move_rock_" + gameKey, "✊");
            Button paperButton = Button.secondary("rps_move_paper_" + gameKey, "✋");
            Button scissorsButton = Button.secondary("rps_move_scissors_" + gameKey, "✌️");
            
            logger.debug("Created move buttons: rock={}, paper={}, scissors={}", 
                        rockButton.getId(), paperButton.getId(), scissorsButton.getId());
            
            event.editMessageEmbeds(gameEmbed.build())
                .setComponents(ActionRow.of(scissorsButton, rockButton, paperButton))
                .queue();
            
        } else if (buttonId.startsWith("rps_reject_")) {
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
    
    private void handleMoveSelection(ButtonInteractionEvent event, String buttonId, String userId) {
        logger.debug("Handling move selection: buttonId={}, userId={}", buttonId, userId);
        
        String[] parts = buttonId.split("_");
        if (parts.length < 4) {
            logger.error("Invalid button ID format: {}", buttonId);
            event.reply("Invalid button format.").setEphemeral(true).queue();
            return;
        }
        
        String move = parts[2]; // rock, paper, or scissors
        // GameKey is everything after "rps_move_{move}_", since it can contain underscores
        // Rejoin all parts from index 3 onwards with underscores
        StringBuilder gameKeyBuilder = new StringBuilder();
        for (int i = 3; i < parts.length; i++) {
            if (i > 3) gameKeyBuilder.append("_");
            gameKeyBuilder.append(parts[i]);
        }
        String gameKey = gameKeyBuilder.toString();
        
        logger.debug("Parsed move: {}, gameKey: {}", move, gameKey);
        logger.debug("Active games: {}", activeGames.keySet());
        
        RpsGame game = activeGames.get(gameKey);
        
        if (game == null) {
            logger.warn("Game not found for gameKey: {}, active games: {}", gameKey, activeGames.keySet());
            event.reply("This game is no longer active.").setEphemeral(true).queue();
            return;
        }
        
        if (game.getState() != RpsGame.GameState.ACTIVE) {
            event.reply("This game is not in an active state.").setEphemeral(true).queue();
            return;
        }
        
        // Check if user is part of this game
        if (!userId.equals(game.getChallengerUserId()) && !userId.equals(game.getChallengedUserId())) {
            event.reply("You are not part of this game.").setEphemeral(true).queue();
            return;
        }
        
        // Record the move
        boolean moveRecorded = game.recordMove(userId, move);
        
        if (!moveRecorded) {
            event.reply("You have already selected your move!").setEphemeral(true).queue();
            return;
        }
        
        // Update the embed to show move was selected
        String challengerStatus = game.getChallengerMove() != null ? "Selected a move" : "Hasn't selected a move";
        String challengedStatus = game.getChallengedMove() != null ? "Selected a move" : "Hasn't selected a move";
        
        EmbedBuilder updateEmbed = new EmbedBuilder()
            .setColor(EMBED_COLOR)
            .setTitle("Rock Paper Scissors...")
            .setDescription(String.format("<@%s> - ```%s```\n<@%s> - ```%s```", 
                game.getChallengerUserId(), challengerStatus,
                game.getChallengedUserId(), challengedStatus))
            .setFooter(String.format("Winner will win 🪙+%d!", game.getBetAmount()));
        
        event.editMessageEmbeds(updateEmbed.build()).queue();
        
        // Check if both players have made their moves
        if (game.getChallengerMove() != null && game.getChallengedMove() != null) {
            // Store display names before starting async sequence (while we have proper context)
            String challengerName = getUserDisplayName(event, game.getChallengerUserId());
            String challengedName = getUserDisplayName(event, game.getChallengedUserId());
            game.setChallengerDisplayName(challengerName);
            game.setChallengedDisplayName(challengedName);
            
            logger.debug("Stored display names: challenger={}, challenged={}", challengerName, challengedName);
            
            // Both moves are in, start the reveal sequence
            startRevealSequence(event, gameKey, game);
        }
    }
    
    private void startRevealSequence(ButtonInteractionEvent event, String gameKey, RpsGame game) {
        logger.debug("Starting reveal sequence for gameKey: {}", gameKey);
        
        // Remove buttons first by setting empty components using the hook since interaction is already acknowledged
        event.getHook().editOriginalComponents().queue();
        
        // Animated reveal sequence: "Rock" → "Paper" → "Scissors.." → "Shoot!"
        CompletableFuture.delayedExecutor(1, TimeUnit.SECONDS).execute(() -> {
            EmbedBuilder rockEmbed = new EmbedBuilder()
                .setColor(EMBED_COLOR)
                .setTitle("Rock");
            
            event.getHook().editOriginalEmbeds(rockEmbed.build())
                .setComponents() // Explicitly remove all components
                .queue();
            
            CompletableFuture.delayedExecutor(1, TimeUnit.SECONDS).execute(() -> {
                EmbedBuilder paperEmbed = new EmbedBuilder()
                    .setColor(EMBED_COLOR)
                    .setTitle("Paper");
                
                event.getHook().editOriginalEmbeds(paperEmbed.build())
                    .setComponents() // Explicitly remove all components
                    .queue();
                
                CompletableFuture.delayedExecutor(1, TimeUnit.SECONDS).execute(() -> {
                    EmbedBuilder scissorsEmbed = new EmbedBuilder()
                        .setColor(EMBED_COLOR)
                        .setTitle("Scissors..");
                    
                    event.getHook().editOriginalEmbeds(scissorsEmbed.build())
                        .setComponents() // Explicitly remove all components
                        .queue();
                    
                    CompletableFuture.delayedExecutor(1, TimeUnit.SECONDS).execute(() -> {
                        EmbedBuilder shootEmbed = new EmbedBuilder()
                            .setColor(EMBED_COLOR)
                            .setTitle("Shoot!");
                        
                        event.getHook().editOriginalEmbeds(shootEmbed.build())
                            .setComponents() // Explicitly remove all components
                            .queue();
                        
                        // Show final result after "Shoot!"
                        CompletableFuture.delayedExecutor(1, TimeUnit.SECONDS).execute(() -> {
                            showGameResult(event, gameKey, game);
                        });
                    });
                });
            });
        });
    }
    
    @Transactional
    private void showGameResult(ButtonInteractionEvent event, String gameKey, RpsGame game) {
        try {
            // Remove game from active games
            activeGames.remove(gameKey);
            
            logger.debug("Game completed and removed from active games: gameKey={}", gameKey);
            logger.debug("Active games after completion: {}", activeGames.keySet());
            
            String challengerMove = game.getChallengerMove();
            String challengedMove = game.getChallengedMove();
            
            // Use stored display names (retrieved when we had proper context)
            String challengerName = game.getChallengerDisplayName();
            String challengedName = game.getChallengedDisplayName();
            
            // Fallback if display names weren't stored for some reason
            if (challengerName == null) {
                challengerName = getUserDisplayName(event, game.getChallengerUserId());
                logger.warn("Had to retrieve challenger display name in async context: {}", challengerName);
            }
            if (challengedName == null) {
                challengedName = getUserDisplayName(event, game.getChallengedUserId());
                logger.warn("Had to retrieve challenged display name in async context: {}", challengedName);
            }
            
            // Determine winner
            String winner = determineWinner(challengerMove, challengedMove);
            
            if (winner == null) {
                // It's a tie
                EmbedBuilder tieEmbed = new EmbedBuilder()
                    .setColor(WARNING_COLOR)
                    .setTitle("It's a tie!")
                    .setDescription(String.format("**%s** picked **%s**\n**%s** picked **%s**", 
                        challengerName, challengerMove,
                        challengedName, challengedMove));
                
                event.getHook().editOriginalEmbeds(tieEmbed.build())
                    .setComponents() // Explicitly remove all components
                    .queue();
                return;
            }
            
            // Process credit transfer
            User challenger = userService.getUserById(game.getChallengerUserId());
            User challenged = userService.getUserById(game.getChallengedUserId());
            
            if (challenger == null || challenged == null) {
                logger.error("User not found during RPS result processing");
                return;
            }
            
            String winnerId = winner.equals("challenger") ? game.getChallengerUserId() : game.getChallengedUserId();
            String loserId = winner.equals("challenger") ? game.getChallengedUserId() : game.getChallengerUserId();
            
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
            
            // Get winner and loser display names from stored values
            String winnerName = winnerId.equals(game.getChallengerUserId()) ? challengerName : challengedName;
            String loserName = loserId.equals(game.getChallengerUserId()) ? challengerName : challengedName;
            
            // Create result embed
            EmbedBuilder resultEmbed = new EmbedBuilder()
                .setColor(SUCCESS_COLOR)
                .setTitle(String.format("%s you won! 🪙+%d", winnerName, game.getBetAmount()))
                .setDescription(String.format("**%s** picked **%s**\n**%s** picked **%s**", 
                    winnerName, winnerId.equals(game.getChallengerUserId()) ? challengerMove : challengedMove,
                    loserName, loserId.equals(game.getChallengerUserId()) ? challengerMove : challengedMove));
            
            event.getHook().editOriginalEmbeds(resultEmbed.build())
                .setComponents() // Explicitly remove all components
                .queue();
            
            logger.info("RPS game completed: {} won {} credits from {}", winnerId, game.getBetAmount(), loserId);
            
        } catch (Exception e) {
            logger.error("Error processing RPS game result: {}", e.getMessage(), e);
            
            EmbedBuilder errorEmbed = new EmbedBuilder()
                .setColor(FAILURE_COLOR)
                .setTitle("❌ Error")
                .setDescription("An error occurred while processing the game result.");
            
            event.getHook().editOriginalEmbeds(errorEmbed.build())
                .setComponents() // Explicitly remove all components
                .queue();
        }
    }
    
    private String determineWinner(String challengerMove, String challengedMove) {
        if (challengerMove.equals(challengedMove)) {
            return null; // Tie
        }
        
        // Rock beats Scissors, Paper beats Rock, Scissors beats Paper
        if ((challengerMove.equals("rock") && challengedMove.equals("scissors")) ||
            (challengerMove.equals("paper") && challengedMove.equals("rock")) ||
            (challengerMove.equals("scissors") && challengedMove.equals("paper"))) {
            return "challenger";
        } else {
            return "challenged";
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
    private static class RpsGame {
        public enum GameState {
            PENDING, ACTIVE, COMPLETED
        }
        
        private final String challengerUserId;
        private final String challengedUserId;
        private final int betAmount;
        private GameState state;
        private String challengerMove;
        private String challengedMove;
        private String challengerDisplayName;
        private String challengedDisplayName;
        
        public RpsGame(String challengerUserId, String challengedUserId, int betAmount) {
            this.challengerUserId = challengerUserId;
            this.challengedUserId = challengedUserId;
            this.betAmount = betAmount;
            this.state = GameState.PENDING;
        }
        
        public boolean recordMove(String userId, String move) {
            if (userId.equals(challengerUserId)) {
                if (challengerMove == null) {
                    challengerMove = move;
                    return true;
                }
                return false;
            } else if (userId.equals(challengedUserId)) {
                if (challengedMove == null) {
                    challengedMove = move;
                    return true;
                }
                return false;
            }
            return false;
        }
        
        // Getters and setters
        public String getChallengerUserId() { return challengerUserId; }
        public String getChallengedUserId() { return challengedUserId; }
        public int getBetAmount() { return betAmount; }
        public GameState getState() { return state; }
        public void setState(GameState state) { this.state = state; }
        public String getChallengerMove() { return challengerMove; }
        public String getChallengedMove() { return challengedMove; }
        public String getChallengerDisplayName() { return challengerDisplayName; }
        public String getChallengedDisplayName() { return challengedDisplayName; }
        public void setChallengerDisplayName(String challengerDisplayName) { this.challengerDisplayName = challengerDisplayName; }
        public void setChallengedDisplayName(String challengedDisplayName) { this.challengedDisplayName = challengedDisplayName; }
    }
} 