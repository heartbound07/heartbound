package com.app.heartbound.services.discord;

import com.app.heartbound.entities.User;
import com.app.heartbound.services.UserService;
import com.app.heartbound.repositories.pairing.PairingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.awt.Color;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.Map;
import java.util.HashMap;

@Component
@Slf4j
public class ChatActivityListener extends ListenerAdapter {
    
    private final UserService userService;
    private final PairingRepository pairingRepository;
    
    @Autowired
    @Lazy
    private DiscordBotSettingsService discordBotSettingsService;
    
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
    
    // Replace static constants with configurable fields
    private String level5RoleId = "1161732022704816250";
    private String level15RoleId = "1162632126068437063";
    private String level30RoleId = "1162628059296432148";
    private String level40RoleId = "1162628114195697794";
    private String level50RoleId = "1166539666674167888";
    private String level70RoleId = "1170429914185465906";
    private String level100RoleId = "1162628179043823657";
    private String starterRoleId = "1303106353014771773";
    
    // Track user cooldowns - userId -> lastMessageTimestamp
    private final ConcurrentHashMap<String, Instant> userCooldowns = new ConcurrentHashMap<>();
    
    // Track user activity - userId -> list of message timestamps
    private final ConcurrentHashMap<String, List<Instant>> userActivity = new ConcurrentHashMap<>();
    
    private ScheduledExecutorService cleanupScheduler;
    
    // Constructor for non-circular dependencies
    public ChatActivityListener(UserService userService, PairingRepository pairingRepository) {
        this.userService = userService;
        this.pairingRepository = pairingRepository;
    }
    
    @PostConstruct
    public void init() {
        // Schedule periodic cleanup of stale activity data
        cleanupScheduler = Executors.newSingleThreadScheduledExecutor();
        cleanupScheduler.scheduleAtFixedRate(this::cleanupStaleActivity, 
                timeWindowMinutes, timeWindowMinutes, TimeUnit.MINUTES);
        log.info("Discord chat activity monitoring initialized");
    }
    
