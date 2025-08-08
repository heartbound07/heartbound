package com.app.heartbound.services.discord;

import com.app.heartbound.dto.CreateAuditDTO;
import com.app.heartbound.entities.DiscordBotSettings;
import com.app.heartbound.entities.User;
import com.app.heartbound.enums.AuditCategory;
import com.app.heartbound.enums.AuditSeverity;
import com.app.heartbound.services.AuditService;
import com.app.heartbound.services.SecureRandomService;
import com.app.heartbound.services.UserService;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import java.awt.Color;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditData;

@Component
public class SlotsCommandListener extends ListenerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(SlotsCommandListener.class);

    // Emoji reel symbols
    private static final String[] REEL = new String[]{
            "🍇","🍈","🍉","🍊","🍋","🍌","🍑","🍐","🍏","🍎","🥭","🍍","🍒","🍓","🫐","🥝","🍅","🫒","🥥"
    };

    // Payout multipliers (including stake)
    private static final int MULTIPLIER_THREE_MATCH = 10; // 10x total payout (includes stake)
    private static final int MULTIPLIER_TWO_MATCH = 2;   // 2x total payout (includes stake)

    // Animation and rate-limiting
    private static final long UPDATE_INTERVAL_MS = 1100; // >= 1 second to avoid rate limits
    private static final int MAX_UPDATES = 3;            // number of interim updates before final
    private static final long USER_COOLDOWN_MS = 5000;   // basic spam prevention per user

    // Embed colors (following BlackjackCommandListener pattern)
    private static final Color WIN_COLOR = new Color(40, 167, 69);
    private static final Color LOSE_COLOR = new Color(220, 53, 69);
    private static final Color EMBED_COLOR = new Color(88, 101, 242);

    private final UserService userService;
    private final SecureRandomService secureRandomService;
    private final AuditService auditService;
    private final DiscordBotSettingsService discordBotSettingsService;
    private final TermsOfServiceService termsOfServiceService;

    @Value("${discord.main.guild.id}")
    private String mainGuildId;

    // Track active slot games and cooldowns
    private final ConcurrentHashMap<String, Boolean> activeGames = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> lastPlayAt = new ConcurrentHashMap<>();

    public SlotsCommandListener(UserService userService,
                                SecureRandomService secureRandomService,
                                AuditService auditService,
                                DiscordBotSettingsService discordBotSettingsService,
                                TermsOfServiceService termsOfServiceService) {
        this.userService = userService;
        this.secureRandomService = secureRandomService;
        this.auditService = auditService;
        this.discordBotSettingsService = discordBotSettingsService;
        this.termsOfServiceService = termsOfServiceService;
        logger.info("SlotsCommandListener initialized");
    }

    @Override
    public void onSlashCommandInteraction(@Nonnull SlashCommandInteractionEvent event) {
        if (!Objects.equals(event.getName(), "slots")) {
            return;
        }

        // Guild restriction
        Guild guild = event.getGuild();
        if (guild == null || !guild.getId().equals(mainGuildId)) {
            event.reply("This command can only be used in the main Heartbound server.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        // Require Terms of Service agreement
        termsOfServiceService.requireAgreement(event, user -> {
            event.deferReply().queue();
            handleSlotsCommand(event);
        });
    }

    private void handleSlotsCommand(@Nonnull SlashCommandInteractionEvent event) {
        String userId = event.getUser().getId();

        try {
            // Rate limiting: simple per-user cooldown
            long now = System.currentTimeMillis();
            Long last = lastPlayAt.get(userId);
            if (last != null && (now - last) < USER_COOLDOWN_MS) {
                long waitMs = USER_COOLDOWN_MS - (now - last);
                long waitSec = Math.max(1, (waitMs + 999) / 1000);
                event.getHook().sendMessage("Please wait " + waitSec + "s before playing /slots again.")
                        .setEphemeral(true)
                        .queue();
                return;
            }

            // Prevent multiple simultaneous games
            if (activeGames.putIfAbsent(userId, Boolean.TRUE) != null) {
                event.getHook().sendMessage("You already have an active slots game!")
                        .setEphemeral(true)
                        .queue();
                return;
            }

            // Parse bet
            var betOption = event.getOption("bet");
            if (betOption == null) {
                event.getHook().sendMessage("Bet amount is a required option.")
                        .setEphemeral(true)
                        .queue();
                activeGames.remove(userId);
                return;
            }
            int betAmount = betOption.getAsInt();
            if (betAmount <= 0) {
                event.getHook().sendMessage("Bet amount must be greater than 0.")
                        .setEphemeral(true)
                        .queue();
                activeGames.remove(userId);
                return;
            }

            // Ensure user exists
            User user = userService.getUserById(userId);
            if (user == null) {
                event.getHook().sendMessage("Could not find your account. Please log in to the web application first.")
                        .setEphemeral(true)
                        .queue();
                activeGames.remove(userId);
                return;
            }

            // Deduct bet atomically
            boolean deducted = userService.deductCreditsIfSufficient(userId, betAmount);
            if (!deducted) {
                Integer credits = user.getCredits() == null ? 0 : user.getCredits();
                event.getHook().sendMessage("You don't have enough credits! You have " + credits + " credits but tried to bet " + betAmount + ".")
                        .setEphemeral(true)
                        .queue();
                activeGames.remove(userId);
                return;
            }

            // Log game start
            try {
                String seed = secureRandomService.generateRollSeed();
                CreateAuditDTO startAudit = CreateAuditDTO.builder()
                        .userId(userId)
                        .action("SLOTS_START")
                        .entityType("USER_CREDITS")
                        .entityId(userId)
                        .description("Slots game started (bet: " + betAmount + ")")
                        .severity(betAmount > 1000 ? AuditSeverity.WARNING : AuditSeverity.INFO)
                        .category(AuditCategory.FINANCIAL)
                        .details(String.format("{\"game\":\"slots\",\"bet\":%d,\"seed\":\"%s\"}", betAmount, seed))
                        .source("DISCORD_BOT")
                        .build();
                auditService.createSystemAuditEntry(startAudit);
            } catch (Exception e) {
                logger.warn("Failed to create SLOTS_START audit entry for user {}: {}", userId, e.getMessage());
            }

            // Role multiplier
            DiscordBotSettings settings = discordBotSettingsService.getDiscordBotSettings();
            Member member = event.getMember();
            double roleMultiplier = userService.getUserHighestMultiplier(member, settings);

            // Prepare final result now (deterministic for this game run)
            int[] finalIdx = new int[]{
                    secureRandomService.getSecureInt(REEL.length),
                    secureRandomService.getSecureInt(REEL.length),
                    secureRandomService.getSecureInt(REEL.length)
            };

            // Send initial message and animate
            String initial = formatReelWithPipes(randomIndices());
            event.getHook().sendMessage(initial).queue(
                    (Message msg) -> {
                        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

                        // Schedule interim updates
                        for (int i = 1; i <= MAX_UPDATES; i++) {
                            long delay = UPDATE_INTERVAL_MS * i;
                            boolean isFinal = (i == MAX_UPDATES);

                            scheduler.schedule(() -> {
                                try {
                                    if (isFinal) {
                                        // Compute payout
                                        int baseMultiplier = computeBaseMultiplier(finalIdx);
                                        // Amount to credit back after initial deduction
                                        int creditToAdd;
                                        int netPayout; // shown to user (+X or -X)
                                        boolean isWin;

                                        if (baseMultiplier <= 0) {
                                            creditToAdd = 0; // loss, keep the deducted bet
                                            netPayout = betAmount; // show as -bet
                                            isWin = false;
                                        } else {
                                            // Net winnings = bet * (baseMultiplier - 1) scaled by role multiplier
                                            int netWin = (int) Math.floor(betAmount * (baseMultiplier - 1) * roleMultiplier);
                                            creditToAdd = betAmount + netWin; // return stake + winnings
                                            netPayout = netWin;
                                            isWin = netWin > 0;
                                        }

                                        // Update credits atomically if any credit to add
                                        if (creditToAdd > 0) {
                                            try {
                                                userService.updateCreditsAtomic(userId, creditToAdd);
                                            } catch (Exception ce) {
                                                logger.error("Failed to credit winnings for user {}: {}", userId, ce.getMessage());
                                            }
                                        }

                                        // Fetch updated balance
                                        int computedCredits;
                                        try {
                                            User updated = userService.getUserById(userId);
                                            computedCredits = (updated != null && updated.getCredits() != null) ? updated.getCredits() : 0;
                                        } catch (Exception ee) {
                                            logger.warn("Failed to fetch updated user credits for {} after slots", userId);
                                            computedCredits = 0;
                                        }
                                        final int finalCredits = computedCredits;

                                        // Build final embed and combined message (embed only, clear text content)
                                        MessageEmbed embed = buildResultEmbed(
                                                event.getUser().getEffectiveName(),
                                                event.getUser().getEffectiveAvatarUrl(),
                                                finalIdx,
                                                isWin,
                                                netPayout,
                                                betAmount,
                                                finalCredits);
                                        MessageEditData finalMessage = new MessageEditBuilder()
                                                .setContent("")
                                                .setEmbeds(embed)
                                                .build();

                                        // Edit message to show final reels and the result embed atomically
                                        msg.editMessage(finalMessage).queue(
                                                success -> {
                                                    // Create audit entry for result
                                                    try {
                                                        logResultAudit(userId, betAmount, finalIdx, baseMultiplier, roleMultiplier, isWin, netPayout, finalCredits);
                                                    } catch (Exception ae) {
                                                        logger.error("Failed to create SLOTS result audit for {}: {}", userId, ae.getMessage());
                                                    }

                                                    // Cleanup state
                                                    activeGames.remove(userId);
                                                    lastPlayAt.put(userId, System.currentTimeMillis());
                                                },
                                                error -> {
                                                    // Refund on failure to display result
                                                    try {
                                                        if (creditToAdd > 0) {
                                                            // no action; already credited successfully; keep it
                                                        } else {
                                                            // Loss case: return the bet due to display failure
                                                            userService.updateCreditsAtomic(userId, betAmount);
                                                        }
                                                    } catch (Exception re) {
                                                        logger.error("Refund failed after display error for user {}: {}", userId, re.getMessage());
                                                    }
                                                    activeGames.remove(userId);
                                                    lastPlayAt.put(userId, System.currentTimeMillis());
                                                    logger.error("Failed to edit final slots message for user {}: {}", userId, error.getMessage());
                                                }
                                        );

                                        scheduler.shutdown();
                                    } else {
                                        // Interim update with random symbols
                                        msg.editMessage(formatReelWithPipes(randomIndices()))
                                                .queue(
                                                        success -> {},
                                                        error -> logger.warn("Interim edit failed for user {}: {}", userId, error.getMessage())
                                                );
                                    }
                                } catch (Exception stepEx) {
                                    logger.error("Error during slots animation for user {}: {}", userId, stepEx.getMessage());
                                    try {
                                        // Refund original bet on animation failure (if we haven't credited anything)
                                        userService.updateCreditsAtomic(userId, betAmount);
                                    } catch (Exception re) {
                                        logger.error("Refund failed after animation exception for user {}: {}", userId, re.getMessage());
                                    }
                                    activeGames.remove(userId);
                                    lastPlayAt.put(userId, System.currentTimeMillis());
                                    scheduler.shutdown();
                                }
                            }, delay, TimeUnit.MILLISECONDS);
                        }
                    },
                    error -> {
                        // Failed to send initial message; refund bet immediately
                        try {
                            userService.updateCreditsAtomic(userId, betAmount);
                        } catch (Exception re) {
                            logger.error("Refund failed after initial send error for user {}: {}", userId, re.getMessage());
                        }
                        activeGames.remove(userId);
                        logger.error("Failed to send initial slots message for user {}: {}", userId, error.getMessage());
                        event.getHook().sendMessage("An error occurred while starting the slots game. Please try again.")
                                .setEphemeral(true)
                                .queue();
                    }
            );
        } catch (Exception e) {
            logger.error("Error processing /slots for user {}: {}", userId, e.getMessage(), e);
            // Best-effort cleanup and user feedback; bet should already be handled above
            activeGames.remove(userId);
            event.getHook().sendMessage("An error occurred while processing the slots game. Please try again.")
                    .setEphemeral(true)
                    .queue();
        }
    }

    private int[] randomIndices() {
        return new int[]{
                secureRandomService.getSecureInt(REEL.length),
                secureRandomService.getSecureInt(REEL.length),
                secureRandomService.getSecureInt(REEL.length)
        };
    }

    private String formatReel(int[] idx) {
        return "[" + REEL[idx[0]] + "] [" + REEL[idx[1]] + "] [" + REEL[idx[2]] + "]";
    }

    private String formatReelWithPipes(int[] idx) {
        return "[" + REEL[idx[0]] + "] | [" + REEL[idx[1]] + "] | [" + REEL[idx[2]] + "]";
    }

    private int computeBaseMultiplier(int[] idx) {
        boolean allEqual = (idx[0] == idx[1] && idx[1] == idx[2]);
        boolean twoEqual = (idx[0] == idx[1]) || (idx[1] == idx[2]) || (idx[0] == idx[2]);
        if (allEqual) return MULTIPLIER_THREE_MATCH;
        if (twoEqual) return MULTIPLIER_TWO_MATCH;
        return 0;
    }

    private MessageEmbed buildResultEmbed(String userName,
                                          String userAvatarUrl,
                                          int[] finalIdx,
                                          boolean isWin,
                                          int netPayout,
                                          int betAmount,
                                          int currentCredits) {
        String authorText;
        Color color = isWin ? WIN_COLOR : LOSE_COLOR;
        if (isWin) {
            authorText = "You won! 🪙+" + netPayout + " credits";
        } else {
            authorText = "You lost! 🪙-" + betAmount + " credits";
        }

        EmbedBuilder embed = createBaseEmbed(authorText, userAvatarUrl, color);
        embed.addField("Result", formatReel(finalIdx), true);
        embed.setFooter(userName + ", you now have " + currentCredits + " credits", null);
        return embed.build();
    }

    private EmbedBuilder createBaseEmbed(String authorText, String avatarUrl, Color color) {
        EmbedBuilder embed = new EmbedBuilder();
        embed.setAuthor(authorText, null, avatarUrl);
        embed.setColor(color != null ? color : EMBED_COLOR);
        return embed;
    }

    private void logResultAudit(String userId,
                                int betAmount,
                                int[] finalIdx,
                                int baseMultiplier,
                                double roleMultiplier,
                                boolean isWin,
                                int netPayout,
                                int newCredits) {
        try {
            String[] symbolArray = new String[] { REEL[finalIdx[0]], REEL[finalIdx[1]], REEL[finalIdx[2]] };
            String symbols = Arrays.toString(symbolArray);
            String action = isWin ? "SLOTS_WIN" : "SLOTS_LOSS";
            String details = String.format("{\"game\":\"slots\",\"bet\":%d,\"symbols\":\"%s\",\"baseMultiplier\":%d,\"roleMultiplier\":%.2f,\"netChange\":%d,\"newBalance\":%d}",
                    betAmount, symbols, baseMultiplier, roleMultiplier, (isWin ? netPayout : -betAmount), newCredits);

            CreateAuditDTO resultAudit = CreateAuditDTO.builder()
                    .userId(userId)
                    .action(action)
                    .entityType("USER_CREDITS")
                    .entityId(userId)
                    .description(isWin ? ("Slots win: +" + netPayout) : ("Slots loss: -" + betAmount))
                    .severity((betAmount > 1000 || netPayout > 1000) ? AuditSeverity.WARNING : AuditSeverity.INFO)
                    .category(AuditCategory.FINANCIAL)
                    .details(details)
                    .source("DISCORD_BOT")
                    .build();
            auditService.createSystemAuditEntry(resultAudit);
        } catch (Exception e) {
            logger.error("Failed to create slots result audit for user {}: {}", userId, e.getMessage());
        }
    }
} 