package com.app.heartbound.services.discord;

import com.app.heartbound.dto.pairing.PairingDTO;
import com.app.heartbound.dto.UserProfileDTO;
import com.app.heartbound.entities.PairLevel;
import com.app.heartbound.services.UserService;
import com.app.heartbound.services.pairing.PairLevelService;
import com.app.heartbound.services.pairing.VoiceStreakService;
import com.app.heartbound.services.pairing.PairingService;
import com.app.heartbound.repositories.pairing.PairingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.awt.Color;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.List;

/**
 * DiscordLeaderboardService
 * 
 * Manages Discord leaderboard embeds for active pairings.
 * Automatically creates, updates, and removes pairing embeds in the configured Discord channel.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DiscordLeaderboardService {

    private static final Color DISCORD_BLURPLE = new Color(88, 101, 242);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MMM dd, yyyy");
    private static final String HEART_EMOJI = "♡";
    
    @Autowired
    private JDA jda;
    
    @Value("${discord.server.id}")
    private String discordServerId;
    
    @Value("${discord.leaderboard.channel.id:1381698742721187930}")
    private String leaderboardChannelId;
    
    @Value("${discord.leaderboard.enabled:true}")
    private boolean leaderboardEnabled;
    
    private final UserService userService;
    private final PairLevelService pairLevelService;
    private final VoiceStreakService voiceStreakService;
    private final PairingRepository pairingRepository;
    
    // Track Discord message IDs for each pairing embed
    private final ConcurrentHashMap<Long, String> pairingMessageMap = new ConcurrentHashMap<>();
    
    @PostConstruct
    public void initialize() {
        if (leaderboardEnabled) {
            log.info("Discord Leaderboard Service initialized - Channel ID: {}", leaderboardChannelId);
            
            // Delay initialization to allow JDA to be fully ready
            CompletableFuture.runAsync(() -> {
                try {
                    // Wait a bit for JDA to be fully ready
                    Thread.sleep(5000);
                    log.info("Starting delayed leaderboard initialization...");
                    refreshEntireLeaderboard().join();
                } catch (Exception e) {
                    log.error("Failed to initialize leaderboard on startup: {}", e.getMessage(), e);
                }
            });
        } else {
            log.info("Discord Leaderboard Service disabled via configuration");
        }
    }
    
    /**
     * Refresh the entire leaderboard with all active pairings
     */
    @Async
    public CompletableFuture<Void> refreshEntireLeaderboard() {
        if (!leaderboardEnabled) {
            return CompletableFuture.completedFuture(null);
        }
        
        return CompletableFuture.runAsync(() -> {
            try {
                log.info("Refreshing Discord leaderboard with all active pairings");
                
                // Get all active pairings
                List<com.app.heartbound.entities.Pairing> activePairings = pairingRepository.findByActiveTrue();
                
                if (activePairings.isEmpty()) {
                    log.info("No active pairings found to display in leaderboard");
                    return;
                }
                
                log.info("Found {} active pairings to add to leaderboard", activePairings.size());
                
                // Convert to DTOs and add to leaderboard
                for (com.app.heartbound.entities.Pairing pairing : activePairings) {
                    PairingDTO pairingDTO = mapToPairingDTO(pairing);
                    addOrUpdatePairingEmbed(pairingDTO).join(); // Wait for each to complete
                    
                    // Small delay to avoid Discord rate limiting
                    Thread.sleep(500);
                }
                
                log.info("Successfully refreshed leaderboard with {} pairings", activePairings.size());
                
            } catch (Exception e) {
                log.error("Failed to refresh entire leaderboard: {}", e.getMessage(), e);
            }
        });
    }
    
    /**
     * Map Pairing entity to PairingDTO (simplified version)
     */
    private PairingDTO mapToPairingDTO(com.app.heartbound.entities.Pairing pairing) {
        return PairingDTO.builder()
                .id(pairing.getId())
                .user1Id(pairing.getUser1Id())
                .user2Id(pairing.getUser2Id())
                .discordChannelId(pairing.getDiscordChannelId())
                .discordChannelName(pairing.getDiscordChannelName())
                .matchedAt(pairing.getMatchedAt())
                .messageCount(pairing.getMessageCount())
                .user1MessageCount(pairing.getUser1MessageCount())
                .user2MessageCount(pairing.getUser2MessageCount())
                .voiceTimeMinutes(pairing.getVoiceTimeMinutes())
                .wordCount(pairing.getWordCount())
                .emojiCount(pairing.getEmojiCount())
                .activeDays(pairing.getActiveDays())
                .compatibilityScore(pairing.getCompatibilityScore())
                .breakupInitiatorId(pairing.getBreakupInitiatorId())
                .breakupReason(pairing.getBreakupReason())
                .breakupTimestamp(pairing.getBreakupTimestamp())
                .mutualBreakup(pairing.isMutualBreakup())
                .active(pairing.isActive())
                .blacklisted(pairing.isBlacklisted())
                .user1Age(pairing.getUser1Age())
                .user1Gender(pairing.getUser1Gender())
                .user1Region(pairing.getUser1Region())
                .user1Rank(pairing.getUser1Rank())
                .user2Age(pairing.getUser2Age())
                .user2Gender(pairing.getUser2Gender())
                .user2Region(pairing.getUser2Region())
                .user2Rank(pairing.getUser2Rank())
                .build();
    }
    
    /**
     * Add or update a pairing embed in the Discord leaderboard
     */
    @Async
    public CompletableFuture<Boolean> addOrUpdatePairingEmbed(PairingDTO pairing) {
        if (!leaderboardEnabled || !pairing.isActive()) {
            return CompletableFuture.completedFuture(false);
        }
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("Adding/updating leaderboard embed for pairing ID: {}", pairing.getId());
                
                TextChannel channel = getLeaderboardChannel();
                if (channel == null) {
                    log.warn("Leaderboard channel not found: {}", leaderboardChannelId);
                    return false;
                }
                
                // Check if embed already exists
                String existingMessageId = pairingMessageMap.get(pairing.getId());
                MessageEmbed embed = buildPairingEmbed(pairing);
                
                if (embed == null) {
                    log.warn("Failed to build embed for pairing {}", pairing.getId());
                    return false;
                }
                
                if (existingMessageId != null) {
                    // Update existing message
                    return updateExistingEmbed(channel, existingMessageId, embed, pairing.getId());
                } else {
                    // Create new message
                    return createNewEmbed(channel, embed, pairing.getId());
                }
                
            } catch (Exception e) {
                log.error("Failed to add/update leaderboard embed for pairing {}: {}", 
                         pairing.getId(), e.getMessage(), e);
                return false;
            }
        });
    }
    
    /**
     * Remove a pairing embed from the Discord leaderboard
     */
    @Async
    public CompletableFuture<Boolean> removePairingEmbed(Long pairingId) {
        if (!leaderboardEnabled) {
            return CompletableFuture.completedFuture(false);
        }
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("Removing leaderboard embed for pairing ID: {}", pairingId);
                
                String messageId = pairingMessageMap.remove(pairingId);
                if (messageId == null) {
                    log.debug("No message found to remove for pairing {}", pairingId);
                    return true; // Not an error, just nothing to remove
                }
                
                TextChannel channel = getLeaderboardChannel();
                if (channel == null) {
                    log.warn("Leaderboard channel not found: {}", leaderboardChannelId);
                    return false;
                }
                
                // Delete the message
                channel.deleteMessageById(messageId).queue(
                    success -> log.debug("Successfully deleted leaderboard message for pairing {}", pairingId),
                    error -> log.warn("Failed to delete leaderboard message for pairing {}: {}", 
                                    pairingId, error.getMessage())
                );
                
                return true;
                
            } catch (Exception e) {
                log.error("Failed to remove leaderboard embed for pairing {}: {}", 
                         pairingId, e.getMessage(), e);
                return false;
            }
        });
    }
    
    /**
     * Build the Discord embed for a pairing
     */
    private MessageEmbed buildPairingEmbed(PairingDTO pairing) {
        try {
            log.info("Building Discord embed for pairing {} with users {} and {}", 
                    pairing.getId(), pairing.getUser1Id(), pairing.getUser2Id());
            
            // Fetch user profiles
            log.debug("Fetching user profile for user1: {}", pairing.getUser1Id());
            UserProfileDTO user1Profile = userService.mapToProfileDTO(userService.getUserById(pairing.getUser1Id()));
            log.debug("Fetching user profile for user2: {}", pairing.getUser2Id());
            UserProfileDTO user2Profile = userService.mapToProfileDTO(userService.getUserById(pairing.getUser2Id()));
            
            log.debug("Successfully fetched user profiles: {} and {}", 
                     user1Profile.getUsername(), user2Profile.getUsername());
            
            // Fetch XP/Level data
            PairLevel pairLevel = pairLevelService.getPairLevel(pairing.getId()).orElse(null);
            
            // Fetch voice streak data
            int currentStreak = voiceStreakService.getCurrentStreakCount(pairing.getId());
            
            // Build description with Discord user mentions for clickable references
            String description;
            if (pairing.getUser1Id() != null && pairing.getUser2Id() != null) {
                description = String.format("<@%s> %s <@%s>", 
                                          pairing.getUser1Id(), 
                                          HEART_EMOJI, 
                                          pairing.getUser2Id());
            } else {
                // Fallback to usernames if Discord IDs are not available
                description = String.format("@%s %s @%s", 
                                          sanitizeUsername(user1Profile.getUsername()), 
                                          HEART_EMOJI, 
                                          sanitizeUsername(user2Profile.getUsername()));
            }
            
            EmbedBuilder embedBuilder = new EmbedBuilder()
                .setColor(DISCORD_BLURPLE)
                .setDescription(description)
                .setTimestamp(LocalDateTime.now());
            
            // Add level and XP field (inline set to false for Level field only)
            if (pairLevel != null) {
                embedBuilder.addField(
                    String.format("**Level %d**", pairLevel.getCurrentLevel()),
                    String.format("%,d XP", pairLevel.getTotalXP()),
                    false
                );
            } else {
                embedBuilder.addField("**Level 1**", "0 XP", false);
            }
            
            // Add message count field
            embedBuilder.addField(
                "**Total Messages**",
                String.format("%,d", pairing.getMessageCount()),
                true
            );
            
            // Add voice time field
            String voiceTimeFormatted = formatVoiceTime(pairing.getVoiceTimeMinutes());
            embedBuilder.addField(
                "**Voice Time**",
                voiceTimeFormatted,
                true
            );
            
            // Add streak field (inline)
            String streakText = currentStreak == 1 ? "1 day streak" : String.format("%d day streak", currentStreak);
            embedBuilder.addField(
                String.format("**%s**", streakText),
                "", // Empty value for streak field
                true
            );
            
            // Add thumbnail (prefer user1 avatar, fallback to user2)
            String thumbnailUrl = null;
            if (user1Profile.getAvatar() != null && !user1Profile.getAvatar().isEmpty()) {
                thumbnailUrl = user1Profile.getAvatar();
            } else if (user2Profile.getAvatar() != null && !user2Profile.getAvatar().isEmpty()) {
                thumbnailUrl = user2Profile.getAvatar();
            }
            
            if (thumbnailUrl != null) {
                embedBuilder.setThumbnail(thumbnailUrl);
            }
            
            // Footer removed as requested
            
            return embedBuilder.build();
            
        } catch (Exception e) {
            log.error("Failed to build embed for pairing {}: {}", pairing.getId(), e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Get the Discord leaderboard channel
     */
        private TextChannel getLeaderboardChannel() {
        try {
            log.debug("Getting Discord guild with ID: {}", discordServerId);
            Guild guild = jda.getGuildById(discordServerId);
            if (guild == null) {
                log.error("Discord guild not found: {}", discordServerId);
                return null;
            }
            
            log.debug("Guild found: {}. Getting leaderboard channel with ID: {}", guild.getName(), leaderboardChannelId);
            TextChannel channel = guild.getTextChannelById(leaderboardChannelId);
            if (channel == null) {
                log.error("Leaderboard channel not found: {}", leaderboardChannelId);
                return null;
            }
            
            log.debug("Successfully found leaderboard channel: {}", channel.getName());
            return channel;
            
        } catch (Exception e) {
            log.error("Failed to get leaderboard channel: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Update an existing embed message
     */
    private boolean updateExistingEmbed(TextChannel channel, String messageId, MessageEmbed embed, Long pairingId) {
        try {
            channel.editMessageEmbedsById(messageId, embed).queue(
                success -> log.debug("Successfully updated leaderboard embed for pairing {}", pairingId),
                error -> {
                    log.warn("Failed to update leaderboard embed for pairing {}: {}", pairingId, error.getMessage());
                    // Remove the invalid message ID from our map
                    pairingMessageMap.remove(pairingId);
                }
            );
            return true;
        } catch (Exception e) {
            log.error("Exception updating embed for pairing {}: {}", pairingId, e.getMessage());
            pairingMessageMap.remove(pairingId);
            return false;
        }
    }
    
    /**
     * Create a new embed message
     */
    private boolean createNewEmbed(TextChannel channel, MessageEmbed embed, Long pairingId) {
        try {
            channel.sendMessageEmbeds(embed).queue(
                message -> {
                    // Store the message ID for future updates
                    pairingMessageMap.put(pairingId, message.getId());
                    log.debug("Successfully created leaderboard embed for pairing {} with message ID {}", 
                             pairingId, message.getId());
                },
                error -> log.warn("Failed to create leaderboard embed for pairing {}: {}", 
                                pairingId, error.getMessage())
            );
            return true;
        } catch (Exception e) {
            log.error("Exception creating embed for pairing {}: {}", pairingId, e.getMessage());
            return false;
        }
    }
    
    /**
     * Format voice time from minutes to "Xh Ym" format
     */
    private String formatVoiceTime(int totalMinutes) {
        if (totalMinutes == 0) {
            return "0m";
        }
        
        int hours = totalMinutes / 60;
        int minutes = totalMinutes % 60;
        
        if (hours > 0 && minutes > 0) {
            return String.format("%dh %dm", hours, minutes);
        } else if (hours > 0) {
            return String.format("%dh", hours);
        } else {
            return String.format("%dm", minutes);
        }
    }
    
    /**
     * Sanitize username for Discord display
     */
    private String sanitizeUsername(String username) {
        if (username == null || username.isEmpty()) {
            return "Unknown";
        }
        
        // Remove potentially problematic characters and limit length
        return username.replaceAll("[`*_~|]", "")
                      .trim()
                      .substring(0, Math.min(username.length(), 32));
    }
    
    /**
     * Clear all tracked messages (useful for cleanup)
     */
    public void clearMessageTracker() {
        pairingMessageMap.clear();
        log.info("Cleared leaderboard message tracker");
    }
    
    /**
     * Get the current number of tracked pairing messages
     */
    public int getTrackedMessageCount() {
        return pairingMessageMap.size();
    }
    
    @PreDestroy
    public void cleanup() {
        if (leaderboardEnabled) {
            log.info("Discord Leaderboard Service shutting down - {} messages tracked", 
                    pairingMessageMap.size());
        }
    }
} 