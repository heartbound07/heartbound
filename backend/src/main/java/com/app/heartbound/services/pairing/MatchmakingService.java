package com.app.heartbound.services.pairing;

import com.app.heartbound.dto.pairing.CreatePairingRequestDTO;
import com.app.heartbound.dto.pairing.PairingDTO;
import com.app.heartbound.entities.MatchQueueUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.*;
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

    private final PairingService pairingService;
    private final QueueService queueService;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Perform automatic matchmaking for users in queue
     */
    @Transactional
    public List<PairingDTO> performMatchmaking() {
        log.info("Starting matchmaking process...");
        
        List<MatchQueueUser> eligibleUsers = queueService.getEligibleUsersForMatching();
        log.info("Found {} eligible users for matching", eligibleUsers.size());
        
        List<PairingDTO> newPairings = new ArrayList<>();
        Set<String> matchedUserIds = new HashSet<>();
        
        // Enhanced matching logic with strict compatibility validation
        for (MatchQueueUser currentUser : eligibleUsers) {
            if (matchedUserIds.contains(currentUser.getUserId())) {
                continue; // User already matched
            }
            
            MatchQueueUser bestMatch = null;
            int highestScore = 0; // CRITICAL: Only consider scores > 0
            
            for (MatchQueueUser potentialMatch : eligibleUsers) {
                if (!currentUser.getUserId().equals(potentialMatch.getUserId()) 
                    && !matchedUserIds.contains(potentialMatch.getUserId())) {
                    
                    // Calculate compatibility score
                    int compatibilityScore = calculateCompatibilityScore(currentUser, potentialMatch);
                    
                    // CRITICAL FIX: Only proceed if compatibility score > 0
                    if (compatibilityScore > 0 && compatibilityScore > highestScore) {
                        // Additional validation - double-check age compatibility
                        if (areAgesCompatible(currentUser.getAge(), potentialMatch.getAge()) 
                            && areGendersCompatible(currentUser.getGender(), potentialMatch.getGender())) {
                            
                            log.info("Valid match candidate found: {} + {} (Score: {})", 
                                    currentUser.getUserId(), potentialMatch.getUserId(), compatibilityScore);
                            bestMatch = potentialMatch;
                            highestScore = compatibilityScore;
                        } else {
                            log.warn("COMPATIBILITY VIOLATION: Users {} and {} failed secondary validation check", 
                                    currentUser.getUserId(), potentialMatch.getUserId());
                        }
                    } else if (compatibilityScore == 0) {
                        log.info("REJECTED MATCH: {} + {} (Incompatible - Score: 0)", 
                                currentUser.getUserId(), potentialMatch.getUserId());
                    }
                }
            }
            
            // Create pairing only if we found a compatible match
            if (bestMatch != null && highestScore > 0) {
                log.info("=== CREATING PAIRING ===");
                log.info("User 1: {} (Age: {}, Gender: {})", 
                        currentUser.getUserId(), currentUser.getAge(), currentUser.getGender());
                log.info("User 2: {} (Age: {}, Gender: {})", 
                        bestMatch.getUserId(), bestMatch.getAge(), bestMatch.getGender());
                log.info("Final Compatibility Score: {}", highestScore);
                
                try {
                    // Create proper DTO for pairing creation
                    CreatePairingRequestDTO pairingRequest = CreatePairingRequestDTO.builder()
                            .user1Id(currentUser.getUserId())
                            .user2Id(bestMatch.getUserId())
                            .discordChannelId(generateTemporaryChannelId())
                            .compatibilityScore(highestScore)
                            .user1Age(currentUser.getAge())
                            .user1Gender(currentUser.getGender().toString())
                            .user1Region(currentUser.getRegion().toString())
                            .user1Rank(currentUser.getRank().toString())
                            .user2Age(bestMatch.getAge())
                            .user2Gender(bestMatch.getGender().toString())
                            .user2Region(bestMatch.getRegion().toString())
                            .user2Rank(bestMatch.getRank().toString())
                            .build();
                            
                    PairingDTO pairing = pairingService.createPairing(pairingRequest);
                    newPairings.add(pairing);
                    matchedUserIds.add(currentUser.getUserId());
                    matchedUserIds.add(bestMatch.getUserId());
                    
                    log.info("Successfully created pairing with ID: {}", pairing.getId());
                    
                    // 🚀 BROADCAST MATCH FOUND NOTIFICATIONS
                    broadcastMatchFound(pairing, currentUser.getUserId(), bestMatch.getUserId());
                    
                } catch (Exception e) {
                    log.error("Failed to create pairing between {} and {}: {}", 
                             currentUser.getUserId(), bestMatch.getUserId(), e.getMessage());
                }
            } else {
                log.info("No compatible match found for user: {} (Age: {}, Gender: {})", 
                        currentUser.getUserId(), currentUser.getAge(), currentUser.getGender());
            }
        }
        
        log.info("Matchmaking completed. Created {} new pairings", newPairings.size());
        return newPairings;
    }

    /**
     * Calculate compatibility score between two users with enhanced gender, age, and region rules
     */
    public int calculateCompatibilityScore(MatchQueueUser user1, MatchQueueUser user2) {
        int score = 0;
        
        log.debug("Calculating compatibility for users {} (Age: {}, Gender: {}) and {} (Age: {}, Gender: {})", 
                  user1.getUserId(), user1.getAge(), user1.getGender(),
                  user2.getUserId(), user2.getAge(), user2.getGender());
        
        log.info("=== COMPATIBILITY CHECK START ===");
        log.info("User 1: ID={}, Age={}, Gender={}, Region={}, Rank={}", 
                  user1.getUserId(), user1.getAge(), user1.getGender(), user1.getRegion(), user1.getRank());
        log.info("User 2: ID={}, Age={}, Gender={}, Region={}, Rank={}", 
                  user2.getUserId(), user2.getAge(), user2.getGender(), user2.getRegion(), user2.getRank());
        
        // First check hard constraints - if violated, return 0 (incompatible)
        
        // Gender compatibility check (hard constraint)
        boolean gendersCompatible = areGendersCompatible(user1.getGender(), user2.getGender());
        log.info("Gender compatibility: {} + {} = {}", user1.getGender(), user2.getGender(), gendersCompatible);
        if (!gendersCompatible) {
            log.info("INCOMPATIBLE: Gender mismatch");
            log.info("=== COMPATIBILITY CHECK END: SCORE = 0 ===");
            return 0; // Incompatible - violates gender matching rules
        }
        
        // Age compatibility check (hard constraint)
        boolean agesCompatible = areAgesCompatible(user1.getAge(), user2.getAge());
        log.info("Age compatibility: {} + {} = {}", user1.getAge(), user2.getAge(), agesCompatible);
        if (!agesCompatible) {
            log.info("INCOMPATIBLE: Age restriction violated");
            log.info("=== COMPATIBILITY CHECK END: SCORE = 0 ===");
            return 0; // Incompatible - violates age restriction rules
        }
        
        // Region compatibility (up to 40 points) - enhanced with super-region logic
        int regionScore = calculateRegionScore(user1.getRegion(), user2.getRegion());
        score += regionScore;
        log.info("Region score: {} + {} = {} points", user1.getRegion(), user2.getRegion(), regionScore);
        
        // Rank compatibility (up to 30 points) - keeping existing logic
        int rankScore = calculateRankScore(user1.getRank(), user2.getRank());
        score += rankScore;
        log.info("Rank score: {} + {} = {} points", user1.getRank(), user2.getRank(), rankScore);
        
        // Age proximity scoring (up to 30 points) - enhanced scoring for compatible ages
        int ageScore = calculateAgeProximityScore(user1.getAge(), user2.getAge());
        score += ageScore;
        log.info("Age proximity score: {} + {} = {} points", user1.getAge(), user2.getAge(), ageScore);
        
        int finalScore = Math.min(score, 100); // Cap at 100 points
        log.info("Total compatibility score: {} (capped at 100)", finalScore);
        log.info("=== COMPATIBILITY CHECK END: SCORE = {} ===", finalScore);
        
        log.debug("Final compatibility score: {} for users {} and {}", finalScore, user1.getUserId(), user2.getUserId());
        return finalScore;
    }

    /**
     * Check if two genders are compatible for matching based on the new rules
     */
    private boolean areGendersCompatible(Gender gender1, Gender gender2) {
        log.info("Checking gender compatibility: {} vs {}", gender1, gender2);
        
        if (gender1 == null || gender2 == null) {
            log.info("Gender compatibility result: false (null gender)");
            return false; // Cannot match users without gender information
        }
        
        // MALE can only match with FEMALE
        if (gender1 == Gender.MALE) {
            boolean result = gender2 == Gender.FEMALE;
            log.info("MALE matching logic: {} == FEMALE? {}", gender2, result);
            return result;
        }
        
        // FEMALE can only match with MALE
        if (gender1 == Gender.FEMALE) {
            boolean result = gender2 == Gender.MALE;
            log.info("FEMALE matching logic: {} == MALE? {}", gender2, result);
            return result;
        }
        
        // NON_BINARY can only match with NON_BINARY or PREFER_NOT_TO_SAY
        if (gender1 == Gender.NON_BINARY) {
            boolean result = gender2 == Gender.NON_BINARY || gender2 == Gender.PREFER_NOT_TO_SAY;
            log.info("NON_BINARY matching logic: {} is NON_BINARY or PREFER_NOT_TO_SAY? {}", gender2, result);
            return result;
        }
        
        // PREFER_NOT_TO_SAY can only match with NON_BINARY or PREFER_NOT_TO_SAY
        if (gender1 == Gender.PREFER_NOT_TO_SAY) {
            boolean result = gender2 == Gender.NON_BINARY || gender2 == Gender.PREFER_NOT_TO_SAY;
            log.info("PREFER_NOT_TO_SAY matching logic: {} is NON_BINARY or PREFER_NOT_TO_SAY? {}", gender2, result);
            return result;
        }
        
        log.info("Gender compatibility result: false (no matching rule)");
        return false; // No valid combination found
    }

    /**
     * Check if two ages are compatible for matching based on safety rules
     */
    private boolean areAgesCompatible(int age1, int age2) {
        log.debug("Checking age compatibility: {} and {}", age1, age2);
        
        // Both users must be 18+ for any pairing
        if (age1 < 18 || age2 < 18) {
            log.debug("AGE RESTRICTION: One or both users under 18 (Ages: {}, {})", age1, age2);
            return false;
        }
        
        int ageGap = Math.abs(age1 - age2);
        
        // Strict age gap rules
        if (ageGap > 5) {
            log.debug("AGE RESTRICTION: Age gap too large (Gap: {} years)", ageGap);
            return false;
        }
        
        // Special restrictions for users exactly 18
        int minAge = Math.min(age1, age2);
        int maxAge = Math.max(age1, age2);
        
        if (minAge == 18 && maxAge > 20) {
            log.debug("AGE RESTRICTION: 18-year-old cannot be paired with user over 20 (Ages: {}, {})", age1, age2);
            return false;
        }
        
        log.debug("Age compatibility check passed: {} and {}", age1, age2);
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

    private Long generateTemporaryChannelId() {
        // Generate a temporary channel ID (this should be replaced with actual Discord channel creation)
        return System.currentTimeMillis();
    }

    /**
     * Broadcast match found notifications to both users
     */
    private void broadcastMatchFound(PairingDTO pairing, String user1Id, String user2Id) {
        log.info("Broadcasting match found notifications for pairing ID: {}", pairing.getId());
        
        // Create the notification message
        Map<String, Object> matchNotification = Map.of(
            "eventType", "MATCH_FOUND",
            "pairing", pairing,
            "message", "Match found! You've been paired with someone special!",
            "timestamp", java.time.LocalDateTime.now().toString()
        );
        
        try {
            // Send to both users individually
            messagingTemplate.convertAndSend(
                "/user/" + user1Id + "/topic/pairings", 
                matchNotification
            );
            
            messagingTemplate.convertAndSend(
                "/user/" + user2Id + "/topic/pairings", 
                matchNotification
            );
            
            log.info("Successfully broadcasted match notifications to users {} and {}", user1Id, user2Id);
            
        } catch (Exception e) {
            log.error("Failed to broadcast match notifications: {}", e.getMessage());
        }
    }
}