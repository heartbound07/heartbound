package com.app.heartbound.services.discord;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.utils.MarkdownUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import java.awt.Color;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.time.Instant;

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
    
    @Value("${frontend.base.url}")
    private String frontendBaseUrl;
    
    @Value("${discord.achievements.enabled:true}")
    private boolean achievementNotificationsEnabled;
    
    @Value("${discord.achievements.mention-users:true}")
    private boolean mentionUsersInAchievements;
    
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
                
                // 🎉 NEW: Send welcome message to the newly created channel
                sendWelcomeMessage(channel, member1, member2, pairingId);
                
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
     * Sends a welcome message to a newly created pairing channel
     * 
     * @param channel The Discord text channel to send the message to
     * @param member1 First member of the pairing
     * @param member2 Second member of the pairing
     * @param pairingId The pairing ID for reference
     */
    private void sendWelcomeMessage(TextChannel channel, Member member1, Member member2, Long pairingId) {
        try {
            // Send the simple welcome header message first
            String welcomeHeader = "Hey " + member1.getAsMention() + " and " + member2.getAsMention() + "! ";
            
            // Create the embed with detailed information
            EmbedBuilder embed = buildWelcomeMessageEmbed(member1, member2, pairingId);
            
            // Send the welcome header message first
            channel.sendMessage(welcomeHeader).queue(
                success -> {
                    // Then send the embed
                    channel.sendMessageEmbeds(embed.build()).queue(
                        embedSuccess -> logger.info("Successfully sent welcome message and embed to channel: {}", channel.getName()),
                        embedError -> logger.warn("Failed to send welcome embed to channel {}: {}", channel.getName(), embedError.getMessage())
                    );
                },
                error -> logger.warn("Failed to send welcome message to channel {}: {}", channel.getName(), error.getMessage())
            );
            
        } catch (Exception e) {
            logger.error("Error sending welcome message to channel {}: {}", channel.getName(), e.getMessage());
        }
    }

    /**
     * Build the welcome message embed with detailed information
     */
    private EmbedBuilder buildWelcomeMessageEmbed(Member member1, Member member2, Long pairingId) {
        EmbedBuilder embed = new EmbedBuilder();
        
        embed.setColor(Color.decode("#58b9ff"));
        embed.setTitle("💕 Welcome to your private pairing channel! 💕");
        
        // About this channel section
        StringBuilder aboutChannel = new StringBuilder();
        aboutChannel.append("**📱 About this channel:**\n");
        aboutChannel.append("• This is your **private channel** - only you two can see it\n");
        aboutChannel.append("• Perfect place to chat, get to know each other, and have fun!\n");
        aboutChannel.append("• Feel free to share memes, talk about games, or just vibe together\n");
        
        // What we track section
        StringBuilder tracking = new StringBuilder();
        tracking.append("**📊 What we track:**\n");
        tracking.append("• **Messages & word count** - Every message contributes to your pair stats\n");
        tracking.append("• **Voice time** - Join voice channels together to build streaks\n");
        tracking.append("• **Active days** - Keep the conversation going to increase your score\n");
        tracking.append("• Use ").append(MarkdownUtil.monospace("/stats")).append(" to view your current progress\n");
        
        // Level up section
        StringBuilder levelUp = new StringBuilder();
        levelUp.append("**🏆 Level up together:**\n");
        levelUp.append("• Unlock **achievements** by hitting activity milestones\n");
        levelUp.append("• Gain **XP points** for every interaction\n");
        levelUp.append("• Build **voice streaks** by chatting in voice channels daily\n");
        levelUp.append("• Check your progress on the [pairings page](").append(frontendBaseUrl).append("/dashboard/pairings) anytime!\n");
        
        // Breakup section
        StringBuilder breakup = new StringBuilder();
        breakup.append("**💔 If things don't work out:**\n");
        breakup.append("• Use the ").append(MarkdownUtil.monospace("/breakup")).append(" command in this channel\n");
        breakup.append("• Or visit the [pairings page](").append(frontendBaseUrl).append("/dashboard/pairings) in the app to end the match\n");
        
        // Add fields to embed
        embed.addField("", aboutChannel.toString(), false);
        embed.addField("", tracking.toString(), false);
        embed.addField("", levelUp.toString(), false);
        embed.addField("", breakup.toString(), false);
        
        // Footer message
        embed.setFooter("💝 Remember: The goal is to have fun and make a genuine connection. Don't catch feelings too quickly, but don't be afraid to be yourself! Good luck, and may the odds be ever in your favor! ✨");
        
        return embed;
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
    
    /**
     * Sends a breakup announcement to the specified Discord channel
     * 
     * @param user1DiscordId Discord ID of the first user in the breakup
     * @param user2DiscordId Discord ID of the second user in the breakup
     * @param pairingId The pairing ID for reference
     * @return CompletableFuture containing announcement result
     */
    public CompletableFuture<Boolean> sendBreakupAnnouncement(String user1DiscordId, String user2DiscordId, Long pairingId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("Sending breakup announcement for users {} and {} (pairing ID: {})", 
                           user1DiscordId, user2DiscordId, pairingId);
                
                // Input validation
                if (user1DiscordId == null || user2DiscordId == null || pairingId == null) {
                    logger.warn("Cannot send breakup announcement: null parameters provided");
                    return false;
                }
                
                // Validate Discord IDs
                if (!isValidDiscordId(user1DiscordId) || !isValidDiscordId(user2DiscordId)) {
                    logger.warn("Cannot send breakup announcement: invalid Discord ID format");
                    return false;
                }
                
                Guild guild = getGuild();
                if (guild == null) {
                    logger.warn("Cannot send breakup announcement: Discord server not accessible");
                    return false;
                }
                
                // Get the announcement channel
                TextChannel announcementChannel = guild.getTextChannelById("1303106586650218518");
                if (announcementChannel == null) {
                    logger.warn("Breakup announcement channel not found with ID: 1303106586650218518");
                    return false;
                }
                
                // Validate that both users are members of the server
                Member member1 = validateServerMember(guild, user1DiscordId);
                Member member2 = validateServerMember(guild, user2DiscordId);
                
                if (member1 == null || member2 == null) {
                    logger.warn("Cannot send breakup announcement: one or both users are not server members");
                    return false;
                }
                
                // Create the breakup announcement embed
                EmbedBuilder embed = buildBreakupAnnouncementEmbed(member1, member2);
                
                // Send the announcement
                announcementChannel.sendMessageEmbeds(embed.build()).queue(
                    success -> logger.info("Successfully sent breakup announcement for pairing {} to channel {}", 
                                          pairingId, announcementChannel.getName()),
                    error -> logger.warn("Failed to send breakup announcement for pairing {}: {}", 
                                        pairingId, error.getMessage())
                );
                
                return true;
                
            } catch (Exception e) {
                logger.error("Error sending breakup announcement for users {} and {}: {}", 
                           user1DiscordId, user2DiscordId, e.getMessage());
                return false;
            }
        }).orTimeout(15, TimeUnit.SECONDS)
          .exceptionally(throwable -> {
              logger.error("Timeout or error in breakup announcement: {}", throwable.getMessage());
              return false;
          });
    }

    /**
     * Build the breakup announcement embed
     */
    private EmbedBuilder buildBreakupAnnouncementEmbed(Member member1, Member member2) {
        EmbedBuilder embed = new EmbedBuilder();
        
        embed.setColor(Color.decode("#ff6b6b")); // Red color for breakup
        embed.setDescription(member1.getAsMention() + " and " + member2.getAsMention() + " have just broken up! 😔💔");
        
        return embed;
    }
    
    /**
     * Sends an achievement notification embed to a pairing channel
     * 
     * @param channelId Discord channel ID of the pairing channel
     * @param user1DiscordId Discord ID of first user
     * @param user2DiscordId Discord ID of second user
     * @param achievementName Name of the achievement unlocked
     * @param achievementDescription Description of the achievement
     * @param xpAwarded XP points awarded for the achievement
     * @param achievementRarity Rarity level of the achievement (bronze, silver, gold, diamond)
     * @param progressValue The progress value when achievement was unlocked
     * @return CompletableFuture containing notification result
     */
    public CompletableFuture<Boolean> sendAchievementNotification(String channelId, String user1DiscordId, 
                                                                  String user2DiscordId, String achievementName,
                                                                  String achievementDescription, int xpAwarded,
                                                                  String achievementRarity, int progressValue) {
        if (!achievementNotificationsEnabled) {
            logger.debug("Achievement notifications are disabled, skipping notification");
            return CompletableFuture.completedFuture(false);
        }
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("Sending achievement notification to channel {}: {}", channelId, achievementName);
                
                // Input validation
                if (channelId == null || channelId.trim().isEmpty()) {
                    logger.warn("Cannot send achievement notification: channelId is null or empty");
                    return false;
                }
                
                if (!isValidDiscordId(channelId)) {
                    logger.warn("Cannot send achievement notification: invalid channel ID format");
                    return false;
                }
                
                // Validate Discord user IDs
                String validUser1Id = user1DiscordId;
                String validUser2Id = user2DiscordId;
                
                if (validUser1Id != null && !isValidDiscordId(validUser1Id)) {
                    logger.warn("Invalid user1 Discord ID format: {}", validUser1Id);
                    validUser1Id = null;
                }
                
                if (validUser2Id != null && !isValidDiscordId(validUser2Id)) {
                    logger.warn("Invalid user2 Discord ID format: {}", validUser2Id);
                    validUser2Id = null;
                }
                
                Guild guild = getGuild();
                if (guild == null) {
                    logger.warn("Cannot send achievement notification: Discord server not accessible");
                    return false;
                }
                
                TextChannel channel = guild.getTextChannelById(channelId);
                if (channel == null) {
                    logger.warn("Achievement notification channel not found: {}", channelId);
                    return false;
                }
                
                // Build achievement embed
                EmbedBuilder embed = buildAchievementNotificationEmbed(
                    validUser1Id, validUser2Id, achievementName, achievementDescription, 
                    xpAwarded, achievementRarity, progressValue, guild);
                
                if (embed == null) {
                    logger.warn("Failed to build achievement embed for channel {}", channelId);
                    return false;
                }
                
                // Send the achievement notification
                channel.sendMessageEmbeds(embed.build()).queue(
                    success -> logger.info("Successfully sent achievement notification '{}' to channel {}", 
                                          achievementName, channelId),
                    error -> logger.warn("Failed to send achievement notification '{}' to channel {}: {}", 
                                        achievementName, channelId, error.getMessage())
                );
                
                return true;
                
            } catch (Exception e) {
                logger.error("Error sending achievement notification to channel {}: {}", channelId, e.getMessage());
                return false;
            }
        }).orTimeout(15, TimeUnit.SECONDS)
          .exceptionally(throwable -> {
              logger.error("Timeout or error in achievement notification: {}", throwable.getMessage());
              return false;
          });
    }
    
    /**
     * Build the achievement notification embed with proper styling and information
     */
    private EmbedBuilder buildAchievementNotificationEmbed(String user1DiscordId, String user2DiscordId,
                                                           String achievementName, String achievementDescription,
                                                           int xpAwarded, String achievementRarity, int progressValue,
                                                           Guild guild) {
        try {
            EmbedBuilder embed = new EmbedBuilder();
            
            // Set achievement title with celebration emoji
            embed.setTitle("🎉 Achievement Unlocked!");
            
            // Set color based on achievement rarity
            embed.setColor(getRarityColor(achievementRarity));
            
            // Add main achievement information
            embed.addField("🏆 " + achievementName, achievementDescription, false);
            
            // Add XP reward information
            embed.addField("✨ XP Reward", String.format("**+%,d XP**", xpAwarded), true);
            
            // Add rarity information with appropriate emoji
            String rarityDisplay = getRarityDisplayName(achievementRarity);
            embed.addField("💎 Rarity", rarityDisplay, true);
            
            // Add timestamp
            embed.setTimestamp(Instant.now());
            
            return embed;
            
        } catch (Exception e) {
            logger.error("Failed to build achievement notification embed: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Get Discord color based on achievement rarity
     */
    private Color getRarityColor(String rarity) {
        if (rarity == null) {
            return Color.decode("#58b9ff"); // Default blue
        }
        
        return switch (rarity.toLowerCase()) {
            case "bronze" -> Color.decode("#CD7F32"); // Bronze color
            case "silver" -> Color.decode("#C0C0C0"); // Silver color
            case "gold" -> Color.decode("#FFD700");   // Gold color
            case "diamond" -> Color.decode("#B9F2FF"); // Diamond blue
            case "legendary" -> Color.decode("#FF6B35"); // Legendary orange
            default -> Color.decode("#58b9ff"); // Default blue
        };
    }
    
    /**
     * Get display name for achievement rarity with emoji
     */
    private String getRarityDisplayName(String rarity) {
        if (rarity == null) {
            return "🔵 Common";
        }
        
        return switch (rarity.toLowerCase()) {
            case "bronze" -> "🥉 Bronze";
            case "silver" -> "🥈 Silver";
            case "gold" -> "🥇 Gold";
            case "diamond" -> "💎 Diamond";
            case "legendary" -> "🌟 Legendary";
            default -> "🔵 Common";
        };
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