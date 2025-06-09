package com.app.heartbound.services.discord;

import com.app.heartbound.entities.Pairing;
import com.app.heartbound.repositories.pairing.PairingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.unions.AudioChannelUnion;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DiscordVoiceTimeTrackerService
 * 
 * Service that tracks voice channel time for paired users when they are
 * alone together in a voice channel. Only counts time when exactly two
 * paired users are in the same voice channel with no one else.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DiscordVoiceTimeTrackerService extends ListenerAdapter {

    private final PairingRepository pairingRepository;
    
    // Track active voice sessions: channelId -> pairingId
    private final ConcurrentHashMap<String, Long> activeVoiceSessions = new ConcurrentHashMap<>();

    @Override
    @Transactional
    public void onGuildVoiceUpdate(GuildVoiceUpdateEvent event) {
        try {
            String userId = event.getMember().getId();
            AudioChannelUnion leftChannel = event.getChannelLeft();
            AudioChannelUnion joinedChannel = event.getChannelJoined();

            // Handle leaving a channel
            if (leftChannel != null) {
                handleVoiceChannelLeft(leftChannel, userId);
            }

            // Handle joining a channel
            if (joinedChannel != null) {
                handleVoiceChannelJoined(joinedChannel, userId);
            }

        } catch (Exception e) {
            log.error("Error processing voice state update for user {}: {}", 
                event.getMember().getId(), e.getMessage(), e);
        }
    }

    private void handleVoiceChannelLeft(AudioChannelUnion channel, String userId) {
        String channelId = channel.getId();
        
        // Check if this channel had an active pairing session
        Long pairingId = activeVoiceSessions.get(channelId);
        if (pairingId != null) {
            // End the voice session and update database
            endVoiceSession(pairingId, channelId);
        }
        
        // After someone leaves, check if channel still has a valid pairing
        checkAndStartVoiceSession(channel);
    }

    private void handleVoiceChannelJoined(AudioChannelUnion channel, String userId) {
        // When someone joins, check if this creates or breaks a valid pairing session
        checkAndStartVoiceSession(channel);
    }

    private void checkAndStartVoiceSession(AudioChannelUnion channel) {
        try {
            List<Member> membersInChannel = channel.getMembers();
            
            // We only track sessions with exactly 2 members
            if (membersInChannel.size() != 2) {
                // If there's an active session but not exactly 2 people, end it
                String channelId = channel.getId();
                Long activePairingId = activeVoiceSessions.get(channelId);
                if (activePairingId != null) {
                    endVoiceSession(activePairingId, channelId);
                }
                return;
            }

            String user1Id = membersInChannel.get(0).getId();
            String user2Id = membersInChannel.get(1).getId();

            // Check if these two users are in an active pairing
            Optional<Pairing> pairingOpt = findActivePairingForUsers(user1Id, user2Id);
            
            if (pairingOpt.isPresent()) {
                Pairing pairing = pairingOpt.get();
                String channelId = channel.getId();
                
                // Check if session is already being tracked
                if (!activeVoiceSessions.containsKey(channelId)) {
                    startVoiceSession(pairing, channelId);
                }
            }

        } catch (Exception e) {
            log.error("Error checking voice session for channel {}: {}", 
                channel.getId(), e.getMessage(), e);
        }
    }

    private Optional<Pairing> findActivePairingForUsers(String user1Id, String user2Id) {
        // Find active pairing where these two users are paired
        return pairingRepository.findByActiveTrue().stream()
                .filter(pairing -> 
                    (pairing.getUser1Id().equals(user1Id) && pairing.getUser2Id().equals(user2Id)) ||
                    (pairing.getUser1Id().equals(user2Id) && pairing.getUser2Id().equals(user1Id))
                )
                .findFirst();
    }

    private void startVoiceSession(Pairing pairing, String channelId) {
        try {
            // Update pairing with session start time
            pairing.setCurrentVoiceSessionStart(LocalDateTime.now());
            pairingRepository.save(pairing);
            
            // Track active session
            activeVoiceSessions.put(channelId, pairing.getId());
            
            log.info("Started voice session for pairing {} in channel {} (users: {} and {})", 
                pairing.getId(), channelId, pairing.getUser1Id(), pairing.getUser2Id());
                
        } catch (Exception e) {
            log.error("Error starting voice session for pairing {}: {}", 
                pairing.getId(), e.getMessage(), e);
        }
    }

    private void endVoiceSession(Long pairingId, String channelId) {
        try {
            Optional<Pairing> pairingOpt = pairingRepository.findById(pairingId);
            if (pairingOpt.isPresent()) {
                Pairing pairing = pairingOpt.get();
                
                if (pairing.getCurrentVoiceSessionStart() != null) {
                    // Calculate session duration
                    LocalDateTime sessionStart = pairing.getCurrentVoiceSessionStart();
                    LocalDateTime sessionEnd = LocalDateTime.now();
                    
                    long sessionMinutes = ChronoUnit.MINUTES.between(sessionStart, sessionEnd);
                    
                    // Only count sessions longer than 1 minute to avoid spam
                    if (sessionMinutes >= 1) {
                        // Add to total voice time
                        int newTotalMinutes = pairing.getVoiceTimeMinutes() + (int) sessionMinutes;
                        pairing.setVoiceTimeMinutes(newTotalMinutes);
                        
                        log.info("Ended voice session for pairing {} (duration: {} minutes, total: {} minutes)", 
                            pairing.getId(), sessionMinutes, newTotalMinutes);
                    }
                    
                    // Clear session start time
                    pairing.setCurrentVoiceSessionStart(null);
                    pairingRepository.save(pairing);
                }
            }
            
            // Remove from active sessions
            activeVoiceSessions.remove(channelId);
            
        } catch (Exception e) {
            log.error("Error ending voice session for pairing {} in channel {}: {}", 
                pairingId, channelId, e.getMessage(), e);
        }
    }

    /**
     * Cleanup method to end any orphaned voice sessions
     * Called when the application shuts down or pairings are ended
     */
    public void cleanupVoiceSessions() {
        log.info("Cleaning up {} active voice sessions", activeVoiceSessions.size());
        
        for (String channelId : activeVoiceSessions.keySet()) {
            Long pairingId = activeVoiceSessions.get(channelId);
            if (pairingId != null) {
                endVoiceSession(pairingId, channelId);
            }
        }
        
        activeVoiceSessions.clear();
    }

    /**
     * End voice session for a specific pairing (called when pairing is ended)
     */
    public void endVoiceSessionForPairing(Long pairingId) {
        // Find and end any active voice sessions for this pairing
        activeVoiceSessions.entrySet().removeIf(entry -> {
            if (entry.getValue().equals(pairingId)) {
                endVoiceSession(pairingId, entry.getKey());
                return true;
            }
            return false;
        });
    }
} 