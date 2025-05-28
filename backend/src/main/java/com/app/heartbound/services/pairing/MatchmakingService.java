package com.app.heartbound.services.pairing;

import com.app.heartbound.dto.pairing.CreatePairingRequestDTO;
import com.app.heartbound.dto.pairing.PairingDTO;
import com.app.heartbound.entities.MatchQueueUser;
import com.app.heartbound.enums.Rank;
import com.app.heartbound.enums.Region;
import com.app.heartbound.repositories.pairing.BlacklistEntryRepository;
import com.app.heartbound.repositories.pairing.MatchQueueUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * MatchmakingService
 * 
 * Service for handling automatic matchmaking logic.
 * Calculates compatibility scores and creates optimal pairings.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MatchmakingService {

    private final MatchQueueUserRepository matchQueueUserRepository;
    private final BlacklistEntryRepository blacklistEntryRepository;
    private final PairingService pairingService;

    /**
     * Potential pairing with compatibility score
     */
    private record PotentialPairing(MatchQueueUser user1, MatchQueueUser user2, int compatibilityScore) {
    }

    /**
     * Perform automatic matchmaking for users in queue
     */
    @Transactional
    public List<PairingDTO> performMatchmaking() {
        log.info("Starting automatic matchmaking process");

        List<MatchQueueUser> queuedUsers = matchQueueUserRepository.findByInQueueTrue();
        log.info("Found {} users in matchmaking queue", queuedUsers.size());

        if (queuedUsers.size() < 2) {
            log.info("Not enough users in queue for matchmaking");
            return Collections.emptyList();
        }

        // Generate all potential pairings with compatibility scores
        List<PotentialPairing> potentialPairings = generatePotentialPairings(queuedUsers);
        log.info("Generated {} potential pairings", potentialPairings.size());

        // Filter out blacklisted pairs
        List<PotentialPairing> validPairings = filterBlacklistedPairs(potentialPairings);
        log.info("Found {} valid (non-blacklisted) pairings", validPairings.size());

        // Sort by compatibility score (highest first)
        validPairings.sort((p1, p2) -> Integer.compare(p2.compatibilityScore(), p1.compatibilityScore()));

        // Create optimal pairings using greedy algorithm
        List<PairingDTO> createdPairings = createOptimalPairings(validPairings);
        
        log.info("Successfully created {} pairings", createdPairings.size());
        return createdPairings;
    }

    /**
     * Calculate compatibility score between two users
     */
    public int calculateCompatibilityScore(MatchQueueUser user1, MatchQueueUser user2) {
        int score = 0;

        // Same region: +50 points
        if (user1.getRegion() == user2.getRegion()) {
            score += 50;
        }

        // Same rank: +30 points
        if (user1.getRank() == user2.getRank()) {
            score += 30;
        }

        // Age difference ≤ 2 years: +20 points
        int ageDifference = Math.abs(user1.getAge() - user2.getAge());
        if (ageDifference <= 2) {
            score += 20;
        }

        return score;
    }

    // Private helper methods

    private List<PotentialPairing> generatePotentialPairings(List<MatchQueueUser> users) {
        List<PotentialPairing> pairings = new ArrayList<>();

        for (int i = 0; i < users.size(); i++) {
            for (int j = i + 1; j < users.size(); j++) {
                MatchQueueUser user1 = users.get(i);
                MatchQueueUser user2 = users.get(j);
                
                int compatibilityScore = calculateCompatibilityScore(user1, user2);
                pairings.add(new PotentialPairing(user1, user2, compatibilityScore));
            }
        }

        return pairings;
    }

    private List<PotentialPairing> filterBlacklistedPairs(List<PotentialPairing> potentialPairings) {
        return potentialPairings.stream()
                .filter(pairing -> !blacklistEntryRepository.existsByUserPair(
                        pairing.user1().getUserId(), 
                        pairing.user2().getUserId()))
                .collect(Collectors.toList());
    }

    private List<PairingDTO> createOptimalPairings(List<PotentialPairing> validPairings) {
        List<PairingDTO> createdPairings = new ArrayList<>();
        Set<String> pairedUsers = new HashSet<>();

        for (PotentialPairing potentialPairing : validPairings) {
            String user1Id = potentialPairing.user1().getUserId();
            String user2Id = potentialPairing.user2().getUserId();

            // Skip if either user is already paired
            if (pairedUsers.contains(user1Id) || pairedUsers.contains(user2Id)) {
                continue;
            }

            try {
                // Create pairing request
                CreatePairingRequestDTO request = CreatePairingRequestDTO.builder()
                        .user1Id(user1Id)
                        .user2Id(user2Id)
                        .discordChannelId(generateTemporaryChannelId()) // TODO: Integrate with Discord service
                        .compatibilityScore(potentialPairing.compatibilityScore())
                        .build();

                // Create the pairing
                PairingDTO createdPairing = pairingService.createPairing(request);
                createdPairings.add(createdPairing);

                // Mark users as paired
                pairedUsers.add(user1Id);
                pairedUsers.add(user2Id);

                log.info("Created pairing between users {} and {} with compatibility score: {}", 
                        user1Id, user2Id, potentialPairing.compatibilityScore());

            } catch (Exception e) {
                log.error("Failed to create pairing between users {} and {}: {}", 
                         user1Id, user2Id, e.getMessage());
            }
        }

        return createdPairings;
    }

    private Long generateTemporaryChannelId() {
        // TODO: Replace with actual Discord channel creation
        // For now, generate a random ID - this should be replaced with Discord API integration
        return System.currentTimeMillis();
    }
} 