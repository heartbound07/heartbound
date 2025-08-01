package com.app.heartbound.services.discord;

import com.app.heartbound.entities.User;
import com.app.heartbound.services.UserService;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.awt.Color;

import javax.annotation.Nonnull;

/**
 * Listener for handling Terms of Service button interactions.
 * Processes user agreement or disagreement to the Terms of Service.
 */
@Component
public class TermsOfServiceListener extends ListenerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(TermsOfServiceListener.class);

    private final UserService userService;

    public TermsOfServiceListener(UserService userService) {
        this.userService = userService;
    }

    @Override
    public void onButtonInteraction(@Nonnull ButtonInteractionEvent event) {
        String componentId = event.getComponentId();
        String clickingUserId = event.getUser().getId();

        // Only handle ToS-related button interactions
        if (!componentId.startsWith("tos-agree:") && !componentId.startsWith("tos-disagree:")) {
            return;
        }

        logger.debug("Processing ToS button interaction: {} by user: {}", componentId, clickingUserId);

        try {
            // Extract the target user ID from the component ID
            String targetUserId;
            boolean isAgree;
            
            if (componentId.startsWith("tos-agree:")) {
                targetUserId = componentId.substring("tos-agree:".length());
                isAgree = true;
            } else {
                targetUserId = componentId.substring("tos-disagree:".length());
                isAgree = false;
            }

            // Critical security check: Verify the clicking user matches the target user
            if (!clickingUserId.equals(targetUserId)) {
                logger.warn("Security violation: User {} attempted to interact with ToS button for user {}", 
                    clickingUserId, targetUserId);
                
                event.reply("❌ You can only interact with your own Terms of Service agreement.")
                        .setEphemeral(true)
                        .queue();
                return;
            }

            if (isAgree) {
                handleAgreeButton(event, targetUserId);
            } else {
                handleDisagreeButton(event, targetUserId);
            }

        } catch (Exception e) {
            logger.error("Error processing ToS button interaction for component ID {}: {}", componentId, e.getMessage(), e);
            
            // Send error response
            try {
                event.reply("❌ An error occurred while processing your response. Please try again later.")
                        .setEphemeral(true)
                        .queue();
            } catch (Exception fallbackError) {
                logger.error("Failed to send error response for ToS button interaction: {}", fallbackError.getMessage());
            }
        }
    }

    /**
     * Handles the "Agree" button interaction.
     * Creates a new user in the database and updates the embed.
     */
    private void handleAgreeButton(ButtonInteractionEvent event, String userId) {
        logger.debug("User {} agreed to Terms of Service", userId);

        try {
            // Check if user already exists (race condition protection)
            User existingUser = userService.getUserById(userId);
            if (existingUser != null) {
                logger.debug("User {} already exists during ToS agreement, skipping creation", userId);
                updateEmbedForAgreement(event, true);
                return;
            }

            // Create new user from Discord data
            userService.createUserFromDiscord(event.getUser());
            logger.info("Successfully created new user {} from ToS agreement", userId);

            // Update the embed to show agreement confirmation
            updateEmbedForAgreement(event, true);

        } catch (Exception e) {
            logger.error("Error creating user {} from ToS agreement: {}", userId, e.getMessage(), e);
            
            // Send error response
            event.reply("❌ An error occurred while creating your account. Please try again later.")
                    .setEphemeral(true)
                    .queue();
        }
    }

    /**
     * Handles the "Disagree" button interaction.
     * Sends an ephemeral message and deletes the original embed.
     */
    private void handleDisagreeButton(ButtonInteractionEvent event, String userId) {
        logger.debug("User {} disagreed to Terms of Service", userId);

        try {
            // Send ephemeral disagreement message
            event.reply("You have disagreed to the Terms of Service.")
                    .setEphemeral(true)
                    .queue(
                            success -> {
                                logger.debug("Sent disagreement message to user {}", userId);
                                // Delete the original ToS embed
                                deleteOriginalEmbed(event, userId);
                            },
                            error -> logger.error("Failed to send disagreement message to user {}: {}", userId, error.getMessage())
                    );

        } catch (Exception e) {
            logger.error("Error handling disagreement for user {}: {}", userId, e.getMessage(), e);
        }
    }

    /**
     * Updates the original embed to show agreement confirmation and removes buttons.
     */
    private void updateEmbedForAgreement(ButtonInteractionEvent event, boolean success) {
        try {
            EmbedBuilder embedBuilder = new EmbedBuilder()
                    .setTitle("Terms of Service")
                    .setColor(success ? Color.GREEN : Color.RED);

            if (success) {
                embedBuilder.setDescription("✅ You have agreed to the Terms of Service. Please run the command again.");
            } else {
                embedBuilder.setDescription("❌ An error occurred while processing your agreement.");
            }

            // Update the message, removing the buttons
            event.editMessageEmbeds(embedBuilder.build())
                    .setComponents() // Remove all components (buttons)
                    .queue(
                            updateSuccess -> logger.debug("Updated ToS embed for agreement confirmation"),
                            updateError -> logger.error("Failed to update ToS embed: {}", updateError.getMessage())
                    );

        } catch (Exception e) {
            logger.error("Error updating ToS embed for agreement: {}", e.getMessage(), e);
        }
    }

    /**
     * Deletes the original Terms of Service embed.
     */
    private void deleteOriginalEmbed(ButtonInteractionEvent event, String userId) {
        try {
            event.getMessage().delete().queue(
                    success -> logger.debug("Deleted ToS embed for user {} after disagreement", userId),
                    error -> logger.error("Failed to delete ToS embed for user {}: {}", userId, error.getMessage())
            );
        } catch (Exception e) {
            logger.error("Error deleting ToS embed for user {}: {}", userId, e.getMessage(), e);
        }
    }
} 