package com.app.heartbound.services.discord;

import com.app.heartbound.entities.User;
import com.app.heartbound.services.UserService;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.util.function.Consumer;

/**
 * Service for handling Terms of Service agreement workflow for Discord bot commands.
 * This service ensures that users agree to the Terms of Service before using bot commands.
 */
@Service
public class TermsOfServiceService {

    private static final Logger logger = LoggerFactory.getLogger(TermsOfServiceService.class);

    private final UserService userService;

    public TermsOfServiceService(UserService userService) {
        this.userService = userService;
    }

    /**
     * Requires Terms of Service agreement from the user before proceeding with command execution.
     * If the user already exists in the database, the onAgree callback is called immediately.
     * If the user doesn't exist, a ToS agreement embed is shown with Agree/Disagree buttons.
     *
     * @param event The slash command interaction event
     * @param onAgree Callback function to execute when user agrees (or already exists)
     */
    public void requireAgreement(SlashCommandInteractionEvent event, Consumer<User> onAgree) {
        String userId = event.getUser().getId();
        logger.debug("Checking ToS agreement for user: {}", userId);

        // Check if user already exists in database
        User existingUser = userService.getUserById(userId);
        if (existingUser != null) {
            logger.debug("User {} already exists, proceeding with command", userId);
            // User exists, proceed with the command
            onAgree.accept(existingUser);
            return;
        }

        logger.debug("User {} not found, showing Terms of Service agreement", userId);

        // User doesn't exist, show Terms of Service agreement
        try {
            EmbedBuilder embedBuilder = new EmbedBuilder()
                    .setTitle("Terms of Service")
                    .setDescription("Please agree to the Terms of Service to use the Bot.")
                    .setColor(Color.BLUE);

            // Create unique button IDs for this user
            String agreeButtonId = "tos-agree:" + userId;
            String disagreeButtonId = "tos-disagree:" + userId;

            Button agreeButton = Button.success(agreeButtonId, "Agree");
            Button disagreeButton = Button.danger(disagreeButtonId, "Disagree");

            ActionRow actionRow = ActionRow.of(agreeButton, disagreeButton);

            // Reply with the ToS embed and buttons
            event.replyEmbeds(embedBuilder.build())
                    .addComponents(actionRow)
                    .setEphemeral(true)
                    .queue(
                            success -> logger.debug("ToS agreement embed sent to user: {}", userId),
                            error -> logger.error("Failed to send ToS agreement embed to user {}: {}", userId, error.getMessage())
                    );

        } catch (Exception e) {
            logger.error("Error showing Terms of Service agreement for user {}: {}", userId, e.getMessage(), e);
            
            // Fallback: reply with a simple error message
            try {
                event.reply("❌ An error occurred while displaying the Terms of Service. Please try again later.")
                        .setEphemeral(true)
                        .queue();
            } catch (Exception fallbackError) {
                logger.error("Failed to send fallback error message to user {}: {}", userId, fallbackError.getMessage());
            }
        }
    }

    /**
     * Requires Terms of Service agreement from the user before proceeding with button interaction.
     * If the user already exists in the database, the onAgree callback is called immediately.
     * If the user doesn't exist, a ToS agreement embed is shown with Agree/Disagree buttons.
     * This overload is specifically for button interactions like role assignments.
     *
     * @param event The button interaction event
     * @param originalComponentId The original component ID to resume after agreement
     * @param onAgree Callback function to execute when user agrees (or already exists)
     */
    public void requireAgreement(ButtonInteractionEvent event, String originalComponentId, Consumer<User> onAgree) {
        String userId = event.getUser().getId();
        logger.debug("Checking ToS agreement for user: {} (button interaction)", userId);

        // Check if user already exists in database
        User existingUser = userService.getUserById(userId);
        if (existingUser != null) {
            logger.debug("User {} already exists, proceeding with button action", userId);
            // User exists, proceed with the action
            onAgree.accept(existingUser);
            return;
        }

        logger.debug("User {} not found, showing Terms of Service agreement for button interaction", userId);

        // User doesn't exist, show Terms of Service agreement
        try {
            EmbedBuilder embedBuilder = new EmbedBuilder()
                    .setTitle("Terms of Service")
                    .setDescription("Please agree to the Terms of Service to use the Bot.")
                    .setColor(Color.BLUE);

            // Create unique button IDs for this user, encoding the original component ID
            String agreeButtonId = "tos-role-agree:" + userId + ":" + originalComponentId;
            String disagreeButtonId = "tos-role-disagree:" + userId;

            Button agreeButton = Button.success(agreeButtonId, "Agree");
            Button disagreeButton = Button.danger(disagreeButtonId, "Disagree");

            ActionRow actionRow = ActionRow.of(agreeButton, disagreeButton);

            // Use hook to edit the original deferred response instead of replying
            event.getHook().editOriginalEmbeds(embedBuilder.build())
                    .setComponents(actionRow)
                    .queue(
                            success -> logger.debug("ToS agreement embed sent to user: {} (button interaction)", userId),
                            error -> logger.error("Failed to send ToS agreement embed to user {}: {}", userId, error.getMessage())
                    );

        } catch (Exception e) {
            logger.error("Error showing Terms of Service agreement for user {}: {}", userId, e.getMessage(), e);
            
            // Fallback: edit original with error message
            try {
                event.getHook().editOriginal("❌ An error occurred while displaying the Terms of Service. Please try again later.")
                        .queue();
            } catch (Exception fallbackError) {
                logger.error("Failed to send fallback error message to user {}: {}", userId, fallbackError.getMessage());
            }
        }
    }
} 