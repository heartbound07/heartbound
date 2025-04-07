package com.app.heartbound.services.discord;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class DiscordChannelService {
    
    private static final Logger logger = LoggerFactory.getLogger(DiscordChannelService.class);
    
    @Autowired
    private JDA jda;
    
    @Value("${discord.server.id}")
    private String discordServerId;
    
    @Value("${discord.category.id}")
    private String discordCategoryId;
    
    /**
     * Creates a voice channel for an LFG party
     * 
     * @param partyId The ID of the LFG party
     * @param partyTitle The title of the LFG party
     * @param partyGame The game associated with the party
     * @return The ID of the created Discord channel, or null if creation failed
     */
    public String createPartyVoiceChannel(UUID partyId, String partyTitle, String partyGame) {
        try {
            // Get the guild (server) by ID
            Guild guild = jda.getGuildById(discordServerId);
            if (guild == null) {
                logger.error("Failed to find Discord server with ID: {}", discordServerId);
                return null;
            }
            
            // Get the category by ID
            net.dv8tion.jda.api.entities.channel.concrete.Category category = guild.getCategoryById(discordCategoryId);
            if (category == null) {
                logger.error("Failed to find Discord category with ID: {}", discordCategoryId);
                return null;
            }
            
            // Just use the raw title, Discord will handle the formatting
            String channelName = partyTitle;
            
            // Ensure channel name length constraints (Discord: 1-100 chars)
            if (channelName.length() > 100) {
                channelName = channelName.substring(0, 100);
            }
            
            // Create the voice channel in the specified category
            VoiceChannel channel = guild.createVoiceChannel(channelName)
                    .setParent(category)
                    .complete(); // .complete() makes this a blocking call
            
            logger.info("Created Discord voice channel with ID: {} for party: {}", 
                        channel.getId(), partyId);
            
            return channel.getId();
        } catch (Exception e) {
            // Log specific JDA exceptions if possible, e.g., InsufficientPermissionException
            logger.error("Failed to create Discord voice channel for party: {}. Error: {}", partyId, e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Sanitizes a string to be used as a Discord channel name
     * Discord channel names must be lowercase with no spaces or special characters,
     * and between 1 and 100 characters long.
     * 
     * @param input The input string
     * @return A sanitized string suitable for a Discord channel name
     */
    private String sanitizeChannelName(String input) {
        // Replace spaces with hyphens, remove invalid characters, convert to lowercase
        String sanitized = input.toLowerCase()
                .replaceAll("\\s+", "-") // Replace whitespace with hyphens
                .replaceAll("[^a-z0-9_-]", ""); // Allow letters, numbers, underscore, hyphen

        // Ensure name is not empty after sanitization
        if (sanitized.isEmpty()) {
            sanitized = "lfg-channel"; // Default name if sanitization results in empty string
        }

        // Ensure name length constraints (Discord: 1-100 chars)
        if (sanitized.length() > 100) {
            sanitized = sanitized.substring(0, 100);
        }
         if (sanitized.length() < 1) {
             // This case should be covered by the isEmpty check, but as a safeguard
             sanitized = "lfg";
         }

        return sanitized;
    }

    /**
     * Deletes a Discord voice channel associated with a party
     * 
     * @param channelId The ID of the Discord channel to delete
     * @return true if deletion was successful, false otherwise
     */
    public boolean deletePartyVoiceChannel(String channelId) {
        if (channelId == null || channelId.isEmpty()) {
            logger.warn("Cannot delete Discord channel: channelId is null or empty");
            return false;
        }
        
        try {
            // Get the guild (server) by ID
            Guild guild = jda.getGuildById(discordServerId);
            if (guild == null) {
                logger.error("Failed to find Discord server with ID: {}", discordServerId);
                return false;
            }
            
            // Get the voice channel by ID
            VoiceChannel channel = guild.getVoiceChannelById(channelId);
            if (channel == null) {
                logger.warn("Voice channel with ID {} not found, may have been already deleted", channelId);
                return true; // Consider this a success since the channel doesn't exist
            }
            
            // Delete the channel
            channel.delete().queue(
                success -> logger.info("Successfully deleted Discord voice channel with ID: {}", channelId),
                error -> logger.error("Failed to delete Discord voice channel: {}", error.getMessage())
            );
            
            return true;
        } catch (Exception e) {
            logger.error("Error deleting Discord voice channel: {}", e.getMessage(), e);
            return false;
        }
    }
} 