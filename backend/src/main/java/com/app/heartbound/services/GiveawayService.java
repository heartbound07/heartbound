package com.app.heartbound.services;

import com.app.heartbound.dto.giveaway.CreateGiveawayDTO;
import com.app.heartbound.entities.Giveaway;
import com.app.heartbound.entities.GiveawayEntry;
import com.app.heartbound.entities.User;
import com.app.heartbound.repositories.GiveawayRepository;
import com.app.heartbound.repositories.GiveawayEntryRepository;
import com.app.heartbound.services.discord.DiscordBotSettingsService;
import com.app.heartbound.services.discord.DiscordService;
import com.app.heartbound.exceptions.ResourceNotFoundException;
import com.app.heartbound.exceptions.UnauthorizedOperationException;
import com.app.heartbound.config.CacheConfig;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.EmbedBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.stream.Collectors;
import java.awt.Color;

@Service
public class GiveawayService {

    private static final Logger logger = LoggerFactory.getLogger(GiveawayService.class);
    
    private final GiveawayRepository giveawayRepository;
    private final GiveawayEntryRepository giveawayEntryRepository;
    private final UserService userService;
    private final CacheConfig cacheConfig;
    private final DiscordService discordService;
    
    // TaskScheduler for individual giveaway completion tasks
    private final TaskScheduler taskScheduler;
    
    // Track scheduled tasks for cancellation when needed
    private final Map<UUID, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    public GiveawayService(GiveawayRepository giveawayRepository,
                          GiveawayEntryRepository giveawayEntryRepository,
                          UserService userService,
                          DiscordBotSettingsService discordBotSettingsService,
                          CacheConfig cacheConfig,
                          @Lazy DiscordService discordService) {
        this.giveawayRepository = giveawayRepository;
        this.giveawayEntryRepository = giveawayEntryRepository;
        this.userService = userService;
        this.cacheConfig = cacheConfig;
        this.discordService = discordService;
        
        // Initialize TaskScheduler for precise giveaway completion scheduling
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2); // Small pool for giveaway completion tasks
        scheduler.setThreadNamePrefix("giveaway-scheduler-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        scheduler.initialize();
        this.taskScheduler = scheduler;
        
        logger.info("GiveawayService initialized with TaskScheduler for precise completion timing");
    }

    /**
     * Initialize service after all dependencies are injected
     * Schedule completion tasks for any existing active giveaways
     */
    @PostConstruct
    public void initializeExistingGiveaways() {
        try {
            // Schedule completion tasks for any active giveaways that might have been running during restart
            List<Giveaway> activeGiveaways = giveawayRepository.findActiveGiveaways(LocalDateTime.now());
            
            for (Giveaway giveaway : activeGiveaways) {
                if (giveaway.getEndDate().isAfter(LocalDateTime.now())) {
                    scheduleGiveawayCompletion(giveaway);
                    logger.info("Rescheduled completion task for giveaway {} (Prize: {})", 
                               giveaway.getId(), giveaway.getPrize());
                } else {
                    // Giveaway should have completed already, complete it now
                    logger.info("Found expired giveaway {} during startup, completing immediately", giveaway.getId());
                    try {
                        completeGiveaway(giveaway.getId());
                    } catch (Exception e) {
                        logger.error("Error completing expired giveaway {} during startup: {}", 
                                   giveaway.getId(), e.getMessage(), e);
                    }
                }
            }
            
            logger.info("Initialized {} existing active giveaway tasks", activeGiveaways.size());
        } catch (Exception e) {
            logger.error("Error initializing existing giveaway tasks: {}", e.getMessage(), e);
        }
    }

