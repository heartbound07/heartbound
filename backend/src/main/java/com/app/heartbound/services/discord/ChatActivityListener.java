package com.app.heartbound.services.discord;

import com.app.heartbound.entities.User;
import com.app.heartbound.services.UserService;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.awt.Color;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class ChatActivityListener extends ListenerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(ChatActivityListener.class);
    
    private final UserService userService;
    
    @Value("${discord.activity.enabled:true}")
    private boolean activityEnabled;
    
    @Value("${discord.activity.credits-to-award:5}")
    private int creditsToAward;
    
    @Value("${discord.activity.message-threshold:10}")
    private int messageThreshold;
    
    @Value("${discord.activity.time-window-minutes:60}")
    private int timeWindowMinutes;
    
    @Value("${discord.activity.cooldown-seconds:30}")
    private int cooldownSeconds;
    
    @Value("${discord.activity.min-message-length:15}")
    private int minMessageLength;
    
    @Value("${discord.leveling.enabled:true}")
    private boolean levelingEnabled;
    
    @Value("${discord.leveling.xp-to-award:15}")
    private int xpToAward;
    
    @Value("${discord.leveling.base-xp:100}")
    private int baseXp;
    
    @Value("${discord.leveling.level-multiplier:50}")
    private int levelMultiplier;
    
    @Value("${discord.leveling.level-exponent:2}")
    private int levelExponent;
    
    @Value("${discord.leveling.level-factor:5}")
    private int levelFactor;
    
    @Value("${discord.leveling.credits-per-level:50}")
    private int creditsPerLevel;
    
    // Track user cooldowns - userId -> lastMessageTimestamp
    private final ConcurrentHashMap<String, Instant> userCooldowns = new ConcurrentHashMap<>();
    
    // Track user activity - userId -> list of message timestamps
    private final ConcurrentHashMap<String, List<Instant>> userActivity = new ConcurrentHashMap<>();
    
    private ScheduledExecutorService cleanupScheduler;
    
    // Add these constants for role IDs at the top of the class
    private static final long LEVEL_5_ROLE_ID = 1161732022704816250L;
    private static final long LEVEL_15_ROLE_ID = 1162632126068437063L;
    private static final long LEVEL_30_ROLE_ID = 1162628059296432148L;
    private static final long LEVEL_40_ROLE_ID = 1162628114195697794L;
    private static final long LEVEL_50_ROLE_ID = 1166539666674167888L;
    private static final long LEVEL_70_ROLE_ID = 1170429914185465906L;
    private static final long LEVEL_100_ROLE_ID = 1162628179043823657L;
    
    @Autowired
    public ChatActivityListener(UserService userService) {
        this.userService = userService;
    }
    
    @PostConstruct
    public void initialize() {
        logger.info("ChatActivityListener initialized with: enabled={}, messageThreshold={}, timeWindowMinutes={}, cooldownSeconds={}, minMessageLength={}, levelingEnabled={}, xpToAward={}, creditsPerLevel={}",
                activityEnabled, messageThreshold, timeWindowMinutes, cooldownSeconds, minMessageLength, levelingEnabled, xpToAward, creditsPerLevel);
        
        // Start periodic cleanup of old tracking data
        cleanupScheduler = Executors.newSingleThreadScheduledExecutor();
        cleanupScheduler.scheduleAtFixedRate(this::cleanupOldActivityData, 
                10, 10, TimeUnit.MINUTES);
    }
    
    @PreDestroy
    public void shutdown() {
        if (cleanupScheduler != null) {
            cleanupScheduler.shutdownNow();
            logger.info("ChatActivityListener cleanup scheduler shut down");
        }
    }
    
    /**
     * Cleanup method to prevent memory leaks by removing old activity data
     */
    private void cleanupOldActivityData() {
        try {
            Instant cutoff = Instant.now().minusSeconds(timeWindowMinutes * 60);
            
            // Cleanup user cooldowns
            userCooldowns.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff));
            
            // Cleanup user activity
            userActivity.forEach((userId, timestamps) -> {
                timestamps.removeIf(timestamp -> timestamp.isBefore(cutoff));
            });
            
            // Remove empty activity lists
            userActivity.entrySet().removeIf(entry -> entry.getValue().isEmpty());
            
            logger.debug("Activity data cleanup completed. Active users in tracking: {}", userActivity.size());
        } catch (Exception e) {
            logger.error("Error during activity data cleanup", e);
        }
    }
    
    private int calculateRequiredXp(int level) {
        return baseXp + (levelFactor * (int)Math.pow(level, levelExponent)) + (levelMultiplier * level);
    }
    
    private void checkAndProcessLevelUp(User user, String userId, MessageChannel channel) {
        if (!levelingEnabled) {
            return;
        }
        
        int currentLevel = user.getLevel() != null ? user.getLevel() : 1;
        int currentXp = user.getExperience() != null ? user.getExperience() : 0;
        int requiredXp = calculateRequiredXp(currentLevel);
        
        logger.debug("[XP DEBUG] Level check: User={}, Level={}, Current XP={}, Required XP={}", 
                    userId, currentLevel, currentXp, requiredXp);
        
        if (currentXp >= requiredXp) {
            int newLevel = currentLevel + 1;
            logger.debug("[XP DEBUG] LEVEL UP! User {} is leveling up from {} to {}", 
                        userId, currentLevel, newLevel);
            user.setLevel(newLevel);
            user.setExperience(currentXp - requiredXp);
            
            // Award credits for leveling up
            int currentCredits = user.getCredits() != null ? user.getCredits() : 0;
            user.setCredits(currentCredits + creditsPerLevel);
            logger.info("Awarded {} credits to user {} for leveling up to {}. New balance: {}",
                        creditsPerLevel, userId, newLevel, user.getCredits());
            
            try {
                userService.updateUser(user);
                
                // Check if user reached a level milestone and assign role
                checkAndAssignRoleForLevel(newLevel, userId, channel);
                
                // Get the achievement channel for level-up announcements
                String achievementChannelId = "1304293304833146951";
                net.dv8tion.jda.api.entities.channel.middleman.MessageChannel achievementChannel = 
                    channel.getJDA().getChannelById(MessageChannel.class, achievementChannelId);
                
                if (achievementChannel != null) {
                    // Create an embed for the level-up announcement
                    EmbedBuilder embed = new EmbedBuilder();
                    embed.setTitle("Level Up Achievement!");
                    embed.setDescription(String.format("<@%s>! You advanced to level %d and earned %d credits!", 
                                                          userId, newLevel, creditsPerLevel));
                    embed.setColor(new Color(75, 181, 67)); // Green color
                    embed.setTimestamp(java.time.Instant.now());
                    
                    // Add XP progress information
                    int nextLevelXp = calculateRequiredXp(newLevel);
                    embed.addField("Experience", String.format("%d/%d XP to next level", user.getExperience(), nextLevelXp), true);
                    
                    // Add credits information
                    embed.addField("Credits Awarded", String.format("🪙 %d", creditsPerLevel), true);
                    
                    // Get the user's avatar if possible
                    net.dv8tion.jda.api.entities.User discordUser = channel.getJDA().getUserById(userId);
                    if (discordUser != null) {
                        embed.setThumbnail(discordUser.getEffectiveAvatarUrl());
                        embed.setAuthor(discordUser.getName(), null, discordUser.getEffectiveAvatarUrl());
                    }
                    
                    // Send the embed to the achievement channel
                    logger.debug("[XP DEBUG] Sending level up embed to achievement channel {}", achievementChannelId);
                    achievementChannel.sendMessageEmbeds(embed.build()).queue(
                        success -> logger.debug("[XP DEBUG] Level up embed sent for user {}", userId),
                        error -> logger.error("Failed to send level up embed for user {}: {}", userId, error.getMessage())
                    );
                    
                    // Also send a simple notification in the original channel
                    String simpleNotification = String.format("🎉 <@%s> leveled up to **Level %d** and earned **%d credits**! Check out <#%s> for details!",
                        userId, newLevel, creditsPerLevel, achievementChannelId);
                    channel.sendMessage(simpleNotification).queue();
                } else {
                    // Fallback to the original channel if achievement channel not found
                    logger.warn("[XP DEBUG] Achievement channel {} not found, sending to original channel", achievementChannelId);
                    
                    String levelUpMessage = String.format("Congratulations <@%s>! You've reached **Level %d** and earned **%d credits**!", 
                                                        userId, newLevel, creditsPerLevel);
                    channel.sendMessage(levelUpMessage).queue(
                        success -> logger.debug("[XP DEBUG] Level up message sent for user {}", userId),
                        error -> logger.error("Failed to send level up message for user {}: {}", userId, error.getMessage())
                    );
                }
                
                logger.info("User {} leveled up to {} (XP: {} -> {}, Credits: +{})", 
                           userId, newLevel, currentXp, user.getExperience(), creditsPerLevel);
                
                // Check for additional level ups
                logger.debug("[XP DEBUG] Checking for additional level ups");
                checkAndProcessLevelUp(user, userId, channel);
                
            } catch (Exception e) {
                logger.error("Error updating user level for {}: {}", userId, e.getMessage(), e);
            }
        }
    }
    
    /**
     * Checks if the user has reached a level milestone and assigns the appropriate role
     * 
     * @param level The new level the user has reached
     * @param userId The Discord ID of the user
     * @param channel The message channel for context
     */
    private void checkAndAssignRoleForLevel(int level, String userId, MessageChannel channel) {
        // Early return if not in a guild channel
        if (!(channel instanceof net.dv8tion.jda.api.entities.channel.concrete.TextChannel)) {
            logger.debug("[ROLE DEBUG] Not a guild channel, skipping role assignment for user {}", userId);
            return;
        }
        
        long roleId = 0L;
        
        // Determine which role to assign based on level
        if (level >= 100) {
            roleId = LEVEL_100_ROLE_ID;
        } else if (level >= 70) {
            roleId = LEVEL_70_ROLE_ID;
        } else if (level >= 50) {
            roleId = LEVEL_50_ROLE_ID;
        } else if (level >= 40) {
            roleId = LEVEL_40_ROLE_ID;
        } else if (level >= 30) {
            roleId = LEVEL_30_ROLE_ID;
        } else if (level >= 15) {
            roleId = LEVEL_15_ROLE_ID;
        } else if (level >= 5) {
            roleId = LEVEL_5_ROLE_ID;
        }
        
        // If no milestone reached, return
        if (roleId == 0L) {
            return;
        }
        
        // Get the guild and member
        net.dv8tion.jda.api.entities.Guild guild = 
            ((net.dv8tion.jda.api.entities.channel.concrete.TextChannel) channel).getGuild();
        
        try {
            // Get the role by ID
            net.dv8tion.jda.api.entities.Role role = guild.getRoleById(roleId);
            if (role == null) {
                logger.warn("[ROLE DEBUG] Role with ID {} not found in guild {}", roleId, guild.getId());
                return;
            }
            
            // Get the member and add the role
            guild.retrieveMemberById(userId).queue(member -> {
                // Only add the role if the member doesn't already have it
                if (!member.getRoles().contains(role)) {
                    guild.addRoleToMember(member, role).queue(
                        success -> {
                            logger.info("[ROLE DEBUG] Successfully added role {} to user {} for reaching level {}", 
                                       role.getName(), userId, level);
                            
                            // Send role achievement notification
                            EmbedBuilder roleEmbed = new EmbedBuilder();
                            roleEmbed.setTitle("Role Achievement Unlocked!");
                            roleEmbed.setDescription(String.format("<@%s> has earned the **%s** role for reaching Level %d!", 
                                                                  userId, role.getName(), level));
                            roleEmbed.setColor(role.getColor());
                            roleEmbed.setTimestamp(java.time.Instant.now());
                            
                            // Get achievement channel
                            String achievementChannelId = "1304293304833146951";
                            net.dv8tion.jda.api.entities.channel.middleman.MessageChannel achievementChannel = 
                                channel.getJDA().getChannelById(MessageChannel.class, achievementChannelId);
                            
                            if (achievementChannel != null) {
                                achievementChannel.sendMessageEmbeds(roleEmbed.build()).queue();
                            }
                        },
                        error -> logger.error("[ROLE DEBUG] Failed to add role {} to user {}: {}", 
                                            role.getName(), userId, error.getMessage())
                    );
                } else {
                    logger.debug("[ROLE DEBUG] User {} already has the role {} for level {}", 
                                userId, role.getName(), level);
                }
            }, error -> logger.error("[ROLE DEBUG] Failed to retrieve member {}: {}", userId, error.getMessage()));
            
        } catch (Exception e) {
            logger.error("[ROLE DEBUG] Error assigning role for level {} to user {}: {}", level, userId, e.getMessage(), e);
        }
    }
    
    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        // Early returns for disabled feature or ineligible messages
        if (!activityEnabled && !levelingEnabled) {
            return;
        }
        
        // Ignore messages from bots, DMs, or self
        if (!event.isFromGuild() || event.getAuthor().isBot() || 
            event.getAuthor().getId().equals(event.getJDA().getSelfUser().getId())) {
            return;
        }
        
        String userId = event.getAuthor().getId();
        String content = event.getMessage().getContentRaw();
        
        // Check minimum message length
        if (content.length() < minMessageLength) {
            logger.debug("Message from user {} ignored: too short ({} chars)", userId, content.length());
            return;
        }
        
        // Check cooldown
        Instant now = Instant.now();
        Instant lastMessageTime = userCooldowns.get(userId);
        if (lastMessageTime != null && now.isBefore(lastMessageTime.plusSeconds(cooldownSeconds))) {
            logger.debug("Message from user {} ignored: on cooldown", userId);
            return;
        }
        
        // Update cooldown timestamp
        userCooldowns.put(userId, now);
        
        try {
            // Check if user exists in database
            User user = userService.getUserById(userId);
            if (user == null) {
                logger.warn("User {} not found in database, cannot track activity", userId);
                return;
            }
            
            boolean userUpdated = false; // Flag to track if user needs saving
            String achievementChannelId = "1304293304833146951";
            MessageChannel achievementChannel = 
                event.getJDA().getChannelById(MessageChannel.class, achievementChannelId);
            
            if (achievementChannel == null) {
                logger.warn("Achievement channel with ID {} not found", achievementChannelId);
                return;
            }
            
            int awardedXp = 0;
            int initialLevel = user.getLevel() != null ? user.getLevel() : 1;
            
            if (levelingEnabled) {
                logger.debug("[XP DEBUG] About to award XP to {}: Current XP={}, Level={}, Adding {} XP",
                    userId, user.getExperience(), user.getLevel(), xpToAward);
                int currentXp = user.getExperience() != null ? user.getExperience() : 0;
                user.setExperience(currentXp + xpToAward);
                userUpdated = true; // Mark user for update
                awardedXp = xpToAward;
                logger.debug("[XP DEBUG] Awarded {} XP to user {}. New XP: {}", xpToAward, userId, user.getExperience());
                
                // Save user's level before potential level-up for later comparison
                int requiredXpForNextLevel = calculateRequiredXp(initialLevel);
                
                // Add right before calling checkAndProcessLevelUp method
                logger.debug("[XP DEBUG] Checking for level up: User={}, Level={}, XP={}",
                    userId, user.getLevel(), user.getExperience());
                checkAndProcessLevelUp(user, userId, event.getChannel()); // Note: checkAndProcessLevelUp calls updateUser internally on level up
                
                // If user didn't level up, send XP-only notification
                if (initialLevel == user.getLevel()) {
                    EmbedBuilder notificationEmbed = new EmbedBuilder();
                    notificationEmbed.setDescription(String.format("<@%s>! You have gained %d xp!", 
                                                userId, awardedXp));
                    notificationEmbed.setColor(new Color(75, 181, 67)); // Green color
                    
                    // Add footer showing XP progress
                    int currentXpAfterAward = user.getExperience() != null ? user.getExperience() : 0;
                    notificationEmbed.setFooter(String.format("%d/%d to next level", 
                            currentXpAfterAward, requiredXpForNextLevel));
                    
                    // Queue the embed send
                    achievementChannel.sendMessageEmbeds(notificationEmbed.build()).queue(
                        success -> logger.debug("Sent XP notification to user {}", userId),
                        failure -> logger.error("Failed to send XP notification to user {}: {}", userId, failure.getMessage())
                    );
                }
            }
            
            // Track message in activity window (for stats purposes only, not for credits)
            if (activityEnabled) {
                List<Instant> userMessages = userActivity.computeIfAbsent(userId, k -> new ArrayList<>());
                
                // Remove messages outside the time window
                Instant cutoff = now.minusSeconds(timeWindowMinutes * 60);
                userMessages.removeIf(timestamp -> timestamp.isBefore(cutoff));
                
                // Add current message
                userMessages.add(now);
                
                logger.debug("[ACTIVITY DEBUG] User {} has {} messages in window. Time window={} min", 
                            userId, userMessages.size(), timeWindowMinutes);
            }
            
            // Persist changes if XP was added but no level up happened
            // Level-up already saves changes internally in checkAndProcessLevelUp
            if (userUpdated && (user.getLevel() == null || calculateRequiredXp(user.getLevel()) > user.getExperience())) {
                try {
                    userService.updateUser(user);
                    logger.debug("Persisted user {} state after activity processing.", userId);
                } catch (Exception e) {
                    logger.error("Error saving user state for {} after activity processing: {}", userId, e.getMessage(), e);
                }
            }
            
        } catch (Exception e) {
            logger.error("Error processing message from user {}: {}", userId, e.getMessage(), e);
        }
    }
} 