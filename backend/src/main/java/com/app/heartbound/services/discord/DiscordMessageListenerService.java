package com.app.heartbound.services.discord;

import com.app.heartbound.entities.Pairing;
import com.app.heartbound.repositories.pairing.PairingRepository;
import com.app.heartbound.services.pairing.PairLevelService;
import com.app.heartbound.services.pairing.AchievementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * DiscordMessageListenerService
 * 
 * Service that listens to Discord messages and updates pairing activity.
 * Tracks message counts, word counts, emoji usage, and triggers XP/achievement systems.
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

        try {
            // Get channel ID and convert to Long for database lookup
            long channelId = event.getChannel().getIdLong();
            String authorId = event.getAuthor().getId();

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