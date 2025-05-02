package com.app.heartbound.services.discord;

import com.app.heartbound.entities.User;
import com.app.heartbound.services.UserService;
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
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class FishCommandListener extends ListenerAdapter {
    
    private static final Logger logger = LoggerFactory.getLogger(FishCommandListener.class);
    private static final Random RANDOM = new Random();
    private static final int COOLDOWN_SECONDS = 45;
    private static final double SUCCESS_CHANCE = 0.7; // 70% success rate
    
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
    
    // Track user cooldowns - userId -> lastFishingTimestamp
    private final ConcurrentHashMap<String, Instant> userCooldowns = new ConcurrentHashMap<>();
    
    private final UserService userService;
    
    @Autowired
    public FishCommandListener(UserService userService) {
        this.userService = userService;
        logger.info("FishCommandListener initialized");
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
            boolean isSuccess = RANDOM.nextDouble() <= SUCCESS_CHANCE;
            
            StringBuilder message = new StringBuilder("🎣 | ");
            int creditChange;
            
            if (isSuccess) {
                // Select a random fish emoji
                String fishEmoji = FISH_EMOJIS.get(RANDOM.nextInt(FISH_EMOJIS.size()));
                
                // Determine credit amount (1-100)
                creditChange = RANDOM.nextInt(100) + 1;
                
                // Bonus for rare catches
                if (RARE_CATCHES.contains(fishEmoji)) {
                    // For rare catches, ensure minimum of 50 credits or add 20 bonus credits
                    if (creditChange < 50) {
                        creditChange = 50 + RANDOM.nextInt(51); // 50-100 range for rare catches
                    } else {
                        creditChange += 20; // Add 20 bonus credits
                        if (creditChange > 100) creditChange = 100; // Cap at 100
                    }
                    
                    message.append("**WOW!** You caught a rare ").append(fishEmoji);
                } else {
                    message.append("You caught ").append(fishEmoji);
                }
                
                message.append("! +").append(creditChange).append(" 🪙");
                
                // Update user credits
                user.setCredits(currentCredits + creditChange);
                logger.debug("User {} fished successfully: +{} credits. New balance: {}", 
                        userId, creditChange, user.getCredits());
                
            } else {
                // Failure - lose 1-50 credits
                creditChange = RANDOM.nextInt(50) + 1;
                
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