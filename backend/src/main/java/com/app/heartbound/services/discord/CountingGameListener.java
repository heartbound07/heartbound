package com.app.heartbound.services.discord;

import com.app.heartbound.services.discord.CountingGameService.CountingResult;
import com.app.heartbound.services.discord.CountingGameService.SaveCountResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.awt.Color;

@Component
@RequiredArgsConstructor
@Slf4j
public class CountingGameListener extends ListenerAdapter {
    
    private final CountingGameService countingGameService;
    
    @Value("${frontend.base.url}")
    private String frontendBaseUrl;
    
    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        // Ignore bot messages
        if (event.getAuthor().isBot()) {
            return;
        }
        
        // Check if this is the counting channel
        if (!countingGameService.isCountingChannel(event.getChannel().getId())) {
            return;
        }
        
        // Check if user is timed out and delete their message
        if (countingGameService.isUserTimedOut(event.getAuthor().getId())) {
            event.getMessage().delete().queue(
                success -> log.debug("Deleted message from timed out user: {}", event.getAuthor().getId()),
                error -> log.warn("Failed to delete message from timed out user: {}", error.getMessage())
            );
            return;
        }
        
        String content = event.getMessage().getContentRaw().trim();
        
        // Check if message is a valid number
        if (!CountingGameService.isValidNumber(content)) {
            return; // Ignore non-number messages in counting channel
        }
        
