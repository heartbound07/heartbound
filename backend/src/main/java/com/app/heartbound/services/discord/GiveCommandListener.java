package com.app.heartbound.services.discord;

import com.app.heartbound.entities.User;
import com.app.heartbound.services.UserService;
import com.app.heartbound.config.CacheConfig;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class GiveCommandListener extends ListenerAdapter {
    
    private static final Logger logger = LoggerFactory.getLogger(GiveCommandListener.class);
    private static final Color SUCCESS_COLOR = new Color(34, 197, 94); // Green
    private static final Color ERROR_COLOR = new Color(239, 68, 68); // Red
    private static final int COOLDOWN_SECONDS = 30; // 30 second cooldown
    
    // Discord role IDs for authorization (hardcoded as per existing pattern)
    private static final String ADMIN_ROLE_ID = "1173102438694264883";
    // Add other authorized role IDs here if needed (e.g., STAFF role)
    private static final String[] AUTHORIZED_ROLE_IDS = {ADMIN_ROLE_ID};
    
    // Track user cooldowns - userId -> lastGiveCommandTimestamp
    private final ConcurrentHashMap<String, Instant> userCooldowns = new ConcurrentHashMap<>();
    
    private final UserService userService;
    private final CacheConfig cacheConfig;
    
    @Autowired
    public GiveCommandListener(UserService userService, CacheConfig cacheConfig) {
        this.userService = userService;
        this.cacheConfig = cacheConfig;
        logger.info("GiveCommandListener initialized");
    }
    
    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("give")) {
            return; // Not our command
        }
        
        logger.info("Give command received from user: {}", event.getUser().getId());
        
        // Acknowledge the interaction immediately to prevent timeout (public response)
        event.deferReply().queue();
        
        try {
            String giverUserId = event.getUser().getId();
            
            // Check cooldown first
            Instant now = Instant.now();
            if (userCooldowns.containsKey(giverUserId)) {
                Instant lastGiveTime = userCooldowns.get(giverUserId);
                long secondsElapsed = ChronoUnit.SECONDS.between(lastGiveTime, now);
                
                if (secondsElapsed < COOLDOWN_SECONDS) {
                    long timeRemaining = COOLDOWN_SECONDS - secondsElapsed;
                    event.getHook().editOriginal("⏱️ You need to wait " + timeRemaining + " seconds before using /give again!")
                            .queue();
                    logger.debug("User {} attempted to use /give while on cooldown. Remaining: {}s", giverUserId, timeRemaining);
                    return;
                }
            }
            
            // Security check - verify permissions
            if (!hasPermission(event)) {
                event.getHook().editOriginal("❌ You do not have permission to use this command. Only administrators can give credits to users.").queue();
                logger.warn("User {} attempted to use /give without required permissions", giverUserId);
                return;
            }
            
            // Check if the command user is registered in the database
            User giverUser = userService.getUserById(giverUserId);
            if (giverUser == null) {
                event.getHook().editOriginal("❌ Your account was not found in the database. Please log in to the web application first.").queue();
                logger.warn("Command user {} not found in database when using /give", giverUserId);
                return;
            }
            
            // Get command options
            OptionMapping userOption = event.getOption("user");
            OptionMapping amountOption = event.getOption("amount");
            
            if (userOption == null || amountOption == null) {
                event.getHook().editOriginal("❌ Both user and amount parameters are required.").queue();
                return;
            }
            
            // Get the target user and amount - using descriptive variable name to distinguish from entity User
            var targetDiscordUser = userOption.getAsUser();
            int amount = amountOption.getAsInt();
            String targetUserId = targetDiscordUser.getId();
            
            // Validation checks
            if (amount <= 10) {
                event.getHook().editOriginal("❌ The amount must be greater than 10 credits.").queue();
                logger.debug("User {} attempted to give invalid amount: {}", giverUserId, amount);
                return;
            }
            
            if (targetUserId.equals(giverUserId)) {
                event.getHook().editOriginal("❌ You cannot give credits to yourself.").queue();
                logger.debug("User {} attempted to give credits to themselves", giverUserId);
                return;
            }
            
            // Fetch the recipient user from the database
            User recipientUser = userService.getUserById(targetUserId);
            if (recipientUser == null) {
                event.getHook().editOriginal("❌ User not found in the database. The recipient must have logged into the web application at least once.").queue();
                logger.warn("Target user {} not found in database when giving credits", targetUserId);
                return;
            }
            
            // Check if giver has enough credits
            Integer giverCredits = giverUser.getCredits();
            int currentGiverCredits = (giverCredits != null) ? giverCredits : 0;
            
            if (currentGiverCredits < amount) {
                event.getHook().editOriginal(String.format("❌ You don't have enough credits! You have **🪙 %d** credits but need **🪙 %d** to give.", currentGiverCredits, amount)).queue();
                logger.debug("User {} attempted to give {} credits but only has {}", giverUserId, amount, currentGiverCredits);
                return;
            }
            
            // Deduct credits from giver
            int newGiverCredits = currentGiverCredits - amount;
            giverUser.setCredits(newGiverCredits);
            
            // Add credits to recipient
            Integer currentRecipientCredits = recipientUser.getCredits();
            int newRecipientCredits = (currentRecipientCredits != null ? currentRecipientCredits : 0) + amount;
            recipientUser.setCredits(newRecipientCredits);
            
            // Save both users
            userService.updateUser(giverUser);
            userService.updateUser(recipientUser);
            
            // Invalidate both users' profile caches to ensure fresh data
            cacheConfig.invalidateUserProfileCache(giverUserId);
            cacheConfig.invalidateUserProfileCache(targetUserId);
            
            // Update cooldown timestamp for successful command
            userCooldowns.put(giverUserId, now);
            
            // Send success message with bold formatting
            String successMessage = String.format("You have successfully given **🪙 %d** credits to **%s**", 
                amount, targetDiscordUser.getAsMention());
            
            event.getHook().editOriginal(successMessage).queue();
            
            logger.info("User {} successfully gave {} credits to user {}. Giver new balance: {}, Recipient new balance: {}", 
                giverUserId, amount, targetUserId, newGiverCredits, newRecipientCredits);
            
        } catch (Exception e) {
            logger.error("Error processing /give command for user {}", event.getUser().getId(), e);
            event.getHook().editOriginal("❌ An error occurred while processing the credit transfer. Please try again later.").queue();
        }
    }
    
    /**
     * Check if the user has permission to use the give command.
     * Only users with specific Discord roles (like ADMINISTRATOR) can use this command.
     */
    private boolean hasPermission(SlashCommandInteractionEvent event) {
        Member member = event.getMember();
        Guild guild = event.getGuild();
        
        if (guild == null || member == null) {
            return false;
        }
        
        // Check if user has any of the authorized roles
        for (String roleId : AUTHORIZED_ROLE_IDS) {
            Role requiredRole = guild.getRoleById(roleId);
            if (requiredRole != null && member.getRoles().contains(requiredRole)) {
                return true;
            }
        }
        
        return false;
    }
} 