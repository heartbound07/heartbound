package com.app.heartbound.services.discord;

import com.app.heartbound.entities.User;
import com.app.heartbound.entities.Shop;
import com.app.heartbound.services.UserService;
import com.app.heartbound.services.SecureRandomService;
import com.app.heartbound.services.AuditService;
import com.app.heartbound.services.discord.DiscordBotSettingsService;
import com.app.heartbound.entities.DiscordBotSettings;
import com.app.heartbound.dto.CreateAuditDTO;
import com.app.heartbound.enums.AuditSeverity;
import com.app.heartbound.enums.AuditCategory;
import com.app.heartbound.repositories.shop.ShopRepository;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.annotation.Lazy;

import javax.annotation.Nonnull;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;
import java.text.DecimalFormat;
import java.util.concurrent.CompletableFuture;

@Component
public class FishCommandListener extends ListenerAdapter {
    
    private static final Logger logger = LoggerFactory.getLogger(FishCommandListener.class);
    private static final double SUCCESS_CHANCE = 0.8; // 80% total success rate
    private static final double RARE_FISH_CHANCE = 0.05; // 5% chance for rare fish
    

    
    // Rare catches that give bonus credits
    private static final List<String> RARE_CATCHES = Arrays.asList(
            "🦈", // :shark:
            "🦦", // :otter:
            "🪼"  // :jellyfish:
    );
    
    // Regular fish (excluding rare catches)
    private static final List<String> REGULAR_FISH = Arrays.asList(
            "🐟", // :fish:
            "🐠", // :tropical_fish:
            "🐡", // :blowfish:
            "🦐"  // :shrimp:
    );
    
    private final UserService userService;
    private final SecureRandomService secureRandomService;
    private final AuditService auditService;
    private final ShopRepository shopRepository;
    private final DiscordBotSettingsService discordBotSettingsService;
    private final FishCommandListener self; // Self-injection for transactional methods

    @Value("${discord.main.guild.id}")
    private String mainGuildId;
    
    public FishCommandListener(UserService userService, SecureRandomService secureRandomService, AuditService auditService, ShopRepository shopRepository, DiscordBotSettingsService discordBotSettingsService, @Lazy FishCommandListener self) {
        this.userService = userService;
        this.secureRandomService = secureRandomService;
        this.auditService = auditService;
        this.shopRepository = shopRepository;
        this.discordBotSettingsService = discordBotSettingsService;
        this.self = self;
        logger.info("FishCommandListener initialized with secure random and audit service");
    }
    
