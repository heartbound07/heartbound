package com.app.heartbound.services.pairing;

import com.app.heartbound.dto.pairing.*;
import com.app.heartbound.dto.PublicUserProfileDTO;
import com.app.heartbound.entities.BlacklistEntry;
import com.app.heartbound.entities.Pairing;
import com.app.heartbound.entities.PairLevel;
import com.app.heartbound.entities.User;
import com.app.heartbound.repositories.UserRepository;
import com.app.heartbound.repositories.pairing.BlacklistEntryRepository;

import com.app.heartbound.config.CacheConfig;
import com.app.heartbound.repositories.pairing.PairingRepository;
import com.app.heartbound.services.discord.DiscordPairingChannelService;
import com.app.heartbound.services.discord.DiscordVoiceTimeTrackerService;
import com.app.heartbound.services.discord.DiscordLeaderboardService;
import com.app.heartbound.services.discord.DiscordMessageListenerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.annotation.PostConstruct;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

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

    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    @Lazy
    private final DiscordPairingChannelService discordPairingChannelService;
    private final DiscordVoiceTimeTrackerService discordVoiceTimeTrackerService;
    private final DiscordLeaderboardService discordLeaderboardService;
    private final DiscordMessageListenerService discordMessageListenerService;
    private final CacheConfig cacheConfig;
    
    // XP System Services
    private final PairLevelService pairLevelService;
    private final AchievementService achievementService;
    private final VoiceStreakService voiceStreakService;
    
    /**
     * Initialize callbacks to avoid circular dependencies
     */
    @PostConstruct
    public void initializeCallbacks() {
        // Set Discord leaderboard refresh callbacks to avoid circular dependencies
        pairLevelService.setDiscordLeaderboardRefreshCallback(this::refreshLeaderboardForPairing);
        achievementService.setDiscordLeaderboardRefreshCallback(this::refreshLeaderboardForPairing);
        voiceStreakService.setDiscordLeaderboardRefreshCallback(this::refreshLeaderboardForPairing);
        discordMessageListenerService.setDiscordLeaderboardRefreshCallback(this::refreshLeaderboardForPairing);
        
        // 🎉 NEW: Set Discord achievement notification callback to avoid circular dependencies
        achievementService.setDiscordAchievementNotificationCallback(this::sendDiscordAchievementNotification);
        
        log.info("Initialized Discord leaderboard refresh callbacks and achievement notification callback for XP system and Discord services");
    }

    /**
     * Create a new pairing between two users
     * Only automated matchmaking or admin operations should call this method
     */
    @Transactional
    public PairingDTO createPairing(CreatePairingRequestDTO request) {
        // Validate caller context to prevent unauthorized pairing creation
        validatePairingCreationAuth();
        
        // Input sanitization and validation
        String sanitizedUser1Id = sanitizeUserId(request.getUser1Id());
        String sanitizedUser2Id = sanitizeUserId(request.getUser2Id());
        
        log.info("Creating pairing between users {} and {}", sanitizedUser1Id, sanitizedUser2Id);

        // Prevent self-pairing attempts
        if (sanitizedUser1Id.equals(sanitizedUser2Id)) {
            log.error("SECURITY VIOLATION: Attempted self-pairing for user {}", sanitizedUser1Id);
            throw new IllegalArgumentException("Users cannot be paired with themselves");
        }

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

        // SECURITY: Additional validation for suspicious pairing requests
        validatePairingRequestIntegrity(request, sanitizedUser1Id, sanitizedUser2Id);

        // Create pairing entity with sanitized data (initially without Discord channel info)
        Pairing pairing = Pairing.builder()
                .user1Id(sanitizedUser1Id)
                .user2Id(sanitizedUser2Id)
                .compatibilityScore(Math.max(0, Math.min(100, request.getCompatibilityScore())))
                .matchedAt(LocalDateTime.now())
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



        // 🚀 NEW: Add pairing to Discord leaderboard
        PairingDTO pairingDTO = mapToPairingDTO(savedPairing);
        addPairingToLeaderboard(pairingDTO);

        log.info("Successfully created pairing with ID: {} and blacklisted users", savedPairing.getId());
        return pairingDTO;
    }

    /**
     * Validate that only authorized callers can create pairings
     * This prevents users from directly calling service methods to bypass controller security
     */
    private void validatePairingCreationAuth() {
        // First, check if the call is coming from one of our trusted internal services.
        // These services (like Discord listeners or matchmaking) operate outside of a
        // standard user authentication context but are considered secure system callers.
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        for (StackTraceElement element : stackTrace) {
            String className = element.getClassName();
            if (className.contains("MatchmakingService") || className.contains("PairCommandListener")) {
                log.info("SECURITY CHECK PASSED: System call from a permitted service ({}).", className);
                return; // System call is trusted, no need to check Spring Security context.
            }
        }

        // If the call is not from a trusted internal service, it must be from an
        // authenticated web context (e.g., an admin using a controller).
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            log.error("SECURITY VIOLATION: Unauthenticated pairing creation attempt from an untrusted source.");
            throw new SecurityException("Authentication required for pairing creation");
        }

        // Check if the authenticated user has the ADMIN role.
        boolean hasAdminRole = authentication.getAuthorities().stream()
                .anyMatch(auth -> auth.getAuthority().equals("ROLE_ADMIN"));

        if (hasAdminRole) {
            log.info("SECURITY CHECK PASSED: Admin {} creating pairing via API.", authentication.getName());
            return;
        }

        // If we reach here, the call is from an authenticated but non-admin user, which is not permitted.
        log.error("SECURITY VIOLATION: Unauthorized pairing creation attempt by {} - not admin and not from a permitted service.",
                 authentication.getName());
        throw new SecurityException("Only automated matchmaking or admin operations can create pairings");
    }

    /**
     * Validate pairing request integrity to detect manipulation attempts
     */
    private void validatePairingRequestIntegrity(CreatePairingRequestDTO request, String user1Id, String user2Id) {
        // Validate compatibility score is reasonable (should come from actual calculation)
        if (request.getCompatibilityScore() > 100) {
            log.error("SECURITY VIOLATION: Compatibility score exceeds maximum: {}", request.getCompatibilityScore());
            throw new IllegalArgumentException("Invalid compatibility score");
        }
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

        // Check if this is an admin direct update or increment update
        boolean isAdminDirectUpdate = request.getUser1MessageCount() != null || 
                                      request.getUser2MessageCount() != null || 
                                      request.getVoiceTimeMinutes() != null;

        if (isAdminDirectUpdate) {
            // Admin direct updates - set values directly
            if (request.getUser1MessageCount() != null) {
                pairing.setUser1MessageCount(Math.max(0, request.getUser1MessageCount()));
            }
            if (request.getUser2MessageCount() != null) {
                pairing.setUser2MessageCount(Math.max(0, request.getUser2MessageCount()));
            }
            if (request.getVoiceTimeMinutes() != null) {
                pairing.setVoiceTimeMinutes(Math.max(0, request.getVoiceTimeMinutes()));
            }
            
            // Recalculate total message count
            pairing.setMessageCount(pairing.getUser1MessageCount() + pairing.getUser2MessageCount());
            
            log.info("Admin direct update applied for pairing {}: user1Messages={}, user2Messages={}, voiceMinutes={}", 
                    pairingId, pairing.getUser1MessageCount(), pairing.getUser2MessageCount(), pairing.getVoiceTimeMinutes());
        } else {
            // Regular increment updates
            pairing.setMessageCount(pairing.getMessageCount() + request.getMessageIncrement());
        }
        
        // Always apply these updates (both admin and regular)
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

        // 🚀 NEW: Refresh Discord leaderboard after activity update
        addPairingToLeaderboard(mapToPairingDTO(updatedPairing));

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

        // 🎉 NEW: Send breakup announcement to Discord channel
        sendBreakupAnnouncementToDiscord(sanitizedInitiatorId, partnerId, pairingId);

        // 🚀 NEW: Remove pairing from Discord leaderboard
        removePairingFromLeaderboard(pairingId);

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
     * Get all active pairings with caching optimization
     */
    @Transactional(readOnly = true)
    public List<PairingDTO> getAllActivePairings() {
        // Check cache first for performance optimization
        Map<String, Object> cachedData = cacheConfig.getBatchOperationsCache()
                .getIfPresent("all_active_pairings");
        
        if (cachedData != null && cachedData.containsKey("pairings")) {
            @SuppressWarnings("unchecked")
            List<PairingDTO> cachedPairings = (List<PairingDTO>) cachedData.get("pairings");
            log.debug("All active pairings cache HIT - returning {} pairings", cachedPairings.size());
            return cachedPairings;
        }
        
        log.debug("All active pairings cache MISS - fetching from database");
        
        List<PairingDTO> activePairings = pairingRepository.findByActiveTrue()
                .stream()
                .map(this::mapToPairingDTO)
                .toList();
        
        // Cache the result wrapped in a Map for type compatibility
        Map<String, Object> cacheData = Map.of(
                "pairings", activePairings,
                "count", activePairings.size(),
                "timestamp", System.currentTimeMillis()
        );
        cacheConfig.getBatchOperationsCache().put("all_active_pairings", cacheData);
        
        log.debug("Cached {} active pairings for improved performance", activePairings.size());
        return activePairings;
    }

    /**
     * Get all active pairings without sensitive user data (for public display)
     */
    @Transactional(readOnly = true)
    public List<PublicPairingDTO> getAllActivePairingsPublic() {
        return pairingRepository.findByActiveTrue()
                .stream()
                .map(this::mapToPublicPairingDTO)
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
     * Get all pairing history (admin function) - returns all inactive pairings
     */
    @Transactional(readOnly = true)
    public List<PairingDTO> getAllPairingHistory() {
        log.info("Fetching all inactive pairings for admin");
        
        List<PairingDTO> inactivePairings = pairingRepository.findByActiveFalse()
                .stream()
                .map(this::mapToPairingDTO)
                .toList();
        
        log.info("Retrieved {} inactive pairings", inactivePairings.size());
        return inactivePairings;
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
            
            // 🚀 NEW: Remove pairing from Discord leaderboard
            removePairingFromLeaderboard(pairing.getId());
            
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
        
        // 🚀 NEW: Remove pairing from Discord leaderboard
        removePairingFromLeaderboard(pairingId);
        
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

        // 🚀 NEW: Remove pairing from Discord leaderboard
        removePairingFromLeaderboard(pairingId);

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

        return dto;
    }

    /**
     * Map Pairing entity to PublicPairingDTO (without sensitive user data)
     */
    private PublicPairingDTO mapToPublicPairingDTO(Pairing pairing) {
        return PublicPairingDTO.builder()
                .id(pairing.getId())
                .user1Id(pairing.getUser1Id())
                .user2Id(pairing.getUser2Id())
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

    /**
     * Send breakup announcement to Discord channel with proper error handling
     */
    private void sendBreakupAnnouncementToDiscord(String user1Id, String user2Id, Long pairingId) {
        try {
            log.info("Attempting to send breakup announcement for pairing {} with users {} and {}", 
                     pairingId, user1Id, user2Id);
            
            // Send breakup announcement asynchronously
            discordPairingChannelService.sendBreakupAnnouncement(user1Id, user2Id, pairingId)
                    .thenAccept(success -> {
                        if (success) {
                            log.info("Successfully sent breakup announcement for pairing {}", pairingId);
                        } else {
                            log.warn("Failed to send breakup announcement for pairing {} - channel may not exist or bot lacks permissions", pairingId);
                        }
                    })
                    .exceptionally(throwable -> {
                        log.error("Exception during breakup announcement for pairing {}: {}", 
                                 pairingId, throwable.getMessage());
                        return null;
                    });
                    
        } catch (Exception e) {
            log.error("Failed to initiate breakup announcement for pairing {}: {}", 
                     pairingId, e.getMessage());
            // Don't throw - breakup should succeed even if announcement fails
        }
    }

    /**
     * Add pairing to Discord leaderboard with proper error handling
     */
    private void addPairingToLeaderboard(PairingDTO pairing) {
        try {
            log.info("Adding pairing {} to Discord leaderboard", pairing.getId());
            
            discordLeaderboardService.addOrUpdatePairingEmbed(pairing)
                    .thenAccept(success -> {
                        if (success) {
                            log.info("Successfully added pairing {} to Discord leaderboard", pairing.getId());
                        } else {
                            log.warn("Failed to add pairing {} to Discord leaderboard", pairing.getId());
                        }
                    })
                    .exceptionally(throwable -> {
                        log.error("Exception adding pairing {} to Discord leaderboard: {}", 
                                 pairing.getId(), throwable.getMessage());
                        return null;
                    });
                    
        } catch (Exception e) {
            log.error("Failed to initiate Discord leaderboard update for pairing {}: {}", 
                     pairing.getId(), e.getMessage());
            // Don't throw - pairing should succeed even if leaderboard update fails
        }
    }

    /**
     * Remove pairing from Discord leaderboard with proper error handling
     */
    private void removePairingFromLeaderboard(Long pairingId) {
        try {
            log.info("Removing pairing {} from Discord leaderboard", pairingId);
            
            discordLeaderboardService.removePairingEmbed(pairingId)
                    .thenAccept(success -> {
                        if (success) {
                            log.info("Successfully removed pairing {} from Discord leaderboard", pairingId);
                        } else {
                            log.warn("Failed to remove pairing {} from Discord leaderboard", pairingId);
                        }
                    })
                    .exceptionally(throwable -> {
                        log.error("Exception removing pairing {} from Discord leaderboard: {}", 
                                 pairingId, throwable.getMessage());
                        return null;
                    });
                    
                 } catch (Exception e) {
             log.error("Failed to initiate Discord leaderboard removal for pairing {}: {}", 
                      pairingId, e.getMessage());
             // Don't throw - breakup should succeed even if leaderboard removal fails
         }
     }

     /**
      * Refresh Discord leaderboard for a specific pairing (public method for external calls)
      */
     public void refreshLeaderboardForPairing(Long pairingId) {
         try {
             log.debug("Refreshing Discord leaderboard for pairing {} after external update", pairingId);
             
             // Get the pairing
             Optional<Pairing> pairingOpt = pairingRepository.findById(pairingId);
             if (pairingOpt.isEmpty() || !pairingOpt.get().isActive()) {
                 log.debug("Pairing {} not found or not active, skipping leaderboard refresh", pairingId);
                 return;
             }
             
             // Refresh leaderboard
             PairingDTO pairingDTO = mapToPairingDTO(pairingOpt.get());
             addPairingToLeaderboard(pairingDTO);
             
         } catch (Exception e) {
             log.error("Failed to refresh Discord leaderboard for pairing {}: {}", 
                      pairingId, e.getMessage());
             // Don't throw - external operations should succeed even if leaderboard refresh fails
         }
     }
     
     /**
      * Send Discord achievement notification to pairing channel (callback method to avoid circular dependency)
      */
     private void sendDiscordAchievementNotification(String channelId, String user1Id, String user2Id,
                                                     String achievementName, String achievementDescription,
                                                     int xpAwarded, String achievementRarity, int progressValue) {
         try {
             log.debug("Sending Discord achievement notification to channel {}: {}", channelId, achievementName);
             
             // Send Discord achievement notification asynchronously
             discordPairingChannelService.sendAchievementNotification(
                 channelId, user1Id, user2Id, achievementName, achievementDescription,
                 xpAwarded, achievementRarity, progressValue
             ).exceptionally(throwable -> {
                 log.warn("Failed to send Discord achievement notification to channel {}: {}", 
                         channelId, throwable.getMessage());
                 return false;
             });
             
         } catch (Exception e) {
             log.error("Failed to initiate Discord achievement notification to channel {}: {}", 
                      channelId, e.getMessage());
             // Don't throw - achievement unlock should succeed even if Discord notification fails
                 }
    }
    
    /**
     * Get pairing leaderboard with embedded user profiles for optimal frontend performance
     */
    @Transactional(readOnly = true)
    public List<PairingLeaderboardDTO> getLeaderboardPairings() {
        log.debug("Fetching pairing leaderboard");
        
        try {
            // Use existing repository method that orders by level DESC, XP DESC
            List<Pairing> activePairings = pairingRepository.findActivePairingsOrderedByLevel();
            
            if (activePairings.isEmpty()) {
                log.debug("No active pairings found for leaderboard");
                return Collections.emptyList();
            }
            
            // Collect all unique user IDs for batch profile fetching
            Set<String> userIds = activePairings.stream()
                .flatMap(p -> Stream.of(p.getUser1Id(), p.getUser2Id()))
                .collect(Collectors.toSet());
            
            // Batch fetch user profiles for performance
            Map<String, User> userProfileMap = userRepository.findAllById(userIds)
                .stream()
                .collect(Collectors.toMap(User::getId, user -> user));
            
            // Map to leaderboard DTOs with individual lookups
            List<PairingLeaderboardDTO> leaderboard = activePairings.stream()
                .map(pairing -> mapToPairingLeaderboardDTO(pairing, userProfileMap))
                .filter(dto -> dto != null) // Filter out any null mappings due to missing user profiles
                .collect(Collectors.toList());
            
            log.debug("Successfully created leaderboard with {} entries", leaderboard.size());
            return leaderboard;
            
        } catch (Exception e) {
            log.error("Error creating pairing leaderboard", e);
            throw new RuntimeException("Failed to generate pairing leaderboard: " + e.getMessage(), e);
        }
    }
    
    /**
     * Map a Pairing entity to PairingLeaderboardDTO with embedded user profiles
     */
    private PairingLeaderboardDTO mapToPairingLeaderboardDTO(Pairing pairing, 
                                                           Map<String, User> userProfileMap) {
        try {
            User user1 = userProfileMap.get(pairing.getUser1Id());
            User user2 = userProfileMap.get(pairing.getUser2Id());
            
            // Skip if either user profile is missing
            if (user1 == null || user2 == null) {
                log.warn("Missing user profile for pairing {}: user1={}, user2={}", 
                        pairing.getId(), user1 != null, user2 != null);
                return null;
            }
            
            // Get level data individually (fallback to defaults if not found)
            Optional<PairLevel> pairLevelOpt = pairLevelService.getPairLevel(pairing.getId());
            PairLevel pairLevel = pairLevelOpt.orElse(null);
            
            // Get current streak individually (fallback to 0 if service unavailable)
            int currentStreak = 0;
            try {
                // Simple streak lookup - this is a simplified approach
                // In a production system, you'd want to optimize this with proper batch operations
                currentStreak = 0; // Placeholder - streak data will be 0 for now
            } catch (Exception e) {
                log.debug("Could not fetch streak for pairing {}: {}", pairing.getId(), e.getMessage());
            }
            
            return PairingLeaderboardDTO.builder()
                .id(pairing.getId())
                .user1Id(pairing.getUser1Id())
                .user2Id(pairing.getUser2Id())
                .user1Profile(mapToPublicUserProfileDTO(user1))
                .user2Profile(mapToPublicUserProfileDTO(user2))
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
                .currentLevel(pairLevel != null ? pairLevel.getCurrentLevel() : 1)
                .totalXP(pairLevel != null ? pairLevel.getTotalXP() : 0)
                .currentStreak(currentStreak)
                .active(pairing.isActive())
                .build();
                
        } catch (Exception e) {
            log.error("Error mapping pairing {} to leaderboard DTO", pairing.getId(), e);
            return null;
        }
    }
    
    /**
     * Map User entity to PublicUserProfileDTO
     */
    private PublicUserProfileDTO mapToPublicUserProfileDTO(User user) {
        // Note: This is a simplified mapping. In a real implementation, you might want to
        // move this to a dedicated mapper service or use the existing UserService
        return PublicUserProfileDTO.builder()
            .id(user.getId())
            .username(user.getUsername())
            .avatar(user.getAvatar())
            .displayName(user.getDisplayName())
            .pronouns(user.getPronouns())
            .about(user.getAbout())
            .bannerColor(user.getBannerColor())
            .bannerUrl(user.getBannerUrl())
            .roles(user.getRoles())
            // Additional fields like badge info would need to be populated based on equipped items
            .build();
    }
} 