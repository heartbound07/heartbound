package com.app.heartbound.services.discord;

import com.app.heartbound.services.discord.CountingGameService.CountingResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.awt.Color;

@Component
@RequiredArgsConstructor
@Slf4j
public class CountingGameListener extends ListenerAdapter {
    
    private final CountingGameService countingGameService;
    
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
                
            case WRONG_NUMBER:
                // React with X and send failure message
                message.addReaction(Emoji.fromUnicode("❌")).queue();
                
                String wrongNumberMsg = String.format("<@%s> RUINED IT AT %d!! The next number was %d.", 
                    userId, result.getCurrentCount(), result.getExpectedNumber());
                
                event.getChannel().sendMessage(wrongNumberMsg).queue(
                    success -> sendLivesEmbed(event, result.getLivesRemaining()),
                    error -> log.warn("Failed to send wrong number message: {}", error.getMessage())
                );
                break;
                
            case CONSECUTIVE_COUNT:
                // React with X and send consecutive count failure message
                message.addReaction(Emoji.fromUnicode("❌")).queue();
                
                String consecutiveMsg = String.format("<@%s> RUINED IT AT %d!! You cannot send two numbers in a row.", 
                    userId, result.getCurrentCount());
                
                event.getChannel().sendMessage(consecutiveMsg).queue(
                    success -> sendLivesEmbed(event, result.getLivesRemaining()),
                    error -> log.warn("Failed to send consecutive count message: {}", error.getMessage())
                );
                break;
                
            case USER_TIMED_OUT:
                // Silently ignore - user is timed out
                log.debug("Ignoring count attempt from timed out user: {}", userId);
                break;
                
            case GAME_DISABLED:
                // Game is disabled, ignore
                log.debug("Counting game is disabled, ignoring count attempt");
                break;
        }
    }
    
    private void sendLivesEmbed(MessageReceivedEvent event, Integer livesRemaining) {
        if (livesRemaining == null) {
            return;
        }
        
        EmbedBuilder embed = new EmbedBuilder();
        
        if (livesRemaining <= 0) {
            embed.setColor(Color.RED);
            embed.setTitle("💀 Game Over!");
            embed.setDescription("You have been timed out from the counting game due to losing all your lives.");
        } else {
            embed.setColor(livesRemaining == 1 ? Color.ORANGE : Color.YELLOW);
            embed.setTitle("❤️ Lives Remaining");
            
            String heartsDisplay = "❤️".repeat(livesRemaining) + "🖤".repeat(Math.max(0, 3 - livesRemaining));
            embed.setDescription(String.format("You now have **%d** %s left.\n%s", 
                livesRemaining, 
                livesRemaining == 1 ? "life" : "lives",
                heartsDisplay));
        }
        
        event.getChannel().sendMessageEmbeds(embed.build()).queue(
            success -> log.debug("Sent lives embed for user with {} lives", livesRemaining),
            error -> log.warn("Failed to send lives embed: {}", error.getMessage())
        );
    }
} 