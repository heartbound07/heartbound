package com.app.heartbound.services.discord;

import com.app.heartbound.entities.User;
import com.app.heartbound.services.UserService;
import com.app.heartbound.services.SecureRandomService;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
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
    
    @Autowired
    public FishCommandListener(UserService userService, SecureRandomService secureRandomService) {
        this.userService = userService;
        this.secureRandomService = secureRandomService;
        logger.info("FishCommandListener initialized with secure random");
    }
    
    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("fish")) {
            return; // Not our command
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
                creditChange = 50 + secureRandomService.getSecureInt(21); // 50-70 range for rare catches
                
                message.append("**WOW!** You caught a rare ").append(fishEmoji);
                message.append("! +").append(creditChange).append(" 🪙");
                
                // Update user credits
                user.setCredits(currentCredits + creditChange);
                logger.debug("User {} fished successfully: +{} credits. New balance: {}", 
                        userId, creditChange, user.getCredits());
                
            } else if (roll <= SUCCESS_CHANCE) {
                // 75% chance: regular fish (80% - 5% = 75%)
                String fishEmoji = REGULAR_FISH.get(secureRandomService.getSecureInt(REGULAR_FISH.size()));
                creditChange = secureRandomService.getSecureInt(20) + 1;
                
                message.append("You caught ").append(fishEmoji);
                message.append("! +").append(creditChange).append(" 🪙");
                
                // Update user credits
                user.setCredits(currentCredits + creditChange);
                logger.debug("User {} fished successfully: +{} credits. New balance: {}", 
                        userId, creditChange, user.getCredits());
                
            } else {
                // 20% chance: failure - lose 1-50 credits
                creditChange = secureRandomService.getSecureInt(50) + 1;
                
                // Ensure credits don't go below 0
                if (creditChange > currentCredits) {
                    creditChange = currentCredits;
                }
                
                // Only show negative message if they actually lost credits
                if (creditChange > 0) {
                    message.append("You got caught 🦀 and it snipped you! -").append(creditChange).append(" 🪙");
                } else {
                    // Special message for users with 0 credits
                    message.append("You got caught 🦀 but it had mercy on you since you have no credits!");
                }
                
                // Update user credits
                user.setCredits(currentCredits - creditChange);
                logger.debug("User {} failed fishing: -{} credits. New balance: {}", 
                        userId, creditChange, user.getCredits());
            }
            
            // Save the updated user
            userService.updateUser(user);
            
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