    /**
     * Cleanup method called before bean destruction
     * Cancels all scheduled tasks and shuts down the task scheduler
     */
    @PreDestroy
    public void cleanup() {
        try {
            // Cancel all scheduled tasks
            scheduledTasks.values().forEach(task -> {
                if (!task.isDone()) {
                    task.cancel(false);
                }
            });
            scheduledTasks.clear();
            
            // Shutdown the task scheduler
            if (taskScheduler instanceof ThreadPoolTaskScheduler) {
                ((ThreadPoolTaskScheduler) taskScheduler).shutdown();
            }
            
            logger.info("GiveawayService cleanup completed - cancelled {} scheduled tasks", scheduledTasks.size());
        } catch (Exception e) {
            logger.error("Error during GiveawayService cleanup: {}", e.getMessage(), e);
        }
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
        
        // Schedule the completion task for this giveaway's end time
        scheduleGiveawayCompletion(savedGiveaway);
        
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
            // Check if this is a paid entry - users must be in database for paid entries
            if (giveaway.getEntryPrice() > 0) {
                throw new UnauthorizedOperationException("not signed up with the bot");
            }
            // For free entries, we can proceed without a database user record
            // We'll skip credit validation and just create the entry
        }
        
        // Check if user meets restrictions (only if user exists in database)
        if (user != null) {
            String eligibilityError = checkUserEligibility(giveaway, user);
            if (eligibilityError != null) {
                throw new UnauthorizedOperationException(eligibilityError);
            }
        }
        
        // Check entry limits
        long userEntries = giveawayEntryRepository.countByGiveawayAndUserId(giveaway, userId);
        if (giveaway.getMaxEntriesPerUser() != null && userEntries >= giveaway.getMaxEntriesPerUser()) {
            throw new IllegalStateException("Maximum entries per user reached");
        }
        
        // Check and deduct credits (only if user exists and entry has a cost)
        if (giveaway.getEntryPrice() > 0 && user != null) {
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
        
        // Cancel any scheduled completion task (in case this was manually completed)
        cancelScheduledTask(giveawayId);
        
        // Announce winners in Discord
        announceWinners(giveaway, winners);
        
        // Update the original giveaway embed to completion state
        updateGiveawayEmbedForCompletion(giveaway, winners);
        
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
        
        // Cancel the scheduled completion task
        cancelScheduledTask(giveawayId);
        
        // Invalidate giveaway cache
        cacheConfig.invalidateGiveawayCache();
        
        logger.info("Cancelled giveaway {} by admin {}", giveawayId, adminUserId);
    }

    /**
     * Find and process expired giveaways
     * Note: This method is now primarily used for manual cleanup or emergency situations
     * since TaskScheduler handles automatic completion
     */
    @Transactional
    public List<Giveaway> processExpiredGiveaways() {
        List<Giveaway> expiredGiveaways = giveawayRepository.findExpiredActiveGiveaways(LocalDateTime.now());
        
        if (!expiredGiveaways.isEmpty()) {
            boolean discordReady = isDiscordReady();
            logger.info("Processing {} expired giveaways (Discord ready: {}) - Note: These should have been handled by TaskScheduler", 
                       expiredGiveaways.size(), discordReady);
        }
        
        for (Giveaway giveaway : expiredGiveaways) {
            try {
                completeGiveaway(giveaway.getId());
            } catch (Exception e) {
                logger.error("Error completing expired giveaway {}: {}", giveaway.getId(), e.getMessage(), e);
            }
        }
        
        return expiredGiveaways;
    }

    /**
     * Delete a completed giveaway
     */
    @Transactional
    public void deleteCompletedGiveaway(UUID giveawayId, String adminUserId) {
        logger.debug("Admin {} deleting completed giveaway {}", adminUserId, giveawayId);
        
        Giveaway giveaway = giveawayRepository.findById(giveawayId)
                .orElseThrow(() -> new ResourceNotFoundException("Giveaway not found"));
        
        if (giveaway.getStatus() != Giveaway.GiveawayStatus.COMPLETED) {
            throw new IllegalStateException("Can only delete completed giveaways");
        }
        
        // For completed giveaways, we can safely delete them as no refunds are needed
        giveawayRepository.delete(giveaway);
        
        // Cancel any scheduled completion task (should already be done, but ensure cleanup)
        cancelScheduledTask(giveawayId);
        
        // Invalidate giveaway cache
        cacheConfig.invalidateGiveawayCache();
        
        logger.info("Deleted completed giveaway {} by admin {}", giveawayId, adminUserId);
    }

