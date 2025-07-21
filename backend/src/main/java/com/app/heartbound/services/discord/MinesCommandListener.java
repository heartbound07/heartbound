package com.app.heartbound.services.discord;

import com.app.heartbound.entities.User;
import com.app.heartbound.services.UserService;
import com.app.heartbound.services.SecureRandomService;

import jakarta.annotation.PostConstruct;
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

    private static final int GRID_SIZE = 3;
    private static final long GAME_TIMEOUT_MINUTES = 10;

    @PostConstruct
    public void init() {
        scheduler.scheduleAtFixedRate(this::cleanupExpiredGames, GAME_TIMEOUT_MINUTES, GAME_TIMEOUT_MINUTES, TimeUnit.MINUTES);
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdown();
    }

    @Override
    public void onSlashCommandInteraction(@Nonnull SlashCommandInteractionEvent event) {
        if (!event.getName().equals("mines")) {
            return;
        }

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

        User user = userService.getUserById(userId);
        if (user == null || user.getCredits() == null || user.getCredits() < bet) {
            event.reply("You do not have enough credits to place this bet.").setEphemeral(true).queue();
            return;
        }

        event.deferReply().queue();

        // Deduct bet amount
        user.setCredits(user.getCredits() - bet);
        userService.updateUser(user);

        MinesGame game = new MinesGame(userId, event.getHook(), bet, mines);
        placeMines(game);
        activeGames.put(userId, game);

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

        String userId = event.getUser().getId();
        MinesGame game = activeGames.get(userId);

        if (game == null) {
            event.reply("This game of Mines has expired or is invalid.").setEphemeral(true).queue();
            event.getInteraction().getMessage().editMessageComponents(
                event.getInteraction().getMessage().getComponents().stream().map(c -> c.asDisabled()).collect(Collectors.toList())
            ).queue();
            return;
        }

        event.deferEdit().queue();

        if (componentId.equals("mines_cashout")) {
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
                EmbedBuilder embed = createGameEmbed(game);
                List<ActionRow> components = createGameComponents(game.getUserId(), false);
                game.getHook().editOriginalEmbeds(embed.build()).setComponents(components).queue();
            }
        }
    }

    private void handleCashout(ButtonInteractionEvent event, MinesGame game) {
        User user = userService.getUserById(game.getUserId());
        int winnings = (int) Math.round(game.getBetAmount() * game.getCurrentMultiplier());
        user.setCredits(user.getCredits() + winnings);
        userService.updateUser(user);

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🎉 You Won!")
                .setColor(WIN_COLOR)
                .appendDescription(String.format("You cashed out successfully!%n%n"))
                .appendDescription(String.format("**Bet Amount:** 🪙 %d credits%n", game.getBetAmount()))
                .appendDescription(String.format("**Won:** 🪙 %d credits (%.2fx)%n%n", winnings, game.getCurrentMultiplier()))
                .appendDescription(String.format("**New Balance:** 🪙 %d credits", user.getCredits()));

        List<ActionRow> components = createRevealedGrid(game, -1, -1, true);
        game.getHook().editOriginalEmbeds(embed.build()).setComponents(components).queue();
        activeGames.remove(game.getUserId());
    }

    private void handleLoss(ButtonInteractionEvent event, MinesGame game, int hitRow, int hitCol) {
        User user = userService.getUserById(game.getUserId());

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("💔 You Lost!")
                .setColor(LOSE_COLOR)
                .appendDescription(String.format("**Bet Amount:** 🪙 %d credits%n", game.getBetAmount()))
                .appendDescription(String.format("**New Balance:** 🪙 %d credits", user.getCredits()));

        List<ActionRow> components = createRevealedGrid(game, hitRow, hitCol, true);
        game.getHook().editOriginalEmbeds(embed.build()).setComponents(components).queue();
        activeGames.remove(game.getUserId());
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
        return new EmbedBuilder()
                .setTitle("💣 Mines - Select Your Squares!")
                .setColor(EMBED_COLOR)
                .appendDescription(String.format("**Bet Amount:** 🪙 %d credits%n", game.getBetAmount()))
                .appendDescription(String.format("**Winnings:** 🪙 %d credits (%.2fx)",
                        (int) Math.round(game.getBetAmount() * game.getCurrentMultiplier()),
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
                String buttonId = String.format("mines_tile_%d_%d", row, col);
                if (game.getRevealed()[row][col]) {
                    button = Button.success(buttonId, "✅").withDisabled(true);
                } else {
                    button = Button.secondary(buttonId, "❓").withDisabled(disabled);
                }
                buttons.add(button);
            }
            rows.add(ActionRow.of(buttons));
        }

        rows.add(ActionRow.of(Button.success("mines_cashout", "Cashout").withDisabled(disabled || game.getSafeTilesRevealed() == 0)));
        return rows;
    }

    private List<ActionRow> createRevealedGrid(MinesGame game, int hitRow, int hitCol, boolean disabled) {
        List<ActionRow> rows = new ArrayList<>();
        for (int r = 0; r < GRID_SIZE; r++) {
            List<Button> buttons = new ArrayList<>();
            for (int c = 0; c < GRID_SIZE; c++) {
                String buttonId = "mines_revealed_" + r + "_" + c;
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
        rows.add(ActionRow.of(Button.success("mines_cashout", "Cashout").withDisabled(true)));
        return rows;
    }

    private void cleanupExpiredGames() {
        long now = System.currentTimeMillis();
        activeGames.entrySet().removeIf(entry -> {
            MinesGame game = entry.getValue();
            if (TimeUnit.MILLISECONDS.toMinutes(now - game.getGameStartTime()) > GAME_TIMEOUT_MINUTES) {
                logger.info("Game for user {} timed out. Bet of {} lost.", game.getUserId(), game.getBetAmount());
                EmbedBuilder embed = new EmbedBuilder()
                        .setTitle("Game Timed Out")
                        .setColor(TIMEOUT_COLOR)
                        .setDescription("Your game of Mines has expired. Your bet of 🪙 " + game.getBetAmount() + " credits has been forfeited.");
                
                List<ActionRow> components = createRevealedGrid(game, -1, -1, true);

                game.getHook().editOriginalEmbeds(embed.build()).setComponents(components).queue(
                    success -> logger.debug("Successfully edited timed out game message for user {}", game.getUserId()),
                    error -> logger.error("Failed to edit timed out game message for user {}", game.getUserId(), error)
                );
                return true;
            }
            return false;
        });
    }
} 