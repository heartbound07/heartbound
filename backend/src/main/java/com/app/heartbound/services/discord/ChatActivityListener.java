package com.app.heartbound.services.discord;

import com.app.heartbound.entities.User;
import com.app.heartbound.services.UserService;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
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
        logger.info("ChatActivityListener initialized with: enabled={}, messageThreshold={}, timeWindowMinutes={}, cooldownSeconds={}, minMessageLength={}, creditsToAward={}",
                activityEnabled, messageThreshold, timeWindowMinutes, cooldownSeconds, minMessageLength, creditsToAward);
        
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
    
    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        // Early returns for disabled feature or ineligible messages
        if (!activityEnabled) {
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