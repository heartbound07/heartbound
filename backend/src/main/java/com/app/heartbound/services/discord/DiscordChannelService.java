package com.app.heartbound.services.discord;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.Invite;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.HashMap;
import java.util.Map;
import java.util.EnumSet;
import java.awt.Color;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import com.app.heartbound.entities.LFGParty;
import com.app.heartbound.enums.Region;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.ItemComponent;
import net.dv8tion.jda.api.entities.UserSnowflake;
import java.util.concurrent.CompletableFuture;

@Service
public class DiscordChannelService {
    
    private static final Logger logger = LoggerFactory.getLogger(DiscordChannelService.class);
    
    // Channel IDs for party announcements
    private static final String CASUAL_NA_CHANNEL_ID = "1222643176561705050";
    private static final String COMPETITIVE_NA_CHANNEL_ID = "1304321374248108083";
    
    // Update this to use your application's URL instead of hardcoded domain
    @Value("${frontend.base.url}")
    private String frontendBaseUrl;
    
    // Add this value annotation for the backend URL
    @Value("${backend.base.url}")
    private String backendBaseUrl;
    
    // This will dynamically build the rank image URL from your application host
    private String getRankImageUrl(String rankName) {
        // Use the backend URL directly instead of deriving it from frontend URL
        return backendBaseUrl + "/images/ranks/" + rankName.toLowerCase() + ".png";
    }
    
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
     * @param creatorDiscordId The Discord ID of the party creator
     * @return A Map containing "channelId" and "inviteUrl" (inviteUrl may be null if creation failed)
     */
    public Map<String, String> createPartyVoiceChannel(UUID partyId, String partyTitle, String partyDescription, String partyGame, boolean inviteOnly, String creatorDiscordId) {
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
                
                // If inviteOnly is true, set permissions to:
                // 1. Allow everyone to VIEW the channel
                // 2. Deny everyone from CONNECTING to the channel
                // 3. ALLOW the party creator to both VIEW and CONNECT to the channel
                if (inviteOnly) {
                    // First set permissions for @everyone
                    channel.getManager()
                          .putPermissionOverride(guild.getPublicRole(), 
                                                 EnumSet.of(Permission.VIEW_CHANNEL), // Allow viewing
                                                 EnumSet.of(Permission.VOICE_CONNECT)) // Deny connecting
                          .complete();
                    
                    // Then add an override for the party creator if their Discord ID is available
                    if (creatorDiscordId != null && !creatorDiscordId.isEmpty()) {
                        Member creator = guild.retrieveMemberById(creatorDiscordId).complete();
                        if (creator != null) {
                            channel.getManager()
                                  .putPermissionOverride(creator, 
                                                        EnumSet.of(Permission.VIEW_CHANNEL, Permission.VOICE_CONNECT), // Allow viewing and connecting
                                                        null) // No explicit denies
                                  .complete();
                            logger.info("Set special permissions for party creator (ID: {}) in channel ID: {}", creatorDiscordId, channel.getId());
                        } else {
                            logger.warn("Could not find Discord server member with ID: {} for creator permissions", creatorDiscordId);
                        }
                    }
                    
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

    /**
     * Grants a user permission to connect to a party voice channel
     *
     * @param channelId The Discord channel ID
     * @param discordUserId The Discord user ID to grant permission to
     * @return true if permissions were successfully updated, false otherwise
     */
    public boolean addUserToVoiceChannel(String channelId, String discordUserId) {
        try {
            if (channelId == null || discordUserId == null || channelId.isEmpty() || discordUserId.isEmpty()) {
                logger.warn("Cannot add user to voice channel: Invalid channel ID or user ID");
                return false;
            }
            
            Guild guild = jda.getGuildById(discordServerId);
            if (guild == null) {
                logger.error("Failed to find Discord server with ID: {}", discordServerId);
                return false;
            }
            
            VoiceChannel channel = guild.getVoiceChannelById(channelId);
            if (channel == null) {
                logger.warn("Voice channel with ID {} not found", channelId);
                return false;
            }
            
            // Retrieve member by ID (this is a REST request that may fail if user is not in the guild)
            try {
                Member member = guild.retrieveMemberById(discordUserId).complete();
                if (member != null) {
                    // Grant VIEW_CHANNEL and VOICE_CONNECT permissions to the user
                    channel.getManager()
                          .putPermissionOverride(member, 
                                               EnumSet.of(Permission.VIEW_CHANNEL, Permission.VOICE_CONNECT), // Allow viewing and connecting
                                               null) // No explicit denies
                          .complete();
                    logger.info("Granted voice channel access to user {} for channel {}", discordUserId, channelId);
                    return true;
                } else {
                    logger.warn("Could not find Discord member with ID: {} to grant channel permissions", discordUserId);
                    return false;
                }
            } catch (Exception e) {
                logger.error("Error retrieving Discord member with ID {}: {}", discordUserId, e.getMessage());
                return false;
            }
        } catch (Exception e) {
            logger.error("Error adding user {} to voice channel {}: {}", discordUserId, channelId, e.getMessage());
            return false;
        }
    }

    /**
     * Removes a user's permission to connect to a party voice channel
     * and disconnects them if currently in the channel
     *
     * @param channelId The Discord channel ID
     * @param discordUserId The Discord user ID to remove permission from
     * @return true if permissions were successfully removed, false otherwise
     */
    public boolean removeUserFromVoiceChannel(String channelId, String discordUserId) {
        try {
            if (channelId == null || discordUserId == null || channelId.isEmpty() || discordUserId.isEmpty()) {
                logger.warn("Cannot remove user from voice channel: Invalid channel ID or user ID");
                return false;
            }
            
            Guild guild = jda.getGuildById(discordServerId);
            if (guild == null) {
                logger.error("Failed to find Discord server with ID: {}", discordServerId);
                return false;
            }
            
            VoiceChannel channel = guild.getVoiceChannelById(channelId);
            if (channel == null) {
                logger.warn("Voice channel with ID {} not found", channelId);
                return false;
            }
            
            // Retrieve member by ID
            try {
                Member member = guild.retrieveMemberById(discordUserId).complete();
                if (member != null) {
                    // Check if user is currently in this voice channel
                    GuildVoiceState voiceState = member.getVoiceState();
                    if (voiceState != null && voiceState.getChannel() != null && 
                        voiceState.getChannel().getId().equals(channelId)) {
                        // Kick the user from voice channel
                        guild.kickVoiceMember(member).queue(
                            success -> logger.info("Kicked user {} from voice channel {}", discordUserId, channelId),
                            error -> logger.error("Failed to kick user {} from voice channel {}: {}", 
                                                discordUserId, channelId, error.getMessage())
                        );
                    }
                    
                    // Remove permission overrides
                    channel.getManager()
                          .removePermissionOverride(member)
                          .queue(
                            success -> logger.info("Removed voice channel access from user {} for channel {}", discordUserId, channelId),
                            error -> logger.error("Failed to remove permissions for user {} from channel {}: {}", 
                                                discordUserId, channelId, error.getMessage())
                          );
                    
                    return true;
                } else {
                    logger.warn("Could not find Discord member with ID: {} to remove channel permissions", discordUserId);
                    return false;
                }
            } catch (Exception e) {
                logger.error("Error retrieving Discord member with ID {}: {}", discordUserId, e.getMessage());
                return false;
            }
        } catch (Exception e) {
            logger.error("Error removing user {} from voice channel {}: {}", discordUserId, channelId, e.getMessage());
            return false;
        }
    }

    /**
     * Sends a welcome message to a voice channel's text chat, pinging the party owner
     *
     * @param channelId The Discord voice channel ID
     * @param ownerDiscordId The Discord user ID of the party owner to ping
     * @param partyTitle The title of the party for the welcome message
     * @return true if message was sent successfully, false otherwise
     */
    public boolean sendWelcomeMessageToVoiceChannel(String channelId, String ownerDiscordId, String partyTitle) {
        try {
            if (channelId == null || ownerDiscordId == null || channelId.isEmpty() || ownerDiscordId.isEmpty()) {
                logger.warn("Cannot send welcome message: Invalid channel ID or owner ID");
                return false;
            }
            
            Guild guild = jda.getGuildById(discordServerId);
            if (guild == null) {
                logger.error("Failed to find Discord server with ID: {}", discordServerId);
                return false;
            }
            
            VoiceChannel voiceChannel = guild.getVoiceChannelById(channelId);
            if (voiceChannel == null) {
                logger.warn("Voice channel with ID {} not found", channelId);
                return false;
            }

            // Format the welcome message with party info and owner ping
            String welcomeMessage = String.format(
                "**Welcome to \"%s\" party!**\n\nThis channel was created by <@%s> who is the party leader.\n\nHave fun gaming together! 🎮",
                partyTitle,
                ownerDiscordId
            );
            
            // VoiceChannel inherits from MessageChannel, so we can send a message directly
            voiceChannel.sendMessage(welcomeMessage).queue(
                success -> logger.info("Sent welcome message to voice channel {}", channelId),
                error -> logger.error("Failed to send welcome message: {}", error.getMessage())
            );
            
            return true;
        } catch (Exception e) {
            logger.error("Error sending welcome message to voice channel {}: {}", channelId, e.getMessage());
            return false;
        }
    }

    /**
     * Sends a notification to a voice channel when a user has been accepted into the party
     *
     * @param channelId The Discord voice channel ID
     * @param acceptedUserId The Discord user ID who was accepted
     * @param partyTitle The title of the party
     * @return true if message was sent successfully, false otherwise
     */
    public boolean sendUserAcceptedMessage(String channelId, String acceptedUserId, String partyTitle) {
        try {
            if (channelId == null || acceptedUserId == null || channelId.isEmpty() || acceptedUserId.isEmpty()) {
                logger.warn("Cannot send user accepted message: Invalid channel ID or user ID");
                return false;
            }
            
            Guild guild = jda.getGuildById(discordServerId);
            if (guild == null) {
                logger.error("Failed to find Discord server with ID: {}", discordServerId);
                return false;
            }
            
            VoiceChannel voiceChannel = guild.getVoiceChannelById(channelId);
            if (voiceChannel == null) {
                logger.warn("Voice channel with ID {} not found", channelId);
                return false;
            }

            // Format the welcome message with the user mention
            String welcomeMessage = String.format(
                "Welcome <@%s> to the \"%s\" party! You have been accepted into the group. Join the voice channel when you're ready to play.",
                acceptedUserId,
                partyTitle
            );
            
            // Send directly to the voice channel (which works because VoiceChannel implements MessageChannel)
            voiceChannel.sendMessage(welcomeMessage).queue(
                success -> logger.info("Sent acceptance message for user {} in channel {}", acceptedUserId, channelId),
                error -> logger.error("Failed to send acceptance message: {}", error.getMessage())
            );
            
            return true;
        } catch (Exception e) {
            logger.error("Error sending user accepted message to channel {}: {}", channelId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Sends a party creation announcement to the appropriate Discord channel
     * based on match type and region
     *
     * @param party The LFG party that was created
     * @return true if the announcement was sent successfully, false otherwise
     */
    public CompletableFuture<String> sendPartyCreationAnnouncement(LFGParty party) {
        CompletableFuture<String> future = new CompletableFuture<>();
        
        try {
            if (party == null) {
                logger.warn("Cannot send party creation announcement: Party is null");
                future.complete(null);
                return future;
            }
            
            // Determine the appropriate channel ID based on match type and region
            String channelId = determineAnnouncementChannelId(party.getMatchType(), party.getRequirements().getRegion());
            
            if (channelId == null) {
                logger.info("No appropriate channel found for party announcement. Match type: {}, Region: {}", 
                            party.getMatchType(), party.getRequirements().getRegion());
                future.complete(null);
                return future;
            }
            
            Guild guild = jda.getGuildById(discordServerId);
            if (guild == null) {
                logger.error("Failed to find Discord server with ID: {}", discordServerId);
                future.complete(null);
                return future;
            }
            
            TextChannel textChannel = guild.getTextChannelById(channelId);
            if (textChannel == null) {
                logger.warn("Text channel with ID {} not found", channelId);
                future.complete(null);
                return future;
            }
            
            // Create and send the embed
            MessageEmbed embed = createPartyAnnouncementEmbed(party);
            
            // Create a button to join the party
            String partyUrl = frontendBaseUrl + "/dashboard/valorant/" + party.getId();
            Button joinButton = Button.link(partyUrl, "Join Party");
            
            textChannel.sendMessageEmbeds(embed)
                .setActionRow(joinButton)
                .queue(
                    success -> {
                        String messageId = success.getId();
                        logger.info("Sent party creation announcement for party {} to channel {}, message ID: {}", 
                                   party.getId(), channelId, messageId);
                        future.complete(messageId);
                    },
                    error -> {
                        logger.error("Failed to send party creation announcement: {}", error.getMessage());
                        future.complete(null);
                    }
                );
            
            return future;
        } catch (Exception e) {
            logger.error("Error sending party creation announcement: {}", e.getMessage(), e);
            future.complete(null);
            return future;
        }
    }
    
    /**
     * Determines the appropriate channel ID for a party announcement based on match type and region
     *
     * @param matchType The match type of the party (casual, competitive, etc.)
     * @param region The region of the party
     * @return The Discord channel ID, or null if no appropriate channel is found
     */
    private String determineAnnouncementChannelId(String matchType, Region region) {
        if (matchType == null || region == null) {
            return null;
        }
        
        // Normalize match type
        String normalizedMatchType = matchType.toLowerCase().trim();
        
        // Check if region is in NA
        boolean isNARegion = region == Region.NA_EAST || 
                              region == Region.NA_WEST || 
                              region == Region.NA_CENTRAL;
        
        if (!isNARegion) {
            // No channel configured for non-NA regions
            logger.debug("No channel configured for region: {}", region);
            return null;
        }
        
        // Select channel based on match type
        if (normalizedMatchType.equals("casual")) {
            return CASUAL_NA_CHANNEL_ID;
        } else if (normalizedMatchType.equals("competitive")) {
            return COMPETITIVE_NA_CHANNEL_ID;
        }
        
        logger.debug("No channel configured for match type: {}", matchType);
        return null;
    }
    
    /**
     * Creates a rich embed message for party announcement with a cleaner format
     *
     * @param party The LFG party to create an announcement for
     * @return A MessageEmbed object representing the announcement
     */
    private MessageEmbed createPartyAnnouncementEmbed(LFGParty party) {
        EmbedBuilder embed = new EmbedBuilder();
        
        // Set embed color based on match type
        if ("competitive".equalsIgnoreCase(party.getMatchType())) {
            embed.setColor(new Color(255, 70, 85)); // Red color for competitive
        } else {
            embed.setColor(new Color(66, 133, 244)); // Blue for casual
        }
        
        // Calculate player count
        int currentPlayers = party.getParticipants().size();
        int maxPlayers = party.getMaxPlayers();
        
        // Check if party is closed
        boolean isClosed = "closed".equalsIgnoreCase(party.getStatus());
        
        // Log status for debugging
        logger.debug("Creating embed for party {} with status '{}', isClosed={}", 
                    party.getId(), party.getStatus(), isClosed);
        
        // Set title with the updated format and make it bold for emphasis
        embed.setTitle(String.format("**(%d/%d) %s**", 
                currentPlayers, 
                maxPlayers,
                party.getTitle()));

        // Add (CLOSED) as description if party is full/closed
        if (isClosed) {
            embed.setDescription("**(CLOSED)**");
        } else {
            // Explicitly set an empty description for open parties
            embed.setDescription("");
        }
        
        // Add the rank image as a thumbnail - using the dynamic method instead of constant
        if (party.getRequirements() != null && party.getRequirements().getRank() != null) {
            String rankName = party.getRequirements().getRank().name();
            String rankImageUrl = getRankImageUrl(rankName);
            if (rankImageUrl != null && !rankImageUrl.isEmpty()) {
                // Ensure URL has proper http/https prefix
                if (!rankImageUrl.startsWith("http://") && !rankImageUrl.startsWith("https://")) {
                    // Either add a prefix
                    rankImageUrl = "https://" + rankImageUrl;
                    // Or log a warning and skip setting the thumbnail
                    logger.warn("Invalid rank image URL format: {}. URL must start with http:// or https://", rankImageUrl);
                } else {
                    try {
                        // Basic URL validation before setting the thumbnail
                        embed.setThumbnail(rankImageUrl);
                        logger.debug("Adding rank image for {} rank: {}", rankName, rankImageUrl);
                    } catch (IllegalArgumentException e) {
                        // Log the error and continue without the thumbnail
                        logger.warn("Failed to set rank thumbnail image: {}", e.getMessage());
                    }
                }
            }
        }
        
        // List participants (including party leader)
        StringBuilder participantsText = new StringBuilder();
        for (String participantId : party.getParticipants()) {
            participantsText.append("<@").append(participantId).append(">\n");
        }
        embed.addField("Players", participantsText.toString(), false);
        
        // Add voice preference info only if it's Discord, the party is not closed, and there's an invite URL
        if (!isClosed && 
            "discord".equalsIgnoreCase(party.getVoicePreference()) && 
            party.getDiscordInviteUrl() != null && 
            !party.getDiscordInviteUrl().isEmpty()) {
            embed.addField("\u200B", 
                    "[Join Channel](" + party.getDiscordInviteUrl() + ")", 
                    false);
        }
        
        // No longer need to store party ID in the author URL for message identification
        
        return embed.build();
    }

    /**
     * Updates an existing party announcement embed to reflect current party state
     *
     * @param party The LFG party with updated information
     * @return true if the announcement was updated successfully, false otherwise
     */
    public boolean updatePartyAnnouncementEmbed(LFGParty party) {
        try {
            if (party == null) {
                logger.warn("Cannot update party announcement: Party is null");
                return false;
            }
            
            // Get the Discord message ID directly from the party entity
            String messageId = party.getDiscordAnnouncementMessageId();
            if (messageId == null || messageId.isEmpty()) {
                logger.warn("Cannot update party announcement: No Discord message ID stored for party {}", party.getId());
                return false;
            }
            
            // Determine the appropriate channel ID based on match type and region
            String channelId = determineAnnouncementChannelId(party.getMatchType(), party.getRequirements().getRegion());
            
            if (channelId == null) {
                logger.info("No appropriate channel found for party announcement update. Match type: {}, Region: {}", 
                            party.getMatchType(), party.getRequirements().getRegion());
                return false;
            }
            
            Guild guild = jda.getGuildById(discordServerId);
            if (guild == null) {
                logger.error("Failed to find Discord server with ID: {}", discordServerId);
                return false;
            }
            
            TextChannel textChannel = guild.getTextChannelById(channelId);
            if (textChannel == null) {
                logger.warn("Text channel with ID {} not found", channelId);
                return false;
            }
            
            // Create the updated embed
            MessageEmbed newEmbed = createPartyAnnouncementEmbed(party);
            
            // Check if the party is closed
            boolean isClosed = "closed".equalsIgnoreCase(party.getStatus());
            
            // Log party status when updating announcement
            logger.debug("Updating announcement for party {} with status: {}, participants: {}/{}", 
                      party.getId(), party.getStatus(), party.getParticipants().size(), party.getMaxPlayers());
            
            if (isClosed) {
                // For closed parties, update the embed and remove the interactive components
                textChannel.editMessageEmbedsById(messageId, newEmbed)
                    .setComponents() // Clear all components
                    .queue(
                        success -> logger.info("Updated party announcement embed (removed components) for party {} ({})", 
                                             party.getId(), messageId),
                        error -> logger.error("Failed to update party announcement embed for party {} ({}): {}", 
                                            party.getId(), messageId, error.getMessage())
                    );
            } else {
                // For open parties, update the embed and preserve the Join button
                String partyUrl = frontendBaseUrl + "/dashboard/valorant/" + party.getId();
                Button joinButton = Button.link(partyUrl, "Join Party");
                
                textChannel.editMessageEmbedsById(messageId, newEmbed)
                    .setActionRow(joinButton)
                    .queue(
                        success -> logger.info("Updated party announcement embed for party {} ({})", 
                                             party.getId(), messageId),
                        error -> logger.error("Failed to update party announcement embed for party {} ({}): {}", 
                                            party.getId(), messageId, error.getMessage())
                    );
            }
            
            return true;
        } catch (Exception e) {
            logger.error("Error updating party announcement for party {}: {}", 
                        party != null ? party.getId() : "unknown", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Updates a party announcement embed to show it as deleted
     *
     * @param party The LFG party that was deleted
     * @return true if the announcement was updated successfully, false otherwise
     */
    public boolean markPartyAnnouncementAsDeleted(LFGParty party) {
        try {
            if (party == null) {
                logger.warn("Cannot update party announcement as deleted: Party is null");
                return false;
            }
            
            // Force the party status to closed
            party.setStatus("closed");
            
            // Update the embed - the updatePartyAnnouncementEmbed method will handle hiding the button
            return updatePartyAnnouncementEmbed(party);
        } catch (Exception e) {
            logger.error("Error marking party announcement as deleted: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Adds a user to the Discord server/guild after successful OAuth authentication
     *
     * @param userId The Discord user ID to add
     * @param accessToken The OAuth access token for the user
     * @return true if user was successfully added, false otherwise
     */
    public boolean addUserToGuild(String userId, String accessToken) {
        try {
            if (userId == null || accessToken == null || userId.isEmpty() || accessToken.isEmpty()) {
                logger.warn("Cannot add user to guild: Invalid user ID or access token");
                return false;
            }
            
            Guild guild = jda.getGuildById(discordServerId);
            if (guild == null) {
                logger.error("Failed to find Discord server with ID: {}", discordServerId);
                return false;
            }
            
            // Check if user is already in the guild
            try {
                Member member = guild.retrieveMemberById(userId).complete();
                if (member != null) {
                    logger.info("User {} is already a member of the guild", userId);
                    return true; // Already a member, considered successful
                }
            } catch (Exception e) {
                // User is not in the guild, which is expected
                logger.debug("User {} is not in the guild yet, will attempt to add", userId);
            }
            
            // Convert the userId String to a UserSnowflake object
            // Use the utility method from the UserSnowflake interface
            guild.addMember(accessToken, UserSnowflake.fromId(userId)).queue(
                success -> logger.info("Successfully added user {} to Discord server {}", userId, discordServerId),
                error -> logger.error("Failed to add user {} to Discord server {}: {}", userId, discordServerId, error.getMessage())
            );
            
            return true;
        } catch (Exception e) {
            logger.error("Error adding user {} to Discord server: {}", userId, e.getMessage(), e);
            return false;
        }
    }
} 