        try {
            int attemptedNumber = Integer.parseInt(content);
            String userId = event.getAuthor().getId();
            
            log.debug("Processing counting attempt: user={}, number={}", userId, attemptedNumber);
            
            // Process the count attempt
            CountingResult result = countingGameService.processCount(userId, attemptedNumber);
            
            // Handle the result
            handleCountingResult(event, result, attemptedNumber, userId);
            
        } catch (NumberFormatException e) {
            log.debug("Invalid number format in counting channel: {}", content);
        } catch (Exception e) {
            log.error("Error processing counting attempt by user {}: {}", event.getAuthor().getId(), e.getMessage(), e);
        }
    }
    
    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        // Check if this is a save count button in the counting channel
        if (!countingGameService.isCountingChannel(event.getChannel().getId())) {
            return;
        }
        
        if (event.getComponentId().equals("save_count")) {
            handleSaveCountButton(event);
        }
    }
    
    private void handleSaveCountButton(ButtonInteractionEvent event) {
        String userId = event.getUser().getId();
        
        try {
            SaveCountResult result = countingGameService.saveCount(userId);
            
            switch (result.getType()) {
                case SUCCESS:
                    // Acknowledge button click and send success message
                    int nextNumber = result.getSavedCount() + 1;
                    event.reply(String.format("✅ <@%s> saved the count at **%d** for **%d** credits! The next number is **%d**.",
                            userId, result.getSavedCount(), result.getCostPaid(), nextNumber))
                            .setEphemeral(false)
                            .queue();
                    
                    // Disable the button since it was used
                    event.editComponents().queue();
                    
                    log.info("User {} successfully saved count at {} for {} credits", userId, result.getSavedCount(), result.getCostPaid());
                    break;
                    
                case USER_NOT_FOUND:
                    event.reply(String.format("❌ You are not a current user with the bot! Please ensure you are signed up via %s", frontendBaseUrl))
                            .setEphemeral(true)
                            .queue();
                    break;
                    
                case INSUFFICIENT_CREDITS:
                    event.reply(String.format("❌ You need **%d** credits to save this count, but you only have **%d** credits.",
                            result.getRequiredCredits(), result.getUserCredits()))
                            .setEphemeral(true)
                            .queue();
                    break;
                    
                case NOTHING_TO_SAVE:
                    event.reply("❌ There's nothing to save right now. The count is not at 0.")
                            .setEphemeral(true)
                            .queue();
                    break;
                    
                case NO_RECENT_FAILURE:
                    event.reply("❌ There's no recent failure to save. This save opportunity has expired.")
                            .setEphemeral(true)
                            .queue();
                    break;
                    
                case GAME_DISABLED:
                    event.reply("❌ The counting game is currently disabled.")
                            .setEphemeral(true)
                            .queue();
                    break;
            }
        } catch (Exception e) {
            log.error("Error handling save count button for user {}: {}", userId, e.getMessage(), e);
            event.reply("❌ An error occurred while trying to save the count. Please try again.")
                    .setEphemeral(true)
                    .queue();
        }
    }
    
    private void handleCountingResult(MessageReceivedEvent event, CountingResult result, 
                                     int attemptedNumber, String userId) {
        Message message = event.getMessage();
        
        switch (result.getType()) {
            case CORRECT:
                // React with green checkmark for correct count
                message.addReaction(Emoji.fromUnicode("✅")).queue(
                    success -> log.debug("Added checkmark reaction for correct count by user {}", userId),
                    error -> log.warn("Failed to add checkmark reaction: {}", error.getMessage())
                );
                break;
                
            case RESTART_DELAYED:
                // React with clock emoji and inform about delay
                message.addReaction(Emoji.fromUnicode("⏰")).queue();
                
                String delayMsg = String.format("⏰ Counting is paused for **%d** more seconds after the recent failure.", 
                    result.getDelaySeconds());
                
                event.getChannel().sendMessage(delayMsg).queue(
                    success -> log.debug("Sent restart delay message"),
                    error -> log.warn("Failed to send restart delay message: {}", error.getMessage())
                );
                break;
                
            case WRONG_NUMBER:
                // React with X and send failure message with save button
                message.addReaction(Emoji.fromUnicode("❌")).queue();
                
                String wrongNumberMsg = String.format("<@%s> RUINED IT AT %d!! The next number was %d.", 
                    userId, result.getCurrentCount(), result.getExpectedNumber());
                
                event.getChannel().sendMessage(wrongNumberMsg).queue(
                    success -> {
                        sendLivesOrTimeoutEmbed(event, result);
                        sendSaveButton(event, result.getSaveCost());
                    },
                    error -> log.warn("Failed to send wrong number message: {}", error.getMessage())
                );
                break;
                
            case WRONG_NUMBER_WARNING:
                // React with warning emoji and send warning message (no penalty)
                message.addReaction(Emoji.fromUnicode("⚠️")).queue();
                
                String warningMsg = String.format("<@%s>!! The next number should be %d.", 
                    userId, result.getExpectedNumber());
                
                event.getChannel().sendMessage(warningMsg).queue(
                    success -> log.debug("Sent warning message for count reset"),
                    error -> log.warn("Failed to send warning message: {}", error.getMessage())
                );
                break;
                
            case CONSECUTIVE_COUNT:
                // React with X and send consecutive count failure message with save button
                message.addReaction(Emoji.fromUnicode("❌")).queue();
                
                String consecutiveMsg = String.format("<@%s> RUINED IT AT %d!! You cannot send two numbers in a row.", 
                    userId, result.getCurrentCount());
                
                event.getChannel().sendMessage(consecutiveMsg).queue(
                    success -> {
                        sendLivesOrTimeoutEmbed(event, result);
                        sendSaveButton(event, result.getSaveCost());
                    },
                    error -> log.warn("Failed to send consecutive count message: {}", error.getMessage())
                );
                break;
                
            case USER_TIMED_OUT:
                // Delete the message and silently ignore - user is timed out
                message.delete().queue(
                    success -> log.debug("Deleted message from timed out user: {}", userId),
                    error -> log.warn("Failed to delete message from timed out user: {}", error.getMessage())
                );
                break;
                
            case USER_NOT_FOUND:
                // Delete the message from non-database user and send signup message
                message.delete().queue(
                    success -> {
                        log.debug("Deleted message from non-database user: {}", userId);
                        
                        // Send signup message
                        String signupMsg = String.format("<@%s> In order to participate in counting, you must sign up for the bot! %s", 
                            userId, frontendBaseUrl);
                        
                        event.getChannel().sendMessage(signupMsg).queue(
                            msgSuccess -> log.debug("Sent signup message to non-database user: {}", userId),
                            msgError -> log.warn("Failed to send signup message to non-database user {}: {}", userId, msgError.getMessage())
                        );
                    },
                    error -> {
                        log.warn("Failed to delete message from non-database user {}: {}", userId, error.getMessage());
                        
                        // Still send signup message even if deletion failed
                        String signupMsg = String.format("<@%s> In order to participate in counting, you must sign up for the bot! %s", 
                            userId, frontendBaseUrl);
                        
                        event.getChannel().sendMessage(signupMsg).queue(
                            msgSuccess -> log.debug("Sent signup message to non-database user: {}", userId),
                            msgError -> log.warn("Failed to send signup message to non-database user {}: {}", userId, msgError.getMessage())
                        );
                    }
                );
                break;
                
            case GAME_DISABLED:
                // Game is disabled, ignore
                log.debug("Counting game is disabled, ignoring count attempt");
                break;
        }
    }
    
    private void sendSaveButton(MessageReceivedEvent event, Integer saveCost) {
        if (saveCost == null) {
            return;
        }
        
        Button saveButton = Button.primary("save_count", String.format("💰 Save This Count? (%d credits)", saveCost));
        
        // Get the highest count from the game state
        int highestCount = countingGameService.getGameState().getHighestCount();
        
        EmbedBuilder embed = new EmbedBuilder();
        embed.setColor(Color.CYAN);
        embed.setTitle("💰 Save This Count?");
        embed.setDescription(String.format("Anyone can click the button below to save this count for **%d** credits!", saveCost));
        embed.setFooter(String.format("The highest count was %d", highestCount));
        
        event.getChannel().sendMessageEmbeds(embed.build())
                .setActionRow(saveButton)
                .queue(
                    success -> log.debug("Sent save count button with cost: {}", saveCost),
                    error -> log.warn("Failed to send save count button: {}", error.getMessage())
                );
    }
    
    private void sendLivesOrTimeoutEmbed(MessageReceivedEvent event, CountingGameService.CountingResult result) {
        EmbedBuilder embed = new EmbedBuilder();
        
        if (result.isTimedOut()) {
            // User lost all lives and was timed out
            embed.setColor(Color.RED);
            embed.setTitle("💀 Game Over!");
            embed.setDescription(String.format("You lost all your lives! You have lost access to counting for **%d hours**!", 
                result.getTimeoutHours()));
        } else {
            // User still has lives remaining
            Integer livesRemaining = result.getLivesRemaining();
            if (livesRemaining == null || livesRemaining <= 0) {
                return; // No embed needed for non-database users
            }
            
            embed.setColor(livesRemaining == 1 ? Color.ORANGE : Color.YELLOW);
            embed.setTitle("❤️ Lives Remaining");
            
            String heartsDisplay = "❤️".repeat(livesRemaining) + "🖤".repeat(Math.max(0, 3 - livesRemaining));
            embed.setDescription(String.format("You now have **%d** %s left.\n%s", 
                livesRemaining, 
                livesRemaining == 1 ? "life" : "lives",
                heartsDisplay));
        }
        
        event.getChannel().sendMessageEmbeds(embed.build()).queue(
            success -> log.debug("Sent lives/timeout embed"),
            error -> log.warn("Failed to send lives/timeout embed: {}", error.getMessage())
        );
    }
} 