package com.app.heartbound.services.discord;

import com.app.heartbound.entities.User;
import com.app.heartbound.services.UserService;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CreditsCommandListener extends ListenerAdapter {
    
    private static final Logger logger = LoggerFactory.getLogger(CreditsCommandListener.class);
    
    private final UserService userService;
    
    @Autowired
    public CreditsCommandListener(UserService userService) {
        this.userService = userService;
        logger.info("CreditsCommandListener initialized");
    }
    
    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("credits")) {
            return; // Not our command
        }
        
        logger.info("User {} requested /credits", event.getUser().getId());
        
        // Acknowledge the interaction quickly and make the response ephemeral (only visible to command user)
        event.deferReply(true).queue();
        
        try {
            // Get the Discord user ID
            String userId = event.getUser().getId();
            
            // Fetch the user from the database
            User user = userService.getUserById(userId);
            
            if (user == null) {
                logger.warn("User {} not found in database when requesting credits", userId);
                event.getHook().editOriginal("Could not find your account. Please log in to the web application first.").queue();
                return;
            }
            
            // Get the user's credits (handle null case)
            Integer credits = user.getCredits();
            int currentCredits = (credits == null) ? 0 : credits;
            
            // Construct and send the response
            String message = String.format("You currently have %d credits.", currentCredits);
            event.getHook().editOriginal(message).queue();
            
            logger.debug("Credits information sent to user {}: {} credits", userId, currentCredits);
            
        } catch (Exception e) {
            logger.error("Error processing /credits command for user {}", event.getUser().getId(), e);
            event.getHook().editOriginal("An error occurred while fetching your credits.").queue();
        }
    }
} 