    @PreDestroy
    public void shutdown() {
        if (cleanupScheduler != null) {
            cleanupScheduler.shutdown();
            try {
                if (!cleanupScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    cleanupScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                cleanupScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        log.info("Discord chat activity monitoring shutdown");
    }
    
    private void cleanupStaleActivity() {
        // Implementation of cleanupStaleActivity method
    }
    
    /**
     * Increments the time-based message counters for a user.
     * Handles resetting counters if the time periods have elapsed.
     */
    private void incrementTimeBasedCounters(User user) {
        LocalDateTime now = LocalDateTime.now();
        
        // Handle daily counter
        if (shouldResetDailyCounter(user, now)) {
            user.setMessagesToday(0);
            user.setLastDailyReset(now);
        }
        user.setMessagesToday((user.getMessagesToday() != null ? user.getMessagesToday() : 0) + 1);
        
        // Handle weekly counter
        if (shouldResetWeeklyCounter(user, now)) {
            user.setMessagesThisWeek(0);
            user.setLastWeeklyReset(now);
        }
        user.setMessagesThisWeek((user.getMessagesThisWeek() != null ? user.getMessagesThisWeek() : 0) + 1);
        
        // Handle bi-weekly counter
        if (shouldResetBiWeeklyCounter(user, now)) {
            user.setMessagesThisTwoWeeks(0);
            user.setLastBiWeeklyReset(now);
        }
        user.setMessagesThisTwoWeeks((user.getMessagesThisTwoWeeks() != null ? user.getMessagesThisTwoWeeks() : 0) + 1);
    }
    
    /**
     * Check if daily counter should be reset (new day)
     */
    private boolean shouldResetDailyCounter(User user, LocalDateTime now) {
        if (user.getLastDailyReset() == null) {
            return true; // First time, needs initialization
        }
        return !user.getLastDailyReset().toLocalDate().equals(now.toLocalDate());
    }
    
    /**
     * Check if weekly counter should be reset (new week - Monday)
     */
    private boolean shouldResetWeeklyCounter(User user, LocalDateTime now) {
        if (user.getLastWeeklyReset() == null) {
            return true; // First time, needs initialization
        }
        
        // Get the start of this week (Monday)
        LocalDateTime startOfThisWeek = now.with(java.time.DayOfWeek.MONDAY).withHour(0).withMinute(0).withSecond(0).withNano(0);
        return user.getLastWeeklyReset().isBefore(startOfThisWeek);
    }
    
    /**
     * Check if bi-weekly counter should be reset (every 2 weeks from a fixed start date)
     */
    private boolean shouldResetBiWeeklyCounter(User user, LocalDateTime now) {
        if (user.getLastBiWeeklyReset() == null) {
            return true; // First time, needs initialization
        }
        
        // Reset every 14 days from the last reset
        return user.getLastBiWeeklyReset().plusDays(14).isBefore(now) || 
               user.getLastBiWeeklyReset().plusDays(14).toLocalDate().equals(now.toLocalDate());
    }
    
    /**
     * Collects all level-specific role IDs into a Set for easy comparison.
     * This ensures we can identify which roles are managed by the level system.
     */
    private Set<String> getAllLevelRoleIds() {
        Set<String> levelRoleIds = new HashSet<>();
        
        if (level5RoleId != null && !level5RoleId.isEmpty()) {
            levelRoleIds.add(level5RoleId);
        }
        if (level15RoleId != null && !level15RoleId.isEmpty()) {
            levelRoleIds.add(level15RoleId);
        }
        if (level30RoleId != null && !level30RoleId.isEmpty()) {
            levelRoleIds.add(level30RoleId);
        }
        if (level40RoleId != null && !level40RoleId.isEmpty()) {
            levelRoleIds.add(level40RoleId);
        }
        if (level50RoleId != null && !level50RoleId.isEmpty()) {
            levelRoleIds.add(level50RoleId);
        }
        if (level70RoleId != null && !level70RoleId.isEmpty()) {
            levelRoleIds.add(level70RoleId);
        }
        if (level100RoleId != null && !level100RoleId.isEmpty()) {
            levelRoleIds.add(level100RoleId);
        }
        
        return levelRoleIds;
    }
    
    private int calculateRequiredXp(int level) {
        return baseXp + (levelFactor * (int)Math.pow(level, levelExponent)) + (levelMultiplier * level);
    }
    
    private void checkAndProcessLevelUp(User user, String userId, MessageChannel channel, double roleMultiplier) {
        if (!levelingEnabled) {
            return;
        }
        
        int currentLevel = (user.getLevel() != null) ? user.getLevel() : 1;
        int currentXp = user.getExperience() != null ? user.getExperience() : 0;
        int requiredXp = calculateRequiredXp(currentLevel);
        
        log.debug("[XP DEBUG] Level check: User={}, Level={}, Current XP={}, Required XP={}", 
                    userId, currentLevel, currentXp, requiredXp);
        
        if (currentXp >= requiredXp) {
            int newLevel = currentLevel + 1;
            log.debug("[XP DEBUG] LEVEL UP! User {} is leveling up from {} to {}", 
                        userId, currentLevel, newLevel);
            user.setLevel(newLevel);
            user.setExperience(currentXp - requiredXp);
            
            // Award credits for leveling up
            int currentCredits = user.getCredits() != null ? user.getCredits() : 0;
            int multipliedCredits = (int) Math.round(creditsPerLevel * roleMultiplier);
            user.setCredits(currentCredits + multipliedCredits);
            log.info("Awarded {} credits to user {} for leveling up to {}. New balance: {}",
                        multipliedCredits, userId, newLevel, user.getCredits());
            
            try {
                userService.updateUser(user);
                
                // Check if user reached a level milestone and assign role
                checkAndAssignRoleForLevel(newLevel, userId, channel);
                
                // Get the achievement channel for level-up announcements
                String achievementChannelId = "1304293304833146951";
                MessageChannel achievementChannel = 
                    channel.getJDA().getChannelById(MessageChannel.class, achievementChannelId);
                
                if (achievementChannel != null) {
                    // Create an embed for the level-up announcement
                    EmbedBuilder embed = new EmbedBuilder();
                    embed.setTitle("Level Up Achievement!");
                    embed.setDescription(String.format("<@%s>! You advanced to level %d and earned %d credits!", 
                                                          userId, newLevel, multipliedCredits));
                    embed.setColor(new Color(75, 181, 67)); // Green color
                    embed.setTimestamp(java.time.Instant.now());
                    
                    // Add XP progress information
                    int nextLevelXp = calculateRequiredXp(newLevel);
                    embed.addField("Experience", String.format("%d/%d XP to next level", user.getExperience(), nextLevelXp), true);
                    
                    // Add credits information
                    embed.addField("Credits Awarded", String.format("🪙 %d", multipliedCredits), true);
                    
                    // Get the user's avatar if possible
                    net.dv8tion.jda.api.entities.User discordUser = channel.getJDA().getUserById(userId);
                    if (discordUser != null) {
                        embed.setThumbnail(discordUser.getEffectiveAvatarUrl());
                        embed.setAuthor(discordUser.getName(), null, discordUser.getEffectiveAvatarUrl());
                    }
                    
                    // Send the embed to the achievement channel
                    log.debug("[XP DEBUG] Sending level up embed to achievement channel {}", achievementChannelId);
                    achievementChannel.sendMessageEmbeds(embed.build()).queue(
                        success -> log.debug("[XP DEBUG] Level up embed sent for user {}", userId),
                        error -> log.error("Failed to send level up embed for user {}: {}", userId, error.getMessage())
                    );
                    
                    // Also send a simple notification in the original channel
                    String simpleNotification = String.format("🎉 <@%s> leveled up to **Level %d** and earned **%d credits**! Check out <#%s> for details!",
                        userId, newLevel, multipliedCredits, achievementChannelId);
                    channel.sendMessage(simpleNotification).queue();
                } else {
                    // Fallback to the original channel if achievement channel not found
                    log.warn("[XP DEBUG] Achievement channel {} not found, sending to original channel", achievementChannelId);
                    
                    String levelUpMessage = String.format("Congratulations <@%s>! You've reached **Level %d** and earned **%d credits**!", 
                                                        userId, newLevel, multipliedCredits);
                    channel.sendMessage(levelUpMessage).queue(
                        success -> log.debug("[XP DEBUG] Level up message sent for user {}", userId),
                        error -> log.error("Failed to send level up message for user {}: {}", userId, error.getMessage())
                    );
                }
                
                log.info("User {} leveled up to {} (XP: {} -> {}, Credits: +{})", 
                           userId, newLevel, currentXp, user.getExperience(), multipliedCredits);
                
                // Check for additional level ups
                log.debug("[XP DEBUG] Checking for additional level ups");
                checkAndProcessLevelUp(user, userId, channel, roleMultiplier);
                
            } catch (Exception e) {
                log.error("Error updating user level for {}: {}", userId, e.getMessage(), e);
            }
        }
    }
    
    private void checkAndAssignRoleForLevel(int level, String userId, MessageChannel channel) {
        // Early return if not in a guild channel
        if (!(channel instanceof TextChannel textChannel)) {
            log.debug("[ROLE DEBUG] Not a guild channel, skipping role assignment for user {}", userId);
            return;
        }
        
        final String roleIdString;
        
        // Determine which role to assign based on level
        if (level >= 100) {
            roleIdString = level100RoleId;
        } else if (level >= 70) {
            roleIdString = level70RoleId;
        } else if (level >= 50) {
            roleIdString = level50RoleId;
        } else if (level >= 40) {
            roleIdString = level40RoleId;
        } else if (level >= 30) {
            roleIdString = level30RoleId;
        } else if (level >= 15) {
            roleIdString = level15RoleId;
        } else if (level >= 5) {
            roleIdString = level5RoleId;
        } else {
            roleIdString = null;
        }
        
        // If no milestone reached or roleId not configured, return
        if (roleIdString == null || roleIdString.isEmpty()) {
            log.debug("[ROLE DEBUG] No role ID configured for level {}, skipping role assignment", level);
            return;
        }
        
        // Add debug log for the role ID being used
        log.debug("[ROLE DEBUG] Level {} reached. Attempting to use configured Role ID: '{}'", level, roleIdString);
        
        try {
            // Parse role ID to long
            long roleId;
            try {
                roleId = Long.parseLong(roleIdString);
            } catch (NumberFormatException e) {
                log.error("[ROLE DEBUG] Failed to parse configured Role ID '{}' for level {} into a long: {}", 
                         roleIdString, level, e.getMessage(), e);
                return;
            }
            
            Guild guild = textChannel.getGuild();
            Role newRole = guild.getRoleById(roleId);
            
            if (newRole == null) {
                log.warn("[ROLE DEBUG] Role with ID {} not found in guild {}", roleId, guild.getId());
                return;
            }
            
            // Get all level-specific role IDs for comparison
            Set<String> allLevelRoleIds = getAllLevelRoleIds();
            
            guild.retrieveMemberById(userId).queue(member -> {
                // Check if user already has the new role
                if (member.getRoles().contains(newRole)) {
                    log.debug("[ROLE DEBUG] User {} already has role {} for level {}", userId, newRole.getName(), level);
                    return;
                }
                
                // CRITICAL FIX: Remove all other level-specific roles before assigning the new one
                List<Role> rolesToRemove = new ArrayList<>();
                for (Role userRole : member.getRoles()) {
                    String userRoleId = userRole.getId();
                    // If this role is a level role but not the new role we want to assign
                    if (allLevelRoleIds.contains(userRoleId) && !userRoleId.equals(roleIdString)) {
                        rolesToRemove.add(userRole);
                    }
                    // SPECIAL CASE: Remove starter role when user reaches any level milestone
                    if (starterRoleId != null && !starterRoleId.isEmpty() && userRoleId.equals(starterRoleId)) {
                        rolesToRemove.add(userRole);
                        log.debug("[ROLE DEBUG] Starter role {} will be removed from user {} as they've reached level {}", 
                                 starterRoleId, userId, level);
                    }
                }
                
                // Remove old level roles first, then assign new role
                if (!rolesToRemove.isEmpty()) {
                    log.info("[ROLE DEBUG] Removing {} old level roles from user {} before assigning new role for level {}", 
                             rolesToRemove.size(), userId, level);
                    
                    // Remove all old level roles
                    guild.modifyMemberRoles(member, null, rolesToRemove).queue(
                        removeSuccess -> {
                            log.info("[ROLE DEBUG] Successfully removed old level roles from user {}: {}", 
                                     userId, rolesToRemove.stream().map(Role::getName).toList());
                            
                            // Now assign the new role
                            assignNewLevelRole(guild, member, newRole, userId, level, textChannel);
                        },
                        removeError -> {
                            log.error("[ROLE DEBUG] Failed to remove old level roles from user {}: {}", 
                                     userId, removeError.getMessage(), removeError);
                            // Try to assign new role anyway
                            assignNewLevelRole(guild, member, newRole, userId, level, textChannel);
                        }
                    );
                } else {
                    // No old roles to remove, just assign the new role
                    log.debug("[ROLE DEBUG] No old level roles found for user {}, proceeding with new role assignment", userId);
                    assignNewLevelRole(guild, member, newRole, userId, level, textChannel);
                }
                
            }, error -> log.error("[ROLE DEBUG] Failed to retrieve member with ID {}: {}", userId, error.getMessage(), error));
            
        } catch (Exception e) {
            log.error("[ROLE DEBUG] Error processing role assignment for level {} to user {}: {}", level, userId, e.getMessage(), e);
        }
    }
    
    /**
     * Helper method to assign a new level role to a user.
     * Extracted for reusability and cleaner code organization.
     */
    private void assignNewLevelRole(Guild guild, net.dv8tion.jda.api.entities.Member member, Role newRole, 
                                   String userId, int level, TextChannel textChannel) {
        guild.addRoleToMember(member, newRole).queue(
            success -> {
                log.info("[ROLE DEBUG] Successfully assigned role {} to user {} for reaching level {}", 
                         newRole.getName(), userId, level);
                
                // Send role assignment notification
                EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("🎖️ Level Up Role Reward!")
                    .setDescription("Congratulations on reaching **Level " + level + "**! You've been awarded the " 
                                  + newRole.getAsMention() + " role!")
                    .setColor(Color.GREEN)
                    .setTimestamp(Instant.now());
                
                textChannel.sendMessageEmbeds(embed.build()).queue(
                    embedSuccess -> log.debug("[ROLE DEBUG] Sent role assignment notification for user {}", userId),
                    embedError -> log.warn("[ROLE DEBUG] Failed to send role assignment notification for user {}: {}", 
                                          userId, embedError.getMessage())
                );
            },
            error -> log.error("[ROLE DEBUG] Failed to assign role {} to user {} for level {}: {}", 
                             newRole.getName(), userId, level, error.getMessage(), error)
        );
    }
    
    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        // Early return only if ALL features are disabled (message counting always enabled)
        // We always track message counts regardless of XP/activity feature status
        
        // Ignore messages from bots, DMs, or self
        if (!event.isFromGuild() || event.getAuthor().isBot() || 
            event.getAuthor().getId().equals(event.getJDA().getSelfUser().getId())) {
            return;
        }
        
        // 🚀 NEW: Skip pairing channels to avoid double XP/credits (pairing XP system handles these)
        long channelId = event.getChannel().getIdLong();
        if (pairingRepository.findByDiscordChannelId(channelId).isPresent()) {
            log.debug("Skipping individual user XP/credits for pairing channel: {} - pairing XP system will handle this", channelId);
            return;
        }
        
        String userId = event.getAuthor().getId();
        String content = event.getMessage().getContentRaw();
        
        // Check minimum message length
        if (content.length() < minMessageLength) {
            log.debug("Message from user {} ignored: too short ({} chars)", userId, content.length());
            return;
        }
        
        // Check cooldown
        Instant now = Instant.now();
        Instant lastMessageTime = userCooldowns.get(userId);
        if (lastMessageTime != null && now.isBefore(lastMessageTime.plusSeconds(cooldownSeconds))) {
            log.debug("Message from user {} ignored: on cooldown", userId);
            return;
        }
        
        // Update cooldown timestamp
        userCooldowns.put(userId, now);
        
        try {
            // Check if user exists in database
            User user = userService.getUserById(userId);
            if (user == null) {
                log.warn("User {} not found in database, cannot track activity", userId);
                return;
            }
            
            boolean userUpdated = false; // Flag to track if user needs saving
            
            // Increment user's global message count (tracks all valid messages)
            Long currentMessageCount = user.getMessageCount() != null ? user.getMessageCount() : 0L;
            user.setMessageCount(currentMessageCount + 1);
            userUpdated = true; // Mark user for update
            log.debug("[MESSAGE COUNT DEBUG] Incremented message count for user {}. New count: {}", userId, user.getMessageCount());
            
            // Increment time-based message counts
            incrementTimeBasedCounters(user);
            userUpdated = true; // Ensure user is marked for update
            
            // 📊 NEW: Track daily message stats for chart display
            userService.trackDailyMessageStat(userId);
            
            // Initialize user level if null
            if (user.getLevel() == null) {
                user.setLevel(1);
                userUpdated = true;
            }
            if (user.getExperience() == null) {
                user.setExperience(0);
                userUpdated = true;
            }
            
            int awardedXp = 0;
            int awardedCredits = 0;
            int initialLevel = user.getLevel(); // Safe to access now, guaranteed to be non-null
            
            // Get role multiplier for this user
            double roleMultiplier = getUserRoleMultiplier(userId, event);
            
            if (levelingEnabled) {
                log.debug("[XP DEBUG] About to award XP to {}: Current XP={}, Level={}, Adding {} XP (base) with {}x multiplier",
                    userId, user.getExperience(), user.getLevel(), xpToAward, roleMultiplier);
                int currentXp = user.getExperience();
                int multipliedXp = (int) Math.round(xpToAward * roleMultiplier);
                user.setExperience(currentXp + multipliedXp);
                userUpdated = true; // Mark user for update
                awardedXp = multipliedXp;
                log.debug("[XP DEBUG] Awarded {} XP ({}x{}) to user {}. New XP: {}", multipliedXp, xpToAward, roleMultiplier, userId, user.getExperience());
                
                // Award credits alongside XP for each eligible message
                if (activityEnabled && creditsToAward > 0) {
                    int currentCredits = user.getCredits() != null ? user.getCredits() : 0;
                    int multipliedCredits = (int) Math.round(creditsToAward * roleMultiplier);
                    user.setCredits(currentCredits + multipliedCredits);
                    awardedCredits = multipliedCredits;
                    log.debug("[CREDITS DEBUG] Awarded {} credits ({}x{}) to user {}. New balance: {}", 
                        multipliedCredits, creditsToAward, roleMultiplier, userId, user.getCredits());
                    userUpdated = true; // Ensure user is marked for update
                }
                
                // Add right before calling checkAndProcessLevelUp method
                log.debug("[XP DEBUG] Checking for level up: User={}, Level={}, XP={}",
                    userId, user.getLevel(), user.getExperience());
                checkAndProcessLevelUp(user, userId, event.getChannel(), roleMultiplier); // Note: checkAndProcessLevelUp calls updateUser internally on level up
                
                // XP notifications removed to reduce chat spam - only level-up notifications are shown
            }
            
            // Track message in activity window (for stats purposes only, not for credits)
            if (activityEnabled) {
                List<Instant> userMessages = userActivity.computeIfAbsent(userId, k -> new ArrayList<>());
                
                // Remove messages outside the time window
                Instant cutoff = now.minusSeconds(timeWindowMinutes * 60);
                userMessages.removeIf(timestamp -> timestamp.isBefore(cutoff));
                
                // Add current message
                userMessages.add(now);
                
                log.debug("[ACTIVITY DEBUG] User {} has {} messages in window. Time window={} min", 
                            userId, userMessages.size(), timeWindowMinutes);
            }
            
            // Persist changes if user was updated (message count, XP, credits) but no level up happened
            // Level-up already saves changes internally in checkAndProcessLevelUp
            if (userUpdated && initialLevel == user.getLevel().intValue()) {
                try {
                    userService.updateUser(user);
                    log.debug("Persisted user {} state after activity processing.", userId);
                } catch (Exception e) {
                    log.error("Error saving user state for {} after activity processing: {}", userId, e.getMessage(), e);
                }
            }
            
        } catch (Exception e) {
            log.error("Error processing message from user {}: {}", userId, e.getMessage(), e);
        }
    }

    public void updateSettings(
            Boolean activityEnabled,
            Integer creditsToAward,
            Integer messageThreshold,
            Integer timeWindowMinutes,
            Integer cooldownSeconds,
            Integer minMessageLength,
            Boolean levelingEnabled,
            Integer xpToAward,
            Integer baseXp,
            Integer levelMultiplier,
            Integer levelExponent,
            Integer levelFactor,
            Integer creditsPerLevel,
            String level5RoleId,
            String level15RoleId,
            String level30RoleId,
            String level40RoleId,
            String level50RoleId,
            String level70RoleId,
            String level100RoleId,
            String starterRoleId,
            String roleMultipliers,
            Boolean roleMultipliersEnabled) {
        
        if (activityEnabled != null) this.activityEnabled = activityEnabled;
        if (creditsToAward != null) this.creditsToAward = creditsToAward;
        if (messageThreshold != null) this.messageThreshold = messageThreshold;
        if (timeWindowMinutes != null) this.timeWindowMinutes = timeWindowMinutes;
        if (cooldownSeconds != null) this.cooldownSeconds = cooldownSeconds;
        if (minMessageLength != null) this.minMessageLength = minMessageLength;
        
        if (levelingEnabled != null) this.levelingEnabled = levelingEnabled;
        if (xpToAward != null) this.xpToAward = xpToAward;
        if (baseXp != null) this.baseXp = baseXp;
        if (levelMultiplier != null) this.levelMultiplier = levelMultiplier;
        if (levelExponent != null) this.levelExponent = levelExponent;
        if (levelFactor != null) this.levelFactor = levelFactor;
        if (creditsPerLevel != null) this.creditsPerLevel = creditsPerLevel;
        
        // Update role ID settings
        if (level5RoleId != null) this.level5RoleId = level5RoleId;
        if (level15RoleId != null) this.level15RoleId = level15RoleId;
        if (level30RoleId != null) this.level30RoleId = level30RoleId;
        if (level40RoleId != null) this.level40RoleId = level40RoleId;
        if (level50RoleId != null) this.level50RoleId = level50RoleId;
        if (level70RoleId != null) this.level70RoleId = level70RoleId;
        if (level100RoleId != null) this.level100RoleId = level100RoleId;
        if (starterRoleId != null) this.starterRoleId = starterRoleId;
        
        log.info("Discord bot activity, leveling and role settings updated at runtime");
        log.debug("Activity: enabled={}, credits={}, threshold={}, window={}, cooldown={}, minLength={}",
                 activityEnabled, creditsToAward, messageThreshold, timeWindowMinutes, cooldownSeconds, minMessageLength);
        log.debug("Leveling: enabled={}, xp={}, baseXp={}, multiplier={}, exponent={}, factor={}, creditsPerLevel={}",
                 levelingEnabled, xpToAward, baseXp, levelMultiplier, levelExponent, levelFactor, creditsPerLevel);
        log.debug("Role IDs: level5={}, level15={}, level30={}, level40={}, level50={}, level70={}, level100={}",
                level5RoleId, level15RoleId, level30RoleId, level40RoleId, level50RoleId, level70RoleId, level100RoleId);
        log.debug("Starter Role ID: {}", starterRoleId);
    }
    
    /**
     * Parse role multipliers configuration string into a map.
     * Format: "roleId1:multiplier1,roleId2:multiplier2"
     * 
     * @param roleMultipliersConfig the configuration string
     * @return map of role ID to multiplier value
     */
    private Map<String, Double> parseRoleMultipliers(String roleMultipliersConfig) {
        Map<String, Double> multipliers = new HashMap<>();
        
        if (roleMultipliersConfig == null || roleMultipliersConfig.trim().isEmpty()) {
            return multipliers;
        }
        
        try {
            String[] entries = roleMultipliersConfig.split(",");
            for (String entry : entries) {
                String[] parts = entry.trim().split(":");
                if (parts.length == 2) {
                    String roleId = parts[0].trim();
                    double multiplier = Double.parseDouble(parts[1].trim());
                    
                    // Validate role ID format (should be numeric)
                    if (roleId.matches("\\d+") && multiplier > 0) {
                        multipliers.put(roleId, multiplier);
                        log.debug("[ROLE MULTIPLIER] Parsed multiplier: roleId={}, multiplier={}", roleId, multiplier);
                    } else {
                        log.warn("[ROLE MULTIPLIER] Invalid entry format: roleId={}, multiplier={}", roleId, multiplier);
                    }
                }
            }
        } catch (Exception e) {
            log.error("[ROLE MULTIPLIER] Error parsing role multipliers configuration: {}", e.getMessage(), e);
        }
        
        return multipliers;
    }
    
    /**
     * Get the highest role multiplier for a user based on their Discord roles.
     * 
     * @param userId the Discord user ID
     * @param event the message event to get guild/member information
     * @return the highest multiplier the user qualifies for, or 1.0 if none
     */
    private double getUserRoleMultiplier(String userId, MessageReceivedEvent event) {
        try {
            // Get Discord bot settings to check if role multipliers are enabled
            var settings = discordBotSettingsService.getCurrentSettings();
            if (!Boolean.TRUE.equals(settings.getRoleMultipliersEnabled()) || 
                settings.getRoleMultipliers() == null || 
                settings.getRoleMultipliers().trim().isEmpty()) {
                return 1.0; // No multiplier if disabled or not configured
            }
            
            // Parse role multipliers configuration
            Map<String, Double> roleMultipliers = parseRoleMultipliers(settings.getRoleMultipliers());
            if (roleMultipliers.isEmpty()) {
                return 1.0; // No valid multipliers configured
            }
            
            // Get the Discord guild and member
            Guild guild = event.getGuild();
            if (guild == null) {
                log.debug("[ROLE MULTIPLIER] No guild found for message event");
                return 1.0;
            }
            
            // Retrieve the member asynchronously but wait for result
            try {
                Member member = guild.retrieveMemberById(userId).complete();
                if (member == null) {
                    log.debug("[ROLE MULTIPLIER] Member not found in guild: userId={}", userId);
                    return 1.0;
                }
                
                // Check user's roles for the highest multiplier
                double highestMultiplier = 1.0;
                List<Role> userRoles = member.getRoles();
                
                for (Role role : userRoles) {
                    String roleId = role.getId();
                    if (roleMultipliers.containsKey(roleId)) {
                        double multiplier = roleMultipliers.get(roleId);
                        if (multiplier > highestMultiplier) {
                            highestMultiplier = multiplier;
                            log.debug("[ROLE MULTIPLIER] User {} has qualifying role {} with multiplier {}", 
                                     userId, roleId, multiplier);
                        }
                    }
                }
                
                if (highestMultiplier > 1.0) {
                    log.debug("[ROLE MULTIPLIER] User {} final multiplier: {}", userId, highestMultiplier);
                }
                
                return highestMultiplier;
                
            } catch (Exception memberException) {
                log.warn("[ROLE MULTIPLIER] Failed to retrieve member {}: {}", userId, memberException.getMessage());
                return 1.0;
            }
            
        } catch (Exception e) {
            log.error("[ROLE MULTIPLIER] Error getting user role multiplier for user {}: {}", userId, e.getMessage(), e);
            return 1.0;
        }
    }
} 