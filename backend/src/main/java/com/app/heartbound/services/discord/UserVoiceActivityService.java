package com.app.heartbound.services.discord;

import com.app.heartbound.entities.User;
import com.app.heartbound.services.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.channel.unions.AudioChannelUnion;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import javax.annotation.Nonnull;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * UserVoiceActivityService
 * 
 * Service that tracks individual user voice channel time across all voice channels.
 * Monitors when users join, leave, or move between voice channels and calculates
 * total voice time for ranking and statistics purposes.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserVoiceActivityService extends ListenerAdapter {

    private final UserService userService;
    
    // Track active voice sessions: userId -> sessionStartTime
    private final ConcurrentHashMap<String, LocalDateTime> activeUserSessions = new ConcurrentHashMap<>();
    
    // Inactivity channel where users don't accumulate voice time
    private volatile String inactivityChannelId;
    
    private ScheduledExecutorService rankUpdateScheduler;

    @PostConstruct
    public void init() {
        // Schedule periodic voice rank updates (every 10 minutes)
        rankUpdateScheduler = Executors.newSingleThreadScheduledExecutor();
        rankUpdateScheduler.scheduleAtFixedRate(this::updateVoiceRanks, 
                10, 10, TimeUnit.MINUTES);
        log.info("User voice activity tracking initialized with rank updates every 10 minutes");
    }
    
    @PreDestroy
    public void shutdown() {
        if (rankUpdateScheduler != null) {
            rankUpdateScheduler.shutdown();
            try {
                if (!rankUpdateScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    rankUpdateScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                rankUpdateScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        // End all active sessions before shutdown
        cleanupActiveSessions();
        log.info("User voice activity tracking shutdown completed");
    }

    @Override
    @Transactional
    public void onGuildVoiceUpdate(@Nonnull GuildVoiceUpdateEvent event) {
        try {
            String userId = event.getMember().getId();
            AudioChannelUnion leftChannel = event.getChannelLeft();
            AudioChannelUnion joinedChannel = event.getChannelJoined();

            // Handle leaving a channel
            if (leftChannel != null) {
                handleVoiceChannelLeft(userId);
            }

            // Handle joining a channel
            if (joinedChannel != null) {
                if (inactivityChannelId != null && !inactivityChannelId.isBlank() && joinedChannel.getId().equals(inactivityChannelId)) {
                    log.debug("User {} joined the designated inactivity channel. Voice session will not be started.", userId);
                } else {
                    handleVoiceChannelJoined(userId);
                }
            }

        } catch (Exception e) {
            log.error("Error processing voice state update for user {}: {}", 
                event.getMember().getId(), e.getMessage(), e);
        }
    }

    private void handleVoiceChannelLeft(String userId) {
        LocalDateTime sessionStart = activeUserSessions.remove(userId);
        if (sessionStart != null) {
            endVoiceSession(userId, sessionStart);
        }
    }

    private void handleVoiceChannelJoined(String userId) {
        // Start new session
        LocalDateTime sessionStart = LocalDateTime.now();
        activeUserSessions.put(userId, sessionStart);
        
        log.debug("Started voice session for user: {}", userId);
    }

    private void endVoiceSession(String userId, LocalDateTime sessionStart) {
        try {
            LocalDateTime sessionEnd = LocalDateTime.now();
            long sessionMinutes = ChronoUnit.MINUTES.between(sessionStart, sessionEnd);
            
            // Only count sessions longer than 1 minute to avoid spam
            if (sessionMinutes >= 1) {
                // The user existence check is now handled within the transactional method.
                // Update voice time counters transactionally.
                userService.incrementVoiceTimeCounters(userId, (int) sessionMinutes);
                
                // 📊 Track daily voice activity for chart display
                userService.trackDailyVoiceActivityStat(userId, (int) sessionMinutes);
                
                log.info("Ended voice session for user {} (duration: {} minutes)", 
                    userId, sessionMinutes);
            } else {
                log.debug("Voice session for user {} too short ({} minutes), not counting", 
                    userId, sessionMinutes);
            }
            
        } catch (Exception e) {
            log.error("Error ending voice session for user {}: {}", 
                userId, e.getMessage(), e);
        }
    }

    /**
     * Updates voice ranks for all users based on total voice time
     */
    private void updateVoiceRanks() {
        try {
            log.debug("Starting scheduled voice rank update");
            userService.updateVoiceRanks();
            log.debug("Completed scheduled voice rank update");
        } catch (Exception e) {
            log.error("Error during scheduled voice rank update: {}", e.getMessage(), e);
        }
    }

    /**
     * Cleanup method to end any orphaned voice sessions
     * Called when the application shuts down
     */
    public void cleanupActiveSessions() {
        log.info("Cleaning up {} active voice sessions", activeUserSessions.size());
        
        for (String userId : activeUserSessions.keySet()) {
            LocalDateTime sessionStart = activeUserSessions.get(userId);
            if (sessionStart != null) {
                endVoiceSession(userId, sessionStart);
            }
        }
        
        activeUserSessions.clear();
    }

    /**
     * End voice session for a specific user (called when user is being processed externally)
     */
    public void endVoiceSessionForUser(String userId) {
        LocalDateTime sessionStart = activeUserSessions.remove(userId);
        if (sessionStart != null) {
            endVoiceSession(userId, sessionStart);
        }
    }

    /**
     * Get active session count for monitoring
     */
    public int getActiveSessionCount() {
        return activeUserSessions.size();
    }

    /**
     * Updates the voice activity settings including the inactivity channel
     */
    public void updateSettings(String inactivityChannelId) {
        this.inactivityChannelId = inactivityChannelId;
        log.info("Updated inactivity channel ID to: {}", inactivityChannelId);
    }
} 