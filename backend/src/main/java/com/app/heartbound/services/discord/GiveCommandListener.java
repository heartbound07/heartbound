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

@Component
public class GiveCommandListener extends ListenerAdapter {
    
    private static final Logger logger = LoggerFactory.getLogger(GiveCommandListener.class);
    private static final Color SUCCESS_COLOR = new Color(34, 197, 94); // Green
    private static final Color ERROR_COLOR = new Color(239, 68, 68); // Red
    
    // Discord role IDs for authorization (hardcoded as per existing pattern)
    private static final String ADMIN_ROLE_ID = "1173102438694264883";
    // Add other authorized role IDs here if needed (e.g., STAFF role)
    private static final String[] AUTHORIZED_ROLE_IDS = {ADMIN_ROLE_ID};
    
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
            // Security check - verify permissions
            if (!hasPermission(event)) {
                event.getHook().editOriginal("❌ You do not have permission to use this command. Only administrators can give credits to users.").queue();
                logger.warn("User {} attempted to use /give without required permissions", event.getUser().getId());
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
            String giverUserId = event.getUser().getId();
            
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
            
            // Update the recipient's credits
            Integer currentCredits = recipientUser.getCredits();
            int newCredits = (currentCredits != null ? currentCredits : 0) + amount;
            recipientUser.setCredits(newCredits);
            
            // Save the updated user
            userService.updateUser(recipientUser);
            
            // Invalidate the user's profile cache to ensure fresh data
            cacheConfig.invalidateUserProfileCache(targetUserId);
            
            // Send success message with bold formatting
            String successMessage = String.format("You have successfully given **🪙 %d** credits to **%s**", 
                amount, targetDiscordUser.getAsMention());
            
            event.getHook().editOriginal(successMessage).queue();
            
            logger.info("User {} successfully gave {} credits to user {}. New balance: {}", 
                giverUserId, amount, targetUserId, newCredits);
            
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