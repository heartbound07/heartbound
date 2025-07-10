package com.app.heartbound.services.pairing;

import com.app.heartbound.dto.pairing.CreatePairingRequestDTO;
import com.app.heartbound.dto.pairing.PairingDTO;
import com.app.heartbound.entities.MatchQueueUser;
import com.app.heartbound.repositories.pairing.BlacklistEntryRepository;
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
    private final BlacklistEntryRepository blacklistEntryRepository;

    /**
     * Perform automatic matchmaking for users in queue
     */
    @Transactional
    public List<PairingDTO> performMatchmaking() {
        log.info("Starting matchmaking process...");
        
        // Update last matchmaking run timestamp
        queueService.updateLastMatchmakingRun();
        
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
                    
                    // **CRITICAL FIX: Check blacklist BEFORE calculating compatibility**
                    if (areUsersBlacklisted(currentUser.getUserId(), potentialMatch.getUserId())) {
                        log.info("BLACKLISTED: Users {} and {} are blacklisted, skipping", 
                                currentUser.getUserId(), potentialMatch.getUserId());
                        continue; // Skip blacklisted pairs
                    }
                    
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
                    // Create proper DTO for pairing creation (Discord channel will be created by PairingService)
                    CreatePairingRequestDTO pairingRequest = CreatePairingRequestDTO.builder()
                            .user1Id(currentUser.getUserId())
                            .user2Id(bestMatch.getUserId())
                            .user1DiscordId(currentUser.getUserId()) // Assuming user ID is Discord ID
                            .user2DiscordId(bestMatch.getUserId())   // Assuming user ID is Discord ID
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
                    
                    // **OPTIMIZATION: Trigger cache invalidation after successful match**
                    // This ensures admin stats reflect the new pairing immediately
                    
                } catch (Exception e) {
                    log.error("Failed to create pairing between {} and {}: {}", 
                             currentUser.getUserId(), bestMatch.getUserId(), e.getMessage());
                }
            } else {
                log.info("No compatible match found for user: {} (Age: {}, Gender: {})", 
                        currentUser.getUserId(), currentUser.getAge(), currentUser.getGender());
            }
        }
        
        // 🚀 NEW: NOTIFY UNMATCHED USERS
        notifyUnmatchedUsers(eligibleUsers, matchedUserIds);
        
        // **CRITICAL: Remove matched users from queue and trigger live admin updates**
        if (!matchedUserIds.isEmpty()) {
            List<String> matchedUserIdsList = new ArrayList<>(matchedUserIds);
            queueService.removeMatchedUsersFromQueue(matchedUserIdsList);
            log.info("Removed {} matched users from queue for live admin updates", matchedUserIdsList.size());
        }
        
        // **OPTIMIZATION: Notify QueueService about created matches for cache invalidation**
        if (!newPairings.isEmpty()) {
            queueService.onMatchesCreated(newPairings.size());
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
        
        // NEW: Region compatibility check (hard constraint)
        if (user1.getRegion() != user2.getRegion()) {
            log.info("INCOMPATIBLE: Region mismatch ({} vs {})", user1.getRegion(), user2.getRegion());
            log.info("=== COMPATIBILITY CHECK END: SCORE = 0 ===");
            return 0; // Incompatible - regions must match exactly
        }
        score += 40; // Same region - highest priority
        log.info("Region score: {} + {} = 40 points (hard requirement)", user1.getRegion(), user2.getRegion());
        
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
     * NEW RULES: Minors (≤17) can ONLY match with other minors, adults (≥18) can ONLY match with other adults.
     * Age difference must not exceed 2 years for any potential pair.
     */
    private boolean areAgesCompatible(int age1, int age2) {
        log.debug("Checking age compatibility: {} and {}", age1, age2);
        
        // Define age categories
        boolean user1IsMinor = age1 <= 17;
        boolean user2IsMinor = age2 <= 17;
        boolean user1IsAdult = age1 >= 18;
        boolean user2IsAdult = age2 >= 18;
        
        // STRICT SEGREGATION: Minors can ONLY match with other minors, adults can ONLY match with other adults
        if (user1IsMinor && user2IsAdult) {
            log.debug("AGE RESTRICTION: Minor-Adult pairing not allowed (Ages: {}, {})", age1, age2);
            return false;
        }
        
        if (user1IsAdult && user2IsMinor) {
            log.debug("AGE RESTRICTION: Adult-Minor pairing not allowed (Ages: {}, {})", age1, age2);
            return false;
        }
        
        // Calculate age difference
        int ageGap = Math.abs(age1 - age2);
        
        // STRICT AGE GAP: Maximum 2 years difference for any pairing
        if (ageGap > 2) {
            log.debug("AGE RESTRICTION: Age gap too large (Gap: {} years, maximum allowed: 2)", ageGap);
            return false;
        }
        
        log.debug("Age compatibility check passed: {} and {} (Gap: {} years)", age1, age2, ageGap);
        return true;
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
     * ENHANCED: Prioritizes same-age minor matches to improve minor matchmaking
     */
    private int calculateAgeProximityScore(int age1, int age2) {
        int ageDifference = Math.abs(age1 - age2);
        
        // Special prioritization for same-age minor matches
        boolean bothAreMinors = age1 <= 17 && age2 <= 17;
        if (bothAreMinors && ageDifference == 0) {
            return 35; // HIGHEST priority for same-age minor matches
        }
        
        // Standard age proximity scoring
        if (ageDifference == 0) {
            return 30; // Same age - highest standard score
        } else if (ageDifference <= 1) {
            return 25; // Very close ages - high score
        } else if (ageDifference <= 2) {
            return 20; // Close ages within limit - good score
        }
        
        return 0; // Should not reach here due to hard constraint, but safety fallback
    }

    // Private helper methods

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

    /**
     * Notify users who remained unmatched after the matchmaking cycle
     */
    private void notifyUnmatchedUsers(List<MatchQueueUser> eligibleUsers, Set<String> matchedUserIds) {
        List<String> unmatchedUserIds = eligibleUsers.stream()
                .map(MatchQueueUser::getUserId)
                .filter(userId -> !matchedUserIds.contains(userId))
                .toList();
        
        if (unmatchedUserIds.isEmpty()) {
            log.info("All eligible users were matched - no notifications needed");
            return;
        }
        
        log.info("Notifying {} unmatched users: {}", unmatchedUserIds.size(), unmatchedUserIds);
        
        // Create the notification message
        Map<String, Object> noMatchNotification = Map.of(
            "eventType", "NO_MATCH_FOUND",
            "message", "No match found this round. Stay in queue for the next matchmaking cycle!",
            "timestamp", java.time.LocalDateTime.now().toString(),
            "totalInQueue", unmatchedUserIds.size()
        );
        
        try {
            // Send notification to each unmatched user individually
            for (String userId : unmatchedUserIds) {
                messagingTemplate.convertAndSend(
                    "/user/" + userId + "/topic/pairings", 
                    noMatchNotification
                );
                log.info("Sent no-match notification to user: {}", userId);
            }
            
            log.info("Successfully sent no-match notifications to {} users", unmatchedUserIds.size());
            
        } catch (Exception e) {
            log.error("Failed to send no-match notifications: {}", e.getMessage());
        }
    }

    /**
     * Check if two users are blacklisted from being matched
     */
    private boolean areUsersBlacklisted(String user1Id, String user2Id) {
        return blacklistEntryRepository.existsByUserPair(user1Id, user2Id);
    }
}