package com.app.heartbound.services.discord;

import com.app.heartbound.entities.User;
import com.app.heartbound.services.UserService;

import javax.annotation.Nonnull;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.awt.Color;

@Component
public class CreditsCommandListener extends ListenerAdapter {
    
    private static final Logger logger = LoggerFactory.getLogger(CreditsCommandListener.class);
    private static final Color EMBED_COLOR = new Color(88, 101, 242); // Discord Blurple
    
    private final UserService userService;
    
    public CreditsCommandListener(UserService userService) {
        this.userService = userService;
        logger.info("CreditsCommandListener initialized");
    }
    
    @Override
    public void onSlashCommandInteraction(@Nonnull SlashCommandInteractionEvent event) {
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
            
            // Create embed for better visual presentation
            EmbedBuilder embed = new EmbedBuilder()
                .setAuthor(event.getUser().getName(), null, event.getUser().getEffectiveAvatarUrl())
                .setTitle("Balance")
                .setDescription(String.format("You have %d credits", currentCredits))
                .setColor(EMBED_COLOR);
            
            // Send the embed response
            event.getHook().editOriginalEmbeds(embed.build()).queue();
            
            logger.debug("Credits information sent to user {}: {} credits", userId, currentCredits);
            
        } catch (Exception e) {
            logger.error("Error processing /credits command for user {}", event.getUser().getId(), e);
            event.getHook().editOriginal("An error occurred while fetching your credits.").queue();
        }
    }
} 