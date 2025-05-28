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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
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

    /**
     * Create a new pairing between two users
     */
    @Transactional
    public PairingDTO createPairing(CreatePairingRequestDTO request) {
        log.info("Creating pairing between users {} and {}", request.getUser1Id(), request.getUser2Id());

        // Validate users exist
        validateUsersExist(request.getUser1Id(), request.getUser2Id());

        // Check if either user is already in an active pairing
        validateUsersNotInActivePairing(request.getUser1Id(), request.getUser2Id());

        // Check if users are blacklisted
        if (areUsersBlacklisted(request.getUser1Id(), request.getUser2Id())) {
            throw new IllegalArgumentException("Users are blacklisted and cannot be paired");
        }

        // Create pairing entity
        Pairing pairing = Pairing.builder()
                .user1Id(request.getUser1Id())
                .user2Id(request.getUser2Id())
                .discordChannelId(request.getDiscordChannelId())
                .compatibilityScore(request.getCompatibilityScore())
                .matchedAt(LocalDateTime.now())
                .build();

        // Save pairing
        Pairing savedPairing = pairingRepository.save(pairing);

        // Remove users from queue if they are queued
        removeUsersFromQueue(request.getUser1Id(), request.getUser2Id());

        log.info("Successfully created pairing with ID: {}", savedPairing.getId());
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
        log.info("Successfully updated activity for pairing ID: {}", pairingId);

        return mapToPairingDTO(updatedPairing);
    }

    /**
     * Handle pairing breakup
     */
    @Transactional
    public PairingDTO breakupPairing(Long pairingId, BreakupRequestDTO request) {
        log.info("Processing breakup for pairing ID: {} by user: {}", pairingId, request.getInitiatorId());

        Pairing pairing = pairingRepository.findById(pairingId)
                .orElseThrow(() -> new IllegalArgumentException("Pairing not found with ID: " + pairingId));

        if (!pairing.isActive()) {
            throw new IllegalStateException("Pairing is already inactive");
        }

        // Validate initiator is part of the pairing
        if (!pairing.involvesUser(request.getInitiatorId())) {
            throw new IllegalArgumentException("Initiator is not part of this pairing");
        }

        // Update pairing with breakup information
        pairing.setActive(false);
        pairing.setBreakupInitiatorId(request.getInitiatorId());
        pairing.setBreakupReason(request.getReason());
        pairing.setBreakupTimestamp(LocalDateTime.now());
        pairing.setMutualBreakup(request.isMutualBreakup());

        // Save updated pairing
        Pairing updatedPairing = pairingRepository.save(pairing);

        // Create blacklist entry to prevent re-pairing
        createBlacklistEntry(pairing.getUser1Id(), pairing.getUser2Id(), 
                           "Breakup on " + LocalDateTime.now() + " - " + 
                           (request.getReason() != null ? request.getReason() : "No reason provided"));

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
            
            log.info("Deactivated pairing {} between users {} and {}", 
                    pairing.getId(), pairing.getUser1Id(), pairing.getUser2Id());
        }
        
        log.info("Admin deleted {} active pairings", deletedCount);
        return deletedCount;
    }

    // Private helper methods

    private void validateUsersExist(String user1Id, String user2Id) {
        if (!userRepository.existsById(user1Id)) {
            throw new IllegalArgumentException("User not found: " + user1Id);
        }
        if (!userRepository.existsById(user2Id)) {
            throw new IllegalArgumentException("User not found: " + user2Id);
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
        return PairingDTO.builder()
                .id(pairing.getId())
                .user1Id(pairing.getUser1Id())
                .user2Id(pairing.getUser2Id())
                .discordChannelId(pairing.getDiscordChannelId())
                .matchedAt(pairing.getMatchedAt())
                .messageCount(pairing.getMessageCount())
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
} 