    /**
     * Delete any giveaway (handles both ACTIVE and COMPLETED)
     */
    @Transactional
    public void deleteGiveaway(UUID giveawayId, String adminUserId) {
        logger.debug("Admin {} deleting giveaway {}", adminUserId, giveawayId);
        
        Giveaway giveaway = giveawayRepository.findById(giveawayId)
                .orElseThrow(() -> new ResourceNotFoundException("Giveaway not found"));
        
        // Validate that only the host can delete their own giveaway
        if (!giveaway.getHostUserId().equals(adminUserId)) {
            throw new UnauthorizedOperationException("You can only delete your own giveaways");
        }
        
        // Cannot delete cancelled giveaways
        if (giveaway.getStatus() == Giveaway.GiveawayStatus.CANCELLED) {
            throw new IllegalStateException("Cannot delete cancelled giveaways");
        }
        
        if (giveaway.getStatus() == Giveaway.GiveawayStatus.ACTIVE) {
            // For active giveaways, cancel them first (includes refunds)
            cancelGiveaway(giveawayId, adminUserId);
        } else if (giveaway.getStatus() == Giveaway.GiveawayStatus.COMPLETED) {
            // For completed giveaways, delete directly
            deleteCompletedGiveaway(giveawayId, adminUserId);
        }
        
        // Cancel any scheduled completion task (in case it wasn't cancelled during cancel/delete)
        cancelScheduledTask(giveawayId);
        
        // Invalidate giveaway cache
        cacheConfig.invalidateGiveawayCache();
        
        logger.info("Successfully deleted giveaway {} by admin {}", giveawayId, adminUserId);
    }

    /**
     * Get giveaways by host for autocomplete (excludes CANCELLED)
     */
    public List<Giveaway> getGiveawaysByHostForAutocomplete(String hostUserId, int limit) {
        List<Giveaway> allGiveaways = giveawayRepository.findByHostUserIdOrderByCreatedAtDesc(hostUserId);
        
        // Filter out cancelled giveaways and limit results
        return allGiveaways.stream()
                .filter(giveaway -> giveaway.getStatus() != Giveaway.GiveawayStatus.CANCELLED)
                .limit(limit)
                .collect(Collectors.toList());
    }

    // Private helper methods

    private LocalDateTime parseDurationToEndDate(String duration) {
        if (duration == null || duration.trim().isEmpty()) {
            throw new IllegalArgumentException("Duration cannot be null or empty");
        }
        
        String normalizedDuration = duration.trim().toLowerCase();
        LocalDateTime now = LocalDateTime.now();
        
        // Regex patterns for different time formats
        // Days: 1d, 2d, 3d... OR 1 day, 2 days, 3 days...
        java.util.regex.Pattern dayPattern = java.util.regex.Pattern.compile("^(\\d+)\\s*(?:d|day|days)$");
        // Weeks: 1w, 2w, 3w... OR 1 week, 2 weeks, 3 weeks...
        java.util.regex.Pattern weekPattern = java.util.regex.Pattern.compile("^(\\d+)\\s*(?:w|week|weeks)$");
        // Minutes: 1m, 2m, 3m... OR 1 minute, 2 minutes, 3 minutes...
        java.util.regex.Pattern minutePattern = java.util.regex.Pattern.compile("^(\\d+)\\s*(?:m|minute|minutes)$");
        // Seconds: 10s, 20s, 30s... OR 10 seconds, 20 seconds, 30 seconds...
        java.util.regex.Pattern secondPattern = java.util.regex.Pattern.compile("^(\\d+)\\s*(?:s|second|seconds)$");
        
        java.util.regex.Matcher matcher;
        
        // Try to match days pattern
        matcher = dayPattern.matcher(normalizedDuration);
        if (matcher.matches()) {
            int days = Integer.parseInt(matcher.group(1));
            validateDaysBounds(days);
            return now.plusDays(days);
        }
        
        // Try to match weeks pattern
        matcher = weekPattern.matcher(normalizedDuration);
        if (matcher.matches()) {
            int weeks = Integer.parseInt(matcher.group(1));
            validateWeeksBounds(weeks);
            return now.plusWeeks(weeks);
        }
        
        // Try to match minutes pattern
        matcher = minutePattern.matcher(normalizedDuration);
        if (matcher.matches()) {
            int minutes = Integer.parseInt(matcher.group(1));
            validateMinutesBounds(minutes);
            return now.plusMinutes(minutes);
        }
        
        // Try to match seconds pattern
        matcher = secondPattern.matcher(normalizedDuration);
        if (matcher.matches()) {
            int seconds = Integer.parseInt(matcher.group(1));
            validateSecondsBounds(seconds);
            return now.plusSeconds(seconds);
        }
        
        // If no pattern matches, throw an error with helpful message
        throw new IllegalArgumentException("Invalid duration format: '" + duration + "'. " +
            "Supported formats: days (1d, 1 day), weeks (1w, 1 week), minutes (1m, 1 minute), seconds (10s, 10 seconds)");
    }
    
