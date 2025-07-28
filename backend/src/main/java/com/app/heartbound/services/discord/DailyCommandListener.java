package com.app.heartbound.services.discord;

import com.app.heartbound.entities.User;
import com.app.heartbound.services.UserService;
import com.app.heartbound.services.AuditService;
import com.app.heartbound.dto.CreateAuditDTO;
import com.app.heartbound.enums.AuditSeverity;
import com.app.heartbound.enums.AuditCategory;
import com.app.heartbound.config.CacheConfig;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import java.awt.Color;
import java.time.LocalDateTime;
import java.time.Duration;

@Component
public class DailyCommandListener extends ListenerAdapter {
    
    private static final Logger logger = LoggerFactory.getLogger(DailyCommandListener.class);
    private static final Color EMBED_COLOR = new Color(88, 101, 242); // Discord Blurple
    
    // Reward tier structure for 7-day streak
    private static final int[] DAILY_REWARDS = {100, 150, 200, 250, 350, 450, 500};
    
    private final UserService userService;
    private final CacheConfig cacheConfig;
    private final AuditService auditService;
    
    @Value("${discord.main.guild.id}")
    private String mainGuildId;

    public DailyCommandListener(UserService userService, CacheConfig cacheConfig, AuditService auditService) {
        this.userService = userService;
        this.cacheConfig = cacheConfig;
        this.auditService = auditService;
        logger.info("DailyCommandListener initialized with audit service");
    }
    
    @Override
    public void onSlashCommandInteraction(@Nonnull SlashCommandInteractionEvent event) {
        if (!event.getName().equals("daily")) {
            return; // Not our command
        }
        
        // Guild restriction check
        final Guild guild = event.getGuild();
        if (guild == null || !guild.getId().equals(mainGuildId)) {
            event.reply("This command can only be used in the main Heartbound server.")
                    .setEphemeral(true)
                    .queue();
            return;
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
            int creditsToAward = DAILY_REWARDS[(newStreak - 1) % DAILY_REWARDS.length]; // Use modulo for cycling
            
            // Process the daily claim
            User user = userService.getUserById(userId);
            if (user == null) {
                event.getHook().editOriginal("Could not find your account. Please try again.").queue();
                return;
            }
            
            // Update user data: Atomically update credits first.
            boolean creditsAwardedSuccess = userService.updateCreditsAtomic(userId, creditsToAward);
            
            if (!creditsAwardedSuccess) {
                event.getHook().editOriginal("An error occurred while awarding your daily credits. Please try again.").queue();
                logger.error("Failed to award daily credits to user {}", userId);
                return;
            }

            // FIX: Re-fetch the user to get the updated credit balance and avoid stale state.
            User updatedUser = userService.getUserById(userId);
            if (updatedUser == null) {
                // This is an unlikely edge case, but it's a good practice to handle it.
                // It means the atomic update may have succeeded, but the user was deleted immediately after.
                event.getHook().editOriginal("Could not find your account after awarding credits. Please contact support.").queue();
                logger.error("User {} not found after successful atomic credit update during daily claim.", userId);
                return;
            }

            // After credits are secure, update the non-critical streak and timestamp info on the fresh user object.
            updatedUser.setDailyStreak(newStreak);
            updatedUser.setLastDailyClaim(now);
            
            // Save to database
            userService.updateUser(updatedUser);
            
            // Create audit entry for daily claim
            try {
                // FIX: Use the accurate new balance from the re-fetched user object.
                CreateAuditDTO auditEntry = CreateAuditDTO.builder()
                    .userId(userId)
                    .action("DAILY_CLAIM")
                    .entityType("USER_CREDITS")
                    .entityId(userId)
                    .description(String.format("Claimed daily reward of %d credits (streak: %d days)", 
                        creditsToAward, newStreak))
                    .severity(creditsToAward > 1000 ? AuditSeverity.WARNING : AuditSeverity.INFO)
                    .category(AuditCategory.FINANCIAL)
                    .details(String.format("{\"game\":\"daily\",\"streak\":%d,\"reward\":%d,\"newBalance\":%d}", 
                        newStreak, creditsToAward, updatedUser.getCredits()))
                    .source("DISCORD_BOT")
                    .build();
                
                auditService.createSystemAuditEntry(auditEntry);
            } catch (Exception e) {
                logger.error("Failed to create audit entry for daily claim by user {}: {}", userId, e.getMessage());
            }
            
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
            // Streak continues indefinitely
            return claimStatus.dailyStreak + 1;
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