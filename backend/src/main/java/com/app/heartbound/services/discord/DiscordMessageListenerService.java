package com.app.heartbound.services.discord;

import com.app.heartbound.entities.Pairing;
import com.app.heartbound.repositories.pairing.PairingRepository;
import com.app.heartbound.services.pairing.PairLevelService;
import com.app.heartbound.services.pairing.AchievementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * DiscordMessageListenerService
 * 
 * Service that listens to Discord messages and updates pairing activity.
 * Tracks message counts, word counts, emoji usage, and triggers XP/achievement systems.
 * 
 * Enhanced with message validation to prevent spam and abuse:
 * - Message length validation (minimum 15 characters)
 * - User cooldown system (30-second cooldown between messages)
 * - Rate limiting within time windows
 * - Periodic cleanup of stale tracking data
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DiscordMessageListenerService extends ListenerAdapter {

    private final PairingRepository pairingRepository;
    private final PairLevelService pairLevelService;
    private final AchievementService achievementService;
    private final SimpMessagingTemplate messagingTemplate;
    
    // Callback for Discord leaderboard refresh (set by PairingService to avoid circular dependency)
    private Consumer<Long> discordLeaderboardRefreshCallback;

    // 🚀 NEW: Message validation configuration parameters
    @Value("${discord.pairing.validation.enabled:true}")
    private boolean validationEnabled;
    
    @Value("${discord.pairing.validation.cooldown-seconds:30}")
    private int cooldownSeconds;
    
    @Value("${discord.pairing.validation.min-message-length:15}")
    private int minMessageLength;
    
    @Value("${discord.pairing.validation.time-window-minutes:60}")
    private int timeWindowMinutes;
    
    @Value("${discord.pairing.validation.message-threshold:10}")
    private int messageThreshold;
    
    // 🚀 NEW: Thread-safe tracking maps for message validation
    
    /**
     * Track user cooldowns to prevent rapid-fire messaging
     * Key: userId, Value: timestamp of last valid message
     */
    private final ConcurrentHashMap<String, Instant> userCooldowns = new ConcurrentHashMap<>();
    
    /**
     * Track user activity within time windows for rate limiting
     * Key: userId, Value: list of message timestamps within current window
     */
    private final ConcurrentHashMap<String, List<Instant>> userActivity = new ConcurrentHashMap<>();
    
    /**
     * Scheduled executor for periodic cleanup of stale validation data
     */
    private ScheduledExecutorService validationCleanupScheduler;

    /**
     * Initialize message validation system
     * Sets up periodic cleanup of stale validation data
     */
    @PostConstruct
    public void initializeValidation() {
        if (validationEnabled) {
            // Schedule periodic cleanup of stale validation data
            validationCleanupScheduler = Executors.newSingleThreadScheduledExecutor();
            validationCleanupScheduler.scheduleAtFixedRate(this::cleanupStaleValidationData, 
                    timeWindowMinutes, timeWindowMinutes, TimeUnit.MINUTES);
            log.info("Discord pairing message validation initialized - cooldown: {}s, minLength: {}, timeWindow: {}min", 
                    cooldownSeconds, minMessageLength, timeWindowMinutes);
        } else {
            log.info("Discord pairing message validation is DISABLED");
        }
    }
    
    /**
     * Cleanup validation system resources
     * Shuts down the scheduled executor gracefully
     */
    @PreDestroy
    public void shutdownValidation() {
        if (validationCleanupScheduler != null) {
            validationCleanupScheduler.shutdown();
            try {
                if (!validationCleanupScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    validationCleanupScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                validationCleanupScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            log.info("Discord pairing message validation shutdown completed");
        }
    }
    
    /**
     * Clean up stale validation data outside the time window
     * Removes old cooldown timestamps and activity records to prevent memory leaks
     */
    private void cleanupStaleValidationData() {
        try {
            Instant cutoffTime = Instant.now().minusSeconds(timeWindowMinutes * 60);
            
            // Clean up old cooldown entries (beyond the time window)
            int cleanedCooldowns = (int) userCooldowns.entrySet().stream()
                    .filter(entry -> entry.getValue().isBefore(cutoffTime))
                    .count();
            userCooldowns.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoffTime));
            
            // Clean up old activity entries and remove empty lists
            int cleanedActivityEntries = 0;
            for (Map.Entry<String, List<Instant>> entry : userActivity.entrySet()) {
                List<Instant> userMessages = entry.getValue();
                userMessages.removeIf(timestamp -> timestamp.isBefore(cutoffTime));
            }
            cleanedActivityEntries = (int) userActivity.entrySet().stream()
                    .filter(entry -> entry.getValue().isEmpty())
                    .count();
            userActivity.entrySet().removeIf(entry -> entry.getValue().isEmpty());
            
            if (cleanedCooldowns > 0 || cleanedActivityEntries > 0) {
                log.debug("Validation cleanup: removed {} stale cooldowns and {} empty activity entries", 
                         cleanedCooldowns, cleanedActivityEntries);
            }
            
        } catch (Exception e) {
            log.error("Error during validation data cleanup: {}", e.getMessage());
        }
    }
    
    /**
     * Validate message against spam prevention rules
     * 
     * @param userId Discord user ID
     * @param messageContent Raw message content
     * @param now Current timestamp
     * @return true if message passes validation, false if it should be ignored
     */
    private boolean validateMessage(String userId, String messageContent, Instant now) {
        if (!validationEnabled) {
            return true; // Skip validation if disabled
        }
        
        // 1. Check minimum message length
        if (messageContent.length() < minMessageLength) {
            log.debug("[VALIDATION] Message from user {} rejected: too short ({} chars, min: {})", 
                     userId, messageContent.length(), minMessageLength);
            return false;
        }
        
        // 2. Check user cooldown
        Instant lastMessageTime = userCooldowns.get(userId);
        if (lastMessageTime != null && now.isBefore(lastMessageTime.plusSeconds(cooldownSeconds))) {
            long remainingCooldown = lastMessageTime.plusSeconds(cooldownSeconds).getEpochSecond() - now.getEpochSecond();
            log.debug("[VALIDATION] Message from user {} rejected: on cooldown ({}s remaining)", 
                     userId, remainingCooldown);
            return false;
        }
        
        // 3. Check rate limiting within time window
        List<Instant> userMessages = userActivity.computeIfAbsent(userId, k -> new ArrayList<>());
        
        // Remove messages outside the time window
        Instant cutoff = now.minusSeconds(timeWindowMinutes * 60);
        userMessages.removeIf(timestamp -> timestamp.isBefore(cutoff));
        
        // Check if user exceeds message threshold within time window
        if (userMessages.size() >= messageThreshold) {
            log.debug("[VALIDATION] Message from user {} rejected: exceeded threshold ({} messages in {}min window)", 
                     userId, userMessages.size(), timeWindowMinutes);
            return false;
        }
        
        // 4. Validation passed - update tracking data
        userCooldowns.put(userId, now);
        userMessages.add(now);
        
        log.debug("[VALIDATION] Message from user {} validated successfully ({} chars, {} messages in window)", 
                 userId, messageContent.length(), userMessages.size());
        return true;
    }

    @Override
    @Transactional
    public void onMessageReceived(MessageReceivedEvent event) {
        // Ignore bot messages to prevent infinite loops
        if (event.getAuthor().isBot()) {
            return;
        }

        // Only process text channel messages
        if (!event.isFromGuild() || !event.getChannelType().isMessage()) {
            return;
        }

        // 🚀 NEW: Message validation - prevent spam and abuse
        String authorId = event.getAuthor().getId();
        String messageContent = event.getMessage().getContentRaw();
        Instant now = Instant.now();
        
        if (!validateMessage(authorId, messageContent, now)) {
            // Message failed validation - ignore silently to prevent spam feedback loops
            return;
        }

        try {
            // Get channel ID and convert to Long for database lookup
            long channelId = event.getChannel().getIdLong();

            // Find pairing by Discord channel ID
            Optional<Pairing> pairingOpt = pairingRepository.findByDiscordChannelId(channelId);
            
            if (pairingOpt.isEmpty()) {
                // This is not a pairing channel, ignore
                return;
            }

            Pairing pairing = pairingOpt.get();
            
            // Only track messages for active pairings
            if (!pairing.isActive()) {
                log.debug("Ignoring message in inactive pairing channel: {}", channelId);
                return;
            }

            // Determine which user sent the message and increment their count
            boolean messageCountUpdated = false;
            
            if (authorId.equals(pairing.getUser1Id())) {
                pairing.setUser1MessageCount(pairing.getUser1MessageCount() + 1);
                messageCountUpdated = true;
                log.debug("Incremented user1 message count for pairing {} (channel: {})", pairing.getId(), channelId);
            } else if (authorId.equals(pairing.getUser2Id())) {
                pairing.setUser2MessageCount(pairing.getUser2MessageCount() + 1);
                messageCountUpdated = true;
                log.debug("Incremented user2 message count for pairing {} (channel: {})", pairing.getId(), channelId);
            } else {
                // Message from someone not in the pairing (shouldn't happen in private channels)
                log.warn("Message in pairing channel {} from unknown user: {}", channelId, authorId);
                return;
            }

            if (messageCountUpdated) {
                // Update total message count
                pairing.setMessageCount(pairing.getUser1MessageCount() + pairing.getUser2MessageCount());
                
                // Save the updated pairing
                pairingRepository.save(pairing);
                
                // 🚀 XP SYSTEM: Update XP and check achievements after message activity
                try {
                    // Update pair level based on new message count
                    pairLevelService.updatePairLevelFromActivity(pairing.getId());
                    
                    // Check for new achievements (every 100 messages to avoid spam)
                    if (pairing.getMessageCount() % 100 == 0) {
                        achievementService.checkAndUnlockAchievements(pairing.getId());
                    }
                    
                    log.debug("Updated XP system for pairing {} after Discord message", pairing.getId());
                } catch (Exception e) {
                    log.error("Failed to update XP system for pairing {}: {}", pairing.getId(), e.getMessage());
                }
                
                // 🔥 REAL-TIME UPDATES: Broadcast activity update via WebSocket
                try {
                    broadcastActivityUpdate(pairing);
                } catch (Exception e) {
                    log.error("Failed to broadcast message activity update for pairing {}: {}", pairing.getId(), e.getMessage());
                }
                
                // 🚀 NEW: Refresh Discord leaderboard after message activity
                if (discordLeaderboardRefreshCallback != null) {
                    try {
                        discordLeaderboardRefreshCallback.accept(pairing.getId());
                    } catch (Exception e) {
                        log.error("Failed to refresh Discord leaderboard for pairing {}: {}", pairing.getId(), e.getMessage());
                    }
                }
                
                log.info("Updated message counts for pairing {}: user1={}, user2={}, total={}", 
                    pairing.getId(), 
                    pairing.getUser1MessageCount(), 
                    pairing.getUser2MessageCount(), 
                    pairing.getMessageCount());
            }

        } catch (Exception e) {
            log.error("Error processing Discord message for channel {}: {}", 
                event.getChannel().getId(), e.getMessage(), e);
        }
    }

    /**
     * Broadcast activity update to both users in the pairing via WebSocket
     */
    private void broadcastActivityUpdate(Pairing pairing) {
        try {
            // Create activity update payload
            Map<String, Object> activityUpdate = Map.of(
                "eventType", "ACTIVITY_UPDATE",
                "pairing", Map.of(
                    "id", pairing.getId(),
                    "messageCount", pairing.getMessageCount(),
                    "user1MessageCount", pairing.getUser1MessageCount(),
                    "user2MessageCount", pairing.getUser2MessageCount()
                ),
                "message", "Message activity updated",
                "timestamp", LocalDateTime.now().toString()
            );

            // Send to both users
            messagingTemplate.convertAndSend("/user/" + pairing.getUser1Id() + "/topic/pairings", activityUpdate);
            messagingTemplate.convertAndSend("/user/" + pairing.getUser2Id() + "/topic/pairings", activityUpdate);

            log.debug("Broadcasted message activity update for pairing {} to users {} and {}", 
                    pairing.getId(), pairing.getUser1Id(), pairing.getUser2Id());

        } catch (Exception e) {
            log.error("Failed to broadcast activity update for pairing {}: {}", 
                    pairing.getId(), e.getMessage());
        }
    }

    /**
     * Set the Discord leaderboard refresh callback (called by PairingService to avoid circular dependency)
     */
    public void setDiscordLeaderboardRefreshCallback(Consumer<Long> callback) {
        this.discordLeaderboardRefreshCallback = callback;
    }
} 