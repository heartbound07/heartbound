package com.app.heartbound.services.discord;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.Permission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * DiscordPairingChannelService
 * 
 * Service for managing Discord text channels specifically for user pairings.
 * Handles creation, permission management, and deletion of private channels
 * for matched users with proper error handling and security validation.
 */
@Service
public class DiscordPairingChannelService {
    
    private static final Logger logger = LoggerFactory.getLogger(DiscordPairingChannelService.class);
    
    @Autowired
    private JDA jda;
    
    @Value("${discord.server.id}")
    private String discordServerId;
    
    @Value("${discord.pairing.category.id:}")
    private String pairingCategoryId;
    
    // Maximum channel name length allowed by Discord
    private static final int MAX_CHANNEL_NAME_LENGTH = 100;

    /**
     * Creates a private text channel for two matched users
     * 
     * @param user1DiscordId Discord ID of first user
     * @param user2DiscordId Discord ID of second user
     * @param pairingId The pairing ID for reference
     * @return CompletableFuture containing channel creation result
     */
    public CompletableFuture<ChannelCreationResult> createPairingChannel(String user1DiscordId, String user2DiscordId, Long pairingId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("Creating pairing channel for users {} and {} (pairing ID: {})", 
                           user1DiscordId, user2DiscordId, pairingId);
                
                // Input validation
                if (user1DiscordId == null || user2DiscordId == null || pairingId == null) {
                    throw new IllegalArgumentException("User Discord IDs and pairing ID cannot be null");
                }
                
                // Sanitize Discord IDs (should be numeric snowflakes)
                if (!isValidDiscordId(user1DiscordId) || !isValidDiscordId(user2DiscordId)) {
                    throw new IllegalArgumentException("Invalid Discord ID format");
                }
                
                Guild guild = getGuild();
                if (guild == null) {
                    return ChannelCreationResult.failure("Discord server not accessible");
                }
                
                // Validate that both users are members of the server
                Member member1 = validateServerMember(guild, user1DiscordId);
                Member member2 = validateServerMember(guild, user2DiscordId);
                
                if (member1 == null || member2 == null) {
                    return ChannelCreationResult.failure("One or both users are not members of the Discord server");
                }
                
                // Generate channel name with conflict resolution
                String channelName = generateChannelName(member1.getEffectiveName(), member2.getEffectiveName(), pairingId);
                
                // Get category (optional, can be null)
                Category category = getPairingCategory();
                
                // Create the text channel
                TextChannel channel = createTextChannelWithPermissions(guild, channelName, category, member1, member2);
                
                logger.info("Successfully created pairing channel '{}' (ID: {}) for users {} and {}", 
                           channelName, channel.getId(), user1DiscordId, user2DiscordId);
                
                return ChannelCreationResult.success(channel.getId(), channelName);
                
            } catch (Exception e) {
                logger.error("Failed to create pairing channel for users {} and {}: {}", 
                           user1DiscordId, user2DiscordId, e.getMessage(), e);
                return ChannelCreationResult.failure("Channel creation failed: " + e.getMessage());
            }
        }).orTimeout(30, TimeUnit.SECONDS)
          .exceptionally(throwable -> {
              logger.error("Timeout or error in channel creation: {}", throwable.getMessage());
              return ChannelCreationResult.failure("Channel creation timed out");
          });
    }

    /**
     * Deletes a pairing channel
     * 
     * @param channelId The Discord channel ID to delete
     * @param reason Reason for deletion (for audit logs)
     * @return CompletableFuture containing deletion result
     */
    public CompletableFuture<Boolean> deletePairingChannel(String channelId, String reason) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("Deleting pairing channel with ID: {} (reason: {})", channelId, reason);
                
                if (channelId == null || channelId.trim().isEmpty()) {
                    logger.warn("Cannot delete channel: channelId is null or empty");
                    return false;
                }
                
                Guild guild = getGuild();
                if (guild == null) {
                    logger.error("Cannot delete channel: Discord server not accessible");
                    return false;
                }
                
                TextChannel channel = guild.getTextChannelById(channelId);
                if (channel == null) {
                    logger.warn("Channel with ID {} not found or not a text channel", channelId);
                    return false;
                }
                
                // Channel ID comes from database, so we know it's a pairing channel
                logger.debug("Proceeding with deletion of pairing channel {}", channelId);
                
                // Delete the channel
                channel.delete()
                       .reason(reason != null ? reason : "Pairing ended")
                       .complete();
                
                logger.info("Successfully deleted pairing channel {}", channelId);
                return true;
                
            } catch (InsufficientPermissionException e) {
                logger.error("Bot lacks permission to delete channel {}: {}", channelId, e.getMessage());
                return false;
            } catch (Exception e) {
                logger.error("Failed to delete pairing channel {}: {}", channelId, e.getMessage(), e);
                return false;
            }
        }).orTimeout(15, TimeUnit.SECONDS)
          .exceptionally(throwable -> {
              logger.error("Timeout or error in channel deletion: {}", throwable.getMessage());
              return false;
          });
    }
    
    // Private helper methods
    
    private Guild getGuild() {
        try {
            return jda.getGuildById(discordServerId);
        } catch (Exception e) {
            logger.error("Failed to get Discord guild with ID {}: {}", discordServerId, e.getMessage());
            return null;
        }
    }
    
    private Category getPairingCategory() {
        if (pairingCategoryId == null || pairingCategoryId.trim().isEmpty()) {
            logger.debug("No pairing category configured, channels will be created in root");
            return null;
        }
        
        try {
            Guild guild = getGuild();
            if (guild != null) {
                Category category = guild.getCategoryById(pairingCategoryId);
                if (category == null) {
                    logger.warn("Pairing category with ID {} not found", pairingCategoryId);
                }
                return category;
            }
        } catch (Exception e) {
            logger.warn("Failed to get pairing category {}: {}", pairingCategoryId, e.getMessage());
        }
        
        return null;
    }
    
    private Member validateServerMember(Guild guild, String discordId) {
        try {
            Member member = guild.retrieveMemberById(discordId).complete();
            if (member == null) {
                logger.warn("User with Discord ID {} is not a member of server {}", discordId, guild.getName());
            }
            return member;
        } catch (Exception e) {
            logger.warn("Failed to retrieve member with Discord ID {}: {}", discordId, e.getMessage());
            return null;
        }
    }
    
    private String generateChannelName(String username1, String username2, Long pairingId) {
        // Sanitize usernames for Discord channel naming
        String sanitizedUser1 = sanitizeForChannelName(username1);
        String sanitizedUser2 = sanitizeForChannelName(username2); 
        
        // Sort usernames to ensure consistent naming regardless of order
        String channelName;
        if (sanitizedUser1.compareToIgnoreCase(sanitizedUser2) <= 0) {
            channelName = sanitizedUser1 + "-" + sanitizedUser2;
        } else {
            channelName = sanitizedUser2 + "-" + sanitizedUser1;
        }
        
        // Ensure the channel name doesn't exceed Discord's limit
        if (channelName.length() > MAX_CHANNEL_NAME_LENGTH) {
            channelName = channelName.substring(0, MAX_CHANNEL_NAME_LENGTH);
        }
        
        // Final validation - fallback to pairing ID if name is too short or invalid
        if (channelName.length() < 2) {
            channelName = "pairing-" + pairingId;
        }
        
        return channelName.toLowerCase();
    }
    
    private String sanitizeForChannelName(String username) {
        if (username == null) {
            return "user";
        }
        
        // Discord channel names: lowercase, alphanumeric, hyphens, underscores only
        return username.toLowerCase()
                      .replaceAll("[^a-z0-9_-]", "")
                      .replaceAll("^[^a-z0-9]+", "") // Remove leading non-alphanumeric
                      .replaceAll("[^a-z0-9]+$", "") // Remove trailing non-alphanumeric
                      .substring(0, Math.min(username.length(), 30)); // Limit individual username length
    }
    
    private TextChannel createTextChannelWithPermissions(Guild guild, String channelName, Category category, Member member1, Member member2) {
        try {
            // Create channel builder
            var channelAction = guild.createTextChannel(channelName);
            
            // Set category if available
            if (category != null) {
                channelAction = channelAction.setParent(category);
            }
            
            // Create the channel
            TextChannel channel = channelAction.complete();
            
            // Set permissions: deny @everyone, allow specific users
            channel.getManager()
                   .putPermissionOverride(guild.getPublicRole(), 
                                        null, // No allows for @everyone
                                        EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_HISTORY))
                   .putPermissionOverride(member1, 
                                        EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, 
                                                  Permission.MESSAGE_HISTORY, Permission.MESSAGE_ATTACH_FILES,
                                                  Permission.MESSAGE_EXT_EMOJI, Permission.MESSAGE_ADD_REACTION),
                                        null)
                   .putPermissionOverride(member2, 
                                        EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND,
                                                  Permission.MESSAGE_HISTORY, Permission.MESSAGE_ATTACH_FILES,
                                                  Permission.MESSAGE_EXT_EMOJI, Permission.MESSAGE_ADD_REACTION),
                                        null)
                   .complete();
            
            return channel;
            
        } catch (InsufficientPermissionException e) {
            logger.error("Bot lacks permission to create channel or set permissions: {}", e.getMessage());
            throw new RuntimeException("Insufficient permissions to create pairing channel", e);
        }
    }
    
    private boolean isPairingChannel(TextChannel channel) {
        String name = channel.getName();
        
        // Check if it's a pairing channel based on naming pattern
        return name.matches(".*-\\d+$"); // Ends with dash and number (pairing ID)
    }
    
    private boolean isValidDiscordId(String discordId) {
        if (discordId == null || discordId.trim().isEmpty()) {
            return false;
        }
        
        // Discord IDs are 17-19 digit numbers (snowflakes)
        return discordId.matches("\\d{17,19}");
    }
    
    /**
     * Result class for channel creation operations
     */
    public static class ChannelCreationResult {
        private final boolean success;
        private final String channelId;
        private final String channelName;
        private final String errorMessage;
        
        private ChannelCreationResult(boolean success, String channelId, String channelName, String errorMessage) {
            this.success = success;
            this.channelId = channelId;
            this.channelName = channelName;
            this.errorMessage = errorMessage;
        }
        
        public static ChannelCreationResult success(String channelId, String channelName) {
            return new ChannelCreationResult(true, channelId, channelName, null);
        }
        
        public static ChannelCreationResult failure(String errorMessage) {
            return new ChannelCreationResult(false, null, null, errorMessage);
        }
        
        public boolean isSuccess() { return success; }
        public String getChannelId() { return channelId; }
        public String getChannelName() { return channelName; }
        public String getErrorMessage() { return errorMessage; }
    }
} 