    /**
     * Validate days are within reasonable bounds
     */
    private void validateDaysBounds(int days) {
        if (days < 1) {
            throw new IllegalArgumentException("Days must be at least 1");
        }
        if (days > 28) { // 4 weeks maximum
            throw new IllegalArgumentException("Duration cannot exceed 4 weeks (28 days)");
        }
    }
    
    /**
     * Validate weeks are within reasonable bounds
     */
    private void validateWeeksBounds(int weeks) {
        if (weeks < 1) {
            throw new IllegalArgumentException("Weeks must be at least 1");
        }
        if (weeks > 4) { // 4 weeks maximum
            throw new IllegalArgumentException("Duration cannot exceed 4 weeks");
        }
    }
    
    /**
     * Validate minutes are within reasonable bounds
     */
    private void validateMinutesBounds(int minutes) {
        if (minutes < 1) {
            throw new IllegalArgumentException("Minutes must be at least 1");
        }
        if (minutes > 40320) { // 4 weeks in minutes
            throw new IllegalArgumentException("Duration cannot exceed 4 weeks (40,320 minutes)");
        }
    }
    
    /**
     * Validate seconds are within reasonable bounds
     */
    private void validateSecondsBounds(int seconds) {
        if (seconds < 10) {
            throw new IllegalArgumentException("Seconds must be at least 10");
        }
        if (seconds > 2419200) { // 4 weeks in seconds
            throw new IllegalArgumentException("Duration cannot exceed 4 weeks (2,419,200 seconds)");
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

    /**
     * Announce giveaway winners in Discord channel
     * @param giveaway The completed giveaway
     * @param winners List of winning entries
     */
    private void announceWinners(Giveaway giveaway, List<GiveawayEntry> winners) {
        try {
            if (winners.isEmpty()) {
                logger.info("No winners to announce for giveaway {}", giveaway.getId());
                return;
            }

            // Get the Discord channel
            TextChannel channel = getDiscordChannel(giveaway.getChannelId());
            if (channel == null) {
                logger.warn("Cannot announce winners for giveaway {}: Discord channel {} not accessible or JDA not ready", 
                           giveaway.getId(), giveaway.getChannelId());
                return;
            }

            // Format the winner announcement message
            String winnerMessage = formatWinnerMessage(giveaway.getPrize(), winners);
            
            // Send the announcement
            channel.sendMessage(winnerMessage)
                .queue(
                    success -> logger.info("Successfully announced {} winners for giveaway {} in channel {}", 
                                          winners.size(), giveaway.getId(), giveaway.getChannelId()),
                    error -> logger.error("Failed to announce winners for giveaway {} in channel {}: {}", 
                                         giveaway.getId(), giveaway.getChannelId(), error.getMessage())
                );

        } catch (Exception e) {
            logger.error("Error announcing winners for giveaway {}: {}", giveaway.getId(), e.getMessage(), e);
        }
    }

    /**
     * Get Discord text channel by ID
     * @param channelId The Discord channel ID
     * @return TextChannel or null if not found
     */
    private TextChannel getDiscordChannel(String channelId) {
        try {
            JDA jda = discordService.getJDA();
            if (jda == null) {
                logger.debug("JDA not available for channel access, skipping Discord operation");
                return null;
            }
            
            // Check if JDA is connected and ready to send messages
            if (jda.getStatus() != JDA.Status.CONNECTED) {
                logger.debug("JDA not connected (status: {}), skipping Discord operation", jda.getStatus());
                return null;
            }
            
            return jda.getTextChannelById(channelId);
        } catch (Exception e) {
            logger.error("Error getting Discord channel {}: {}", channelId, e.getMessage());
            return null;
        }
    }

        /**
     * Format the winner announcement message according to requirements
     * @param prize The giveaway prize
     * @param winners List of winning entries
     * @return Formatted message string
     */
    private String formatWinnerMessage(String prize, List<GiveawayEntry> winners) {
        if (winners.size() == 1) {
            // Single winner: "Congratulations {winner}! You have won {prize}, please make a ticket to collect your prize!"
            return String.format("Congratulations <@%s>! You have won %s, please make a ticket to collect your prize!", 
                                winners.get(0).getUserId(), prize);
        } else {
            // Multiple winners: "Congratulations @{winner1} @{winner2} @{winner3} ! You have won {prize}, please make a ticket to collect your prize!"
            String winnerMentions = winners.stream()
                .map(winner -> "<@" + winner.getUserId() + ">")
                .collect(Collectors.joining(" "));
            
            return String.format("Congratulations %s ! You have won %s, please make a ticket to collect your prize!", 
                                winnerMentions, prize);
        }
    }

    /**
     * Check if Discord service is ready for messaging operations
     * @return true if Discord is ready, false otherwise
     */
    public boolean isDiscordReady() {
        try {
            JDA jda = discordService.getJDA();
            return jda != null && jda.getStatus() == JDA.Status.CONNECTED;
        } catch (Exception e) {
            logger.debug("Error checking Discord readiness: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Schedule a giveaway completion task for the exact end time
     * @param giveaway The giveaway to schedule completion for
     */
    private void scheduleGiveawayCompletion(Giveaway giveaway) {
        try {
            // Convert LocalDateTime to Instant for scheduling
            java.time.Instant executionTime = giveaway.getEndDate()
                .atZone(ZoneId.systemDefault())
                .toInstant();
            
            // Only schedule if the end time is in the future
            if (executionTime.isAfter(java.time.Instant.now())) {
                ScheduledFuture<?> task = taskScheduler.schedule(() -> {
                    try {
                        logger.info("TaskScheduler executing completion for giveaway {} (Prize: {})", 
                                   giveaway.getId(), giveaway.getPrize());
                        completeGiveaway(giveaway.getId());
                        
                        // Remove from scheduled tasks map after completion
                        scheduledTasks.remove(giveaway.getId());
                    } catch (Exception e) {
                        logger.error("Error in scheduled completion of giveaway {}: {}", 
                                   giveaway.getId(), e.getMessage(), e);
                    }
                }, executionTime);
                
                // Store the scheduled task for potential cancellation
                scheduledTasks.put(giveaway.getId(), task);
                
                logger.info("Scheduled completion task for giveaway {} at {}", 
                           giveaway.getId(), giveaway.getEndDate());
            } else {
                logger.debug("Giveaway {} end time is in the past, not scheduling", giveaway.getId());
            }
        } catch (Exception e) {
            logger.error("Error scheduling completion for giveaway {}: {}", giveaway.getId(), e.getMessage(), e);
        }
    }

    /**
     * Cancel a scheduled completion task for a giveaway
     * @param giveawayId The giveaway ID whose task should be cancelled
     */
    private void cancelScheduledTask(UUID giveawayId) {
        try {
            ScheduledFuture<?> task = scheduledTasks.remove(giveawayId);
            if (task != null && !task.isDone()) {
                boolean cancelled = task.cancel(false);
                logger.info("Cancelled scheduled completion task for giveaway {} (success: {})", 
                           giveawayId, cancelled);
            } else {
                logger.debug("No active scheduled task found for giveaway {}", giveawayId);
            }
        } catch (Exception e) {
            logger.error("Error cancelling scheduled task for giveaway {}: {}", giveawayId, e.getMessage(), e);
        }
    }

    /**
     * Get the number of currently scheduled giveaway completion tasks
     * @return Number of active scheduled tasks
     */
    public int getActiveScheduledTasksCount() {
        return (int) scheduledTasks.values().stream()
            .filter(task -> !task.isDone())
            .count();
    }

    /**
     * Update the original giveaway embed to show completion status
     * @param giveaway The completed giveaway
     * @param winners List of winning entries
     */
    private void updateGiveawayEmbedForCompletion(Giveaway giveaway, List<GiveawayEntry> winners) {
        try {
            if (giveaway.getMessageId() == null || giveaway.getMessageId().equals("temp")) {
                logger.debug("Cannot update giveaway embed for completion: messageId is null or temporary for giveaway {}", giveaway.getId());
                return;
            }

            // Get the Discord channel
            TextChannel channel = getDiscordChannel(giveaway.getChannelId());
            if (channel == null) {
                logger.warn("Cannot update giveaway embed for completion {}: Discord channel {} not accessible or JDA not ready", 
                           giveaway.getId(), giveaway.getChannelId());
                return;
            }

            // Create completion embed
            MessageEmbed completionEmbed = createCompletionEmbed(giveaway, winners);
            
            // Update the embed and remove the button
            channel.editMessageEmbedsById(giveaway.getMessageId(), completionEmbed)
                .setComponents() // Remove all action rows (buttons)
                .queue(
                    success -> logger.info("Successfully updated giveaway embed to completion state for giveaway {} in channel {}", 
                                          giveaway.getId(), giveaway.getChannelId()),
                    error -> logger.error("Failed to update giveaway embed for completion of giveaway {} in channel {}: {}", 
                                         giveaway.getId(), giveaway.getChannelId(), error.getMessage())
                );

        } catch (Exception e) {
            logger.error("Error updating giveaway embed for completion of giveaway {}: {}", giveaway.getId(), e.getMessage(), e);
        }
    }

    /**
     * Create a completion embed for a finished giveaway
     * @param giveaway The completed giveaway
     * @param winners List of winning entries
     * @return MessageEmbed formatted for completion state
     */
    private MessageEmbed createCompletionEmbed(Giveaway giveaway, List<GiveawayEntry> winners) {
        EmbedBuilder embedBuilder = new EmbedBuilder();
        
        // Set title to the prize (same as original)
        embedBuilder.setTitle(giveaway.getPrize());
        
        // Build description for completion state
        StringBuilder description = new StringBuilder();
        
        // Calculate and format time elapsed
        String timeElapsed = calculateTimeElapsed(giveaway.getEndDate(), giveaway.getCompletedAt());
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' hh:mm a");
        description.append("**Ended:** ").append(timeElapsed).append(" (").append(giveaway.getCompletedAt().format(formatter)).append(")\n");
        description.append("**Hosted by:** <@").append(giveaway.getHostUserId()).append(">\n");
        
        // Get total entries for completion display
        long totalEntries = getTotalEntries(giveaway);
        description.append("**Entries:** ").append(totalEntries).append("\n");
        
        // Format winners
        if (!winners.isEmpty()) {
            String winnersFormatted = formatWinnersForEmbed(winners);
            description.append("**Winners:** ").append(winnersFormatted);
        } else {
            description.append("**Winners:** No winners (no entries)");
        }
        
        embedBuilder.setDescription(description.toString());
        embedBuilder.setColor(Color.decode("#58b9ff")); // Preserve existing blue color
        
        return embedBuilder.build();
    }

    /**
     * Calculate time elapsed between giveaway end date and completion
     * @param endDate The giveaway's scheduled end date
     * @param completedAt The actual completion time
     * @return Formatted string showing days elapsed or same day completion
     */
    private String calculateTimeElapsed(LocalDateTime endDate, LocalDateTime completedAt) {
        if (endDate == null || completedAt == null) {
            return "0 days ago";
        }
        
        // Calculate the difference between completion and end date
        long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(endDate.toLocalDate(), completedAt.toLocalDate());
        
        if (daysBetween == 0) {
            return "0 days ago";
        } else if (daysBetween == 1) {
            return "1 day ago";
        } else {
            return daysBetween + " days ago";
        }
    }

    /**
     * Format winners for embed display using Discord mentions
     * @param winners List of winning entries
     * @return Formatted string with Discord mentions
     */
    private String formatWinnersForEmbed(List<GiveawayEntry> winners) {
        if (winners.isEmpty()) {
            return "No winners";
        }
        
        return winners.stream()
            .map(winner -> "<@" + winner.getUserId() + ">")
            .collect(Collectors.joining(" "));
    }
} 