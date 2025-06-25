package com.app.heartbound.services.discord;

import com.app.heartbound.entities.User;
import com.app.heartbound.services.UserService;
import com.app.heartbound.config.CacheConfig;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.time.LocalDateTime;
import java.time.Duration;
import java.time.format.DateTimeFormatter;

@Component
public class DailyCommandListener extends ListenerAdapter {
    
    private static final Logger logger = LoggerFactory.getLogger(DailyCommandListener.class);
    private static final Color EMBED_COLOR = new Color(88, 101, 242); // Discord Blurple
    
    // Reward tier structure for 7-day streak
    private static final int[] DAILY_REWARDS = {100, 150, 200, 250, 350, 450, 500};
    
    private final UserService userService;
    private final CacheConfig cacheConfig;
    
    @Autowired
    public DailyCommandListener(UserService userService, CacheConfig cacheConfig) {
        this.userService = userService;
        this.cacheConfig = cacheConfig;
        logger.info("DailyCommandListener initialized");
    }
    
    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("daily")) {
            return; // Not our command
        }
        
        logger.info("User {} requested /daily", event.getUser().getId());
        
        // Acknowledge the interaction quickly and make the response public (visible to everyone)
        event.deferReply(false).queue();
        
        try {
            // Get the Discord user ID
            String userId = event.getUser().getId();
            
            // Check cache first for daily claim status
            DailyClaimStatus claimStatus = getCachedDailyClaimStatus(userId);
            
            if (claimStatus == null) {
                // Fetch the user from the database if not in cache
                User user = userService.getUserById(userId);
                
                if (user == null) {
                    logger.warn("User {} not found in database when requesting daily", userId);
                    event.getHook().editOriginal("Could not find your account. Please log in to the web application first.").queue();
                    return;
                }
                
                // Create claim status from database and cache it
                claimStatus = new DailyClaimStatus(
                    user.getDailyStreak() != null ? user.getDailyStreak() : 0,
                    user.getLastDailyClaim()
                );
                cacheDailyClaimStatus(userId, claimStatus);
            }
            
            LocalDateTime now = LocalDateTime.now();
            
            // Check if user can claim (24-hour cooldown)
            if (claimStatus.lastDailyClaim != null) {
                Duration timeSinceLastClaim = Duration.between(claimStatus.lastDailyClaim, now);
                
                if (timeSinceLastClaim.toHours() < 24) {
                    // Still in cooldown period
                    sendCooldownEmbed(event, claimStatus.lastDailyClaim, event.getUser().getEffectiveName());
                    return;
                }
            }
            
            // Calculate new streak
            int newStreak = calculateNewStreak(claimStatus, now);
            int creditsToAward = DAILY_REWARDS[newStreak - 1]; // Array is 0-indexed
            
            // Process the daily claim
            User user = userService.getUserById(userId);
            if (user == null) {
                event.getHook().editOriginal("Could not find your account. Please try again.").queue();
                return;
            }
            
            // Update user data
            int currentCredits = user.getCredits() != null ? user.getCredits() : 0;
            user.setCredits(currentCredits + creditsToAward);
            user.setDailyStreak(newStreak);
            user.setLastDailyClaim(now);
            
            // Save to database
            userService.updateUser(user);
            
            // Invalidate caches to ensure fresh data
            cacheConfig.invalidateDailyClaimCache(userId);
            cacheConfig.invalidateUserProfileCache(userId);
            
            // Send success embed
            sendSuccessEmbed(event, newStreak, creditsToAward, now, event.getUser().getEffectiveName());
            
            logger.debug("Daily claim processed for user {}: {} credits awarded, streak: {}", userId, creditsToAward, newStreak);
            
        } catch (Exception e) {
            logger.error("Error processing /daily command for user {}", event.getUser().getId(), e);
            event.getHook().editOriginal("An error occurred while processing your daily claim.").queue();
        }
    }
    
    /**
     * Get cached daily claim status for a user
     */
    private DailyClaimStatus getCachedDailyClaimStatus(String userId) {
        try {
            return (DailyClaimStatus) cacheConfig.getDailyClaimCache().getIfPresent(userId);
        } catch (Exception e) {
            logger.warn("Failed to retrieve cached daily claim status for user {}: {}", userId, e.getMessage());
            return null;
        }
    }
    
    /**
     * Cache daily claim status for a user
     */
    private void cacheDailyClaimStatus(String userId, DailyClaimStatus status) {
        try {
            cacheConfig.getDailyClaimCache().put(userId, status);
            logger.debug("Cached daily claim status for user: {}", userId);
        } catch (Exception e) {
            logger.warn("Failed to cache daily claim status for user {}: {}", userId, e.getMessage());
        }
    }
    
    /**
     * Calculate the new streak based on the time since last claim
     */
    private int calculateNewStreak(DailyClaimStatus claimStatus, LocalDateTime now) {
        if (claimStatus.lastDailyClaim == null) {
            // First time claiming
            return 1;
        }
        
        Duration timeSinceLastClaim = Duration.between(claimStatus.lastDailyClaim, now);
        long hoursSinceLastClaim = timeSinceLastClaim.toHours();
        
        if (hoursSinceLastClaim <= 48) {
            // Streak continues
            int newStreak = claimStatus.dailyStreak + 1;
            if (newStreak > 7) {
                // Reset streak after day 7
                return 1;
            }
            return newStreak;
        } else {
            // Streak broken (more than 48 hours)
            return 1;
        }
    }
    
    /**
     * Send success embed with streak information
     */
    private void sendSuccessEmbed(SlashCommandInteractionEvent event, int currentDay, 
                                 int creditsAwarded, LocalDateTime claimTime, String userName) {
        EmbedBuilder embed = new EmbedBuilder()
                .setColor(EMBED_COLOR)
                .setTitle("💰 Daily Reward Claimed!");
        
        StringBuilder description = new StringBuilder();
        description.append(String.format("💰 | %s, You got %d **credits** 🪙!\n", userName, creditsAwarded));
        description.append(String.format("🔥 | You're on a **%d day streak!**\n", currentDay));
        
        // Calculate next claim time (24 hours from now)
        LocalDateTime nextClaim = claimTime.plusDays(1);
        String timeUntilNext = formatTimeUntil(nextClaim);
        description.append(String.format("⏱️ | Your next daily is in: %s", timeUntilNext));
        
        embed.setDescription(description.toString());
        embed.setTimestamp(java.time.Instant.now());
        
        event.getHook().editOriginalEmbeds(embed.build()).queue();
    }
    
    /**
     * Send cooldown embed when user tries to claim too early
     */
    private void sendCooldownEmbed(SlashCommandInteractionEvent event, LocalDateTime lastClaim, String userName) {
        LocalDateTime nextClaim = lastClaim.plusDays(1);
        String timeRemaining = formatTimeUntil(nextClaim);
        
        EmbedBuilder embed = new EmbedBuilder()
                .setColor(Color.ORANGE)
                .setTitle("⏱ Daily Cooldown")
                .setDescription(String.format("⏱ | %s, You need to wait %s", userName, timeRemaining))
                .setTimestamp(java.time.Instant.now());
        
        event.getHook().editOriginalEmbeds(embed.build()).queue();
    }
    
    /**
     * Format time until next claim in a user-friendly way
     */
    private String formatTimeUntil(LocalDateTime target) {
        LocalDateTime now = LocalDateTime.now();
        Duration duration = Duration.between(now, target);
        
        if (duration.isNegative()) {
            return "available now";
        }
        
        long hours = duration.toHours();
        long minutes = duration.toMinutes() % 60;
        
        if (hours > 0) {
            return String.format("%d hours and %d minutes", hours, minutes);
        } else {
            return String.format("%d minutes", minutes);
        }
    }
    
    /**
     * Record class to store daily claim status in cache
     */
    private static class DailyClaimStatus {
        final int dailyStreak;
        final LocalDateTime lastDailyClaim;
        
        public DailyClaimStatus(int dailyStreak, LocalDateTime lastDailyClaim) {
            this.dailyStreak = dailyStreak;
            this.lastDailyClaim = lastDailyClaim;
        }
    }
} 