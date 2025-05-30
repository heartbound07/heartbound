package com.app.heartbound.services.pairing;

import com.app.heartbound.dto.pairing.CreatePairingRequestDTO;
import com.app.heartbound.dto.pairing.PairingDTO;
import com.app.heartbound.dto.pairing.PairingUpdateEvent;
import com.app.heartbound.entities.MatchQueueUser;
import com.app.heartbound.repositories.pairing.BlacklistEntryRepository;
import com.app.heartbound.repositories.pairing.MatchQueueUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.Set;
import com.app.heartbound.enums.Gender;
import com.app.heartbound.enums.Region;
import com.app.heartbound.enums.Rank;

/**
 * MatchmakingService
 * 
 * Service for handling automatic matchmaking logic.
 * Calculates compatibility scores and creates optimal pairings.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@EnableScheduling
public class MatchmakingService {

    private final MatchQueueUserRepository matchQueueUserRepository;
    private final BlacklistEntryRepository blacklistEntryRepository;
    private final PairingService pairingService;
    private final SimpMessagingTemplate messagingTemplate;
    private final QueueService queueService;
    private final ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(2);

    /**
     * Potential pairing with compatibility score
     */
    private record PotentialPairing(MatchQueueUser user1, MatchQueueUser user2, int compatibilityScore) {
    }

    /**
     * Pairing notification data
     */
    private record PairingNotification(String userId, PairingDTO pairing) {
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
     * Calculate compatibility score between two users with enhanced gender, age, and region rules
     */
    public int calculateCompatibilityScore(MatchQueueUser user1, MatchQueueUser user2) {
        // First check hard constraints - if violated, return 0 (incompatible)
        
        // Gender compatibility check (hard constraint)
        if (!areGendersCompatible(user1.getGender(), user2.getGender())) {
            return 0; // Incompatible - violates gender matching rules
        }
        
        // Age compatibility check (hard constraint)
        if (!areAgesCompatible(user1.getAge(), user2.getAge())) {
            return 0; // Incompatible - violates age restriction rules
        }
        
        int score = 0;
        
        // Region compatibility (up to 40 points) - enhanced with super-region logic
        score += calculateRegionScore(user1.getRegion(), user2.getRegion());
        
        // Rank compatibility (up to 30 points) - keeping existing logic
        score += calculateRankScore(user1.getRank(), user2.getRank());
        
        // Age proximity scoring (up to 30 points) - enhanced scoring for compatible ages
        score += calculateAgeProximityScore(user1.getAge(), user2.getAge());
        
        return Math.min(score, 100); // Cap at 100 points
    }

    /**
     * Check if two genders are compatible for matching based on the new rules
     */
    private boolean areGendersCompatible(Gender gender1, Gender gender2) {
        if (gender1 == null || gender2 == null) {
            return false; // Cannot match users without gender information
        }
        
        // MALE can only match with FEMALE
        if (gender1 == Gender.MALE) {
            return gender2 == Gender.FEMALE;
        }
        
        // FEMALE can only match with MALE  
        if (gender1 == Gender.FEMALE) {
            return gender2 == Gender.MALE;
        }
        
        // NON_BINARY can match with NON_BINARY or PREFER_NOT_TO_SAY
        if (gender1 == Gender.NON_BINARY) {
            return gender2 == Gender.NON_BINARY || gender2 == Gender.PREFER_NOT_TO_SAY;
        }
        
        // PREFER_NOT_TO_SAY can match with PREFER_NOT_TO_SAY or NON_BINARY
        if (gender1 == Gender.PREFER_NOT_TO_SAY) {
            return gender2 == Gender.PREFER_NOT_TO_SAY || gender2 == Gender.NON_BINARY;
        }
        
        return false;
    }

    /**
     * Check if two ages are compatible based on the 18+/17- rule
     */
    private boolean areAgesCompatible(int age1, int age2) {
        // Hard constraint: Users 18+ cannot pair with users under 17
        if (age1 >= 18 && age2 < 17) {
            return false;
        }
        if (age2 >= 18 && age1 < 17) {
            return false;
        }
        return true;
    }

    /**
     * Calculate region-based compatibility score with super-region logic
     */
    private int calculateRegionScore(Region region1, Region region2) {
        if (region1 == region2) {
            return 40; // Same region - highest priority
        }
        
        // Check if regions are in the same super-region
        if (areInSameSuperRegion(region1, region2)) {
            return 25; // Same super-region, different specific region - high priority
        }
        
        // Cross-super-region matching - lower priority but still possible
        return 10;
    }

    /**
     * Determine if two regions belong to the same super-region
     */
    private boolean areInSameSuperRegion(Region region1, Region region2) {
        // North America super-region: NA_EAST, NA_WEST, NA_CENTRAL
        Set<Region> northAmerica = Set.of(Region.NA_EAST, Region.NA_WEST, Region.NA_CENTRAL);
        if (northAmerica.contains(region1) && northAmerica.contains(region2)) {
            return true;
        }
        
        // Latin America super-region: LATAM, BR
        Set<Region> latinAmerica = Set.of(Region.LATAM, Region.BR);
        if (latinAmerica.contains(region1) && latinAmerica.contains(region2)) {
            return true;
        }
        
        // Asia-Pacific super-region: KR, AP  
        Set<Region> asiaPacific = Set.of(Region.KR, Region.AP);
        if (asiaPacific.contains(region1) && asiaPacific.contains(region2)) {
            return true;
        }
        
        // EU is its own super-region (single region)
        // Same region matches are handled above, so EU-EU would not reach here
        
        return false;
    }

    /**
     * Calculate rank-based compatibility score (keeping existing logic)
     */
    private int calculateRankScore(Rank rank1, Rank rank2) {
        int rankDifference = Math.abs(rank1.ordinal() - rank2.ordinal());
        if (rankDifference <= 1) {
            return 30;
        } else if (rankDifference <= 2) {
            return 20;
        } else if (rankDifference <= 3) {
            return 10;
        }
        return 0;
    }

    /**
     * Calculate age proximity score for users who passed the hard age constraint
     */
    private int calculateAgeProximityScore(int age1, int age2) {
        int ageDifference = Math.abs(age1 - age2);
        if (ageDifference <= 2) {
            return 30; // Very close ages - highest score
        } else if (ageDifference <= 5) {
            return 20; // Close ages - good score
        } else if (ageDifference <= 10) {
            return 10; // Moderate age difference - lower score
        }
        return 0; // Large age difference - no points
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
        List<PairingNotification> pendingNotifications = new ArrayList<>();
        Set<String> pairedUsers = new HashSet<>();

        for (PotentialPairing potentialPairing : validPairings) {
            String user1Id = potentialPairing.user1().getUserId();
            String user2Id = potentialPairing.user2().getUserId();

            // Skip if either user is already paired
            if (pairedUsers.contains(user1Id) || pairedUsers.contains(user2Id)) {
                continue;
            }

            try {
                CreatePairingRequestDTO pairingRequest = CreatePairingRequestDTO.builder()
                        .user1Id(user1Id)
                        .user2Id(user2Id)
                        .discordChannelId(generateTemporaryChannelId())
                        .compatibilityScore(potentialPairing.compatibilityScore())
                        .build();

                PairingDTO createdPairing = pairingService.createPairing(pairingRequest);
                createdPairings.add(createdPairing);

                // Store notifications for later broadcast instead of sending immediately
                pendingNotifications.add(new PairingNotification(user1Id, createdPairing));
                pendingNotifications.add(new PairingNotification(user2Id, createdPairing));

                pairedUsers.add(user1Id);
                pairedUsers.add(user2Id);

                log.info("Created pairing between users {} and {} with compatibility score {}%", 
                         user1Id, user2Id, potentialPairing.compatibilityScore());

            } catch (Exception e) {
                log.error("Failed to create pairing between users {} and {}: {}", 
                         user1Id, user2Id, e.getMessage());
            }
        }

        // Schedule notifications to be sent after 5 seconds
        if (!pendingNotifications.isEmpty()) {
            scheduleNotificationBroadcast(pendingNotifications);
        }

        // Update queue after removing users
        if (!pairedUsers.isEmpty()) {
            queueService.broadcastQueueUpdate();
        }

        return createdPairings;
    }

    private void scheduleNotificationBroadcast(List<PairingNotification> notifications) {
        log.info("Broadcasting {} match notifications immediately", notifications.size());
        
        for (PairingNotification notification : notifications) {
            try {
                PairingUpdateEvent updateEvent = PairingUpdateEvent.builder()
                        .eventType("MATCH_FOUND")
                        .pairing(notification.pairing())
                        .message("Match found! You've been paired with someone special!")
                        .timestamp(LocalDateTime.now())
                        .build();

                // Use direct destination send (this should work since frontend subscribes to exact path)
                String userDestination = "/user/" + notification.userId() + "/topic/pairings";
                messagingTemplate.convertAndSend(userDestination, updateEvent);
                
                log.info("Successfully sent MATCH_FOUND notification to user: {} via direct method", notification.userId());
                log.info("User destination: {}", userDestination);
                log.info("Update event: {}", updateEvent);
                
            } catch (Exception e) {
                log.error("Failed to send match notification to user {}: {}", notification.userId(), e.getMessage(), e);
            }
        }
        
        log.info("Completed broadcasting all match notifications");
    }

    private Long generateTemporaryChannelId() {
        // TODO: Replace with actual Discord channel creation
        // For now, generate a random ID - this should be replaced with Discord API integration
        return System.currentTimeMillis();
    }
} 