package com.app.heartbound.services.discord;

import com.app.heartbound.entities.User;
import com.app.heartbound.entities.Shop;
import com.app.heartbound.services.UserService;
import com.app.heartbound.services.UserInventoryService;
import com.app.heartbound.services.SecureRandomService;
import com.app.heartbound.services.AuditService;
import com.app.heartbound.dto.CreateAuditDTO;
import com.app.heartbound.enums.AuditSeverity;
import com.app.heartbound.enums.AuditCategory;
import com.app.heartbound.repositories.shop.ShopRepository;
import com.app.heartbound.services.shop.ShopService;
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
import com.app.heartbound.entities.ItemInstance;
import com.app.heartbound.repositories.ItemInstanceRepository;
import com.app.heartbound.enums.FishingRodPart;
import java.util.ArrayList;

import java.util.Map;
import java.util.HashMap;

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
    private final UserInventoryService userInventoryService;
    private final SecureRandomService secureRandomService;
    private final AuditService auditService;
    private final DiscordBotSettingsService discordBotSettingsService;
    private final FishCommandListener self; // Self-injection for transactional methods
    private final ItemInstanceRepository itemInstanceRepository;
    private final TermsOfServiceService termsOfServiceService;

    @Value("${discord.main.guild.id}")
    private String mainGuildId;
    
    public FishCommandListener(UserService userService, UserInventoryService userInventoryService, SecureRandomService secureRandomService, AuditService auditService, ShopRepository shopRepository, DiscordBotSettingsService discordBotSettingsService, @Lazy FishCommandListener self, ItemInstanceRepository itemInstanceRepository, @Lazy ShopService shopService, TermsOfServiceService termsOfServiceService) {
        this.userService = userService;
        this.userInventoryService = userInventoryService;
        this.secureRandomService = secureRandomService;
        this.auditService = auditService;
        this.discordBotSettingsService = discordBotSettingsService;
        this.self = self;
        this.itemInstanceRepository = itemInstanceRepository;
        this.termsOfServiceService = termsOfServiceService;
        logger.info("FishCommandListener initialized with secure random and audit service");
    }
    
    /**
     * Check if user has reached fishing limit and is on cooldown
     * @param user The user to check
     * @param settings The Discord bot settings containing fishing configuration
     * @return FishingLimitStatus containing the result and any remaining time
     */
    private FishingLimitStatus checkFishingLimit(User user, DiscordBotSettingsService.FishingSettings settings) {
        int min = settings.getMinCatches();
        int max = settings.getMaxCatches();
        Integer currentFishingLimit = user.getCurrentFishingLimit();

        // A user's limit is invalid if it's null or falls outside the admin-configured range.
        if (currentFishingLimit == null || currentFishingLimit < min || currentFishingLimit > max) {
            int newLimit;
            // If the limit is invalid, start a new session for the user with a new random limit.
            if (min > 0 && max >= min) {
                newLimit = secureRandomService.getSecureInt(max - min + 1) + min;
                logger.info("User {} limit was invalid or null. Assigned new random limit: {}.", user.getId(), newLimit);
            } else {
                newLimit = settings.getDefaultMaxCatches();
                logger.warn("Invalid min/max fishing settings (min: {}, max: {}). Assigning fallback limit {} to user {}.", min, max, newLimit, user.getId());
            }
            user.setCurrentFishingLimit(newLimit);
            
            // Reset their progress for this new session.
            user.setFishCaughtSinceLimit(0);
            return new FishingLimitStatus(false, 0, 0, newLimit);
        }

        int currentCatches = user.getFishCaughtSinceLimit() != null ? user.getFishCaughtSinceLimit() : 0;

        // If user hasn't reached the limit, they can fish.
        // At this point, currentFishingLimit is guaranteed to be non-null.
        if (currentCatches < currentFishingLimit) {
            return new FishingLimitStatus(false, 0, currentCatches, currentFishingLimit);
        }
        
        // User has reached the limit, check cooldown
        LocalDateTime cooldownUntil = user.getFishingLimitCooldownUntil();
        if (cooldownUntil == null) {
            // User has exceeded the limit but no cooldown is set
            // This can happen for legacy users who had high fish counts before the limit system
            // Allow them to fish but set a cooldown after their next successful catch
            logger.info("User {} has {} catches (exceeds limit of {}) but no cooldown set. Allowing fishing.", 
                       user.getId(), currentCatches, currentFishingLimit);
            return new FishingLimitStatus(false, 0, currentCatches, currentFishingLimit);
        }
        
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(cooldownUntil)) {
            // Still on cooldown, calculate remaining time
            long totalRemainingMinutes = ChronoUnit.MINUTES.between(now, cooldownUntil);
            
            return new FishingLimitStatus(true, totalRemainingMinutes, currentCatches, currentFishingLimit);
        } else {
            // Cooldown has expired, reset the cooldown field AND the fish-since-limit count
            user.setFishingLimitCooldownUntil(null);
            user.setFishCaughtSinceLimit(0); // <-- THE FIX
            
            // Generate a new random fishing limit for the user for their next "session"
            int newLimit = secureRandomService.getSecureInt(max - min + 1) + min;
            user.setCurrentFishingLimit(newLimit);

            logger.info("Fishing cooldown expired for user {}. Resetting cooldown and fish-since-limit count. New limit: {}", user.getId(), newLimit);
            // Return a new status with the reset count and new limit
            return new FishingLimitStatus(false, 0, 0, newLimit);
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

    /**
     * Helper class to encapsulate successful catch processing results
     */
    private static class CatchProcessingResult {
        private final StringBuilder message;
        private final String warningMessage;
        
        public CatchProcessingResult(StringBuilder message, String warningMessage) {
            this.message = message;
            this.warningMessage = warningMessage;
        }
        
        public StringBuilder getMessage() { return message; }
        public String getWarningMessage() { return warningMessage; }
    }

    /**
     * Get the fishing rod multiplier from an equipped rod instance
     * @param equippedRodInstance The equipped rod instance (can be null)
     * @return The multiplier value (1.0 if no rod or no multiplier)
     */
    private double getRodMultiplier(ItemInstance equippedRodInstance) {
        if (equippedRodInstance != null) {
            try {
                Shop rod = equippedRodInstance.getBaseItem();
                if (rod.getFishingRodMultiplier() != null && rod.getFishingRodMultiplier() > 1.0) {
                    return rod.getFishingRodMultiplier();
                }
            } catch (Exception e) {
                logger.error("Failed to apply fishing rod multiplier: {}", e.getMessage());
            }
        }
        return 1.0;
    }

    /**
     * Process a successful fishing catch (rare or regular)
     * @param userId The user ID
     * @param user The user entity
     * @param equippedRodInstance The equipped fishing rod instance
     * @param bonuses The part bonuses
     * @param fishEmoji The fish emoji caught
     * @param baseCreditChange The base credit reward
     * @param multiplier The total multiplier to apply
     * @param isRare Whether this is a rare catch
     * @param fishingSettings The fishing settings
     * @param brokenParts List to add broken part names to
     * @return CatchProcessingResult containing the message and optional warning
     */
    private CatchProcessingResult processSuccessfulCatch(
            String userId, 
            User user, 
            ItemInstance equippedRodInstance, 
            PartBonuses bonuses,
            String fishEmoji, 
            int baseCreditChange, 
            double multiplier, 
            boolean isRare,
            DiscordBotSettingsService.FishingSettings fishingSettings,
            List<String> brokenParts) {
        
        int currentCredits = (user.getCredits() == null) ? 0 : user.getCredits();
        StringBuilder message = new StringBuilder();
        String warningMessage = null;
        
        // Calculate final credit change with multiplier
        int finalCreditChange = (int) Math.round(baseCreditChange * multiplier);
        if (multiplier > 1.0) {
            logger.info("Applied {}x fishing rod multiplier for user {}. Original credits: {}, New credits: {}", 
                       multiplier, userId, baseCreditChange, finalCreditChange);
        }
        
        // Build initial message
        message.append("🎣 ");
        if (multiplier > 1.0) {
            DecimalFormat df = new DecimalFormat("0.#");
            message.append("**").append(df.format(multiplier)).append("x** | ");
        } else {
            message.append("| ");
        }
        
        if (isRare) {
            message.append("**WOW!** You caught a rare ").append(fishEmoji);
        } else {
            message.append("You caught ").append(fishEmoji);
        }
        message.append("! +").append(finalCreditChange).append(" 🪙");
        
        // Atomically update credits
        userService.updateCreditsAtomic(userId, finalCreditChange);
        user.setCredits(currentCredits + finalCreditChange);

        // Bonus Loot Chance
        if (bonuses.totalBonusLootChance > 0 && secureRandomService.getSecureDouble() <= (bonuses.totalBonusLootChance / 100.0)) {
            int bonusCredits = 5 + secureRandomService.getSecureInt(11); // 5-15 bonus credits
            userService.updateCreditsAtomic(userId, bonusCredits);
            user.setCredits(currentCredits + finalCreditChange + bonusCredits);
            message.append(" Your reel snagged some extra loot! +").append(bonusCredits).append(" 🪙");
        }

        // Durability and XP Logic
        if (equippedRodInstance != null && equippedRodInstance.getDurability() != null && equippedRodInstance.getDurability() > 0) {
            equippedRodInstance.setDurability(equippedRodInstance.getDurability() - 1);

            for (ItemInstance partInstance : bonuses.getEquippedParts(equippedRodInstance).values()) {
                if (partInstance != null && partInstance.getDurability() != null && partInstance.getDurability() > 0) {
                    // ROD_SHAFT parts have infinite durability and should not lose durability
                    if (partInstance.getBaseItem().getFishingRodPartType() != FishingRodPart.ROD_SHAFT) {
                        partInstance.setDurability(partInstance.getDurability() - 1);
                        if (partInstance.getDurability() == 0) {
                            brokenParts.add(partInstance.getBaseItem().getName());
                        }
                    }
                }
            }

            // XP Gain Logic (25% chance)
            if (secureRandomService.getSecureDouble() <= 0.25) {
                long xpGained = secureRandomService.getSecureInt(5) + 1; // 1-5 XP
                equippedRodInstance.setExperience((equippedRodInstance.getExperience() == null ? 0L : equippedRodInstance.getExperience()) + xpGained);
                userInventoryService.handleRodLevelUp(equippedRodInstance);
                message.append(" +").append(xpGained).append(" XP");
            }

            if (equippedRodInstance.getDurability() <= 0) {
                user.setEquippedFishingRodInstanceId(null);
                message.append("\n\n**Oh no!** Your fishing rod broke and has been unequipped. You'll need to repair it.");
                logger.info("Fishing rod instance {} broke for user {}", equippedRodInstance.getId(), userId);
            }
            itemInstanceRepository.save(equippedRodInstance);
        }

        // Update non-credit user stats
        int oldFishSinceLimit = user.getFishCaughtSinceLimit() != null ? user.getFishCaughtSinceLimit() : 0;
        int newFishCount = (user.getFishCaughtCount() != null ? user.getFishCaughtCount() : 0) + 1;
        int newFishSinceLimit = oldFishSinceLimit + 1;
        user.setFishCaughtCount(newFishCount);
        user.setFishCaughtSinceLimit(newFishSinceLimit);
        
        // Check if user has reached the fishing limit
        int maxCatches = user.getCurrentFishingLimit();
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
                // Prepare a warning to be sent as a followup message
                warningMessage = String.format("You are approaching the fishing limit! **%d/%d**", newFishSinceLimit, maxCatches);
            }
        }
        
        // Create audit entry for catch
        String auditAction = isRare ? "FISHING_RARE_CATCH" : "FISHING_CATCH";
        String catchType = isRare ? "rare" : "regular";
        CreateAuditDTO auditEntry = CreateAuditDTO.builder()
            .userId(userId)
            .action(auditAction)
            .entityType("USER_CREDITS")
            .entityId(userId)
            .description(String.format("Caught %sfish %s and earned %d credits", isRare ? "rare " : "", fishEmoji, finalCreditChange))
            .severity(AuditSeverity.INFO)
            .category(AuditCategory.FINANCIAL)
            .details(String.format("{\"game\":\"fishing\",\"catchType\":\"%s\",\"fish\":\"%s\",\"won\":%d,\"newBalance\":%d,\"fishCaughtCount\":%d,\"multiplier\":%.2f}", 
                catchType, fishEmoji, finalCreditChange, user.getCredits(), newFishCount, multiplier))
            .source("DISCORD_BOT")
            .build();
        
        // Use async audit logging to prevent blocking the Discord response
        createAuditEntryAsync(auditEntry);
        
        logger.debug("User {} fished successfully: +{} credits. New balance: {}", 
                userId, finalCreditChange, user.getCredits());
        
        return new CatchProcessingResult(message, warningMessage);
    }

    private static class PartBonuses {
        double totalBonusLootChance = 0.0;
        double totalRarityChanceIncrease = 0.0;
        double totalMultiplierIncrease = 0.0;
        double totalNegationChance = 0.0;

        public Map<FishingRodPart, ItemInstance> getEquippedParts(ItemInstance rodInstance) {
            Map<FishingRodPart, ItemInstance> equippedParts = new HashMap<>();
            if (rodInstance != null) {
                equippedParts.put(FishingRodPart.ROD_SHAFT, rodInstance.getEquippedRodShaft());
                equippedParts.put(FishingRodPart.REEL, rodInstance.getEquippedReel());
                equippedParts.put(FishingRodPart.HOOK, rodInstance.getEquippedHook());
                equippedParts.put(FishingRodPart.FISHING_LINE, rodInstance.getEquippedFishingLine());
                equippedParts.put(FishingRodPart.GRIP, rodInstance.getEquippedGrip());
            }
            return equippedParts;
        }
    }

    private PartBonuses getPartBonuses(ItemInstance rodInstance) {
        PartBonuses bonuses = new PartBonuses();
        if (rodInstance == null) {
            return bonuses;
        }

        Map<FishingRodPart, ItemInstance> equippedParts = bonuses.getEquippedParts(rodInstance);

        for (ItemInstance partInstance : equippedParts.values()) {
            if (partInstance != null) {
                // Backward compatibility for parts without durability
                if (partInstance.getDurability() == null) {
                    Shop basePart = partInstance.getBaseItem();
                    if (basePart.getMaxDurability() != null) {
                        partInstance.setDurability(basePart.getMaxDurability());
                        itemInstanceRepository.save(partInstance);
                    }
                }

                if (partInstance.getDurability() != null && partInstance.getDurability() > 0) {
                    Shop part = partInstance.getBaseItem();
                    if (part.getBonusLootChance() != null) {
                        bonuses.totalBonusLootChance += part.getBonusLootChance();
                    }
                    if (part.getRarityChanceIncrease() != null) {
                        bonuses.totalRarityChanceIncrease += part.getRarityChanceIncrease();
                    }
                    if (part.getMultiplierIncrease() != null) {
                        bonuses.totalMultiplierIncrease += part.getMultiplierIncrease();
                    }
                    if (part.getNegationChance() != null) {
                        bonuses.totalNegationChance += part.getNegationChance();
                    }
                }
            }
        }
        return bonuses;
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
        
        // Require Terms of Service agreement before proceeding
        termsOfServiceService.requireAgreement(event, user -> {
            // Defer reply to prevent timeout
            event.deferReply().queue(); 

            // Use the self-injected proxy to call the transactional method
            self.handleFishCommand(event);
        });
    }

    @Transactional
    public void handleFishCommand(@Nonnull SlashCommandInteractionEvent event) {
        String userId = event.getUser().getId();
        
        try {
            // Fetch cached fishing settings for performance (avoids database call per command)
            DiscordBotSettingsService.FishingSettings fishingSettings = discordBotSettingsService.getCachedFishingSettings();
            String warningMessage = null; // To be sent as a followup message
            List<String> brokenParts = new ArrayList<>();
            
            // Fetch the user from the database with a lock
            // Note: User is guaranteed to exist due to Terms of Service agreement check
            User user = userService.getUserByIdWithLock(userId);

            // New Fishing Rod Logic
            ItemInstance equippedRodInstance = null;
            if (user.getEquippedFishingRodInstanceId() != null) {
                equippedRodInstance = itemInstanceRepository.findById(user.getEquippedFishingRodInstanceId()).orElse(null);
                if (equippedRodInstance == null) {
                    logger.warn("User {} has an equipped rod instance ID ({}) that does not exist. Unequipping.", userId, user.getEquippedFishingRodInstanceId());
                    user.setEquippedFishingRodInstanceId(null);
                }
            }

            PartBonuses bonuses = getPartBonuses(equippedRodInstance);

            if (equippedRodInstance != null) {
                // Backward Compatibility: Initialize durability for legacy rods
                if (equippedRodInstance.getDurability() == null) {
                    Shop baseRod = equippedRodInstance.getBaseItem();
                    if (baseRod.getMaxDurability() != null) {
                        equippedRodInstance.setDurability(baseRod.getMaxDurability());
                        equippedRodInstance.setExperience(0L); // Initialize XP as well
                        itemInstanceRepository.save(equippedRodInstance);
                        logger.info("Initialized durability for legacy rod instance {} for user {}", equippedRodInstance.getId(), userId);
                    }
                }

                // Initialize durability for legacy parts
                for (ItemInstance partInstance : bonuses.getEquippedParts(equippedRodInstance).values()) {
                    if (partInstance != null && partInstance.getDurability() == null) {
                        Shop basePart = partInstance.getBaseItem();
                        // ROD_SHAFT parts have infinite durability and don't need durability initialization
                        if (basePart.getMaxDurability() != null && basePart.getFishingRodPartType() != FishingRodPart.ROD_SHAFT) {
                            partInstance.setDurability(basePart.getMaxDurability());
                            itemInstanceRepository.save(partInstance);
                            logger.info("Initialized durability for legacy part instance {} for user {}", partInstance.getId(), userId);
                        }
                    }
                }

                // Durability Check
                if (equippedRodInstance.getDurability() != null && equippedRodInstance.getDurability() <= 0) {
                    event.getHook().sendMessage("🎣 | Your equipped fishing rod is broken and needs to be repaired before you can fish again.").queue();
                    return;
                }
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
            double multiplier = getRodMultiplier(equippedRodInstance) + bonuses.totalMultiplierIncrease;
            
            double effectiveRareFishChance = RARE_FISH_CHANCE + (bonuses.totalRarityChanceIncrease / 100.0);

            if (roll <= effectiveRareFishChance) {
                // 5% chance: rare fish
                String fishEmoji = RARE_CATCHES.get(secureRandomService.getSecureInt(RARE_CATCHES.size()));
                int baseCreditChange = 50 + secureRandomService.getSecureInt(21); // 50-70 range for rare catches

                                 CatchProcessingResult result = processSuccessfulCatch(userId, user, equippedRodInstance, bonuses, fishEmoji, baseCreditChange, multiplier, true, fishingSettings, brokenParts);
                 message.append(result.getMessage());
                 warningMessage = result.getWarningMessage();
                
            } else if (roll <= SUCCESS_CHANCE) {
                // 75% chance: regular fish (80% - 5% = 75%)
                String fishEmoji = REGULAR_FISH.get(secureRandomService.getSecureInt(REGULAR_FISH.size()));
                int baseCreditChange = secureRandomService.getSecureInt(20) + 1;

                                 CatchProcessingResult result = processSuccessfulCatch(userId, user, equippedRodInstance, bonuses, fishEmoji, baseCreditChange, multiplier, false, fishingSettings, brokenParts);
                 message.append(result.getMessage());
                 warningMessage = result.getWarningMessage();
                
            } else {
                // Failure logic
                double negationRoll = secureRandomService.getSecureDouble();
                if (bonuses.totalNegationChance > 0 && negationRoll <= (bonuses.totalNegationChance / 100.0)) {
                    message.append("🎣 | A crab tried to snip you, but your grip helped you get away safely!");
                    // Create audit entry for negated failure
                    CreateAuditDTO auditEntry = CreateAuditDTO.builder()
                        .userId(userId)
                        .action("FISHING_FAILURE_NEGATED")
                        .entityType("USER_CREDITS")
                        .entityId(userId)
                        .description("Fishing failure was negated by equipped gear.")
                        .severity(AuditSeverity.INFO)
                        .category(AuditCategory.SYSTEM)
                        .details(String.format("{\"game\":\"fishing\",\"catchType\":\"negated_failure\",\"negationChance\":%.2f}", bonuses.totalNegationChance))
                        .source("DISCORD_BOT")
                        .build();
                    createAuditEntryAsync(auditEntry);
                    logger.debug("User {}'s fishing failure was negated.", userId);
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
                        user.setCredits(currentCredits - creditChange);
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
            }
            
            // Final save operation for non-credit user stats
            userService.updateUser(user);
            
            event.getHook().sendMessage(message.toString()).queue();

            // Send a followup message if a warning was generated
            if (warningMessage != null) {
                event.getHook().sendMessage(warningMessage).setEphemeral(true).queue();
            }

            if (!brokenParts.isEmpty()) {
                String brokenPartsMessage;
                if (brokenParts.size() == 1) {
                    brokenPartsMessage = String.format("Your **%s** has broken! Repair it in your inventory!", brokenParts.get(0));
                } else {
                    String lastPart = brokenParts.remove(brokenParts.size() - 1);
                    String otherParts = String.join("**, **", brokenParts);
                    brokenPartsMessage = String.format("Your **%s** and **%s** have broken! Repair them in your inventory!", otherParts, lastPart);
                }
                event.getHook().sendMessage(brokenPartsMessage).setEphemeral(true).queue();
            }
            
        } catch (Exception e) {
            logger.error("An unexpected error occurred in /fish command for user {}: {}", userId, e.getMessage(), e);
            event.getHook().sendMessage("An error occurred while fishing. Please try again later.").setEphemeral(true).queue();
        }
    }
} 