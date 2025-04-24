package com.app.heartbound.services.discord;

import com.app.heartbound.entities.User;
import com.app.heartbound.services.UserService;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
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
    
    // Track user cooldowns - userId -> lastMessageTimestamp
    private final ConcurrentHashMap<String, Instant> userCooldowns = new ConcurrentHashMap<>();
    
    // Track user activity - userId -> list of message timestamps
    private final ConcurrentHashMap<String, List<Instant>> userActivity = new ConcurrentHashMap<>();
    
    private ScheduledExecutorService cleanupScheduler;
    
    @Autowired
    public ChatActivityListener(UserService userService) {
        this.userService = userService;
    }
    
    @PostConstruct
    public void initialize() {
        logger.info("ChatActivityListener initialized with: enabled={}, messageThreshold={}, timeWindowMinutes={}, cooldownSeconds={}, minMessageLength={}, creditsToAward={}, levelingEnabled={}, xpToAward={}",
                activityEnabled, messageThreshold, timeWindowMinutes, cooldownSeconds, minMessageLength, creditsToAward, levelingEnabled, xpToAward);
        
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
    
    private void checkAndProcessLevelUp(User user, String userId, net.dv8tion.jda.api.entities.channel.middleman.MessageChannel channel) {
        if (!levelingEnabled) {
            return;
        }
        
        int currentLevel = user.getLevel() != null ? user.getLevel() : 1;
        int currentXp = user.getExperience() != null ? user.getExperience() : 0;
        int requiredXp = calculateRequiredXp(currentLevel);
        
        logger.debug("[XP DEBUG] Level check: User={}, Level={}, Current XP={}, Required XP={}", 
                    userId, currentLevel, currentXp, requiredXp);
        
        if (currentXp >= requiredXp) {
            logger.debug("[XP DEBUG] LEVEL UP! User {} is leveling up from {} to {}", 
                        userId, currentLevel, currentLevel + 1);
            user.setLevel(currentLevel + 1);
            user.setExperience(currentXp - requiredXp);
            
            try {
                userService.updateUser(user);
                
                // Get the achievement channel for level-up announcements
                String achievementChannelId = "1304293304833146951";
                net.dv8tion.jda.api.entities.channel.middleman.MessageChannel achievementChannel = 
                    channel.getJDA().getChannelById(net.dv8tion.jda.api.entities.channel.middleman.MessageChannel.class, achievementChannelId);
                
                if (achievementChannel != null) {
                    // Create an embed for the level-up announcement
                    EmbedBuilder embed = new EmbedBuilder();
                    embed.setTitle("Level Up Achievement!");
                    embed.setDescription(String.format("<@%s>! You advanced to level %d!", userId, currentLevel + 1));
                    embed.setColor(new Color(75, 181, 67)); // Green color
                    embed.setTimestamp(java.time.Instant.now());
                    
                    // Add XP progress information
                    int nextLevelXp = calculateRequiredXp(currentLevel + 1);
                    embed.addField("Experience", String.format("%d/%d XP to next level", user.getExperience(), nextLevelXp), true);
                    
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
                    String simpleNotification = String.format("🎉 <@%s> leveled up to **Level %d**! Check out <#%s> for details!",
                        userId, currentLevel + 1, achievementChannelId);
                    channel.sendMessage(simpleNotification).queue();
                } else {
                    // Fallback to the original channel if achievement channel not found
                    logger.warn("[XP DEBUG] Achievement channel {} not found, sending to original channel", achievementChannelId);
                    
                    String levelUpMessage = String.format("�� Congratulations <@%s>! You've reached **Level %d**!", 
                                                        userId, currentLevel + 1);
                    channel.sendMessage(levelUpMessage).queue(
                        success -> logger.debug("[XP DEBUG] Level up message sent for user {}", userId),
                        error -> logger.error("Failed to send level up message for user {}: {}", userId, error.getMessage())
                    );
                }
                
                logger.info("User {} leveled up to {} (XP: {} -> {})", 
                           userId, currentLevel + 1, currentXp, user.getExperience());
                
                // Check for additional level ups
                logger.debug("[XP DEBUG] Checking for additional level ups");
                checkAndProcessLevelUp(user, userId, channel);
                
            } catch (Exception e) {
                logger.error("Error updating user level for {}: {}", userId, e.getMessage(), e);
            }
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
            
            if (levelingEnabled) {
                logger.debug("[XP DEBUG] About to award XP to {}: Current XP={}, Level={}, Adding {} XP", 
                    userId, user.getExperience(), user.getLevel(), xpToAward);
                int currentXp = user.getExperience() != null ? user.getExperience() : 0;
                user.setExperience(currentXp + xpToAward);
                logger.debug("[XP DEBUG] Awarded {} XP to user {}. New XP: {}", xpToAward, userId, user.getExperience());
                
                // Add right before calling checkAndProcessLevelUp method
                logger.debug("[XP DEBUG] Checking for level up: User={}, Level={}, XP={}", 
                    userId, user.getLevel(), user.getExperience());
                checkAndProcessLevelUp(user, userId, event.getChannel());
            }
            
            // Track message in activity window
            List<Instant> userMessages = userActivity.computeIfAbsent(userId, k -> new ArrayList<>());
            
            // Remove messages outside the time window
            Instant cutoff = now.minusSeconds(timeWindowMinutes * 60);
            userMessages.removeIf(timestamp -> timestamp.isBefore(cutoff));
            
            // Add current message
            userMessages.add(now);
            
            // Check if threshold reached
            if (userMessages.size() >= messageThreshold) {
                // Award credits
                try {
                    int currentCredits = user.getCredits() != null ? user.getCredits() : 0;
                    int newCredits = currentCredits + creditsToAward;
                    
                    // Update user credits (note: this directly updates user and saves)
                    user.setCredits(newCredits);
                    userService.updateUser(user); // Using updateUser instead of updateUserCredits to avoid @PreAuthorize restriction
                    
                    logger.info("Awarded {} credits to user {} for chat activity. New balance: {}", 
                                creditsToAward, userId, newCredits);
                    
                    // Reset activity tracking for this user after awarding
                    userActivity.remove(userId);
                    
                } catch (Exception e) {
                    logger.error("Error awarding credits to user {}: {}", userId, e.getMessage(), e);
                }
            } else {
                logger.debug("User {} has {} messages in the activity window. Threshold is {}", 
                            userId, userMessages.size(), messageThreshold);
            }
            
        } catch (Exception e) {
            logger.error("Error processing message from user {}: {}", userId, e.getMessage(), e);
        }
    }
} 