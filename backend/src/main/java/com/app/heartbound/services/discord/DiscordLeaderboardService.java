package com.app.heartbound.services.discord;

import com.app.heartbound.dto.pairing.PairingDTO;
import com.app.heartbound.dto.UserProfileDTO;
import com.app.heartbound.entities.PairLevel;
import com.app.heartbound.entities.Pairing;
import com.app.heartbound.services.UserService;
import com.app.heartbound.services.pairing.PairLevelService;
import com.app.heartbound.services.pairing.VoiceStreakService;
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
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * DiscordLeaderboardService
 * 
 * Manages Discord leaderboard embeds for active pairings.
 * Automatically creates, updates, and removes pairing embeds in the configured Discord channel.
 * 
 * FEATURES:
 * - Persistent Discord message ID tracking to prevent duplicates on restart
 * - Level-based ordering (highest level first, then by XP)
 * - Startup validation and cleanup of orphaned messages
 * - Graceful error handling without breaking core functionality
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DiscordLeaderboardService {

    private static final Color DISCORD_BLURPLE = new Color(88, 101, 242);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MMM dd, yyyy");
    private static final String HEART_EMOJI = "♡";
    
    // Medal emojis for top ranks
    private static final String GOLD_MEDAL_EMOJI = "🥇";
    private static final String SILVER_MEDAL_EMOJI = "🥈";
    private static final String BRONZE_MEDAL_EMOJI = "🥉";
    
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
    
    // Keep in-memory map for fast lookups, but now backed by database persistence
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
                    
                    // Load existing message IDs from database to in-memory map
                    loadPersistedMessageIds();
                    
                    // Validate and clean up existing Discord messages
                    validateAndCleanupExistingMessages().join();
                    
                    // Refresh leaderboard with proper ordering
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
     * Load persisted Discord message IDs from database into in-memory map
     */
    private void loadPersistedMessageIds() {
        try {
            List<com.app.heartbound.entities.Pairing> activePairings = pairingRepository.findByActiveTrue();
            int loadedCount = 0;
            
            for (com.app.heartbound.entities.Pairing pairing : activePairings) {
                if (pairing.getDiscordLeaderboardMessageId() != null && !pairing.getDiscordLeaderboardMessageId().isEmpty()) {
                    pairingMessageMap.put(pairing.getId(), pairing.getDiscordLeaderboardMessageId());
                    loadedCount++;
                }
            }
            
            log.info("Loaded {} persisted Discord message IDs into memory", loadedCount);
        } catch (Exception e) {
            log.error("Failed to load persisted message IDs: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Validate existing Discord messages and clean up orphaned ones
     */
    @Async
    public CompletableFuture<Void> validateAndCleanupExistingMessages() {
        if (!leaderboardEnabled) {
            return CompletableFuture.completedFuture(null);
        }
        
        return CompletableFuture.runAsync(() -> {
            try {
                log.info("Validating and cleaning up existing Discord leaderboard messages");
                
                TextChannel channel = getLeaderboardChannel();
                if (channel == null) {
                    log.warn("Cannot validate messages - leaderboard channel not found");
                    return;
                }
                
                // Get recent messages from Discord channel (last 100 messages should be enough)
                List<Message> discordMessages = channel.getHistory().retrievePast(100).complete();
                
                // Get active pairings with their message IDs
                List<com.app.heartbound.entities.Pairing> activePairings = pairingRepository.findByActiveTrue();
                Set<String> validMessageIds = activePairings.stream()
                    .map(com.app.heartbound.entities.Pairing::getDiscordLeaderboardMessageId)
                    .filter(id -> id != null && !id.isEmpty())
                    .collect(Collectors.toSet());
                
                
                int deletedCount = 0;
                int validatedCount = 0;
                
                // Check each Discord message
                for (Message message : discordMessages) {
                    // Skip non-bot messages
                    if (!message.getAuthor().equals(jda.getSelfUser())) {
                        continue;
                    }
                    
                    // Skip messages that aren't embeds (our leaderboard messages are embeds)
                    if (message.getEmbeds().isEmpty()) {
                        continue;
                    }
                    
                    String messageId = message.getId();
                    
                    // Check if this message ID is associated with an active pairing
                    boolean isValidMessage = validMessageIds.contains(messageId);
                    
                    if (!isValidMessage) {
                        // This is an orphaned leaderboard message - delete it
                        try {
                            message.delete().queue(
                                success -> log.debug("Deleted orphaned leaderboard message: {}", messageId),
                                error -> log.warn("Failed to delete orphaned message {}: {}", messageId, error.getMessage())
                            );
                            deletedCount++;
                            
                            // Small delay to avoid rate limiting
                            Thread.sleep(200);
                        } catch (Exception e) {
                            log.warn("Error deleting orphaned message {}: {}", messageId, e.getMessage());
                        }
                    } else {
                        validatedCount++;
                    }
                }
                
                log.info("Message validation complete - {} valid messages, {} orphaned messages deleted", 
                        validatedCount, deletedCount);
                
            } catch (Exception e) {
                log.error("Failed to validate and cleanup messages: {}", e.getMessage(), e);
            }
        });
    }
    
    /**
     * Refresh the entire leaderboard with all active pairings in proper order
     */
    @Async
    public CompletableFuture<Void> refreshEntireLeaderboard() {
        if (!leaderboardEnabled) {
            return CompletableFuture.completedFuture(null);
        }
        
        return CompletableFuture.runAsync(() -> {
            try {
                log.info("Refreshing Discord leaderboard with level-based ordering");
                
                // Get all active pairings ordered by level (highest first), then by XP
                List<com.app.heartbound.entities.Pairing> orderedPairings = pairingRepository.findActivePairingsOrderedByLevel();
                
                if (orderedPairings.isEmpty()) {
                    log.info("No active pairings found to display in leaderboard");
                    return;
                }
                
                log.info("Found {} active pairings to add to leaderboard in level order", orderedPairings.size());
                
                // To ensure proper Discord ordering, we need to delete existing messages and recreate them
                // This guarantees the correct visual order in Discord channel
                TextChannel channel = getLeaderboardChannel();
                if (channel == null) {
                    log.warn("Cannot refresh leaderboard - channel not found");
                    return;
                }
                
                // First, delete all existing leaderboard messages for proper ordering
                for (com.app.heartbound.entities.Pairing pairing : orderedPairings) {
                    String existingMessageId = pairing.getDiscordLeaderboardMessageId();
                    if (existingMessageId != null && !existingMessageId.isEmpty()) {
                        try {
                            channel.deleteMessageById(existingMessageId).queue(
                                success -> log.debug("Deleted existing message for pairing {} for reordering", pairing.getId()),
                                error -> log.debug("Could not delete existing message {} (may not exist): {}", existingMessageId, error.getMessage())
                            );
                            
                            // Clear from database and memory
                            pairing.setDiscordLeaderboardMessageId(null);
                            pairingRepository.save(pairing);
                            pairingMessageMap.remove(pairing.getId());
                            
                            // Small delay to avoid rate limiting
                            Thread.sleep(300);
                        } catch (Exception e) {
                            log.debug("Error deleting existing message for pairing {}: {}", pairing.getId(), e.getMessage());
                        }
                    }
                }
                
                // Wait a bit for deletions to complete
                Thread.sleep(2000);
                
                // Now create new embeds in correct order (highest level first)
                for (int i = 0; i < orderedPairings.size(); i++) {
                    try {
                        Pairing pairing = orderedPairings.get(i);
                        PairingDTO pairingDTO = mapToPairingDTO(pairing);
                        int rank = i + 1; // 1-indexed rank
                        addOrUpdatePairingEmbedWithRank(pairingDTO, rank).join();
                        
                        // Delay between embeds to ensure proper ordering and avoid rate limits
                        Thread.sleep(800);
                    } catch (Exception e) {
                        log.error("Failed to create embed for pairing {} during refresh: {}", orderedPairings.get(i).getId(), e.getMessage());
                    }
                }
                
                log.info("Successfully refreshed leaderboard with {} pairings in level order", orderedPairings.size());
                
            } catch (Exception e) {
                log.error("Failed to refresh entire leaderboard: {}", e.getMessage(), e);
            }
        });
    }
    
    /**
     * Map Pairing entity to PairingDTO (updated with new field)
     */
    private PairingDTO mapToPairingDTO(com.app.heartbound.entities.Pairing pairing) {
        return PairingDTO.builder()
                .id(pairing.getId())
                .user1Id(pairing.getUser1Id())
                .user2Id(pairing.getUser2Id())
                .discordChannelId(pairing.getDiscordChannelId())
                .discordChannelName(pairing.getDiscordChannelName())
                .discordLeaderboardMessageId(pairing.getDiscordLeaderboardMessageId())
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
                .build();
    }
    
    /**
     * Add or update a pairing embed in the Discord leaderboard
     */
    @Async
    public CompletableFuture<Boolean> addOrUpdatePairingEmbed(PairingDTO pairing) {
        // Determine rank for this pairing
        Integer rank = determinePairingRank(pairing.getId());
        return addOrUpdatePairingEmbedWithRank(pairing, rank);
    }
    
    /**
     * Add or update a pairing embed in the Discord leaderboard with specific rank
     */
    @Async
    public CompletableFuture<Boolean> addOrUpdatePairingEmbedWithRank(PairingDTO pairing, Integer rank) {
        if (!leaderboardEnabled || !pairing.isActive()) {
            return CompletableFuture.completedFuture(false);
        }
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("Adding/updating leaderboard embed for pairing ID: {} with rank: {}", pairing.getId(), rank);
                
                TextChannel channel = getLeaderboardChannel();
                if (channel == null) {
                    log.warn("Leaderboard channel not found: {}", leaderboardChannelId);
                    return false;
                }
                
                // Check for existing message ID (now from database/memory)
                String existingMessageId = pairing.getDiscordLeaderboardMessageId();
                if (existingMessageId == null || existingMessageId.isEmpty()) {
                    existingMessageId = pairingMessageMap.get(pairing.getId());
                }
                
                MessageEmbed embed = buildPairingEmbed(pairing, rank);
                if (embed == null) {
                    log.warn("Failed to build embed for pairing {}", pairing.getId());
                    return false;
                }
                
                if (existingMessageId != null && !existingMessageId.isEmpty()) {
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
     * Determine the rank of a pairing in the leaderboard
     */
    private Integer determinePairingRank(Long pairingId) {
        try {
            List<Pairing> orderedPairings = pairingRepository.findActivePairingsOrderedByLevel();
            
            for (int i = 0; i < orderedPairings.size(); i++) {
                if (orderedPairings.get(i).getId().equals(pairingId)) {
                    return i + 1; // 1-indexed rank
                }
            }
            
            log.warn("Could not determine rank for pairing {} - not found in active pairings", pairingId);
            return null;
        } catch (Exception e) {
            log.error("Failed to determine rank for pairing {}: {}", pairingId, e.getMessage());
            return null;
        }
    }
    
    /**
     * Format rank title with appropriate medal emojis for top 3 positions
     * 
     * @param rank The 1-indexed rank position
     * @return Formatted title string with medal emoji for top 3, or standard format for others
     */
    private String formatRankTitle(int rank) {
        return switch (rank) {
            case 1 -> String.format("%s Rank %d", GOLD_MEDAL_EMOJI, rank);
            case 2 -> String.format("%s Rank %d", SILVER_MEDAL_EMOJI, rank);
            case 3 -> String.format("%s Rank %d", BRONZE_MEDAL_EMOJI, rank);
            default -> String.format("Rank %d", rank);
        };
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
                
                // Get message ID from database first, then memory
                Optional<com.app.heartbound.entities.Pairing> pairingOpt = pairingRepository.findById(pairingId);
                String messageId = null;
                
                if (pairingOpt.isPresent()) {
                    messageId = pairingOpt.get().getDiscordLeaderboardMessageId();
                }
                
                if (messageId == null || messageId.isEmpty()) {
                    messageId = pairingMessageMap.get(pairingId);
                }
                
                if (messageId == null || messageId.isEmpty()) {
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
                    success -> {
                        log.debug("Successfully deleted leaderboard message for pairing {}", pairingId);
                        
                        // Clean up database and memory
                        try {
                            Optional<com.app.heartbound.entities.Pairing> pairing = pairingRepository.findById(pairingId);
                            if (pairing.isPresent()) {
                                pairing.get().setDiscordLeaderboardMessageId(null);
                                pairingRepository.save(pairing.get());
                            }
                            pairingMessageMap.remove(pairingId);
                        } catch (Exception e) {
                            log.warn("Failed to clean up message ID for pairing {}: {}", pairingId, e.getMessage());
                        }
                    },
                    error -> {
                        log.warn("Failed to delete leaderboard message for pairing {}: {}", pairingId, error.getMessage());
                        
                        // Clean up tracking even if delete failed (message might not exist)
                        try {
                            Optional<com.app.heartbound.entities.Pairing> pairing = pairingRepository.findById(pairingId);
                            if (pairing.isPresent()) {
                                pairing.get().setDiscordLeaderboardMessageId(null);
                                pairingRepository.save(pairing.get());
                            }
                            pairingMessageMap.remove(pairingId);
                        } catch (Exception e) {
                            log.warn("Failed to clean up message ID for pairing {}: {}", pairingId, e.getMessage());
                        }
                    }
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
     * Build the Discord embed for a pairing with rank information
     */
    private MessageEmbed buildPairingEmbed(PairingDTO pairing, Integer rank) {
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
                .setDescription(description);
            
            // Add title with rank if provided
            if (rank != null) {
                embedBuilder.setTitle(formatRankTitle(rank));
            }
            
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
            embedBuilder.addField(
                "**Streak**",
                String.valueOf(currentStreak),
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
            
            // Add footer with clean match date format
            if (pairing.getMatchedAt() != null) {
                String matchedDate = DATE_FORMATTER.format(pairing.getMatchedAt());
                embedBuilder.setFooter("Matched on " + matchedDate);
            }
            
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
                    // Remove the invalid message ID from our map and database
                    pairingMessageMap.remove(pairingId);
                    clearMessageIdFromDatabase(pairingId);
                }
            );
            return true;
        } catch (Exception e) {
            log.error("Exception updating embed for pairing {}: {}", pairingId, e.getMessage());
            pairingMessageMap.remove(pairingId);
            clearMessageIdFromDatabase(pairingId);
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
                    // Store the message ID for future updates (both in memory and database)
                    String messageId = message.getId();
                    pairingMessageMap.put(pairingId, messageId);
                    saveMessageIdToDatabase(pairingId, messageId);
                    log.debug("Successfully created leaderboard embed for pairing {} with message ID {}", 
                             pairingId, messageId);
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
     * Save Discord message ID to database for persistent tracking
     */
    private void saveMessageIdToDatabase(Long pairingId, String messageId) {
        try {
            Optional<com.app.heartbound.entities.Pairing> pairingOpt = pairingRepository.findById(pairingId);
            if (pairingOpt.isPresent()) {
                com.app.heartbound.entities.Pairing pairing = pairingOpt.get();
                pairing.setDiscordLeaderboardMessageId(messageId);
                pairingRepository.save(pairing);
                log.debug("Saved Discord message ID {} for pairing {} to database", messageId, pairingId);
            } else {
                log.warn("Could not save message ID - pairing {} not found", pairingId);
            }
        } catch (Exception e) {
            log.error("Failed to save message ID to database for pairing {}: {}", pairingId, e.getMessage());
        }
    }
    
    /**
     * Clear Discord message ID from database
     */
    private void clearMessageIdFromDatabase(Long pairingId) {
        try {
            Optional<com.app.heartbound.entities.Pairing> pairingOpt = pairingRepository.findById(pairingId);
            if (pairingOpt.isPresent()) {
                com.app.heartbound.entities.Pairing pairing = pairingOpt.get();
                pairing.setDiscordLeaderboardMessageId(null);
                pairingRepository.save(pairing);
                log.debug("Cleared Discord message ID for pairing {} from database", pairingId);
            }
        } catch (Exception e) {
            log.error("Failed to clear message ID from database for pairing {}: {}", pairingId, e.getMessage());
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