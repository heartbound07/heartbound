package com.app.heartbound.services.discord;

import com.app.heartbound.entities.Pairing;
import com.app.heartbound.entities.PairLevel;
import com.app.heartbound.repositories.pairing.PairingRepository;
import com.app.heartbound.services.pairing.PairLevelService;
import com.app.heartbound.services.pairing.AchievementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.awt.Color;
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
 * 
 * NEW: Pair XP System - Awards XP to pairs for Discord message activity
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
    
    // 🎉 NEW: Pair XP System configuration parameters
    @Value("${discord.pairing.xp.enabled:true}")
    private boolean pairXpEnabled;
    
    @Value("${discord.pairing.xp.per-message:10}")
    private int xpPerMessage;
    
    @Value("${discord.pairing.xp.cooldown-seconds:45}")
    private int xpCooldownSeconds;
    
    @Value("${discord.pairing.xp.min-message-length:20}")
    private int xpMinMessageLength;
    
    @Value("${discord.pairing.xp.max-per-hour:50}")
    private int maxXpPerHour;
    
    @Value("${discord.pairing.xp.show-embeds:true}")
    private boolean showXpEmbeds;
    
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
    
    // 🎉 NEW: Thread-safe tracking maps for pair XP system
    
    /**
     * Track per-user XP cooldowns for pairing channels to prevent XP farming
     * Key: userId, Value: timestamp of last XP-earning message
     */
    private final ConcurrentHashMap<String, Instant> userXpCooldowns = new ConcurrentHashMap<>();
    
    /**
     * Track XP awarded per pairing per hour for rate limiting
     * Key: pairingId, Value: list of XP award timestamps within current hour
     */
    private final ConcurrentHashMap<Long, List<Instant>> pairingXpActivity = new ConcurrentHashMap<>();
    
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
        
        // 🎉 NEW: Log pair XP system status
        if (pairXpEnabled) {
            log.info("Discord pairing XP system ENABLED - XP per message: {}, cooldown: {}s, minLength: {}, maxPerHour: {}, embeds: {}", 
                    xpPerMessage, xpCooldownSeconds, xpMinMessageLength, maxXpPerHour, showXpEmbeds ? "ON" : "OFF");
        } else {
            log.info("Discord pairing XP system is DISABLED");
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
            Instant xpCutoffTime = Instant.now().minusSeconds(3600); // 1 hour for XP tracking
            
            // Clean up old cooldown entries (beyond the time window)
            int cleanedCooldowns = (int) userCooldowns.entrySet().stream()
                    .filter(entry -> entry.getValue().isBefore(cutoffTime))
                    .count();
            userCooldowns.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoffTime));
            
            // 🎉 NEW: Clean up old XP cooldown entries
            int cleanedXpCooldowns = (int) userXpCooldowns.entrySet().stream()
                    .filter(entry -> entry.getValue().isBefore(xpCutoffTime))
                    .count();
            userXpCooldowns.entrySet().removeIf(entry -> entry.getValue().isBefore(xpCutoffTime));
            
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
            
            // 🎉 NEW: Clean up old pairing XP activity entries
            int cleanedXpActivityEntries = 0;
            for (Map.Entry<Long, List<Instant>> entry : pairingXpActivity.entrySet()) {
                List<Instant> xpTimestamps = entry.getValue();
                xpTimestamps.removeIf(timestamp -> timestamp.isBefore(xpCutoffTime));
            }
            cleanedXpActivityEntries = (int) pairingXpActivity.entrySet().stream()
                    .filter(entry -> entry.getValue().isEmpty())
                    .count();
            pairingXpActivity.entrySet().removeIf(entry -> entry.getValue().isEmpty());
            
            if (cleanedCooldowns > 0 || cleanedActivityEntries > 0 || cleanedXpCooldowns > 0 || cleanedXpActivityEntries > 0) {
                log.debug("Validation cleanup: removed {} cooldowns, {} activity entries, {} XP cooldowns, {} XP activity entries", 
                         cleanedCooldowns, cleanedActivityEntries, cleanedXpCooldowns, cleanedXpActivityEntries);
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

    // 🎉 NEW: Validate message for XP eligibility with stricter requirements
    /**
     * Validate message for XP eligibility with stricter anti-farming rules
     * 
     * @param userId Discord user ID
     * @param messageContent Raw message content
     * @param pairingId The pairing ID for XP tracking
     * @param now Current timestamp
     * @return true if message is eligible for XP, false otherwise
     */
    private boolean validateMessageForXP(String userId, String messageContent, Long pairingId, Instant now) {
        if (!pairXpEnabled) {
            return false; // XP system disabled
        }
        
        // 1. Check minimum message length for XP (stricter than basic validation)
        if (messageContent.length() < xpMinMessageLength) {
            log.debug("[XP VALIDATION] Message from user {} not eligible for XP: too short ({} chars, min: {})", 
                     userId, messageContent.length(), xpMinMessageLength);
            return false;
        }
        
        // 2. Check XP-specific cooldown (stricter than basic message cooldown)
        Instant lastXpTime = userXpCooldowns.get(userId);
        if (lastXpTime != null && now.isBefore(lastXpTime.plusSeconds(xpCooldownSeconds))) {
            long remainingCooldown = lastXpTime.plusSeconds(xpCooldownSeconds).getEpochSecond() - now.getEpochSecond();
            log.debug("[XP VALIDATION] Message from user {} not eligible for XP: on XP cooldown ({}s remaining)", 
                     userId, remainingCooldown);
            return false;
        }
        
        // 3. Check pairing XP rate limiting (prevent excessive XP farming)
        List<Instant> pairingXpTimestamps = pairingXpActivity.computeIfAbsent(pairingId, k -> new ArrayList<>());
        
        // Remove XP timestamps outside the 1-hour window
        Instant xpCutoff = now.minusSeconds(3600); // 1 hour
        pairingXpTimestamps.removeIf(timestamp -> timestamp.isBefore(xpCutoff));
        
        // Check if pairing has exceeded XP limit for this hour
        int currentHourXpCount = pairingXpTimestamps.size();
        if (currentHourXpCount >= maxXpPerHour) {
            log.debug("[XP VALIDATION] Pairing {} has reached max XP per hour ({}/{}), message not eligible for XP", 
                     pairingId, currentHourXpCount, maxXpPerHour);
            return false;
        }
        
        // 4. XP validation passed - update tracking data
        userXpCooldowns.put(userId, now);
        pairingXpTimestamps.add(now);
        
        log.debug("[XP VALIDATION] Message from user {} eligible for XP: {} chars, pairing XP count: {}/{}", 
                 userId, messageContent.length(), currentHourXpCount + 1, maxXpPerHour);
        return true;
    }

    // 🎉 NEW: Award XP to the pairing for valid message activity
    /**
     * Award XP to the pairing for message activity with optional Discord embed notification
     * 
     * @param pairing The pairing to award XP to
     * @param authorId The Discord user ID of the message author
     * @param messageContent The message content for context
     * @param messageChannel The Discord channel to send embeds to
     */
    private void awardPairXP(Pairing pairing, String authorId, String messageContent, MessageChannel messageChannel) {
        try {
            String reason = String.format("Discord message activity from user %s", authorId);
            
            // Get current pair level data before XP award
            Optional<PairLevel> pairLevelOpt = pairLevelService.getPairLevel(pairing.getId());
            int oldLevel = pairLevelOpt.map(PairLevel::getCurrentLevel).orElse(1);
            
            // Award XP to the pair using PairLevelService
            PairLevel updatedPairLevel = pairLevelService.addXP(pairing.getId(), xpPerMessage, reason);
            
            // Check if level up occurred
            boolean leveledUp = updatedPairLevel.getCurrentLevel() > oldLevel;
            
            // 🎨 DISCORD EMBED: Send XP notification embed if enabled
            if (showXpEmbeds && messageChannel != null) {
                sendPairXPEmbed(pairing, authorId, updatedPairLevel, xpPerMessage, leveledUp, oldLevel, messageChannel);
            }
            
            log.debug("[PAIR XP] Awarded {} XP to pairing {} for message from user {} ({} chars) - Level: {} -> {}", 
                     xpPerMessage, pairing.getId(), authorId, messageContent.length(), oldLevel, updatedPairLevel.getCurrentLevel());
            
        } catch (Exception e) {
            log.error("[PAIR XP] Failed to award XP to pairing {} for message from user {}: {}", 
                     pairing.getId(), authorId, e.getMessage());
        }
    }

    /**
     * Send Discord embed notification for pair XP gain following ChatActivityListener theme
     * 
     * @param pairing The pairing that gained XP
     * @param authorId The Discord user ID who triggered the XP gain
     * @param pairLevel Updated pair level data
     * @param xpGained Amount of XP gained
     * @param leveledUp Whether a level up occurred
     * @param oldLevel Previous level before XP gain
     * @param messageChannel The Discord channel to send the embed to
     */
    private void sendPairXPEmbed(Pairing pairing, String authorId, PairLevel pairLevel, int xpGained, 
                                boolean leveledUp, int oldLevel, MessageChannel messageChannel) {
        try {
            EmbedBuilder embed = new EmbedBuilder();
            
            if (leveledUp) {
                // 🎉 LEVEL UP EMBED - Similar to ChatActivityListener level up style
                embed.setTitle("💕 Pair Level Up Achievement!");
                embed.setDescription(String.format("Congratulations <@%s> and <@%s>! Your pair advanced to **Level %d**! 💪", 
                                                   pairing.getUser1Id(), pairing.getUser2Id(), pairLevel.getCurrentLevel()));
                embed.setColor(new Color(255, 215, 0)); // Gold color for level up
                
                // Add level progression information
                embed.addField("Level Progress", String.format("Level %d → **Level %d**", oldLevel, pairLevel.getCurrentLevel()), true);
                embed.addField("XP Gained", String.format("💫 +%d XP", xpGained), true);
                embed.addField("Total XP", String.format("🔥 %d XP", pairLevel.getTotalXP()), true);
                
                // Add progress to next level
                embed.addField("Next Level Progress", 
                               String.format("%d/%d XP", pairLevel.getCurrentLevelXP(), pairLevel.getNextLevelXP()), false);
                
                // Set timestamp
                embed.setTimestamp(Instant.now());
                
                log.debug("[PAIR XP EMBED] Sending level up embed for pairing {} (Level {} -> {})", 
                         pairing.getId(), oldLevel, pairLevel.getCurrentLevel());
                
            } else {
                // 💫 REGULAR XP GAIN EMBED - Similar to ChatActivityListener XP notification style
                embed.setDescription(String.format("💕 <@%s> and <@%s>! Your pair gained **%d XP**!", 
                                                   pairing.getUser1Id(), pairing.getUser2Id(), xpGained));
                embed.setColor(new Color(75, 181, 67)); // Green color matching ChatActivityListener
                
                // Add XP progress footer - similar to ChatActivityListener format
                embed.setFooter(String.format("%d/%d XP to next level", 
                               pairLevel.getCurrentLevelXP(), pairLevel.getNextLevelXP()));
                
                log.debug("[PAIR XP EMBED] Sending XP notification embed for pairing {} (+{} XP)", 
                         pairing.getId(), xpGained);
            }
            
            // Send the embed to the pairing channel
            messageChannel.sendMessageEmbeds(embed.build()).queue(
                success -> log.debug("[PAIR XP EMBED] Successfully sent {} embed for pairing {}", 
                                    leveledUp ? "level up" : "XP", pairing.getId()),
                error -> log.error("[PAIR XP EMBED] Failed to send {} embed for pairing {}: {}", 
                                 leveledUp ? "level up" : "XP", pairing.getId(), error.getMessage())
            );
            
        } catch (Exception e) {
            log.error("[PAIR XP EMBED] Error creating/sending XP embed for pairing {}: {}", 
                     pairing.getId(), e.getMessage());
        }
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
                
                // 🎉 NEW: Award XP to pair for message activity (before other XP system updates)
                if (validateMessageForXP(authorId, messageContent, pairing.getId(), now)) {
                    awardPairXP(pairing, authorId, messageContent, event.getChannel());
                }
                
                // 🚀 XP SYSTEM: Update XP and check achievements after message activity
                try {
                    // Update pair level based on new message count (milestone-based XP)
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