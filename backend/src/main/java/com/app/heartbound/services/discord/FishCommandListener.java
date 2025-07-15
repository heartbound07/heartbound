package com.app.heartbound.services.discord;

import com.app.heartbound.entities.User;
import com.app.heartbound.entities.Shop;
import com.app.heartbound.services.UserService;
import com.app.heartbound.services.SecureRandomService;
import com.app.heartbound.services.AuditService;
import com.app.heartbound.dto.CreateAuditDTO;
import com.app.heartbound.enums.AuditSeverity;
import com.app.heartbound.enums.AuditCategory;
import com.app.heartbound.repositories.shop.ShopRepository;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class FishCommandListener extends ListenerAdapter {
    
    private static final Logger logger = LoggerFactory.getLogger(FishCommandListener.class);
    private static final int COOLDOWN_SECONDS = 5;
    private static final double SUCCESS_CHANCE = 0.8; // 80% total success rate
    private static final double RARE_FISH_CHANCE = 0.05; // 5% chance for rare fish
    
    // Fishing emojis with their Unicode representations
    private static final List<String> FISH_EMOJIS = Arrays.asList(
            "🐟", // :fish:
            "🐠", // :tropical_fish:
            "🐡", // :blowfish:
            "🪼", // :jellyfish:
            "🦈", // :shark:
            "🦦", // :otter:
            "🦐"  // :shrimp:
    );
    
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
    
    // Track user cooldowns - userId -> lastFishingTimestamp
    private final ConcurrentHashMap<String, Instant> userCooldowns = new ConcurrentHashMap<>();
    
    private final UserService userService;
    private final SecureRandomService secureRandomService;
    private final AuditService auditService;
    private final ShopRepository shopRepository;

    @Value("${discord.main.guild.id}")
    private String mainGuildId;
    
    @Autowired
    public FishCommandListener(UserService userService, SecureRandomService secureRandomService, AuditService auditService, ShopRepository shopRepository) {
        this.userService = userService;
        this.secureRandomService = secureRandomService;
        this.auditService = auditService;
        this.shopRepository = shopRepository;
        logger.info("FishCommandListener initialized with secure random and audit service");
    }
    
    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("fish")) {
            return; // Not our command
        }
        
        // Guild restriction check
        if (!event.isFromGuild() || !event.getGuild().getId().equals(mainGuildId)) {
            event.reply("This command can only be used in the main Heartbound server.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        String userId = event.getUser().getId();
        logger.info("User {} requested /fish", userId);
        
        // Acknowledge the interaction immediately (public, not ephemeral)
        event.deferReply().queue();
        
        try {
            // Check cooldown
            Instant now = Instant.now();
            if (userCooldowns.containsKey(userId)) {
                Instant lastFishTime = userCooldowns.get(userId);
                long secondsElapsed = ChronoUnit.SECONDS.between(lastFishTime, now);
                
                if (secondsElapsed < COOLDOWN_SECONDS) {
                    long timeRemaining = COOLDOWN_SECONDS - secondsElapsed;
                    event.getHook().sendMessage("You need to wait " + timeRemaining + " seconds before fishing again!")
                            .setEphemeral(true) // Only the user can see this message
                            .queue();
                    return; // Make sure we exit the method completely
                }
            }
            
            // Fetch the user from the database
            User user = userService.getUserById(userId);
            
            if (user == null) {
                logger.warn("User {} not found in database when using /fish", userId);
                event.getHook().sendMessage("Could not find your account. Please log in to the web application first.").queue();
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
            
            StringBuilder message = new StringBuilder("🎣 | ");
            int creditChange;
            
            if (roll <= RARE_FISH_CHANCE) {
                // 5% chance: rare fish
                String fishEmoji = RARE_CATCHES.get(secureRandomService.getSecureInt(RARE_CATCHES.size()));
                int baseCreditChange = 50 + secureRandomService.getSecureInt(21); // 50-70 range for rare catches

                // Apply fishing rod multiplier if equipped
                double multiplier = 1.0;
                int finalCreditChange = baseCreditChange;

                if (user.getEquippedFishingRodId() != null) {
                    try {
                        Optional<Shop> rodOpt = shopRepository.findById(user.getEquippedFishingRodId());
                        if (rodOpt.isPresent()) {
                            Shop rod = rodOpt.get();
                            if (rod.getFishingRodMultiplier() != null && rod.getFishingRodMultiplier() > 1.0) {
                                multiplier = rod.getFishingRodMultiplier();
                                finalCreditChange = (int) Math.round(baseCreditChange * multiplier);
                                logger.info("Applied {}x fishing rod multiplier for user {}. Original credits: {}, New credits: {}", multiplier, userId, baseCreditChange, finalCreditChange);
                            }
                        }
                    } catch (Exception e) {
                        logger.error("Failed to apply fishing rod multiplier for user {}: {}", userId, e.getMessage());
                    }
                }
                
                message.append("**WOW!** You caught a rare ").append(fishEmoji);
                message.append("! +").append(finalCreditChange).append(" 🪙");
                
                // Update user credits and fish count
                user.setCredits(currentCredits + finalCreditChange);
                int newFishCount = (user.getFishCaughtCount() != null ? user.getFishCaughtCount() : 0) + 1;
                user.setFishCaughtCount(newFishCount);
                
                // Save the updated user
                userService.updateUser(user);
                
                // Create audit entry for rare fish catch
                try {
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
                    
                    auditService.createSystemAuditEntry(auditEntry);
                } catch (Exception e) {
                    logger.error("Failed to create audit entry for rare fish catch by user {}: {}", userId, e.getMessage());
                }
                
                logger.debug("User {} fished successfully: +{} credits. New balance: {}", 
                        userId, finalCreditChange, user.getCredits());
                
            } else if (roll <= SUCCESS_CHANCE) {
                // 75% chance: regular fish (80% - 5% = 75%)
                String fishEmoji = REGULAR_FISH.get(secureRandomService.getSecureInt(REGULAR_FISH.size()));
                int baseCreditChange = secureRandomService.getSecureInt(20) + 1;

                // Apply fishing rod multiplier if equipped
                double multiplier = 1.0;
                int finalCreditChange = baseCreditChange;

                if (user.getEquippedFishingRodId() != null) {
                    try {
                        Optional<Shop> rodOpt = shopRepository.findById(user.getEquippedFishingRodId());
                        if (rodOpt.isPresent()) {
                            Shop rod = rodOpt.get();
                            if (rod.getFishingRodMultiplier() != null && rod.getFishingRodMultiplier() > 1.0) {
                                multiplier = rod.getFishingRodMultiplier();
                                finalCreditChange = (int) Math.round(baseCreditChange * multiplier);
                                logger.info("Applied {}x fishing rod multiplier for user {}. Original credits: {}, New credits: {}", multiplier, userId, baseCreditChange, finalCreditChange);
                            }
                        }
                    } catch (Exception e) {
                        logger.error("Failed to apply fishing rod multiplier for user {}: {}", userId, e.getMessage());
                    }
                }
                
                message.append("You caught ").append(fishEmoji);
                message.append("! +").append(finalCreditChange).append(" 🪙");
                
                // Update user credits and fish count
                user.setCredits(currentCredits + finalCreditChange);
                int newFishCount = (user.getFishCaughtCount() != null ? user.getFishCaughtCount() : 0) + 1;
                user.setFishCaughtCount(newFishCount);
                
                // Save the updated user
                userService.updateUser(user);
                
                // Create audit entry for regular fish catch
                try {
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
                    
                    auditService.createSystemAuditEntry(auditEntry);
                } catch (Exception e) {
                    logger.error("Failed to create audit entry for regular fish catch by user {}: {}", userId, e.getMessage());
                }
                
                logger.debug("User {} fished successfully: +{} credits. New balance: {}", 
                        userId, finalCreditChange, user.getCredits());
                
            } else {
                // 20% chance: failure - lose 1-50 credits
                creditChange = secureRandomService.getSecureInt(50) + 1;
                
                // Ensure credits don't go below 0
                if (creditChange > currentCredits) {
                    creditChange = currentCredits;
                }
                
                // Update user credits
                user.setCredits(currentCredits - creditChange);
                
                // Save the updated user
                userService.updateUser(user);
                
                // Only show negative message if they actually lost credits
                if (creditChange > 0) {
                    message.append("You got caught 🦀 and it snipped you! -").append(creditChange).append(" 🪙");
                    
                    // Create audit entry for fishing failure with credit loss
                    try {
                        CreateAuditDTO auditEntry = CreateAuditDTO.builder()
                            .userId(userId)
                            .action("FISHING_FAILURE")
                            .entityType("USER_CREDITS")
                            .entityId(userId)
                            .description(String.format("Got caught by crab and lost %d credits", creditChange))
                            .severity(AuditSeverity.INFO)
                            .category(AuditCategory.FINANCIAL)
                            .details(String.format("{\"game\":\"fishing\",\"catchType\":\"failure\",\"lost\":%d,\"newBalance\":%d}", 
                                creditChange, user.getCredits()))
                            .source("DISCORD_BOT")
                            .build();
                        
                        auditService.createSystemAuditEntry(auditEntry);
                    } catch (Exception e) {
                        logger.error("Failed to create audit entry for fishing failure by user {}: {}", userId, e.getMessage());
                    }
                } else {
                    // Special message for users with 0 credits
                    message.append("You got caught 🦀 but it had mercy on you since you have no credits!");
                }
                
                logger.debug("User {} failed fishing: -{} credits. New balance: {}", 
                        userId, creditChange, user.getCredits());
            }
            
            // Update cooldown timestamp
            userCooldowns.put(userId, now);
            
            // Send the response
            event.getHook().sendMessage(message.toString()).queue();
            
        } catch (Exception e) {
            logger.error("Error processing /fish command for user {}", userId, e);
            event.getHook().sendMessage("An error occurred while trying to fish.").setEphemeral(true).queue();
        }
    }
} 