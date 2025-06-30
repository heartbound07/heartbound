package com.app.heartbound.services;

import com.app.heartbound.dto.giveaway.CreateGiveawayDTO;
import com.app.heartbound.entities.Giveaway;
import com.app.heartbound.entities.GiveawayEntry;
import com.app.heartbound.entities.User;
import com.app.heartbound.repositories.GiveawayRepository;
import com.app.heartbound.repositories.GiveawayEntryRepository;
import com.app.heartbound.services.discord.DiscordBotSettingsService;
import com.app.heartbound.exceptions.ResourceNotFoundException;
import com.app.heartbound.exceptions.UnauthorizedOperationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class GiveawayService {

    private static final Logger logger = LoggerFactory.getLogger(GiveawayService.class);
    
    private final GiveawayRepository giveawayRepository;
    private final GiveawayEntryRepository giveawayEntryRepository;
    private final UserService userService;
    private final DiscordBotSettingsService discordBotSettingsService;

    @Autowired
    public GiveawayService(GiveawayRepository giveawayRepository,
                          GiveawayEntryRepository giveawayEntryRepository,
                          UserService userService,
                          DiscordBotSettingsService discordBotSettingsService) {
        this.giveawayRepository = giveawayRepository;
        this.giveawayEntryRepository = giveawayEntryRepository;
        this.userService = userService;
        this.discordBotSettingsService = discordBotSettingsService;
    }

    /**
     * Create a new giveaway from the provided DTO
     */
    @Transactional
    public Giveaway createGiveaway(CreateGiveawayDTO dto, String hostUserId, String hostUsername, 
                                  String channelId, String messageId) {
        logger.debug("Creating giveaway for host: {} in channel: {}", hostUserId, channelId);
        
        // Validate DTO
        if (!dto.hasValidRestrictions()) {
            throw new IllegalArgumentException("Exactly one restriction type must be selected");
        }
        
        // Parse duration to end date
        LocalDateTime endDate = parseDurationToEndDate(dto.getDuration());
        
        // Create giveaway entity
        Giveaway giveaway = Giveaway.builder()
                .hostUserId(hostUserId)
                .hostUsername(hostUsername)
                .prize(dto.getPrize())
                .numberOfWinners(dto.getNumberOfWinners())
                .endDate(endDate)
                .channelId(channelId)
                .messageId(messageId)
                .boostersOnly(dto.getBoostersOnly())
                .levelRestricted(dto.getLevelRestricted())
                .noRestrictions(dto.getNoRestrictions())
                .maxEntriesPerUser(dto.getMaxEntriesPerUser())
                .entryPrice(dto.getEntryPrice())
                .status(Giveaway.GiveawayStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build();
        
        Giveaway savedGiveaway = giveawayRepository.save(giveaway);
        logger.info("Created giveaway {} for prize: '{}'", savedGiveaway.getId(), dto.getPrize());
        
        return savedGiveaway;
    }

    /**
     * Enter a user into a giveaway
     */
    @Transactional
    public GiveawayEntry enterGiveaway(UUID giveawayId, String userId, String username) {
        logger.debug("User {} attempting to enter giveaway {}", userId, giveawayId);
        
        // Get the giveaway
        Giveaway giveaway = giveawayRepository.findById(giveawayId)
                .orElseThrow(() -> new ResourceNotFoundException("Giveaway not found"));
        
        // Validate giveaway is still active
        if (!giveaway.isActive()) {
            throw new IllegalStateException("Giveaway is no longer active");
        }
        
        // Get user for validation and credit deduction
        User user = userService.getUserById(userId);
        if (user == null) {
            throw new ResourceNotFoundException("User not found");
        }
        
        // Check if user meets restrictions
        String eligibilityError = checkUserEligibility(giveaway, user);
        if (eligibilityError != null) {
            throw new UnauthorizedOperationException(eligibilityError);
        }
        
        // Check entry limits
        long userEntries = giveawayEntryRepository.countByGiveawayAndUserId(giveaway, userId);
        if (giveaway.getMaxEntriesPerUser() != null && userEntries >= giveaway.getMaxEntriesPerUser()) {
            throw new IllegalStateException("Maximum entries per user reached");
        }
        
        // Check and deduct credits
        if (giveaway.getEntryPrice() > 0) {
            if (user.getCredits() < giveaway.getEntryPrice()) {
                throw new IllegalStateException("You don't have enough credits to enter!");
            }
            user.setCredits(user.getCredits() - giveaway.getEntryPrice());
            userService.updateUser(user);
            logger.debug("Deducted {} credits from user {} for giveaway entry", giveaway.getEntryPrice(), userId);
        }
        
        // Create entry
        int nextEntryNumber = giveawayEntryRepository.getMaxEntryNumberForUser(giveaway, userId) + 1;
        
        GiveawayEntry entry = GiveawayEntry.builder()
                .giveaway(giveaway)
                .userId(userId)
                .username(username)
                .entryNumber(nextEntryNumber)
                .creditsPaid(giveaway.getEntryPrice())
                .entryDate(LocalDateTime.now())
                .build();
        
        GiveawayEntry savedEntry = giveawayEntryRepository.save(entry);
        logger.info("User {} entered giveaway {} (entry #{})", userId, giveawayId, nextEntryNumber);
        
        return savedEntry;
    }

    /**
     * Get active giveaways
     */
    public List<Giveaway> getActiveGiveaways() {
        return giveawayRepository.findActiveGiveaways(LocalDateTime.now());
    }

    /**
     * Get giveaway by message ID
     */
    public Optional<Giveaway> getGiveawayByMessageId(String messageId) {
        return giveawayRepository.findByMessageId(messageId);
    }

    /**
     * Get total entries for a giveaway
     */
    public long getTotalEntries(Giveaway giveaway) {
        return giveawayEntryRepository.countByGiveaway(giveaway);
    }

    /**
     * Get user's entry count for a specific giveaway
     */
    public long getUserEntries(Giveaway giveaway, String userId) {
        return giveawayEntryRepository.countByGiveawayAndUserId(giveaway, userId);
    }

    /**
     * Update giveaway message ID
     */
    @Transactional
    public void updateGiveawayMessageId(UUID giveawayId, String messageId) {
        Giveaway giveaway = giveawayRepository.findById(giveawayId)
                .orElseThrow(() -> new ResourceNotFoundException("Giveaway not found"));
        giveaway.setMessageId(messageId);
        giveawayRepository.save(giveaway);
        logger.debug("Updated message ID for giveaway {} to {}", giveawayId, messageId);
    }

    /**
     * Get giveaway by ID
     */
    public Optional<Giveaway> getGiveawayById(UUID giveawayId) {
        return giveawayRepository.findById(giveawayId);
    }

    /**
     * Complete a giveaway and select winners
     */
    @Transactional
    public List<GiveawayEntry> completeGiveaway(UUID giveawayId) {
        logger.debug("Completing giveaway {}", giveawayId);
        
        Giveaway giveaway = giveawayRepository.findById(giveawayId)
                .orElseThrow(() -> new ResourceNotFoundException("Giveaway not found"));
        
        if (giveaway.getStatus() != Giveaway.GiveawayStatus.ACTIVE) {
            throw new IllegalStateException("Giveaway is not active");
        }
        
        // Get all entries
        List<GiveawayEntry> allEntries = giveawayEntryRepository.findByGiveawayOrderByEntryDateAsc(giveaway);
        
        // Select random winners
        List<GiveawayEntry> winners = selectRandomWinners(allEntries, giveaway.getNumberOfWinners());
        
        // Update giveaway status
        giveaway.setStatus(Giveaway.GiveawayStatus.COMPLETED);
        giveaway.setCompletedAt(LocalDateTime.now());
        giveawayRepository.save(giveaway);
        
        logger.info("Completed giveaway {} with {} winners", giveawayId, winners.size());
        return winners;
    }

    /**
     * Cancel a giveaway and refund entries
     */
    @Transactional
    public void cancelGiveaway(UUID giveawayId, String adminUserId) {
        logger.debug("Admin {} cancelling giveaway {}", adminUserId, giveawayId);
        
        Giveaway giveaway = giveawayRepository.findById(giveawayId)
                .orElseThrow(() -> new ResourceNotFoundException("Giveaway not found"));
        
        if (giveaway.getStatus() != Giveaway.GiveawayStatus.ACTIVE) {
            throw new IllegalStateException("Can only cancel active giveaways");
        }
        
        // Refund all entries if they had a cost
        if (giveaway.getEntryPrice() > 0) {
            List<GiveawayEntry> entries = giveawayEntryRepository.findByGiveawayOrderByEntryDateAsc(giveaway);
            refundEntries(entries);
        }
        
        // Update giveaway status
        giveaway.setStatus(Giveaway.GiveawayStatus.CANCELLED);
        giveaway.setCompletedAt(LocalDateTime.now());
        giveawayRepository.save(giveaway);
        
        logger.info("Cancelled giveaway {} by admin {}", giveawayId, adminUserId);
    }

    /**
     * Find and process expired giveaways
     */
    @Transactional
    public List<Giveaway> processExpiredGiveaways() {
        List<Giveaway> expiredGiveaways = giveawayRepository.findExpiredActiveGiveaways(LocalDateTime.now());
        
        for (Giveaway giveaway : expiredGiveaways) {
            try {
                completeGiveaway(giveaway.getId());
            } catch (Exception e) {
                logger.error("Error completing expired giveaway {}: {}", giveaway.getId(), e.getMessage(), e);
            }
        }
        
        return expiredGiveaways;
    }

    // Private helper methods

    private LocalDateTime parseDurationToEndDate(String duration) {
        LocalDateTime now = LocalDateTime.now();
        
        switch (duration.toLowerCase()) {
            case "1 day":
                return now.plusDays(1);
            case "2 days":
                return now.plusDays(2);
            case "3 days":
                return now.plusDays(3);
            case "4 days":
                return now.plusDays(4);
            case "5 days":
                return now.plusDays(5);
            case "6 days":
                return now.plusDays(6);
            case "1 week":
                return now.plusWeeks(1);
            case "2 weeks":
                return now.plusWeeks(2);
            default:
                throw new IllegalArgumentException("Invalid duration: " + duration);
        }
    }

    /**
     * Check user eligibility for giveaway entry
     * Note: Level and booster restrictions are handled in the Discord listener where we have access to member data
     * @param giveaway The giveaway to check eligibility for
     * @param user The user to check
     * @return Error message if user is not eligible, null if user is eligible
     */
    public String checkUserEligibility(Giveaway giveaway, User user) {
        // If no restrictions, everyone can enter
        if (Boolean.TRUE.equals(giveaway.getNoRestrictions())) {
            return null;
        }
        
        // Level and booster restrictions are handled in Discord listener where we have access to Discord roles/member data
        // This method now only handles database-level validations if any are added in the future
        
        return null; // User is eligible (Discord restrictions checked in listener)
    }

    private List<GiveawayEntry> selectRandomWinners(List<GiveawayEntry> allEntries, int numberOfWinners) {
        if (allEntries.isEmpty()) {
            return new ArrayList<>();
        }
        
        // Group entries by user to ensure fair selection
        Map<String, List<GiveawayEntry>> entriesByUser = allEntries.stream()
                .collect(Collectors.groupingBy(GiveawayEntry::getUserId));
        
        // Create a weighted list where each user gets represented once per entry
        List<GiveawayEntry> weightedEntries = new ArrayList<>();
        for (List<GiveawayEntry> userEntries : entriesByUser.values()) {
            weightedEntries.addAll(userEntries);
        }
        
        // Shuffle and select winners
        Collections.shuffle(weightedEntries);
        
        // Ensure unique users as winners (one win per user)
        Set<String> winnerUserIds = new HashSet<>();
        List<GiveawayEntry> winners = new ArrayList<>();
        
        for (GiveawayEntry entry : weightedEntries) {
            if (!winnerUserIds.contains(entry.getUserId())) {
                winners.add(entry);
                winnerUserIds.add(entry.getUserId());
                
                if (winners.size() >= numberOfWinners) {
                    break;
                }
            }
        }
        
        return winners;
    }

    private void refundEntries(List<GiveawayEntry> entries) {
        Map<String, Integer> refundsByUser = entries.stream()
                .collect(Collectors.groupingBy(
                    GiveawayEntry::getUserId,
                    Collectors.summingInt(GiveawayEntry::getCreditsPaid)
                ));
        
        for (Map.Entry<String, Integer> refund : refundsByUser.entrySet()) {
            String userId = refund.getKey();
            Integer refundAmount = refund.getValue();
            
            if (refundAmount > 0) {
                User user = userService.getUserById(userId);
                if (user != null) {
                    user.setCredits(user.getCredits() + refundAmount);
                    userService.updateUser(user);
                    logger.debug("Refunded {} credits to user {}", refundAmount, userId);
                }
            }
        }
    }
} 