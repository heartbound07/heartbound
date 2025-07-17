package com.app.heartbound.services.discord;

import com.app.heartbound.entities.DiscordBotSettings;
import com.app.heartbound.repositories.DiscordBotSettingsRepository;
import com.app.heartbound.config.CacheConfig;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import jakarta.annotation.PostConstruct;
import javax.annotation.Nonnull;

/**
 * Service responsible for monitoring Discord channel activity and automatically
 * applying slowmode when activity thresholds are exceeded.
 */
@Service
public class AutoSlowmodeService extends ListenerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(AutoSlowmodeService.class);

    private final DiscordBotSettingsRepository discordBotSettingsRepository;
    private final CacheConfig cacheConfig;

    // JDA instance obtained from first event
    private JDA jda;

    // Track message activity per channel
    private final Map<String, List<LocalDateTime>> channelActivity = new ConcurrentHashMap<>();
    
    // Track when slowmode was last applied to prevent rapid changes
    private final Map<String, LocalDateTime> slowmodeLastApplied = new ConcurrentHashMap<>();
    
    // Track current slowmode status to avoid unnecessary API calls
    private final Map<String, Integer> currentSlowmodeStatus = new ConcurrentHashMap<>();

    public AutoSlowmodeService(DiscordBotSettingsRepository discordBotSettingsRepository, CacheConfig cacheConfig) {
        this.discordBotSettingsRepository = discordBotSettingsRepository;
        this.cacheConfig = cacheConfig;
    }

    @PostConstruct
    public void initialize() {
        logger.info("AutoSlowmodeService initialized and ready to monitor channel activity");
    }

    @Override
    public void onMessageReceived(@Nonnull MessageReceivedEvent event) {
        // Only process guild messages
        if (!event.isFromGuild() || event.getAuthor().isBot()) {
            return;
        }

        // Store JDA instance from first event (following pattern of other services)
        if (this.jda == null) {
            this.jda = event.getJDA();
            logger.debug("JDA instance stored from first message event");
        }

        try {
            DiscordBotSettings settings = getDiscordBotSettings();
            if (settings == null || !Boolean.TRUE.equals(settings.getAutoSlowmodeEnabled())) {
                return;
            }

            String channelId = event.getChannel().getId();
            if (!isMonitoredChannel(channelId, settings)) {
                return;
            }

            // Record message activity
            recordMessageActivity(channelId);
            
            // Check if slowmode should be applied
            evaluateSlowmodeForChannel(channelId, event.getChannel().asTextChannel(), settings);

        } catch (Exception e) {
            logger.error("Error processing message for auto slowmode: {}", e.getMessage(), e);
        }
    }

    /**
     * Scheduled task to periodically evaluate and remove slowmode from channels
     * that have calmed down.
     */
    @Scheduled(fixedRate = 60000) // Run every minute
    public void evaluateSlowmodeRemoval() {
        try {
            // Check if JDA is available (service needs to receive at least one event first)
            if (this.jda == null) {
                logger.debug("JDA not yet available for scheduled slowmode evaluation, skipping this cycle");
                return;
            }

            DiscordBotSettings settings = getDiscordBotSettings();
            if (settings == null || !Boolean.TRUE.equals(settings.getAutoSlowmodeEnabled())) {
                return;
            }

            List<String> channelIds = parseChannelIds(settings.getSlowmodeChannelIds());
            for (String channelId : channelIds) {
                if (currentSlowmodeStatus.containsKey(channelId) && currentSlowmodeStatus.get(channelId) > 0) {
                    // Channel currently has slowmode, check if it should be removed
                    int currentActivity = getRecentActivityCount(channelId, settings.getSlowmodeTimeWindow());
                    if (currentActivity < settings.getActivityThreshold()) {
                        // Activity has calmed down, consider removing slowmode
                        LocalDateTime lastApplied = slowmodeLastApplied.get(channelId);
                        if (lastApplied != null) {
                            long minutesSinceApplied = ChronoUnit.MINUTES.between(lastApplied, LocalDateTime.now());
                            if (minutesSinceApplied >= settings.getSlowmodeCooldown()) {
                                removeSlowmodeIfPossible(channelId);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error during scheduled slowmode evaluation: {}", e.getMessage(), e);
        }
    }

    private void recordMessageActivity(String channelId) {
        channelActivity.computeIfAbsent(channelId, k -> new ArrayList<>()).add(LocalDateTime.now());
        
        // Clean up old activity records to prevent memory leaks
        cleanupOldActivity(channelId);
    }

    private void cleanupOldActivity(String channelId) {
        List<LocalDateTime> activity = channelActivity.get(channelId);
        if (activity != null) {
            LocalDateTime cutoff = LocalDateTime.now().minusHours(1); // Keep 1 hour of history
            activity.removeIf(timestamp -> timestamp.isBefore(cutoff));
        }
    }

    private void evaluateSlowmodeForChannel(String channelId, TextChannel channel, DiscordBotSettings settings) {
        int recentActivity = getRecentActivityCount(channelId, settings.getSlowmodeTimeWindow());
        
        if (recentActivity >= settings.getActivityThreshold()) {
            // Check cooldown before applying slowmode
            LocalDateTime lastApplied = slowmodeLastApplied.get(channelId);
            if (lastApplied != null) {
                long minutesSinceApplied = ChronoUnit.MINUTES.between(lastApplied, LocalDateTime.now());
                if (minutesSinceApplied < settings.getSlowmodeCooldown()) {
                    logger.debug("Slowmode cooldown active for channel {}, skipping application", channelId);
                    return;
                }
            }

            applySlowmode(channel, settings.getSlowmodeDuration());
        }
    }

    private int getRecentActivityCount(String channelId, int timeWindowMinutes) {
        List<LocalDateTime> activity = channelActivity.get(channelId);
        if (activity == null || activity.isEmpty()) {
            return 0;
        }

        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(timeWindowMinutes);
        return (int) activity.stream()
                .filter(timestamp -> timestamp.isAfter(cutoff))
                .count();
    }

    private void applySlowmode(TextChannel channel, int slowmodeDuration) {
        try {
            // Check if bot has permission to manage channel
            Guild guild = channel.getGuild();
            if (!guild.getSelfMember().hasPermission(channel, Permission.MANAGE_CHANNEL)) {
                logger.warn("Bot lacks MANAGE_CHANNEL permission in channel {}", channel.getId());
                return;
            }

            // Check if slowmode is already applied
            Integer currentSlowmode = currentSlowmodeStatus.get(channel.getId());
            if (currentSlowmode != null && currentSlowmode >= slowmodeDuration) {
                logger.debug("Slowmode already applied to channel {} with duration >= {}", channel.getId(), slowmodeDuration);
                return;
            }

            // Apply slowmode
            channel.getManager().setSlowmode(slowmodeDuration).queue(
                success -> {
                    currentSlowmodeStatus.put(channel.getId(), slowmodeDuration);
                    slowmodeLastApplied.put(channel.getId(), LocalDateTime.now());
                    logger.info("Applied slowmode ({} seconds) to channel {} due to high activity", 
                               slowmodeDuration, channel.getId());
                },
                error -> {
                    logger.error("Failed to apply slowmode to channel {}: {}", channel.getId(), error.getMessage());
                }
            );

        } catch (Exception e) {
            logger.error("Error applying slowmode to channel {}: {}", channel.getId(), e.getMessage(), e);
        }
    }

    private void removeSlowmodeIfPossible(String channelId) {
        if (!isJDAAvailable()) {
            logger.debug("JDA not available for slowmode removal on channel {}", channelId);
            return;
        }

        try {
            TextChannel channel = jda.getTextChannelById(channelId);
            if (channel == null) {
                logger.warn("Channel {} not found when trying to remove slowmode", channelId);
                currentSlowmodeStatus.remove(channelId);
                slowmodeLastApplied.remove(channelId);
                return;
            }

            // Check if bot has permission to manage channel
            Guild guild = channel.getGuild();
            if (!guild.getSelfMember().hasPermission(channel, Permission.MANAGE_CHANNEL)) {
                logger.warn("Bot lacks MANAGE_CHANNEL permission in channel {} for slowmode removal", channelId);
                return;
            }

            // Remove slowmode (set to 0)
            channel.getManager().setSlowmode(0).queue(
                success -> {
                    currentSlowmodeStatus.remove(channelId);
                    slowmodeLastApplied.remove(channelId);
                    logger.info("Removed slowmode from channel {} (activity calmed down)", channelId);
                },
                error -> {
                    logger.error("Failed to remove slowmode from channel {}: {}", channelId, error.getMessage());
                }
            );

        } catch (Exception e) {
            logger.error("Error removing slowmode from channel {}: {}", channelId, e.getMessage(), e);
        }
    }

    private boolean isMonitoredChannel(String channelId, DiscordBotSettings settings) {
        List<String> monitoredChannels = parseChannelIds(settings.getSlowmodeChannelIds());
        return monitoredChannels.contains(channelId);
    }

    private List<String> parseChannelIds(String channelIdsString) {
        if (channelIdsString == null || channelIdsString.trim().isEmpty()) {
            return new ArrayList<>();
        }
        
        return Arrays.stream(channelIdsString.split(","))
                .map(String::trim)
                .filter(id -> !id.isEmpty())
                .filter(id -> id.matches("\\d+")) // Validate that it's all digits
                .toList();
    }

    private DiscordBotSettings getDiscordBotSettings() {
        try {
            // Check cache first
            DiscordBotSettings cachedSettings = (DiscordBotSettings) cacheConfig.getDiscordBotSettingsCache().getIfPresent(1L);
            if (cachedSettings != null) {
                return cachedSettings;
            }

            // Fetch from database
            Optional<DiscordBotSettings> settingsOpt = discordBotSettingsRepository.findById(1L);
            if (settingsOpt.isPresent()) {
                DiscordBotSettings settings = settingsOpt.get();
                cacheConfig.getDiscordBotSettingsCache().put(1L, settings);
                return settings;
            }

            logger.warn("Discord bot settings not found in database");
            return null;
        } catch (Exception e) {
            logger.error("Error fetching Discord bot settings: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Check if JDA is available for operations
     */
    private boolean isJDAAvailable() {
        return this.jda != null;
    }

    /**
     * Manual method to clear slowmode from a channel (for admin use)
     */
    public void clearSlowmode(String channelId) {
        currentSlowmodeStatus.remove(channelId);
        slowmodeLastApplied.remove(channelId);
        logger.info("Manually cleared slowmode tracking for channel {}", channelId);
    }

    /**
     * Check if the service is ready to handle slowmode operations
     */
    public boolean isServiceReady() {
        return isJDAAvailable();
    }

    /**
     * Get current slowmode status for monitoring
     */
    public Map<String, Integer> getCurrentSlowmodeStatus() {
        return new HashMap<>(currentSlowmodeStatus);
    }

    /**
     * Get recent activity statistics for monitoring
     */
    public Map<String, Integer> getChannelActivityStats(int timeWindowMinutes) {
        Map<String, Integer> stats = new HashMap<>();
        for (Map.Entry<String, List<LocalDateTime>> entry : channelActivity.entrySet()) {
            stats.put(entry.getKey(), getRecentActivityCount(entry.getKey(), timeWindowMinutes));
        }
        return stats;
    }
} 