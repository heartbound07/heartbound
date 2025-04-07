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
            // Sanitize the title to create a valid channel name
            String channelName = sanitizeChannelName(partyGame + "-" + partyTitle);
            
            // Limit channel name length to Discord's limit (less than 100 chars)
            if (channelName.length() > 90) {
                channelName = channelName.substring(0, 90);
            }
            
            // Add party ID suffix to ensure uniqueness
            channelName = channelName + "-" + partyId.toString().substring(0, 8);
            
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
                // Optionally, create the channel without a parent category or return null
                // For now, let's return null as the category is expected
                return null;
            }
            
            // Create the voice channel in the specified category
            VoiceChannel channel = guild.createVoiceChannel(channelName)
                    .setParent(category) // Use the fetched category object
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
} 