package com.app.heartbound.services.riot;

import com.app.heartbound.dto.lfg.LFGPartyEventDTO;
import com.app.heartbound.dto.riot.RiotMatchDto;
import com.app.heartbound.dto.riot.RiotMatchlistDto;
import com.app.heartbound.dto.riot.RiotMatchlistEntryDto;
import com.app.heartbound.dto.riot.RiotMatchInfoDto;
import com.app.heartbound.dto.riot.RiotPlayerDto;
import com.app.heartbound.entities.LFGParty;
import com.app.heartbound.entities.User;
import com.app.heartbound.enums.TrackingStatus;
import com.app.heartbound.repositories.UserRepository;
import com.app.heartbound.repositories.lfg.LFGPartyRepository;
import com.app.heartbound.services.UserService;
import com.app.heartbound.services.riot.RiotMatchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ValorantPartyTrackingService {

    private static final Logger logger = LoggerFactory.getLogger(ValorantPartyTrackingService.class);

    private final LFGPartyRepository partyRepository;
    private final UserRepository userRepository;
    private final RiotMatchService riotMatchService;
    private final UserService userService;
    private final SimpMessagingTemplate messagingTemplate;

    @Value("${valorant.tracking.reward.credits:10}")
    private int creditsRewardAmount;

    @Value("${valorant.tracking.min-participants:2}")
    private int minParticipantsForTracking;

    @Value("${valorant.tracking.match-history.limit:5}")
    private int matchHistoryLimit;

    @Value("${valorant.tracking.match-start.threshold-minutes:15}")
    private long matchStartTimeThresholdMinutes;

    // Define WebSocket topic
    private static final String PARTY_TOPIC = "/topic/party"; // Matches WebSocketConfig

    @Autowired
    public ValorantPartyTrackingService(LFGPartyRepository partyRepository,
                                        UserRepository userRepository,
                                        RiotMatchService riotMatchService,
                                        UserService userService,
                                        SimpMessagingTemplate messagingTemplate) {
        this.partyRepository = partyRepository;
        this.userRepository = userRepository;
        this.riotMatchService = riotMatchService;
        this.userService = userService;
        this.messagingTemplate = messagingTemplate;
    }

    @Scheduled(fixedDelayString = "${valorant.tracking.schedule.fixed-delay-ms:60000}")
    @Transactional // Manage transaction for the whole tracking cycle
    public void trackValorantParties() {
        logger.info("Starting Valorant party tracking cycle...");
        Instant now = Instant.now();

        // Find parties that are potentially active and need tracking
        List<LFGParty> partiesToTrack = partyRepository
                .findByStatusInAndExpiresAtAfterAndTrackingStatusIn(
                        Arrays.asList("open", "full"), // Active statuses
                        now,                           // Not expired
                        Arrays.asList(TrackingStatus.IDLE, TrackingStatus.SEARCHING, TrackingStatus.GAME_DETECTED, TrackingStatus.GAME_IN_PROGRESS) // Statuses that need checking
                );

        logger.debug("Found {} parties potentially needing tracking.", partiesToTrack.size());

        for (LFGParty party : partiesToTrack) {
            try {
                processPartyTracking(party, now);
            } catch (Exception e) {
                logger.error("Error processing tracking for party {}: {}", party.getId(), e.getMessage(), e);
                // Optionally set status to FAILED here, but be cautious about error loops
                // updatePartyStatus(party, TrackingStatus.TRACKING_FAILED, "Internal error during tracking");
            }
        }
        logger.info("Valorant party tracking cycle finished.");
    }

    private void processPartyTracking(LFGParty party, Instant now) {
        logger.debug("Processing party: {}", party.getId());
        Set<String> participantIds = party.getParticipants();

        if (participantIds.size() < minParticipantsForTracking) {
            logger.debug("Party {} has fewer than {} participants. Skipping.", party.getId(), minParticipantsForTracking);
            if (party.getTrackingStatus() != TrackingStatus.IDLE) {
                updatePartyStatus(party, TrackingStatus.IDLE, "Not enough participants");
            }
            return;
        }

        // Fetch users and filter those with linked Riot accounts
        List<User> participants = userRepository.findAllById(participantIds);
        Map<String, String> participantPuuids = participants.stream()
                .filter(u -> u.getRiotPuuid() != null && !u.getRiotPuuid().isEmpty())
                .collect(Collectors.toMap(User::getId, User::getRiotPuuid));

        if (participantPuuids.size() < minParticipantsForTracking) {
            logger.debug("Party {} has fewer than {} participants with linked Riot accounts. Skipping.", party.getId(), minParticipantsForTracking);
            if (party.getTrackingStatus() != TrackingStatus.IDLE) {
                updatePartyStatus(party, TrackingStatus.IDLE, "Not enough linked participants");
            }
            return;
        }

        // --- State Machine Logic ---
        switch (party.getTrackingStatus()) {
            case IDLE:
            case SEARCHING:
                findSharedGame(party, participantPuuids, now);
                break;
            case GAME_DETECTED:
            case GAME_IN_PROGRESS:
                checkGameCompletion(party, participantPuuids);
                break;
            // GAME_COMPLETED, REWARDED, TRACKING_FAILED are terminal states for this cycle
            default:
                logger.debug("Party {} is in state {}, no action needed in this cycle.", party.getId(), party.getTrackingStatus());
                break;
        }
    }

    private void findSharedGame(LFGParty party, Map<String, String> participantPuuids, Instant now) {
        logger.debug("Party {}: Searching for shared game.", party.getId());
        Map<String, List<RiotMatchlistEntryDto>> recentMatches = new HashMap<>();
        Instant lookbackTime = now.minus(matchStartTimeThresholdMinutes, ChronoUnit.MINUTES);

        // Fetch recent matches for all linked participants
        for (String puuid : participantPuuids.values()) {
            Optional<RiotMatchlistDto> matchlistOpt = riotMatchService.getPlayerMatchHistory(puuid, matchHistoryLimit);
            if (matchlistOpt.isPresent()) {
                // Filter matches that started recently and limit the history
                List<RiotMatchlistEntryDto> filteredHistory = matchlistOpt.get().getHistory().stream()
                    .filter(entry -> Instant.ofEpochMilli(entry.getGameStartTimeMillis()).isAfter(lookbackTime))
                    .limit(matchHistoryLimit)
                    .collect(Collectors.toList());
                recentMatches.put(puuid, filteredHistory);
            } else {
                // If we fail to get history for one player, we can't confirm a shared game
                logger.warn("Party {}: Failed to get match history for PUUID {}. Cannot confirm shared game.", party.getId(), puuid);
                updatePartyStatus(party, TrackingStatus.TRACKING_FAILED, "Failed to fetch match history for a participant");
                return;
            }
        }

        if (recentMatches.size() < participantPuuids.size()) {
            logger.debug("Party {}: Did not retrieve match history for all participants.", party.getId());
            // Status might already be set to FAILED above, or keep as SEARCHING
            if (party.getTrackingStatus() == TrackingStatus.IDLE) {
                updatePartyStatus(party, TrackingStatus.SEARCHING, "Fetching participant match history");
            }
            return;
        }

        // Find common matches across all participants
        Optional<String> commonMatchIdOpt = findCommonMatchId(recentMatches, party.getProcessedMatchIds());

        if (commonMatchIdOpt.isPresent()) {
            String commonMatchId = commonMatchIdOpt.get();
            logger.info("Party {}: Found potential common match: {}", party.getId(), commonMatchId);
            party.setCurrentTrackedMatchId(commonMatchId);
            // We can go straight to GAME_IN_PROGRESS or use GAME_DETECTED first
            updatePartyStatus(party, TrackingStatus.GAME_DETECTED, "Potential shared game found: " + commonMatchId);
            // Immediately try to check completion in the same cycle if desired, or wait for next cycle
            checkGameCompletion(party, participantPuuids);
        } else {
            logger.debug("Party {}: No new common match found.", party.getId());
            if (party.getTrackingStatus() == TrackingStatus.IDLE) {
                updatePartyStatus(party, TrackingStatus.SEARCHING, "Actively looking for shared game");
            }
            // else keep SEARCHING
        }
    }

    private Optional<String> findCommonMatchId(Map<String, List<RiotMatchlistEntryDto>> recentMatches, Set<String> processedMatchIds) {
        if (recentMatches.isEmpty()) {
            return Optional.empty();
        }

        // Get match IDs from the first participant as the base set
        Set<String> commonIds = recentMatches.values().iterator().next().stream()
                .map(RiotMatchlistEntryDto::getMatchId)
                .filter(id -> !processedMatchIds.contains(id)) // Exclude already processed matches
                .collect(Collectors.toSet());

        // Intersect with match IDs from other participants
        for (List<RiotMatchlistEntryDto> matches : recentMatches.values()) {
            Set<String> currentParticipantIds = matches.stream()
                    .map(RiotMatchlistEntryDto::getMatchId)
                    .collect(Collectors.toSet());
            commonIds.retainAll(currentParticipantIds);
        }

        // Find the most recent common match (optional, could just take any)
        return commonIds.stream()
                .max(Comparator.comparing(matchId -> getMatchStartTime(recentMatches, matchId)));
    }

    // Helper to get start time for sorting common matches
    private Instant getMatchStartTime(Map<String, List<RiotMatchlistEntryDto>> recentMatches, String matchId) {
        return recentMatches.values().stream()
                .flatMap(List::stream)
                .filter(entry -> entry.getMatchId().equals(matchId))
                .map(entry -> Instant.ofEpochMilli(entry.getGameStartTimeMillis()))
                .findFirst()
                .orElse(Instant.MIN); // Should not happen if matchId came from the map
    }

    private void checkGameCompletion(LFGParty party, Map<String, String> participantPuuids) {
        String matchId = party.getCurrentTrackedMatchId();
        if (matchId == null) {
            logger.warn("Party {}: Attempted to check game completion but currentTrackedMatchId is null. Resetting status.", party.getId());
            updatePartyStatus(party, TrackingStatus.IDLE, "Tracking inconsistency");
            return;
        }

        logger.debug("Party {}: Checking completion status for match {}", party.getId(), matchId);
        Optional<RiotMatchDto> matchDetailsOpt = riotMatchService.getMatchDetails(matchId);

        if (!matchDetailsOpt.isPresent()) {
            logger.warn("Party {}: Failed to get details for match {}. Setting status to FAILED.", party.getId(), matchId);
            updatePartyStatus(party, TrackingStatus.TRACKING_FAILED, "Failed to fetch match details");
            // Consider clearing currentTrackedMatchId here or retrying later
            // party.setCurrentTrackedMatchId(null);
            return;
        }

        RiotMatchDto matchDetails = matchDetailsOpt.get();
        RiotMatchInfoDto matchInfo = matchDetails.getMatchInfo();

        // Determine if match is finished (Riot API doesn't have a simple boolean)
        // Using gameLengthMillis > 0 is a common heuristic
        boolean isCompleted = matchInfo != null && matchInfo.getGameLengthMillis() > 0; // Adjust logic if needed

        if (isCompleted) {
            logger.info("Party {}: Match {} confirmed as completed.", party.getId(), matchId);

            // Verify all intended participants were actually in the match
            Set<String> matchPlayerPuuids = matchDetails.getPlayers().stream()
                    .map(RiotPlayerDto::getPuuid)
                    .collect(Collectors.toSet());

            Set<String> partyPuuids = new HashSet<>(participantPuuids.values());

            if (!matchPlayerPuuids.containsAll(partyPuuids)) {
                logger.warn("Party {}: Match {} completed, but not all party participants were found in the match details. Expected: {}, Found in Match: {}. No rewards.",
                        party.getId(), matchId, partyPuuids, matchPlayerPuuids);
                party.getProcessedMatchIds().add(matchId); // Mark as processed to avoid re-checking
                party.setCurrentTrackedMatchId(null);
                updatePartyStatus(party, TrackingStatus.IDLE, "Match completed, participants mismatch");
                return;
            }

            // --- Reward Credits ---
            logger.info("Party {}: Rewarding credits for completed match {}", party.getId(), matchId);
            boolean allRewardsSuccessful = true;
            for (String userId : participantPuuids.keySet()) { // Iterate over original party user IDs
                try {
                    userService.addCredits(userId, creditsRewardAmount);
                } catch (Exception e) {
                    allRewardsSuccessful = false;
                    logger.error("Party {}: Failed to grant credits to user {} for match {}: {}",
                            party.getId(), userId, matchId, e.getMessage(), e);
                    // Decide how to handle partial failures. Continue rewarding others?
                }
            }

            // Update party state after rewards
            party.getProcessedMatchIds().add(matchId);
            party.setCurrentTrackedMatchId(null);
            party.setLastTrackedMatchCompletionTime(Instant.now());

            if (allRewardsSuccessful) {
                updatePartyStatus(party, TrackingStatus.REWARDED, "Game completed and credits awarded!");
            } else {
                updatePartyStatus(party, TrackingStatus.GAME_COMPLETED, "Game completed, partial reward failure");
                // GAME_COMPLETED indicates it finished but rewards weren't fully successful.
                // Could retry rewards later or require manual intervention.
            }

        } else {
            // Match is not yet completed
            logger.debug("Party {}: Match {} is still in progress.", party.getId(), matchId);
            if (party.getTrackingStatus() != TrackingStatus.GAME_IN_PROGRESS) {
                updatePartyStatus(party, TrackingStatus.GAME_IN_PROGRESS, "Shared game is in progress");
            }
            // else keep GAME_IN_PROGRESS
        }
    }

    // Helper to update status, save party, and send WebSocket event
    private void updatePartyStatus(LFGParty party, TrackingStatus newStatus, String message) {
        TrackingStatus oldStatus = party.getTrackingStatus();
        if (oldStatus == newStatus) return; // No change

        party.setTrackingStatus(newStatus);
        partyRepository.save(party); // Persist changes

        logger.info("Party {}: Status changed from {} to {} - {}", party.getId(), oldStatus, newStatus, message);

        // Broadcast WebSocket event
        LFGPartyEventDTO event = LFGPartyEventDTO.builder()
                .eventType("PARTY_TRACKING_UPDATE") // Use a specific event type
                .minimalParty(LFGPartyEventDTO.MinimalPartyDTO.builder() // Send minimal data
                        .id(party.getId())
                        .status(party.getStatus()) // Include party status if relevant
                        .trackingStatus(newStatus.name()) // Add tracking status
                        .build())
                .message(message) // Provide context
                .build();
        messagingTemplate.convertAndSend(PARTY_TOPIC, event);
    }
}