    /**
     * Check if user has reached fishing limit and is on cooldown
     * @param user The user to check
     * @param settings The Discord bot settings containing fishing configuration
     * @return FishingLimitStatus containing the result and any remaining time
     */
    private FishingLimitStatus checkFishingLimit(User user, DiscordBotSettingsService.FishingSettings settings) {
        int currentCatches = user.getFishCaughtSinceLimit() != null ? user.getFishCaughtSinceLimit() : 0;
        int maxCatches = settings.getMaxCatches();
        
        // If user hasn't reached the limit, they can fish
        if (currentCatches < maxCatches) {
            return new FishingLimitStatus(false, 0, currentCatches, maxCatches);
        }
        
        // User has reached the limit, check cooldown
        LocalDateTime cooldownUntil = user.getFishingLimitCooldownUntil();
        if (cooldownUntil == null) {
            // User has exceeded the limit but no cooldown is set
            // This can happen for legacy users who had high fish counts before the limit system
            // Allow them to fish but set a cooldown after their next successful catch
            logger.info("User {} has {} catches (exceeds limit of {}) but no cooldown set. Allowing fishing.", 
                       user.getId(), currentCatches, maxCatches);
            return new FishingLimitStatus(false, 0, currentCatches, maxCatches);
        }
        
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(cooldownUntil)) {
            // Still on cooldown, calculate remaining time
            long totalRemainingMinutes = ChronoUnit.MINUTES.between(now, cooldownUntil);
            
            return new FishingLimitStatus(true, totalRemainingMinutes, currentCatches, maxCatches);
        } else {
            // Cooldown has expired, reset the cooldown field AND the fish-since-limit count
            user.setFishingLimitCooldownUntil(null);
            user.setFishCaughtSinceLimit(0); // <-- THE FIX
            // userService.updateUser(user); // REMOVED - will be saved in the main transaction
            logger.info("Fishing cooldown expired for user {}. Resetting cooldown and fish-since-limit count.", user.getId());
            // Return a new status with the reset count
            return new FishingLimitStatus(false, 0, 0, maxCatches);
        }
    }
    
    /**
     * Set fishing limit cooldown for user who has reached the maximum catches
     * @param user The user to set cooldown for
     * @param settings The Discord bot settings containing fishing configuration
     */
    private void setFishingLimitCooldown(User user, DiscordBotSettingsService.FishingSettings settings) {
        int cooldownHours = settings.getCooldownHours();
        LocalDateTime cooldownUntil = LocalDateTime.now().plusHours(cooldownHours);
        user.setFishingLimitCooldownUntil(cooldownUntil);
        // userService.updateUser(user); // REMOVED - will be saved in the main transaction
        logger.info("Set fishing limit cooldown for user {} until {}", user.getId(), cooldownUntil);
    }
    
    /**
     * Helper class to represent fishing limit status
     */
    private static class FishingLimitStatus {
        private final boolean onCooldown;
        private final long remainingMinutes;
        private final int currentCatches;
        private final int maxCatches;
        
        public FishingLimitStatus(boolean onCooldown, long remainingMinutes, int currentCatches, int maxCatches) {
            this.onCooldown = onCooldown;
            this.remainingMinutes = remainingMinutes;
            this.currentCatches = currentCatches;
            this.maxCatches = maxCatches;
        }
        
        public boolean isOnCooldown() { return onCooldown; }
        public long getRemainingMinutes() { return remainingMinutes; }
        public int getCurrentCatches() { return currentCatches; }
        public int getMaxCatches() { return maxCatches; }
    }
    
    private double getEquippedRodMultiplier(User user) {
        if (user.getEquippedFishingRodId() != null) {
            try {
                Optional<Shop> rodOpt = shopRepository.findById(user.getEquippedFishingRodId());
                if (rodOpt.isPresent()) {
                    Shop rod = rodOpt.get();
                    if (rod.getFishingRodMultiplier() != null && rod.getFishingRodMultiplier() > 1.0) {
                        return rod.getFishingRodMultiplier();
                    }
                }
            } catch (Exception e) {
                logger.error("Failed to apply fishing rod multiplier for user {}: {}", user.getId(), e.getMessage());
            }
        }
        return 1.0;
    }

    /**
     * Asynchronously creates audit entries for fishing activities to prevent blocking the main command flow
     * @param auditEntry The audit entry to create
     */
    @Async
    private CompletableFuture<Void> createAuditEntryAsync(CreateAuditDTO auditEntry) {
        try {
            auditService.createSystemAuditEntry(auditEntry);
            logger.debug("Async audit entry created successfully for action: {}", auditEntry.getAction());
        } catch (Exception e) {
            logger.error("Failed to create async audit entry for action {}: {}", auditEntry.getAction(), e.getMessage());
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void onSlashCommandInteraction(@Nonnull SlashCommandInteractionEvent event) {
        if (!event.getName().equals("fish")) {
            return; // Not our command
        }
        
        // Defer reply to prevent timeout
        event.deferReply().queue(); 

        // Use the self-injected proxy to call the transactional method
        self.handleFishCommand(event);
    }

    @Transactional
    public void handleFishCommand(@Nonnull SlashCommandInteractionEvent event) {
        String userId = event.getUser().getId();
        
        try {
            // Fetch cached fishing settings for performance (avoids database call per command)
            DiscordBotSettingsService.FishingSettings fishingSettings = discordBotSettingsService.getCachedFishingSettings();
            
            // Fetch the user from the database with a lock
            User user = userService.getUserByIdWithLock(userId);
            
            if (user == null) {
                logger.warn("User {} not found in database when using /fish", userId);
                event.getHook().sendMessage("Could not find your account. Please log in to the web application first.").queue();
                return;
            }
            
            // Check fishing limit and cooldown using cached settings
            FishingLimitStatus limitStatus = checkFishingLimit(user, fishingSettings);
            if (limitStatus.isOnCooldown()) {
                // Silent penalty: Atomically deduct credits from user for attempting to fish on cooldown
                int penaltyCredits = fishingSettings.getPenaltyCredits();
                boolean penaltyApplied = userService.deductCreditsIfSufficient(userId, penaltyCredits);
                
                if (penaltyApplied) {
                    // Create audit entry for the penalty (silent to user) - sanitized details
                    CreateAuditDTO auditEntry = CreateAuditDTO.builder()
                        .userId(userId)
                        .action("FISHING_LIMIT_PENALTY")
                        .entityType("USER_CREDITS")
                        .entityId(userId)
                        .description("Silent credit penalty applied for fishing during cooldown")
                        .severity(AuditSeverity.WARNING)
                        .category(AuditCategory.FINANCIAL)
                        .details(String.format("{\"penalty\":%d,\"cooldownMinutes\":%d}", 
                            penaltyCredits, limitStatus.getRemainingMinutes()))
                        .source("DISCORD_BOT")
                        .build();
                    
                    // Use async audit logging to prevent blocking the Discord response
                    createAuditEntryAsync(auditEntry);
                    logger.info("Applied silent penalty of {} credits to user {} for fishing on cooldown", penaltyCredits, userId);
                }
                
                long hoursRemaining = limitStatus.getRemainingMinutes() / 60;
                long minutesRemaining = limitStatus.getRemainingMinutes() % 60;
                
                String timeMessage;
                if (hoursRemaining > 0) {
                    timeMessage = String.format("%d hours and %d minutes", hoursRemaining, minutesRemaining);
                } else {
                    timeMessage = String.format("%d minutes", minutesRemaining);
                }
                
                String limitMessage = String.format("🎣 | **Fishing Limit Reached!** You've caught %d/%d fish and must wait **%s** before fishing again.", 
                    limitStatus.getCurrentCatches(), limitStatus.getMaxCatches(), timeMessage);
                
                event.getHook().sendMessage(limitMessage).queue();
                logger.info("User {} attempted to fish while on limit cooldown. Remaining: {} minutes", userId, limitStatus.getRemainingMinutes());
                return;
            }
            
            // Get current credits (handle null case)
            Integer credits = user.getCredits();
            int currentCredits = (credits == null) ? 0 : credits;
            
            logger.info("User {} current credits: {}, raw credits value: {}", 
                    userId, currentCredits, credits);
            
            if (currentCredits < 5) { // Require at least 5 credits to fish
                event.getHook().sendMessage("You need at least 5 credits to go fishing!").queue();
                return;
            }
            
            // Determine success or failure
            double roll = secureRandomService.getSecureDouble();
            
            StringBuilder message = new StringBuilder();
            int creditChange;
            double multiplier = getEquippedRodMultiplier(user);
            
            if (roll <= RARE_FISH_CHANCE) {
                // 5% chance: rare fish
                String fishEmoji = RARE_CATCHES.get(secureRandomService.getSecureInt(RARE_CATCHES.size()));
                int baseCreditChange = 50 + secureRandomService.getSecureInt(21); // 50-70 range for rare catches

                int finalCreditChange = (int) Math.round(baseCreditChange * multiplier);
                if (multiplier > 1.0) {
                    logger.info("Applied {}x fishing rod multiplier for user {}. Original credits: {}, New credits: {}", multiplier, userId, baseCreditChange, finalCreditChange);
                }
                
                message.append("🎣 ");
                if (multiplier > 1.0) {
                    DecimalFormat df = new DecimalFormat("0.#");
                    message.append("**").append(df.format(multiplier)).append("x** | ");
                } else {
                    message.append("| ");
                }
                
                message.append("**WOW!** You caught a rare ").append(fishEmoji);
                message.append("! +").append(finalCreditChange).append(" 🪙");
                
                // Atomically update credits
                userService.updateCreditsAtomic(userId, finalCreditChange);

                // Update non-credit user stats
                int oldFishSinceLimit = user.getFishCaughtSinceLimit() != null ? user.getFishCaughtSinceLimit() : 0;
                int newFishCount = (user.getFishCaughtCount() != null ? user.getFishCaughtCount() : 0) + 1;
                int newFishSinceLimit = oldFishSinceLimit + 1;
                user.setFishCaughtCount(newFishCount);
                user.setFishCaughtSinceLimit(newFishSinceLimit);
                
                // Check if user has reached the fishing limit
                int maxCatches = fishingSettings.getMaxCatches();
                int cooldownHours = fishingSettings.getCooldownHours();
                double limitWarningThreshold = fishingSettings.getLimitWarningThreshold();
                
                if (newFishSinceLimit >= maxCatches && user.getFishingLimitCooldownUntil() == null) {
                    setFishingLimitCooldown(user, fishingSettings);
                    
                    // Improved grammar for the cooldown message
                    String hourText = cooldownHours == 1 ? "hour" : "hours";
                    message.append(String.format("\n\n🎯 **Fishing Limit Reached!** You've caught %d/%d fish and must wait **%d %s** before fishing again.", 
                        newFishSinceLimit, maxCatches, cooldownHours, hourText));
                    logger.info("User {} reached fishing limit of {} catches. Cooldown set for {} hours.", userId, maxCatches, cooldownHours);
                } else {
                    int warningMark = (int) (maxCatches * limitWarningThreshold);
                    if (oldFishSinceLimit < warningMark && newFishSinceLimit >= warningMark) {
                        // Send a warning if the user is approaching the limit
                        String warningMessage = String.format("You are approaching the fishing limit! **%d/%d**", newFishSinceLimit, maxCatches);
                        event.getHook().sendMessage(warningMessage).setEphemeral(true).queue();
                    }
                }
                
                // Save the updated user
                // userService.updateUser(user); // REMOVED
                
                // Create audit entry for rare fish catch
                CreateAuditDTO auditEntry = CreateAuditDTO.builder()
                    .userId(userId)
                    .action("FISHING_RARE_CATCH")
                    .entityType("USER_CREDITS")
                    .entityId(userId)
                    .description(String.format("Caught rare fish %s and earned %d credits", fishEmoji, finalCreditChange))
                    .severity(AuditSeverity.INFO)
                    .category(AuditCategory.FINANCIAL)
                    .details(String.format("{\"game\":\"fishing\",\"catchType\":\"rare\",\"fish\":\"%s\",\"won\":%d,\"newBalance\":%d,\"fishCaughtCount\":%d,\"multiplier\":%.2f}", 
                        fishEmoji, finalCreditChange, user.getCredits(), newFishCount, multiplier))
                    .source("DISCORD_BOT")
                    .build();
                
                // Use async audit logging to prevent blocking the Discord response
                createAuditEntryAsync(auditEntry);
                
                logger.debug("User {} fished successfully: +{} credits. New balance: {}", 
                        userId, finalCreditChange, user.getCredits());
                
            } else if (roll <= SUCCESS_CHANCE) {
                // 75% chance: regular fish (80% - 5% = 75%)
                String fishEmoji = REGULAR_FISH.get(secureRandomService.getSecureInt(REGULAR_FISH.size()));
                int baseCreditChange = secureRandomService.getSecureInt(20) + 1;

                int finalCreditChange = (int) Math.round(baseCreditChange * multiplier);
                if (multiplier > 1.0) {
                    logger.info("Applied {}x fishing rod multiplier for user {}. Original credits: {}, New credits: {}", multiplier, userId, baseCreditChange, finalCreditChange);
                }

                message.append("🎣 ");
                if (multiplier > 1.0) {
                    DecimalFormat df = new DecimalFormat("0.#");
                    message.append("**").append(df.format(multiplier)).append("x** | ");
                } else {
                    message.append("| ");
                }
                
                message.append("You caught ").append(fishEmoji);
                message.append("! +").append(finalCreditChange).append(" 🪙");
                
                // Atomically update credits
                userService.updateCreditsAtomic(userId, finalCreditChange);

                // Update non-credit user stats
                int oldFishSinceLimit = user.getFishCaughtSinceLimit() != null ? user.getFishCaughtSinceLimit() : 0;
                int newFishCount = (user.getFishCaughtCount() != null ? user.getFishCaughtCount() : 0) + 1;
                int newFishSinceLimit = oldFishSinceLimit + 1;
                user.setFishCaughtCount(newFishCount);
                user.setFishCaughtSinceLimit(newFishSinceLimit);
                
                // Check if user has reached the fishing limit
                int maxCatches = fishingSettings.getMaxCatches();
                int cooldownHours = fishingSettings.getCooldownHours();
                double limitWarningThreshold = fishingSettings.getLimitWarningThreshold();
                
                if (newFishSinceLimit >= maxCatches && user.getFishingLimitCooldownUntil() == null) {
                    setFishingLimitCooldown(user, fishingSettings);
                    
                    // Improved grammar for the cooldown message
                    String hourText = cooldownHours == 1 ? "hour" : "hours";
                    message.append(String.format("\n\n🎯 **Fishing Limit Reached!** You've caught %d/%d fish and must wait **%d %s** before fishing again.", 
                        newFishSinceLimit, maxCatches, cooldownHours, hourText));
                    logger.info("User {} reached fishing limit of {} catches. Cooldown set for {} hours.", userId, maxCatches, cooldownHours);
                } else {
                    int warningMark = (int) (maxCatches * limitWarningThreshold);
                    if (oldFishSinceLimit < warningMark && newFishSinceLimit >= warningMark) {
                        // Send a warning if the user is approaching the limit
                        String warningMessage = String.format("You are approaching the fishing limit! **%d/%d**", newFishSinceLimit, maxCatches);
                        event.getHook().sendMessage(warningMessage).setEphemeral(true).queue();
                    }
                }
                
                // Save the updated user
                // userService.updateUser(user); // REMOVED
                
                // Create audit entry for regular fish catch
                CreateAuditDTO auditEntry = CreateAuditDTO.builder()
                    .userId(userId)
                    .action("FISHING_CATCH")
                    .entityType("USER_CREDITS")
                    .entityId(userId)
                    .description(String.format("Caught fish %s and earned %d credits", fishEmoji, finalCreditChange))
                    .severity(AuditSeverity.INFO)
                    .category(AuditCategory.FINANCIAL)
                    .details(String.format("{\"game\":\"fishing\",\"catchType\":\"regular\",\"fish\":\"%s\",\"won\":%d,\"newBalance\":%d,\"fishCaughtCount\":%d,\"multiplier\":%.2f}", 
                        fishEmoji, finalCreditChange, user.getCredits(), newFishCount, multiplier))
                    .source("DISCORD_BOT")
                    .build();
                
                // Use async audit logging to prevent blocking the Discord response
                createAuditEntryAsync(auditEntry);
                
                logger.debug("User {} fished successfully: +{} credits. New balance: {}", 
                        userId, finalCreditChange, user.getCredits());
                
            } else {
                // 20% chance: failure - lose 1-50 credits
                creditChange = secureRandomService.getSecureInt(50) + 1;
                
                // Ensure credits don't go below 0
                if (creditChange > currentCredits) {
                    creditChange = currentCredits;
                }
                
                // Atomically update credits
                if (creditChange > 0) {
                    userService.updateCreditsAtomic(userId, -creditChange);
                }
                
                // Only show negative message if they actually lost credits
                if (creditChange > 0) {
                    message.append("🎣 | You got caught 🦀 and it snipped you! -").append(creditChange).append(" 🪙");
                    
                    // Create audit entry for fishing failure with credit loss
                    CreateAuditDTO auditEntry = CreateAuditDTO.builder()
                        .userId(userId)
                        .action("FISHING_FAILURE")
                        .entityType("USER_CREDITS")
                        .entityId(userId)
                        .description(String.format("Got caught by crab and lost %d credits", creditChange))
                        .severity(AuditSeverity.INFO)
                        .category(AuditCategory.FINANCIAL)
                        .details(String.format("{\"game\":\"fishing\",\"catchType\":\"failure\",\"lost\":%d,\"newBalance\":%d}", 
                            creditChange, currentCredits - creditChange))
                        .source("DISCORD_BOT")
                        .build();
                    
                    // Use async audit logging to prevent blocking the Discord response
                    createAuditEntryAsync(auditEntry);
                } else {
                    // Special message for users with 0 credits
                    message.append("🎣 | You got caught 🦀 but it had mercy on you since you have no credits!");
                }
                
                logger.debug("User {} failed fishing: -{} credits. New balance: {}", 
                        userId, creditChange, currentCredits - creditChange);
            }
            
            // Final save operation for non-credit user stats
            userService.updateUser(user);
            
            event.getHook().sendMessage(message.toString()).queue();
            
        } catch (Exception e) {
            logger.error("An unexpected error occurred in /fish command for user {}: {}", userId, e.getMessage(), e);
            event.getHook().sendMessage("An error occurred while fishing. Please try again later.").setEphemeral(true).queue();
        }
    }
} 