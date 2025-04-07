package com.app.heartbound.services.discord;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.entities.Invite;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.HashMap;
import java.util.Map;
import java.util.EnumSet;
import net.dv8tion.jda.api.Permission;

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
     * Creates a voice channel for an LFG party and generates an invite link.
     *
     * @param partyId The ID of the LFG party
     * @param partyTitle The title of the LFG party (used for channel name)
     * @param partyDescription The description of the LFG party (currently unused)
     * @param partyGame The game associated with the party (currently unused)
     * @param inviteOnly Whether the channel should be invite-only
     * @return A Map containing "channelId" and "inviteUrl" (inviteUrl may be null if creation failed)
     */
    public Map<String, String> createPartyVoiceChannel(UUID partyId, String partyTitle, String partyDescription, String partyGame, boolean inviteOnly) {
        Map<String, String> result = new HashMap<>();
        try {
            // Get the guild (server) by ID
            Guild guild = jda.getGuildById(discordServerId);
            if (guild == null) {
                logger.error("Failed to find Discord server with ID: {}", discordServerId);
                result.put("channelId", null);
                result.put("inviteUrl", null);
                return result;
            }
            
            // Get the category by ID
            net.dv8tion.jda.api.entities.channel.concrete.Category category = guild.getCategoryById(discordCategoryId);
            if (category == null) {
                logger.error("Failed to find Discord category with ID: {}", discordCategoryId);
                result.put("channelId", null);
                result.put("inviteUrl", null);
                return result;
            }
            
            // Use the raw title for the channel name
            String channelName = partyTitle;
            
            // Ensure channel name length constraints (Discord: 1-100 chars)
            if (channelName.length() > 100) {
                channelName = channelName.substring(0, 100);
            }
             if (channelName.isEmpty()) {
                 // Use a default name if the title is empty after potential truncation
                 channelName = "lfg-channel-" + partyId.toString().substring(0, 4); 
             }

            // Sanitize and truncate the description for the channel status (Limit: 128 chars)
            String channelStatus = partyDescription != null ? partyDescription : ""; // Default to empty if null
            if (channelStatus.length() > 128) {
                channelStatus = channelStatus.substring(0, 128);
            }
            
            // Create the voice channel in the specified category
            VoiceChannel channel = guild.createVoiceChannel(channelName)
                    .setParent(category)
                    .complete(); // .complete() makes this a blocking call

            result.put("channelId", channel.getId()); // Store channel ID immediately

            String inviteUrl = null;
            try {
                // Create a permanent, unlimited invite
                Invite invite = channel.createInvite()
                        .setMaxAge(0)    // 0 = never expires
                        .setMaxUses(0)   // 0 = unlimited uses
                        .complete();     // Blocking call
                inviteUrl = invite.getUrl();
                result.put("inviteUrl", inviteUrl); // Store invite URL
                logger.info("Created Discord invite link {} for channel ID: {}", inviteUrl, channel.getId());
                
                // If inviteOnly is true, set permissions to allow VIEW_CHANNEL but deny VOICE_CONNECT for @everyone
                if (inviteOnly) {
                    channel.getManager()
                          .putPermissionOverride(guild.getPublicRole(), 
                                                 EnumSet.of(Permission.VIEW_CHANNEL), // Allow viewing
                                                 EnumSet.of(Permission.VOICE_CONNECT)) // Deny connecting
                          .complete();
                    logger.info("Set invite-only permissions for channel ID: {}", channel.getId());
                }
                
            } catch (InsufficientPermissionException e) {
                logger.warn("Bot lacks permission to create invites for channel ID: {}. Invite link will be null.", channel.getId(), e);
                // inviteUrl remains null, result map will not contain "inviteUrl" or it will be null
            } catch (Exception e) {
                logger.error("Failed to create invite link for channel ID {}: {}", channel.getId(), e.getMessage(), e);
                // inviteUrl remains null
            }

            logger.info("Created Discord voice channel with ID: {} for party: {}",
                        channel.getId(), partyId);

            return result; // Return map containing channelId and potentially inviteUrl
        } catch (Exception e) {
            logger.error("Failed to create Discord voice channel for party: {}. Error: {}", partyId, e.getMessage(), e);
            result.put("channelId", null); // Indicate channel creation failed
            result.put("inviteUrl", null);
            return result; // Return map with null values
        }
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