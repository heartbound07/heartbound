package com.app.heartbound.services.pairing;

import com.app.heartbound.dto.pairing.*;
import com.app.heartbound.entities.BlacklistEntry;
import com.app.heartbound.entities.MatchQueueUser;
import com.app.heartbound.entities.Pairing;
import com.app.heartbound.entities.User;
import com.app.heartbound.repositories.UserRepository;
import com.app.heartbound.repositories.pairing.BlacklistEntryRepository;
import com.app.heartbound.repositories.pairing.MatchQueueUserRepository;
import com.app.heartbound.repositories.pairing.PairingRepository;
import com.app.heartbound.services.discord.DiscordPairingChannelService;
import com.app.heartbound.services.discord.DiscordVoiceTimeTrackerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * PairingService
 * 
 * Service for managing user pairings in the "Don't Catch Feelings Challenge".
 * Handles creation, updates, breakups, and activity tracking.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PairingService {

    private final PairingRepository pairingRepository;
    private final BlacklistEntryRepository blacklistEntryRepository;
    private final MatchQueueUserRepository matchQueueUserRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final DiscordPairingChannelService discordPairingChannelService;
    private final DiscordVoiceTimeTrackerService discordVoiceTimeTrackerService;
    
    // XP System Services
    private final PairLevelService pairLevelService;
    private final AchievementService achievementService;
    private final VoiceStreakService voiceStreakService;

    /**
     * Create a new pairing between two users
     */
    @Transactional
    public PairingDTO createPairing(CreatePairingRequestDTO request) {
        // Input sanitization and validation
        String sanitizedUser1Id = sanitizeUserId(request.getUser1Id());
        String sanitizedUser2Id = sanitizeUserId(request.getUser2Id());
        
        log.info("Creating pairing between users {} and {}", sanitizedUser1Id, sanitizedUser2Id);

        // Validate users exist
        validateUsersExist(sanitizedUser1Id, sanitizedUser2Id);

        // Check if either user is already in an active pairing
        validateUsersNotInActivePairing(sanitizedUser1Id, sanitizedUser2Id);

        // Check if users are blacklisted
        if (areUsersBlacklisted(sanitizedUser1Id, sanitizedUser2Id)) {
            throw new IllegalArgumentException("Users are blacklisted and cannot be paired");
        }

        // SAFETY CHECK: Prevent pairing creation if compatibility score is 0
        if (request.getCompatibilityScore() <= 0) {
            log.error("CRITICAL ERROR: Attempted to create pairing with invalid compatibility score: {}", request.getCompatibilityScore());
            throw new IllegalArgumentException("Cannot create pairing with compatibility score of " + request.getCompatibilityScore());
        }

        // Create pairing entity with sanitized data (initially without Discord channel info)
        Pairing pairing = Pairing.builder()
                .user1Id(sanitizedUser1Id)
                .user2Id(sanitizedUser2Id)
                .compatibilityScore(Math.max(0, Math.min(100, request.getCompatibilityScore())))
                .matchedAt(LocalDateTime.now())
                .user1Age(request.getUser1Age())
                .user1Gender(request.getUser1Gender())
                .user1Region(request.getUser1Region())
                .user1Rank(request.getUser1Rank())
                .user2Age(request.getUser2Age())
                .user2Gender(request.getUser2Gender())
                .user2Region(request.getUser2Region())
                .user2Rank(request.getUser2Rank())
                .build();

        // Save pairing first to get the ID
        Pairing savedPairing = pairingRepository.save(pairing);

        // 🚀 NEW: Create Discord channel for the pairing
        createDiscordChannelForPairing(savedPairing, request.getUser1DiscordId(), request.getUser2DiscordId());

        // 🚀 XP SYSTEM: Initialize pair level for new pairing
        try {
            pairLevelService.getOrCreatePairLevel(savedPairing);
            log.info("Initialized XP system for pairing {}", savedPairing.getId());
        } catch (Exception e) {
            log.error("Failed to initialize XP system for pairing {}: {}", savedPairing.getId(), e.getMessage());
        }

        // CREATE BLACKLIST ENTRY IMMEDIATELY - prevents future re-matching
        createBlacklistEntry(sanitizedUser1Id, sanitizedUser2Id, 
                           "Matched on " + LocalDateTime.now() + " - permanent blacklist");

        // Remove users from queue if they are queued
        removeUsersFromQueue(sanitizedUser1Id, sanitizedUser2Id);

        log.info("Successfully created pairing with ID: {} and blacklisted users", savedPairing.getId());
        return mapToPairingDTO(savedPairing);
    }

    /**
     * Get current active pairing for a user
     */
    @Transactional(readOnly = true)
    public Optional<PairingDTO> getCurrentPairing(String userId) {
        return pairingRepository.findActivePairingByUserId(userId)
                .map(this::mapToPairingDTO);
    }

    /**
     * Update activity metrics for a pairing
     */
    @Transactional
    public PairingDTO updatePairingActivity(Long pairingId, UpdatePairingActivityDTO request) {
        log.info("Updating activity for pairing ID: {}", pairingId);

        Pairing pairing = pairingRepository.findById(pairingId)
                .orElseThrow(() -> new IllegalArgumentException("Pairing not found with ID: " + pairingId));

        if (!pairing.isActive()) {
            throw new IllegalStateException("Cannot update activity for inactive pairing");
        }

        // Update activity metrics
        pairing.setMessageCount(pairing.getMessageCount() + request.getMessageIncrement());
        pairing.setWordCount(pairing.getWordCount() + request.getWordIncrement());
        pairing.setEmojiCount(pairing.getEmojiCount() + request.getEmojiIncrement());

        // Update active days if provided
        if (request.getActiveDays() != null) {
            pairing.setActiveDays(request.getActiveDays());
        }

        Pairing updatedPairing = pairingRepository.save(pairing);

        // 🚀 XP SYSTEM: Update XP and check achievements after activity update
        try {
            // Update pair level based on new activity
            pairLevelService.updatePairLevelFromActivity(pairingId);
            
            // Check for new achievements
            achievementService.checkAndUnlockAchievements(pairingId);
            
            log.info("Updated XP system for pairing {} after activity update", pairingId);
        } catch (Exception e) {
            log.error("Failed to update XP system for pairing {}: {}", pairingId, e.getMessage());
        }

        log.info("Successfully updated activity for pairing ID: {}", pairingId);
        return mapToPairingDTO(updatedPairing);
    }

    /**
     * Handle pairing breakup
     */
    @Transactional
    public PairingDTO breakupPairing(Long pairingId, BreakupRequestDTO request) {
        String sanitizedInitiatorId = sanitizeUserId(request.getInitiatorId());
        String sanitizedReason = sanitizeInput(request.getReason());
        
        log.info("Processing breakup for pairing {} initiated by {}", pairingId, sanitizedInitiatorId);

        Pairing pairing = pairingRepository.findById(pairingId)
                .orElseThrow(() -> new IllegalArgumentException("Pairing not found"));

        if (!pairing.isActive()) {
            throw new IllegalStateException("Pairing is already inactive");
        }

        // Validate initiator is part of the pairing
        if (!pairing.involvesUser(sanitizedInitiatorId)) {
            throw new IllegalArgumentException("Initiator is not part of this pairing");
        }

        // Identify partner
        String partnerId = pairing.getUser1Id().equals(sanitizedInitiatorId) 
                          ? pairing.getUser2Id() 
                          : pairing.getUser1Id();

        // Update pairing with breakup information
        pairing.setActive(false);
        pairing.setBreakupInitiatorId(sanitizedInitiatorId);
        pairing.setBreakupReason(sanitizedReason);
        pairing.setBreakupTimestamp(LocalDateTime.now());
        pairing.setMutualBreakup(request.isMutualBreakup());

        // Save updated pairing
        Pairing updatedPairing = pairingRepository.save(pairing);

        // 🚀 NEW: End any active voice sessions for this pairing
        discordVoiceTimeTrackerService.endVoiceSessionForPairing(pairingId);

        // 🚀 NEW: Delete Discord channel for the pairing
        deleteDiscordChannelForPairing(updatedPairing, "Pairing ended: " + (sanitizedReason != null ? sanitizedReason : "No reason provided"));

        // DON'T create blacklist entry here - it already exists from pairing creation
        // Just update the reason if needed
        blacklistEntryRepository.findByUserPair(pairing.getUser1Id(), pairing.getUser2Id())
                .ifPresent(blacklistEntry -> {
                    blacklistEntry.setReason("Originally matched on " + pairing.getMatchedAt() + 
                                           ", broke up on " + LocalDateTime.now() + 
                                           " - " + (sanitizedReason != null ? sanitizedReason : "No reason provided"));
                    blacklistEntryRepository.save(blacklistEntry);
                });

        // 🚀 NEW: Broadcast PAIRING_ENDED events to both users
        broadcastPairingEnded(mapToPairingDTO(updatedPairing), sanitizedInitiatorId, partnerId);

        log.info("Successfully processed breakup for pairing ID: {}", pairingId);
        return mapToPairingDTO(updatedPairing);
    }

    /**
     * Get all active pairings
     */
    @Transactional(readOnly = true)
    public List<PairingDTO> getAllActivePairings() {
        return pairingRepository.findByActiveTrue()
                .stream()
                .map(this::mapToPairingDTO)
                .toList();
    }

    /**
     * Get pairing history for a user
     */
    @Transactional(readOnly = true)
    public List<PairingDTO> getPairingHistory(String userId) {
        return pairingRepository.findAllPairingsByUserId(userId)
                .stream()
                .map(this::mapToPairingDTO)
                .toList();
    }

    /**
     * Check if two users are blacklisted
     */
    @Transactional(readOnly = true)
    public BlacklistStatusDTO checkBlacklistStatus(String user1Id, String user2Id) {
        Optional<BlacklistEntry> blacklistEntry = blacklistEntryRepository.findByUserPair(user1Id, user2Id);
        
        if (blacklistEntry.isPresent()) {
            return BlacklistStatusDTO.builder()
                    .blacklisted(true)
                    .reason(blacklistEntry.get().getReason())
                    .build();
        }
        
        return BlacklistStatusDTO.builder()
                .blacklisted(false)
                .build();
    }

    /**
     * Delete all active pairings (admin function)
     */
    @Transactional
    public int deleteAllPairings() {
        log.info("Admin deleting all active pairings");
        
        List<Pairing> activePairings = pairingRepository.findByActiveTrue();
        int deletedCount = activePairings.size();
        
        for (Pairing pairing : activePairings) {
            pairing.setActive(false);
            pairing.setBreakupReason("Admin deletion");
            pairing.setBreakupTimestamp(LocalDateTime.now());
            pairingRepository.save(pairing);

            // 🚀 NEW: End any active voice sessions for this pairing
            discordVoiceTimeTrackerService.endVoiceSessionForPairing(pairing.getId());

            // 🚀 NEW: Delete Discord channel when admin deletes pairing
            deleteDiscordChannelForPairing(pairing, "Admin bulk deletion of all active pairings");
            
            log.info("Deactivated pairing {} between users {} and {}", 
                    pairing.getId(), pairing.getUser1Id(), pairing.getUser2Id());
        }
        
        log.info("Admin deleted {} active pairings", deletedCount);
        return deletedCount;
    }

    /**
     * Admin unpair users - ends the pairing but keeps blacklist entry (admin function)
     */
    @Transactional
    public void unpairUsers(Long pairingId, String adminUsername) {
        log.info("Admin {} unpairing pairing with ID: {}", adminUsername, pairingId);

        Pairing pairing = pairingRepository.findById(pairingId)
                .orElseThrow(() -> new IllegalArgumentException("Pairing not found with ID: " + pairingId));

        if (!pairing.isActive()) {
            throw new IllegalStateException("Pairing is already inactive");
        }

        // Update pairing to inactive
        pairing.setActive(false);
        pairing.setBreakupInitiatorId("ADMIN_" + adminUsername);
        pairing.setBreakupReason("Admin unpair - users remain blacklisted");
        pairing.setBreakupTimestamp(LocalDateTime.now());
        pairing.setMutualBreakup(false);

        pairingRepository.save(pairing);

        // 🚀 NEW: End any active voice sessions for this pairing
        discordVoiceTimeTrackerService.endVoiceSessionForPairing(pairingId);

        // 🚀 NEW: Delete Discord channel for the admin unpaired pairing
        deleteDiscordChannelForPairing(pairing, "Admin unpair by " + adminUsername);
        
        // Update blacklist entry reason (blacklist STAYS - users cannot match again)
        blacklistEntryRepository.findByUserPair(pairing.getUser1Id(), pairing.getUser2Id())
                .ifPresent(blacklistEntry -> {
                    blacklistEntry.setReason("Originally matched on " + pairing.getMatchedAt() + 
                                           ", admin unpaired on " + LocalDateTime.now() + 
                                           " - users remain permanently blacklisted");
                    blacklistEntryRepository.save(blacklistEntry);
                });
        
        log.info("Successfully unpaired pairing {} between users {} and {} (blacklist MAINTAINED)", 
                pairingId, pairing.getUser1Id(), pairing.getUser2Id());
    }

    /**
     * Permanently delete a pairing record AND remove blacklist entry (admin function)
     */
    @Transactional
    public void deletePairingPermanently(Long pairingId) {
        log.info("Permanently deleting pairing with ID: {}", pairingId);

        Pairing pairing = pairingRepository.findById(pairingId)
                .orElseThrow(() -> new IllegalArgumentException("Pairing not found with ID: " + pairingId));

        String user1Id = pairing.getUser1Id();
        String user2Id = pairing.getUser2Id();

        // 🚀 NEW: Delete Discord channel before deleting pairing record
        deleteDiscordChannelForPairing(pairing, "Pairing permanently deleted by admin");

        // 🎯 NEW: Delete all XP system data first to avoid foreign key constraints
        try {
            // Delete PairLevel record
            pairLevelService.deletePairLevel(pairingId);
            log.info("Deleted XP level data for pairing {}", pairingId);
            
            // Delete all achievements for this pairing
            achievementService.deleteAllPairAchievements(pairingId);
            log.info("Deleted all achievements for pairing {}", pairingId);
            
            // Delete all voice streak records for this pairing
            voiceStreakService.deleteAllVoiceStreaks(pairingId);
            log.info("Deleted all voice streak data for pairing {}", pairingId);
            
        } catch (Exception e) {
            log.warn("Error cleaning up XP system data for pairing {}: {}", pairingId, e.getMessage());
            // Continue with pairing deletion even if XP cleanup fails
        }

        // REMOVE the blacklist entry - allow future matching
        blacklistEntryRepository.findByUserPair(user1Id, user2Id).ifPresent(blacklistEntry -> {
            blacklistEntryRepository.delete(blacklistEntry);
            log.info("Removed blacklist entry for users {} and {} - they can match again", user1Id, user2Id);
        });

        // Permanently delete the pairing record
        pairingRepository.deleteById(pairingId);
        
        log.info("Successfully permanently deleted pairing {} between users {} and {} (blacklist REMOVED - can match again)", 
                pairingId, user1Id, user2Id);
    }

    /**
     * Permanently delete all inactive pairings AND remove their blacklist entries (admin function)
     */
    @Transactional
    public long deleteAllInactivePairings() {
        log.info("Admin permanently deleting all inactive pairings");
        
        List<Pairing> inactivePairings = pairingRepository.findByActiveFalse();
        long deletedCount = inactivePairings.size();
        
        for (Pairing pairing : inactivePairings) {
            // 🚀 NEW: Delete Discord channel before deleting pairing
            deleteDiscordChannelForPairing(pairing, "Inactive pairing bulk deletion by admin");

            // 🎯 NEW: Delete all XP system data first to avoid foreign key constraints
            try {
                // Delete PairLevel record
                pairLevelService.deletePairLevel(pairing.getId());
                log.info("Deleted XP level data for pairing {}", pairing.getId());
                
                // Delete all achievements for this pairing
                achievementService.deleteAllPairAchievements(pairing.getId());
                log.info("Deleted all achievements for pairing {}", pairing.getId());
                
                // Delete all voice streak records for this pairing
                voiceStreakService.deleteAllVoiceStreaks(pairing.getId());
                log.info("Deleted all voice streak data for pairing {}", pairing.getId());
                
            } catch (Exception e) {
                log.warn("Error cleaning up XP system data for pairing {}: {}", pairing.getId(), e.getMessage());
                // Continue with pairing deletion even if XP cleanup fails
            }

            // REMOVE blacklist entry - allow future matching
            blacklistEntryRepository.findByUserPair(pairing.getUser1Id(), pairing.getUser2Id())
                    .ifPresent(blacklistEntry -> {
                        blacklistEntryRepository.delete(blacklistEntry);
                        log.info("Removed blacklist entry for users {} and {} - they can match again", 
                                pairing.getUser1Id(), pairing.getUser2Id());
                    });
            
            // Delete the pairing record
            pairingRepository.deleteById(pairing.getId());
            
            log.info("Permanently deleted inactive pairing {} between users {} and {} (blacklist REMOVED - can match again)", 
                    pairing.getId(), pairing.getUser1Id(), pairing.getUser2Id());
        }
        
        log.info("Admin permanently deleted {} inactive pairings (all blacklists REMOVED)", deletedCount);
        return deletedCount;
    }

    // Private helper methods

    private void validateUsersExist(String user1Id, String user2Id) {
        if (!userRepository.existsById(user1Id)) {
            throw new IllegalArgumentException("User with ID " + user1Id + " does not exist");
        }
        if (!userRepository.existsById(user2Id)) {
            throw new IllegalArgumentException("User with ID " + user2Id + " does not exist");
        }
        
        // Prevent self-pairing
        if (user1Id.equals(user2Id)) {
            throw new IllegalArgumentException("Cannot pair user with themselves");
        }
    }

    private void validateUsersNotInActivePairing(String user1Id, String user2Id) {
        if (pairingRepository.findActivePairingByUserId(user1Id).isPresent()) {
            throw new IllegalStateException("User " + user1Id + " is already in an active pairing");
        }
        if (pairingRepository.findActivePairingByUserId(user2Id).isPresent()) {
            throw new IllegalStateException("User " + user2Id + " is already in an active pairing");
        }
    }

    private boolean areUsersBlacklisted(String user1Id, String user2Id) {
        return blacklistEntryRepository.existsByUserPair(user1Id, user2Id);
    }

    private void removeUsersFromQueue(String user1Id, String user2Id) {
        matchQueueUserRepository.findByUserId(user1Id).ifPresent(queueUser -> {
            queueUser.setInQueue(false);
            matchQueueUserRepository.save(queueUser);
        });
        
        matchQueueUserRepository.findByUserId(user2Id).ifPresent(queueUser -> {
            queueUser.setInQueue(false);
            matchQueueUserRepository.save(queueUser);
        });
    }

    private void createBlacklistEntry(String user1Id, String user2Id, String reason) {
        BlacklistEntry blacklistEntry = BlacklistEntry.create(user1Id, user2Id, reason);
        blacklistEntryRepository.save(blacklistEntry);
        log.info("Created blacklist entry for users {} and {}", user1Id, user2Id);
    }

    private PairingDTO mapToPairingDTO(Pairing pairing) {
        PairingDTO dto = PairingDTO.builder()
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
                .build();

        dto.setUser1Age(pairing.getUser1Age());
        dto.setUser1Gender(pairing.getUser1Gender());
        dto.setUser1Region(pairing.getUser1Region());
        dto.setUser1Rank(pairing.getUser1Rank());
        
        dto.setUser2Age(pairing.getUser2Age());
        dto.setUser2Gender(pairing.getUser2Gender());
        dto.setUser2Region(pairing.getUser2Region());
        dto.setUser2Rank(pairing.getUser2Rank());

        return dto;
    }

    // Add input sanitization methods
    private String sanitizeUserId(String userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        // Remove potentially dangerous characters, keep alphanumeric and common safe chars
        return userId.replaceAll("[^a-zA-Z0-9_-]", "").trim();
    }

    private String sanitizeInput(String input) {
        if (input == null) return null;
        // Basic HTML/script tag removal and length limiting
        return input.replaceAll("<[^>]*>", "")
                   .replaceAll("javascript:", "")
                   .trim()
                   .substring(0, Math.min(input.length(), 500)); // Limit length
    }

    /**
     * Create Discord channel for a pairing with proper error handling
     */
    private void createDiscordChannelForPairing(Pairing pairing, String user1DiscordId, String user2DiscordId) {
        try {
            // Use user IDs as Discord IDs if not provided (assuming users have Discord linked)
            String discordId1 = user1DiscordId != null ? user1DiscordId : pairing.getUser1Id();
            String discordId2 = user2DiscordId != null ? user2DiscordId : pairing.getUser2Id();
            
            log.info("Attempting to create Discord channel for pairing {} with Discord IDs {} and {}", 
                     pairing.getId(), discordId1, discordId2);
            
            // Create Discord channel asynchronously
            discordPairingChannelService.createPairingChannel(discordId1, discordId2, pairing.getId())
                    .thenAccept(result -> {
                        if (result.isSuccess()) {
                            // Update pairing with Discord channel info
                            try {
                                pairing.setDiscordChannelId(Long.parseLong(result.getChannelId()));
                                pairing.setDiscordChannelName(result.getChannelName());
                                pairingRepository.save(pairing);
                                
                                log.info("Successfully created and linked Discord channel '{}' (ID: {}) to pairing {}", 
                                         result.getChannelName(), result.getChannelId(), pairing.getId());
                            } catch (Exception e) {
                                log.error("Failed to update pairing {} with Discord channel info: {}", 
                                         pairing.getId(), e.getMessage());
                            }
                        } else {
                            log.warn("Discord channel creation failed for pairing {}: {}", 
                                    pairing.getId(), result.getErrorMessage());
                            // Pairing continues without Discord channel - not a critical failure
                        }
                    })
                    .exceptionally(throwable -> {
                        log.error("Exception during Discord channel creation for pairing {}: {}", 
                                 pairing.getId(), throwable.getMessage());
                        return null;
                    });
                    
        } catch (Exception e) {
            log.error("Failed to initiate Discord channel creation for pairing {}: {}", 
                     pairing.getId(), e.getMessage());
            // Don't throw - pairing should succeed even if Discord channel creation fails
        }
    }

    /**
     * Delete Discord channel for a pairing with proper error handling
     */
    private void deleteDiscordChannelForPairing(Pairing pairing, String reason) {
        try {
            if (pairing.getDiscordChannelId() != null) {
                String channelId = pairing.getDiscordChannelId().toString();
                String channelName = pairing.getDiscordChannelName();
                
                log.info("Attempting to delete Discord channel '{}' (ID: {}) for pairing {}", 
                         channelName, channelId, pairing.getId());
                
                // Delete Discord channel asynchronously
                discordPairingChannelService.deletePairingChannel(channelId, reason)
                        .thenAccept(success -> {
                            if (success) {
                                log.info("Successfully deleted Discord channel '{}' for pairing {}", 
                                         channelName, pairing.getId());
                            } else {
                                log.warn("Failed to delete Discord channel '{}' for pairing {} - channel may not exist or bot lacks permissions", 
                                        channelName, pairing.getId());
                            }
                        })
                        .exceptionally(throwable -> {
                            log.error("Exception during Discord channel deletion for pairing {}: {}", 
                                     pairing.getId(), throwable.getMessage());
                            return null;
                        });
            } else {
                log.debug("No Discord channel to delete for pairing {}", pairing.getId());
            }
        } catch (Exception e) {
            log.error("Failed to initiate Discord channel deletion for pairing {}: {}", 
                     pairing.getId(), e.getMessage());
            // Don't throw - breakup should succeed even if Discord channel deletion fails
        }
    }

    /**
     * Broadcast PAIRING_ENDED events to both users via WebSocket
     */
    private void broadcastPairingEnded(PairingDTO pairing, String initiatorId, String partnerId) {
        log.info("Broadcasting pairing ended notifications for pairing ID: {}", pairing.getId());
        
        try {
            // Create notification for initiator (success confirmation)
            Map<String, Object> initiatorNotification = Map.of(
                "eventType", "PAIRING_ENDED",
                "pairing", pairing,
                "message", "You have successfully unmatched!",
                "timestamp", LocalDateTime.now().toString(),
                "isInitiator", true
            );
            
            // Create notification for partner (partner unmatched notification)
            Map<String, Object> partnerNotification = Map.of(
                "eventType", "PAIRING_ENDED",
                "pairing", pairing,
                "message", "Your partner has unmatched with you :(",
                "timestamp", LocalDateTime.now().toString(),
                "isInitiator", false
            );
            
            // Send to initiator
            messagingTemplate.convertAndSend(
                "/user/" + initiatorId + "/topic/pairings", 
                initiatorNotification
            );
            
            // Send to partner
            messagingTemplate.convertAndSend(
                "/user/" + partnerId + "/topic/pairings", 
                partnerNotification
            );
            
            log.info("Successfully broadcasted pairing ended notifications to users {} and {}", 
                     initiatorId, partnerId);
            
        } catch (Exception e) {
            log.error("Failed to broadcast pairing ended notifications: {}", e.getMessage());
        }
    }
} 