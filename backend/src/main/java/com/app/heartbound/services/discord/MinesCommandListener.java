package com.app.heartbound.services.discord;

import com.app.heartbound.entities.User;
import com.app.heartbound.services.UserService;
import com.app.heartbound.services.SecureRandomService;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.Nonnull;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.concurrent.ScheduledFuture;

@Service
@RequiredArgsConstructor
public class MinesCommandListener extends ListenerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(MinesCommandListener.class);
    private static final Color EMBED_COLOR = new Color(88, 101, 242); // Discord Blurple
    private static final Color WIN_COLOR = new Color(40, 167, 69);
    private static final Color LOSE_COLOR = new Color(220, 53, 69);
    private static final Color TIMEOUT_COLOR = Color.ORANGE;
    private final Map<String, MinesGame> activeGames = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    private final UserService userService;
    private final SecureRandomService secureRandomService;
    private final TermsOfServiceService termsOfServiceService;

    private static final int GRID_SIZE = 3;

    @PreDestroy
    public void shutdown() {
        scheduler.shutdown();
    }

    @Override
    public void onSlashCommandInteraction(@Nonnull SlashCommandInteractionEvent event) {
        if (!event.getName().equals("mines")) {
            return;
        }

        logger.info("User {} requested /mines", event.getUser().getId());

        // Check Terms of Service agreement before proceeding
        termsOfServiceService.requireAgreement(event, (user) -> {
            // ToS check passed, continue with mines logic
            continueMinesCommand(event, user);
        });
    }

    /**
     * Continues the mines command logic after Terms of Service agreement is confirmed.
     */
    private void continueMinesCommand(@Nonnull SlashCommandInteractionEvent event, User user) {
        String userId = event.getUser().getId();
        if (activeGames.containsKey(userId)) {
            event.reply("You already have an active game of Mines. Please complete or cash out your current game first.").setEphemeral(true).queue();
            return;
        }
        
        OptionMapping betOption = event.getOption("bet");
        OptionMapping minesOption = event.getOption("mines");

        if (betOption == null || minesOption == null) {
            event.reply("Missing required options. Please provide both 'bet' and 'mines'.").setEphemeral(true).queue();
            logger.error("Mines command invoked without required options by user {}", userId);
            return;
        }

        int bet = betOption.getAsInt();
        int mines = minesOption.getAsInt();

        if (bet <= 0) {
            event.reply("The bet amount must be a positive number.").setEphemeral(true).queue();
            return;
        }

        if (mines < 1 || mines > 8) {
            event.reply("The number of mines must be between 1 and 8.").setEphemeral(true).queue();
            return;
        }

        // Atomically check for and deduct credits. This also implicitly checks if the user exists.
        boolean sufficientCredits = userService.deductCreditsIfSufficient(userId, bet);

        if (!sufficientCredits) {
            // Since deduction failed, we fetch the user to give a more specific error message.
            User userForBalance = userService.getUserById(userId);
            if (userForBalance == null) {
                event.reply("You must be registered with the bot to use this command. Please log in to the web application first.").setEphemeral(true).queue();
            } else {
                int currentCredits = userForBalance.getCredits() != null ? userForBalance.getCredits() : 0;
                event.reply(String.format("You do not have enough credits to place this bet. You tried to bet **🪙 %d** but only have **🪙 %d** credits.", bet, currentCredits))
                     .setEphemeral(true).queue();
            }
            return;
        }

        event.deferReply().queue();

        MinesGame game = new MinesGame(userId, event.getHook(), bet, mines);
        placeMines(game);
        activeGames.put(userId, game);

        // Schedule a one-time 15-second expiration task for this game.
        ScheduledFuture<?> expirationTask = scheduler.schedule(() -> handleGameExpiration(userId, game), 15, TimeUnit.SECONDS);
        game.setExpirationTask(expirationTask);

        EmbedBuilder embed = createGameEmbed(game);
        List<ActionRow> components = createGameComponents(game.getUserId(), false);

        event.getHook().sendMessageEmbeds(embed.build()).addComponents(components).queue();
    }

    @Override
    public void onButtonInteraction(@Nonnull ButtonInteractionEvent event) {
        String componentId = event.getComponentId();
        if (!componentId.startsWith("mines_")) {
            return;
        }

        // Extract the game owner's ID, which is the last part of the component ID.
        String[] parts = componentId.split("_");
        String ownerId = parts[parts.length - 1];
        String interactingUserId = event.getUser().getId();

        // Check if the user clicking the button is the one who started the game.
        if (!interactingUserId.equals(ownerId)) {
            event.reply("This is not your game to play!").setEphemeral(true).queue();
            return;
        }

        // Use the owner's ID to retrieve the game.
        MinesGame game = activeGames.get(ownerId);

        if (game == null) {
            event.reply("This game of Mines has expired or is invalid.").setEphemeral(true).queue();
            event.getInteraction().getMessage().editMessageComponents(
                event.getInteraction().getMessage().getComponents().stream().map(c -> c.asDisabled()).collect(Collectors.toList())
            ).queue();
            return;
        }

        event.deferEdit().queue();

        if (componentId.startsWith("mines_cashout")) {
            handleCashout(event, game);
        } else {
            handleGridClick(event, game);
        }
    }

    private void placeMines(MinesGame game) {
        List<Integer> positions = IntStream.range(0, GRID_SIZE * GRID_SIZE).boxed().collect(Collectors.toList());
        Collections.shuffle(positions, secureRandomService.getSecureRandom());

        for (int i = 0; i < game.getMineCount(); i++) {
            int pos = positions.get(i);
            int row = pos / GRID_SIZE;
            int col = pos % GRID_SIZE;
            game.getMines()[row][col] = true;
        }
    }

    private void handleGridClick(ButtonInteractionEvent event, MinesGame game) {
        String[] parts = event.getComponentId().split("_");
        int row = Integer.parseInt(parts[2]);
        int col = Integer.parseInt(parts[3]);

        if (game.getRevealed()[row][col]) {
            return; // Ignore clicks on already revealed tiles
        }

        if (game.getMines()[row][col]) {
            // Hit a mine
            handleLoss(event, game, row, col);
        } else {
            // Safe tile
            game.getRevealed()[row][col] = true;
            game.setSafeTilesRevealed(game.getSafeTilesRevealed() + 1);

            updateMultiplier(game);

            if (game.getSafeTilesRevealed() == game.getTotalSafeTiles()) {
                // All safe tiles revealed, automatic win
                handleCashout(event, game);
            } else {
                // Successfully revealed a safe tile, so reset the expiration timer.
                if (game.getExpirationTask() != null) {
                    game.getExpirationTask().cancel(false);
                }
                ScheduledFuture<?> newExpirationTask = scheduler.schedule(() -> handleGameExpiration(game.getUserId(), game), 15, TimeUnit.SECONDS);
                game.setExpirationTask(newExpirationTask);
                
                EmbedBuilder embed = createGameEmbed(game);
                List<ActionRow> components = createGameComponents(game.getUserId(), false);
                game.getHook().editOriginalEmbeds(embed.build()).setComponents(components).queue();
            }
        }
    }

    private void handleCashout(ButtonInteractionEvent event, MinesGame game) {
        // Atomically remove the game to prevent the expiration task from running on a completed game.
        if (activeGames.remove(game.getUserId(), game)) {
            // Also cancel the scheduled expiration task.
            if (game.getExpirationTask() != null) {
                game.getExpirationTask().cancel(false);
            }

            int totalPayout = (int) Math.round(game.getBetAmount() * game.getCurrentMultiplier());
            int profit = totalPayout - game.getBetAmount();

            // Atomically increment credits for the payout.
            userService.updateCreditsAtomic(game.getUserId(), totalPayout);

            // Re-fetch the user to get the updated balance for the embed.
            User user = userService.getUserById(game.getUserId());
            int newBalance = user != null && user.getCredits() != null ? user.getCredits() : 0;

            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("🎉 You Won!")
                    .setColor(WIN_COLOR)
                    .appendDescription(String.format("You cashed out successfully!%n%n"))
                    .appendDescription(String.format("**Bet Amount:** 🪙 %d credits%n", game.getBetAmount()))
                    .appendDescription(String.format("**Winnings:** 🪙 +%d credits (%.2fx)%n%n", profit, game.getCurrentMultiplier()))
                    .appendDescription(String.format("**New Balance:** 🪙 %d credits", newBalance));

            List<ActionRow> components = createRevealedGrid(game, -1, -1, true);
            game.getHook().editOriginalEmbeds(embed.build()).setComponents(components).queue();
        }
    }

    private void handleLoss(ButtonInteractionEvent event, MinesGame game, int hitRow, int hitCol) {
        // Atomically remove the game to prevent the expiration task from running on a completed game.
        if (activeGames.remove(game.getUserId(), game)) {
            // Also cancel the scheduled expiration task.
            if (game.getExpirationTask() != null) {
                game.getExpirationTask().cancel(false);
            }
            
            // Re-fetch user to display the correct balance after the initial bet was deducted.
            User user = userService.getUserById(game.getUserId());
            int newBalance = user != null && user.getCredits() != null ? user.getCredits() : 0;

            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("💔 You Lost!")
                    .setColor(LOSE_COLOR)
                    .appendDescription(String.format("You hit a mine and lost your bet. Better luck next time!%n%n"))
                    .appendDescription(String.format("**Bet Amount:** 🪙 %d credits%n", game.getBetAmount()))
                    .appendDescription(String.format("**New Balance:** 🪙 %d credits", newBalance));

            List<ActionRow> components = createRevealedGrid(game, hitRow, hitCol, true);
            game.getHook().editOriginalEmbeds(embed.build()).setComponents(components).queue();
        }
    }
    
    private void updateMultiplier(MinesGame game) {
        int safeTilesFound = game.getSafeTilesRevealed();
        int mineCount = game.getMineCount();
        final int TOTAL_TILES = GRID_SIZE * GRID_SIZE;

        // Calculate the multiplier for the step of finding the `safeTilesFound`-th safe tile.
        // Before this tile was revealed, there were `safeTilesFound - 1` tiles revealed.
        double unrevealedTiles = TOTAL_TILES - (safeTilesFound - 1);

        // This check prevents division by zero if all remaining tiles are mines.
        if (unrevealedTiles <= mineCount) {
            logger.warn("Cannot calculate multiplier increase for user {}, as number of unrevealed tiles ({}) is not greater than mine count ({}).",
                    game.getUserId(), unrevealedTiles, mineCount);
            return;
        }

        // The multiplier for this single step.
        double stepMultiplier = unrevealedTiles / (unrevealedTiles - mineCount);

        // Apply a house edge. A lower value means a higher house edge (lower payout).
        double houseEdge = 0.92; // Lowered from 0.97 to reduce payouts slightly.
        stepMultiplier *= houseEdge;

        // Update the game's cumulative multiplier.
        game.setCurrentMultiplier(game.getCurrentMultiplier() * stepMultiplier);

        logger.debug("Updated multiplier for user {}: safe_tiles={}, step_mult={}, new_total_mult={}",
                game.getUserId(), safeTilesFound, stepMultiplier, game.getCurrentMultiplier());
    }


    private EmbedBuilder createGameEmbed(MinesGame game) {
        int potentialWinnings = (int) Math.round(game.getBetAmount() * game.getCurrentMultiplier());
        int profit = potentialWinnings - game.getBetAmount();
        return new EmbedBuilder()
                .setTitle("💣 Mines - Select Your Squares!")
                .setColor(EMBED_COLOR)
                .appendDescription(String.format("**Bet Amount:** 🪙 %d credits%n", game.getBetAmount()))
                .appendDescription(String.format("**Winnings:** 🪙 +%d credits (%.2fx)",
                        profit,
                        game.getCurrentMultiplier()));
    }

    private List<ActionRow> createGameComponents(String userId, boolean disabled) {
        MinesGame game = activeGames.get(userId);
        if (game == null) {
            return Collections.emptyList();
        }
        
        List<ActionRow> rows = new ArrayList<>();
        
        for (int row = 0; row < GRID_SIZE; row++) {
            List<Button> buttons = new ArrayList<>();
            for (int col = 0; col < GRID_SIZE; col++) {
                Button button;
                // Append the owner's userId to the button ID
                String buttonId = String.format("mines_tile_%d_%d_%s", row, col, userId);
                if (game.getRevealed()[row][col]) {
                    button = Button.success(buttonId, "✅").withDisabled(true);
                } else {
                    button = Button.secondary(buttonId, "❓").withDisabled(disabled);
                }
                buttons.add(button);
            }
            rows.add(ActionRow.of(buttons));
        }

        // Only add the cashout button if at least one safe tile has been revealed.
        if (game.getSafeTilesRevealed() > 0) {
            rows.add(ActionRow.of(Button.success("mines_cashout_" + userId, "Cashout").withDisabled(disabled)));
        }
        return rows;
    }

    private List<ActionRow> createRevealedGrid(MinesGame game, int hitRow, int hitCol, boolean disabled) {
        List<ActionRow> rows = new ArrayList<>();
        for (int r = 0; r < GRID_SIZE; r++) {
            List<Button> buttons = new ArrayList<>();
            for (int c = 0; c < GRID_SIZE; c++) {
                // Append the owner's userId to the button ID
                String buttonId = String.format("mines_revealed_%d_%d_%s", r, c, game.getUserId());
                Button button;

                if (r == hitRow && c == hitCol) {
                    // The exact mine the user clicked to lose
                    button = Button.danger(buttonId, "💣").withDisabled(disabled);
                } else if (game.getMines()[r][c]) {
                    // Other mines that were not clicked
                    button = Button.secondary(buttonId, "💣").withDisabled(disabled);
                } else {
                    // This is a safe square. Now check if it was revealed.
                    if (game.getRevealed()[r][c]) {
                        // Safe square the user clicked
                        button = Button.success(buttonId, "✅").withDisabled(disabled);
                    } else {
                        // Safe square the user did NOT click
                        button = Button.secondary(buttonId, "✅").withDisabled(disabled);
                    }
                }
                buttons.add(button);
            }
            rows.add(ActionRow.of(buttons));
        }
        rows.add(ActionRow.of(Button.success("mines_cashout_" + game.getUserId(), "Cashout").withDisabled(true)));
        return rows;
    }

    private void handleGameExpiration(String userId, MinesGame game) {
        // Atomically remove the game. If it fails, another thread (e.g., a button click) already handled it.
        if (activeGames.remove(userId, game)) {
            if (game.getSafeTilesRevealed() == 0) {
                // Scenario 1: No moves made. Refund the bet and show a timeout message.
                logger.info("Mines game for user {} timed out with no moves. Refunding bet.", userId);
                
                // Atomically refund the bet amount.
                userService.updateCreditsAtomic(userId, game.getBetAmount());

                EmbedBuilder embed = new EmbedBuilder()
                        .setTitle("⏳ Mines Timed Out")
                        .setColor(TIMEOUT_COLOR)
                        .setDescription("The mines session has timed out and your bet has been refunded.\nIf you want to try again, simply start a new mines game.");

                // Remove all buttons, leaving only the timeout embed.
                game.getHook().editOriginalEmbeds(embed.build()).setComponents().queue();
            } else {
                // Scenario 2: At least one move made. Auto-cashout.
                logger.info("Mines game for user {} timed out with {} tiles revealed. Auto-cashing out.", userId, game.getSafeTilesRevealed());
                
                int totalPayout = (int) Math.round(game.getBetAmount() * game.getCurrentMultiplier());
                int profit = totalPayout - game.getBetAmount();

                // Atomically process the cashout.
                userService.updateCreditsAtomic(userId, totalPayout);

                // Re-fetch the user to get the updated balance for the embed.
                User user = userService.getUserById(userId);
                int newBalance = user != null && user.getCredits() != null ? user.getCredits() : 0;

                EmbedBuilder embed = new EmbedBuilder()
                        .setTitle("🎉 Auto Cashed Out!")
                        .setColor(WIN_COLOR)
                        .appendDescription(String.format("The game timed out, so you were automatically cashed out!%n%n"))
                        .appendDescription(String.format("**Bet Amount:** 🪙 %d credits%n", game.getBetAmount()))
                        .appendDescription(String.format("**Winnings:** 🪙 +%d credits (%.2fx)%n%n", profit, game.getCurrentMultiplier()))
                        .appendDescription(String.format("**New Balance:** 🪙 %d credits", newBalance));

                List<ActionRow> components = createRevealedGrid(game, -1, -1, true);
                game.getHook().editOriginalEmbeds(embed.build()).setComponents(components).queue();
            }
        }
    }
} 