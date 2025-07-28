package com.app.heartbound.services.discord;

import com.app.heartbound.entities.CountingGameState;
import com.app.heartbound.entities.CountingUserData;
import com.app.heartbound.entities.User;
import com.app.heartbound.repositories.CountingGameStateRepository;
import com.app.heartbound.repositories.CountingUserDataRepository;
import com.app.heartbound.services.UserService;
import com.app.heartbound.config.CacheConfig;
import com.app.heartbound.services.AuditService;
import com.app.heartbound.dto.CreateAuditDTO;
import com.app.heartbound.enums.AuditSeverity;
import com.app.heartbound.enums.AuditCategory;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class CountingGameService {
    
    private final CountingGameStateRepository gameStateRepository;
    private final CountingUserDataRepository userDataRepository;
    private final UserService userService;
    private final AuditService auditService;
    private final CacheConfig cacheConfig;
    private final DiscordService discordService;
    
    public CountingGameService(
            CountingGameStateRepository gameStateRepository,
            CountingUserDataRepository userDataRepository,
            UserService userService,
            CacheConfig cacheConfig,
            @Lazy DiscordService discordService,
            AuditService auditService) {
        this.gameStateRepository = gameStateRepository;
        this.userDataRepository = userDataRepository;
        this.userService = userService;
        this.cacheConfig = cacheConfig;
        this.discordService = discordService;
        this.auditService = auditService;
        log.info("CountingGameService initialized with audit service");
    }
    
    private String countingChannelId;
    private String timeoutRoleId;
    private Integer creditsPerCount;
    private Integer countingLives;
    private boolean countingGameEnabled;
    
    private ScheduledExecutorService timeoutScheduler;
    
    @PostConstruct
    public void init() {
        // Initialize timeout scheduler for removing timeout roles
        timeoutScheduler = Executors.newSingleThreadScheduledExecutor();
        timeoutScheduler.scheduleAtFixedRate(this::processExpiredTimeouts, 
                1, 1, TimeUnit.MINUTES); // Check every minute
        
        // Initialize game state if it doesn't exist
        initializeGameState();
        
        log.info("Counting game service initialized with timeout scheduler");
    }
    
    @PreDestroy
    public void shutdown() {
        if (timeoutScheduler != null) {
            timeoutScheduler.shutdown();
            try {
                if (!timeoutScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    timeoutScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                timeoutScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        log.info("Counting game service shutdown completed");
    }
    
    @Transactional
    public void initializeGameState() {
        if (gameStateRepository.count() == 0) {
            CountingGameState initialState = CountingGameState.builder()
                    .id(1L)
                    .currentCount(0)
                    .lastUserId(null)
                    .totalResets(0L)
                    .highestCount(0)
                    .saveCost(200)
                    .restartDelayUntil(null)
                    .lastFailedCount(null)
                    .build();
            gameStateRepository.save(initialState);
            log.info("Initialized counting game state");
        }
    }
    
    /**
     * Update settings from DiscordBotSettings
     */
    public void updateSettings(String countingChannelId, String timeoutRoleId, 
                              Integer creditsPerCount, Integer countingLives, boolean countingGameEnabled) {
        this.countingChannelId = countingChannelId;
        this.timeoutRoleId = timeoutRoleId;
        this.creditsPerCount = creditsPerCount;
        this.countingLives = countingLives;
        this.countingGameEnabled = countingGameEnabled;
        
        log.info("Updated counting game settings - enabled: {}, channel: {}, timeout role: {}, credits: {}, lives: {}", 
                countingGameEnabled, countingChannelId, timeoutRoleId, creditsPerCount, countingLives);
    }
    
    /**
     * Check if counting game is enabled and properly configured
     */
    public boolean isGameActive() {
        return countingGameEnabled && countingChannelId != null && !countingChannelId.isEmpty();
    }
    
    /**
     * Check if the given channel is the counting channel
     */
    public boolean isCountingChannel(String channelId) {
        return isGameActive() && countingChannelId.equals(channelId);
    }
    
    /**
     * Get current game state from cache or database
     */
    public CountingGameState getGameState() {
        return (CountingGameState) cacheConfig.getCountingGameCache()
                .get("game_state", key -> gameStateRepository.findById(1L)
                        .orElseGet(() -> {
                            initializeGameState();
                            return gameStateRepository.findById(1L).orElseThrow();
                        }));
    }
    
    /**
     * Get or create user data
     */
    public CountingUserData getUserData(String userId) {
        return userDataRepository.findById(userId)
                .orElseGet(() -> {
                    CountingUserData newUserData = CountingUserData.builder()
                            .userId(userId)
                            .livesRemaining(countingLives != null ? countingLives : 3)
                            .timeoutLevel(0)
                            .timeoutExpiry(null)
                            .totalCorrectCounts(0L)
                            .totalMistakes(0L)
                            .bestCount(0)
                            .build();
                    return userDataRepository.save(newUserData);
                });
    }
    
    /**
     * Check if counting is currently delayed after a failure
     */
    public boolean isRestartDelayed() {
        CountingGameState gameState = getGameState();
        return gameState.getRestartDelayUntil() != null && 
               LocalDateTime.now().isBefore(gameState.getRestartDelayUntil());
    }

    /**
     * Check if a user is currently timed out
     */
    public boolean isUserTimedOut(String userId) {
        try {
            return userDataRepository.isUserTimedOut(userId, LocalDateTime.now());
        } catch (Exception e) {
            log.debug("Error checking timeout status for user {}: {}", userId, e.getMessage());
            return false;
        }
    }

    /**
     * Get remaining restart delay in seconds
     */
    public long getRestartDelaySeconds() {
        CountingGameState gameState = getGameState();
        if (gameState.getRestartDelayUntil() == null) {
            return 0;
        }
        
        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(gameState.getRestartDelayUntil())) {
            return 0;
        }
        
        return ChronoUnit.SECONDS.between(now, gameState.getRestartDelayUntil());
    }
    
    /**
     * Process a counting attempt and return the result
     */
    @Transactional
    public CountingResult processCount(String userId, int attemptedNumber, String messageId) {
        if (!isGameActive()) {
            return CountingResult.GAME_DISABLED;
        }
        
        // Check if user exists in database - EARLY validation to prevent non-database users from participating
        User user;
        try {
            user = userService.getUserById(userId);
            if (user == null) {
                log.debug("User {} not found in database, denying participation", userId);
                return CountingResult.USER_NOT_FOUND;
            }
        } catch (Exception e) {
            log.debug("User {} not found in database, denying participation: {}", userId, e.getMessage());
            return CountingResult.USER_NOT_FOUND;
        }
        
        // Check if restart is delayed
        if (isRestartDelayed()) {
            long remainingSeconds = getRestartDelaySeconds();
            return CountingResult.RESTART_DELAYED.withDelayData(remainingSeconds);
        }
        
        // Check if user is timed out
        if (userDataRepository.isUserTimedOut(userId, LocalDateTime.now())) {
            return CountingResult.USER_TIMED_OUT;
        }
        
        CountingGameState gameState = getGameState();
        CountingUserData userData = getUserData(userId);
        
        // Check if user is trying to count twice in a row
        if (userId.equals(gameState.getLastUserId())) {
            return handleMistake(userData, gameState, attemptedNumber, CountingResult.CONSECUTIVE_COUNT);
        }
        
        // Check if number is correct
        int expectedNumber = gameState.getCurrentCount() + 1;
        if (attemptedNumber != expectedNumber) {
            // Special case: if count is 0 and next should be 1, just give a warning instead of penalty
            if (gameState.getCurrentCount() == 0 && expectedNumber == 1) {
                return CountingResult.WRONG_NUMBER_WARNING.withWarningData(expectedNumber);
            }
            return handleMistake(userData, gameState, attemptedNumber, CountingResult.WRONG_NUMBER);
        }
        
        // Correct count!
        return handleCorrectCount(userId, userData, gameState, attemptedNumber, messageId);
    }
    
    @Transactional
    protected CountingResult handleCorrectCount(String userId, CountingUserData userData, 
                                               CountingGameState gameState, int number, String messageId) {
        // If the count is starting over (i.e., at 1) after a failure (indicated by a non-null lastFailedCount),
        // it means the opportunity to save the count was missed. Reset the save cost for the next cycle.
        if (number == 1 && gameState.getLastFailedCount() != null) {
            gameState.setSaveCost(200);
            log.info("Counting game save cost reset to 200. A new count has started, and the previous failed count was not saved.");
        }
        
        // Update game state
        gameState.setCurrentCount(number);
        gameState.setLastUserId(userId);
        gameState.setLastCorrectMessageId(messageId); // Store the message ID
        if (number > gameState.getHighestCount()) {
            gameState.setHighestCount(number);
        }
        
        gameState.setLastFailedCount(null);
        gameStateRepository.save(gameState);
        
        // Update user stats (all users reaching this point are database users)
        userData.setTotalCorrectCounts(userData.getTotalCorrectCounts() + 1);
        if (number > userData.getBestCount()) {
            userData.setBestCount(number);
        }
        userDataRepository.save(userData);
        
        // Award credits
        awardCredits(userId, creditsPerCount != null ? creditsPerCount : 1);
        
        // Invalidate cache
        cacheConfig.invalidateCountingGameCache();
        
        log.debug("User {} successfully counted {}", userId, number);
        return CountingResult.CORRECT;
    }
    
    @Transactional
    protected CountingResult handleMistake(CountingUserData userData, CountingGameState gameState, 
                                          int attemptedNumber, CountingResult mistakeType) {
        // Anti-griefing check
        // We add 1 to totalMistakes to include the current mistake in the calculation.
        long totalAttempts = userData.getTotalCorrectCounts() + userData.getTotalMistakes() + 1;
        double successRate = (double) userData.getTotalCorrectCounts() / totalAttempts;

        boolean isGriefer = totalAttempts >= 5 && userData.getBestCount() < 10 && successRate < 0.25;

        if (isGriefer) {
            int failedCount = gameState.getCurrentCount();
            log.warn("Griefer detected: user={}, failed at count {}. Criteria: attempts={}, best={}, rate={}",
                userData.getUserId(), failedCount, totalAttempts, userData.getBestCount(), successRate);

            // Immediately apply timeout
            applyTimeout(userData);

            // Update user stats for the mistake
            userData.setTotalMistakes(userData.getTotalMistakes() + 1);
            userDataRepository.save(userData);

            // Restore game state, but do not reset count
            gameState.setCurrentCount(failedCount);
            gameState.setLastUserId(null); // Allow anyone to continue
            // Ensure no restart delay or save opportunity is created
            gameState.setRestartDelayUntil(null);
            gameState.setLastFailedCount(null);
            gameStateRepository.save(gameState);

            // Invalidate cache
            cacheConfig.invalidateCountingGameCache();
            
            return CountingResult.GRIEFER_PUNISHED.withGrieferData(failedCount);
        }

        // Store the count that failed for save feature
        int failedCount = gameState.getCurrentCount();
        
        // Set 5-second restart delay and remember the failed count
        gameState.setRestartDelayUntil(LocalDateTime.now().plusSeconds(5));
        gameState.setLastFailedCount(failedCount);
        
        // Update user stats (all users reaching this point are database users)
        userData.setTotalMistakes(userData.getTotalMistakes() + 1);
        userData.setLivesRemaining(userData.getLivesRemaining() - 1);
        
        // Reset game state
        gameState.setCurrentCount(0);
        gameState.setLastUserId(null);
        gameState.setTotalResets(gameState.getTotalResets() + 1);
        gameStateRepository.save(gameState);
        
        // Handle timeout for database users
        boolean willBeTimedOut = userData.getLivesRemaining() <= 0;
        int timeoutHours = 0;
        int livesToShow = 0;
        
        if (willBeTimedOut) {
            // Calculate timeout hours before applying timeout (which will increment timeout level)
            timeoutHours = (userData.getTimeoutLevel() + 1) * 24;
            applyTimeout(userData);
            livesToShow = 0;
        } else {
            livesToShow = userData.getLivesRemaining();
        }
        
        userDataRepository.save(userData);
        
        // Invalidate cache
        cacheConfig.invalidateCountingGameCache();
        
        log.info("User {} made mistake at count {} (attempted {}), type: {}, lives remaining: {}", 
                userData.getUserId(), failedCount, attemptedNumber, mistakeType, livesToShow);
        
        return mistakeType.withMistakeData(failedCount, livesToShow, timeoutHours, gameState.getSaveCost());
    }
    
    protected void applyTimeout(CountingUserData userData) {
        userData.setTimeoutLevel(userData.getTimeoutLevel() + 1);
        
        // Progressive timeout: 24h, 48h, 72h, etc.
        int timeoutHours = userData.getTimeoutLevel() * 24;
        LocalDateTime timeoutExpiry = LocalDateTime.now().plusHours(timeoutHours);
        userData.setTimeoutExpiry(timeoutExpiry);
        
        // Reset lives for next time
        userData.setLivesRemaining(countingLives != null ? countingLives : 3);
        
        // Apply Discord timeout role if available
        applyDiscordTimeoutRole(userData.getUserId());
        
        log.info("Applied {}h timeout to user {} (timeout level {})", 
                timeoutHours, userData.getUserId(), userData.getTimeoutLevel());
    }
    
    protected void awardCredits(String userId, int credits) {
        if (credits <= 0) return;
        try {
            boolean success = userService.updateCreditsAtomic(userId, credits);
            if (!success) {
                log.error("Failed to award {} credits to user {} in counting game.", credits, userId);
                return;
            }
            
            // For audit log, we need the new balance.
            User user = userService.getUserById(userId);
            int newBalance = (user != null && user.getCredits() != null) ? user.getCredits() : 0;
            
            // Create audit entry for counting credits
            try {
                CreateAuditDTO auditEntry = CreateAuditDTO.builder()
                    .userId(userId)
                    .action("COUNTING_REWARD")
                    .entityType("USER_CREDITS")
                    .entityId(userId)
                    .description(String.format("Earned %d credits for correct counting", credits))
                    .severity(AuditSeverity.INFO)
                    .category(AuditCategory.FINANCIAL)
                    .details(String.format("{\"game\":\"counting\",\"creditsAwarded\":%d,\"newBalance\":%d}", 
                        credits, newBalance))
                    .source("DISCORD_BOT")
                    .build();
                
                auditService.createSystemAuditEntry(auditEntry);
            } catch (Exception e) {
                log.error("Failed to create audit entry for counting reward for user {}: {}", userId, e.getMessage());
            }
            
            log.debug("Awarded {} credits to user {}", credits, userId);
        } catch (Exception e) {
            log.error("Failed to award credits to user {}: {}", userId, e.getMessage());
        }
    }
    
    protected void applyDiscordTimeoutRole(String userId) {
        if (timeoutRoleId == null || timeoutRoleId.isEmpty()) {
            log.debug("Cannot apply Discord timeout role - timeout role ID not configured");
            return;
        }
        
        try {
            boolean success = discordService.grantRole(userId, timeoutRoleId);
            if (success) {
                log.info("Applied timeout role to user {}", userId);
            } else {
                log.warn("Failed to apply timeout role to user {}", userId);
            }
        } catch (Exception e) {
            log.error("Error applying Discord timeout role to user {}: {}", userId, e.getMessage());
        }
    }
    
    protected void removeDiscordTimeoutRole(String userId) {
        if (timeoutRoleId == null || timeoutRoleId.isEmpty()) {
            return;
        }
        
        try {
            boolean success = discordService.removeRole(userId, timeoutRoleId);
            if (success) {
                log.info("Removed timeout role from user {}", userId);
            } else {
                log.warn("Failed to remove timeout role from user {}", userId);
            }
        } catch (Exception e) {
            log.error("Error removing Discord timeout role from user {}: {}", userId, e.getMessage());
        }
    }
    
    /**
     * Scheduled task to process expired timeouts
     */
    @Scheduled(fixedRate = 60000) // Every minute
    @Transactional
    public void processExpiredTimeouts() {
        try {
            List<CountingUserData> expiredTimeouts = userDataRepository.findExpiredTimeouts(LocalDateTime.now());
            
            for (CountingUserData userData : expiredTimeouts) {
                // Remove Discord timeout role
                removeDiscordTimeoutRole(userData.getUserId());
                
                // Clear timeout expiry
                userData.setTimeoutExpiry(null);
                userDataRepository.save(userData);
                
                log.info("Processed expired timeout for user {}", userData.getUserId());
            }
            
            if (!expiredTimeouts.isEmpty()) {
                log.info("Processed {} expired timeouts", expiredTimeouts.size());
            }
        } catch (Exception e) {
            log.error("Error processing expired timeouts: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Handle the deletion of a message in the counting channel.
     * This is the core of the anti-griefing feature.
     */
    @Transactional
    public void handleMessageDeletion(String channelId, String deletedMessageId) {
        if (!isCountingChannel(channelId)) {
            return; // Not our channel
        }

        CountingGameState gameState = getGameState();

        // Check if the deleted message was the last correct one
        if (gameState.getLastCorrectMessageId() != null && gameState.getLastCorrectMessageId().equals(deletedMessageId)) {
            log.info("Detected deletion of last correct count message (ID: {}). Announcing next number.", deletedMessageId);

            int nextNumber = gameState.getCurrentCount() + 1;
            String announcement = "The previous count was deleted. The next number is **" + nextNumber + "**.";

            // Announce the next number in the channel
            try {
                JDA jda = discordService.getJDA();
                TextChannel channel = jda.getTextChannelById(countingChannelId);
                if (channel != null) {
                    channel.sendMessage(announcement).queue();
                } else {
                    log.error("Could not find counting channel with ID: {}", countingChannelId);
                }
            } catch (Exception e) {
                log.error("Failed to send deletion announcement to channel {}: {}", countingChannelId, e.getMessage());
            }

            // Nullify the message ID to prevent re-triggering
            gameState.setLastCorrectMessageId(null);
            gameStateRepository.save(gameState);
            
            // Invalidate cache to reflect the change
            cacheConfig.invalidateCountingGameCache();
        }
    }

    /**
     * Get all currently timed out users with their details
     */
    @Transactional(readOnly = true)
    public List<com.app.heartbound.dto.discord.TimedOutUserDTO> getTimedOutUsers() {
        List<CountingUserData> timedOutUsers = userDataRepository.findActiveTimeouts(LocalDateTime.now());
        
        List<com.app.heartbound.dto.discord.TimedOutUserDTO> result = new ArrayList<>();
        
        for (CountingUserData userData : timedOutUsers) {
            try {
                // Get user details from UserService
                User user = userService.getUserById(userData.getUserId());
                String username = user != null ? user.getUsername() : "Unknown User";
                String avatar = user != null ? user.getAvatar() : "";
                
                // Calculate remaining timeout duration
                LocalDateTime now = LocalDateTime.now();
                long hoursRemaining = ChronoUnit.HOURS.between(now, userData.getTimeoutExpiry());
                String timeoutDuration = formatDuration(hoursRemaining);
                
                com.app.heartbound.dto.discord.TimedOutUserDTO dto = com.app.heartbound.dto.discord.TimedOutUserDTO.builder()
                        .userId(userData.getUserId())
                        .username(username)
                        .avatar(avatar)
                        .timeoutLevel(userData.getTimeoutLevel())
                        .timeoutExpiry(userData.getTimeoutExpiry())
                        .livesRemaining(userData.getLivesRemaining())
                        .totalCorrectCounts(userData.getTotalCorrectCounts())
                        .totalMistakes(userData.getTotalMistakes())
                        .bestCount(userData.getBestCount())
                        .timeoutHoursRemaining(hoursRemaining)
                        .timeoutDuration(timeoutDuration)
                        .build();
                
                result.add(dto);
            } catch (Exception e) {
                log.warn("Error processing timed out user {}: {}", userData.getUserId(), e.getMessage());
            }
        }
        
        // Sort by timeout expiry (soonest first)
        result.sort((a, b) -> a.getTimeoutExpiry().compareTo(b.getTimeoutExpiry()));
        
        return result;
    }
    
    /**
     * Remove timeout for a specific user (admin action)
     */
    @Transactional
    public boolean removeUserTimeout(String userId) {
        try {
            CountingUserData userData = userDataRepository.findById(userId).orElse(null);
            
            if (userData == null || userData.getTimeoutExpiry() == null) {
                log.warn("User {} is not currently timed out", userId);
                return false;
            }
            
            // Remove Discord timeout role
            removeDiscordTimeoutRole(userId);
            
            // Clear timeout expiry
            userData.setTimeoutExpiry(null);
            userDataRepository.save(userData);
            
            log.info("Admin removed timeout for user {}", userId);
            return true;
        } catch (Exception e) {
            log.error("Error removing timeout for user {}: {}", userId, e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Format duration in hours to human-readable string
     */
    private String formatDuration(long hours) {
        if (hours <= 0) {
            return "Expired";
        }
        
        long days = hours / 24;
        long remainingHours = hours % 24;
        
        if (days > 0) {
            if (remainingHours > 0) {
                return String.format("%d day%s, %d hour%s", 
                        days, days == 1 ? "" : "s", 
                        remainingHours, remainingHours == 1 ? "" : "s");
            } else {
                return String.format("%d day%s", days, days == 1 ? "" : "s");
            }
        } else {
            return String.format("%d hour%s", hours, hours == 1 ? "" : "s");
        }
    }
    
    /**
     * Check if a string represents a valid counting number
     */
    public static boolean isValidNumber(String content) {
        if (content == null || content.trim().isEmpty()) {
            return false;
        }
        
        try {
            int number = Integer.parseInt(content.trim());
            return number > 0; // Only positive numbers allowed
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    /**
     * Save the current count for the specified cost
     */
    @Transactional
    public SaveCountResult saveCount(String userId) {
        if (!isGameActive()) {
            return SaveCountResult.GAME_DISABLED;
        }
        
        // Check if user exists in database
        User user;
        try {
            user = userService.getUserById(userId);
            if (user == null) {
                return SaveCountResult.USER_NOT_FOUND;
            }
        } catch (Exception e) {
            log.error("Error checking user existence for save count: {}", e.getMessage(), e);
            return SaveCountResult.USER_NOT_FOUND;
        }
        
        CountingGameState gameState = getGameState();
        
        // Check if there's actually something to save (must be at count 0 due to recent failure)
        if (gameState.getCurrentCount() != 0) {
            return SaveCountResult.NOTHING_TO_SAVE;
        }
        
        // Check if there's a failed count to restore (this persists even after restart delay expires)
        if (gameState.getLastFailedCount() == null) {
            return SaveCountResult.NO_RECENT_FAILURE;
        }
        
        int saveCost = gameState.getSaveCost();
        
        // Atomically deduct credits for the save cost
        boolean success = userService.updateCreditsAtomic(userId, -saveCost);

        if (!success) {
            // This can happen if the user doesn't have enough credits.
            // Refetch user to get current balance for the error message.
            User latestUser = userService.getUserById(userId);
            int currentCredits = (latestUser != null && latestUser.getCredits() != null) ? latestUser.getCredits() : 0;
            return SaveCountResult.INSUFFICIENT_CREDITS.withCreditData(currentCredits, saveCost);
        }
        
        // Get the count that was lost
        int savedCount = gameState.getLastFailedCount();

        // Fetch user again to get the new balance for logging and auditing
        User updatedUser = userService.getUserById(userId);
        int newBalance = (updatedUser != null && updatedUser.getCredits() != null) ? updatedUser.getCredits() : 0;
        
        // Create audit entry for save cost
        try {
            CreateAuditDTO auditEntry = CreateAuditDTO.builder()
                .userId(userId)
                .action("COUNTING_SAVE_COST")
                .entityType("USER_CREDITS")
                .entityId(userId)
                .description(String.format("Paid %d credits to save counting progress at %d", savedCount, savedCount))
                .severity(AuditSeverity.INFO)
                .category(AuditCategory.FINANCIAL)
                .details(String.format("{\"game\":\"counting\",\"saveCount\":%d,\"costPaid\":%d,\"newBalance\":%d}", 
                    savedCount, saveCost, newBalance))
                .source("DISCORD_BOT")
                .build();
            
            auditService.createSystemAuditEntry(auditEntry);
        } catch (Exception e) {
            log.error("Failed to create audit entry for counting save cost for user {}: {}", userId, e.getMessage());
        }
        
        // Restore the count
        gameState.setCurrentCount(savedCount);
        gameState.setLastUserId(null); // Allow any user to continue
        
        // Double the save cost for next time
        gameState.setSaveCost(saveCost * 2);
        
        // Clear restart delay and failed count
        gameState.setRestartDelayUntil(null);
        gameState.setLastFailedCount(null);
        
        gameStateRepository.save(gameState);
        
        // Invalidate cache
        cacheConfig.invalidateCountingGameCache();
        
        log.info("User {} saved count at {} for {} credits (new save cost: {})", 
                userId, savedCount, saveCost, gameState.getSaveCost());
        
        return SaveCountResult.SUCCESS.withSaveData(savedCount, saveCost, newBalance);
    }
    
    /**
     * Result class for counting attempts
     */
    public static class CountingResult {
        public static final CountingResult CORRECT = new CountingResult(Type.CORRECT, null, null, null, null, null, null);
        public static final CountingResult GAME_DISABLED = new CountingResult(Type.GAME_DISABLED, null, null, null, null, null, null);
        public static final CountingResult USER_TIMED_OUT = new CountingResult(Type.USER_TIMED_OUT, null, null, null, null, null, null);
        public static final CountingResult USER_NOT_FOUND = new CountingResult(Type.USER_NOT_FOUND, null, null, null, null, null, null);
        public static final CountingResult WRONG_NUMBER = new CountingResult(Type.WRONG_NUMBER, null, null, null, null, null, null);
        public static final CountingResult CONSECUTIVE_COUNT = new CountingResult(Type.CONSECUTIVE_COUNT, null, null, null, null, null, null);
        public static final CountingResult WRONG_NUMBER_WARNING = new CountingResult(Type.WRONG_NUMBER_WARNING, null, null, null, null, null, null);
        public static final CountingResult RESTART_DELAYED = new CountingResult(Type.RESTART_DELAYED, null, null, null, null, null, null);
        public static final CountingResult GRIEFER_PUNISHED = new CountingResult(Type.GRIEFER_PUNISHED, null, null, null, null, null, null);
        
        public enum Type {
            CORRECT, GAME_DISABLED, USER_TIMED_OUT, USER_NOT_FOUND, WRONG_NUMBER, CONSECUTIVE_COUNT, WRONG_NUMBER_WARNING, RESTART_DELAYED, GRIEFER_PUNISHED
        }
        
        private final Type type;
        private final Integer currentCount;
        private final Integer expectedNumber;
        private final Integer livesRemaining;
        private final Integer timeoutHours;
        private final Integer saveCost;
        private final Long delaySeconds;
        
        private CountingResult(Type type, Integer currentCount, Integer expectedNumber, Integer livesRemaining, Integer timeoutHours, Integer saveCost, Long delaySeconds) {
            this.type = type;
            this.currentCount = currentCount;
            this.expectedNumber = expectedNumber;
            this.livesRemaining = livesRemaining;
            this.timeoutHours = timeoutHours;
            this.saveCost = saveCost;
            this.delaySeconds = delaySeconds;
        }
        
        public CountingResult withMistakeData(int currentCount, int livesRemaining, int timeoutHours, int saveCost) {
            return new CountingResult(this.type, currentCount, currentCount + 1, livesRemaining, timeoutHours, saveCost, null);
        }
        
        public CountingResult withGrieferData(int restoredCount) {
            return new CountingResult(this.type, restoredCount, restoredCount + 1, null, null, null, null);
        }
        
        public CountingResult withWarningData(int expectedNumber) {
            return new CountingResult(this.type, 0, expectedNumber, null, null, null, null);
        }
        
        public CountingResult withDelayData(long delaySeconds) {
            return new CountingResult(this.type, null, null, null, null, null, delaySeconds);
        }
        
        public Type getType() { return type; }
        public Integer getCurrentCount() { return currentCount; }
        public Integer getExpectedNumber() { return expectedNumber; }
        public Integer getLivesRemaining() { return livesRemaining; }
        public Integer getTimeoutHours() { return timeoutHours; }
        public Integer getSaveCost() { return saveCost; }
        public Long getDelaySeconds() { return delaySeconds; }
        
        public boolean isSuccess() { return type == Type.CORRECT; }
        public boolean isMistake() { return type == Type.WRONG_NUMBER || type == Type.CONSECUTIVE_COUNT; }
        public boolean isTimedOut() { return timeoutHours != null && timeoutHours > 0; }
    }
    
    /**
     * Result class for save count attempts
     */
    public static class SaveCountResult {
        public static final SaveCountResult GAME_DISABLED = new SaveCountResult(SaveType.GAME_DISABLED, null, null, null, null, null);
        public static final SaveCountResult USER_NOT_FOUND = new SaveCountResult(SaveType.USER_NOT_FOUND, null, null, null, null, null);
        public static final SaveCountResult NOTHING_TO_SAVE = new SaveCountResult(SaveType.NOTHING_TO_SAVE, null, null, null, null, null);
        public static final SaveCountResult NO_RECENT_FAILURE = new SaveCountResult(SaveType.NO_RECENT_FAILURE, null, null, null, null, null);
        public static final SaveCountResult INSUFFICIENT_CREDITS = new SaveCountResult(SaveType.INSUFFICIENT_CREDITS, null, null, null, null, null);
        public static final SaveCountResult SUCCESS = new SaveCountResult(SaveType.SUCCESS, null, null, null, null, null);
        
        public enum SaveType {
            GAME_DISABLED, USER_NOT_FOUND, NOTHING_TO_SAVE, NO_RECENT_FAILURE, INSUFFICIENT_CREDITS, SUCCESS
        }
        
        private final SaveType type;
        private final Integer savedCount;
        private final Integer costPaid;
        private final Integer remainingCredits;
        private final Integer userCredits;
        private final Integer requiredCredits;
        
        private SaveCountResult(SaveType type, Integer savedCount, Integer costPaid, Integer remainingCredits, Integer userCredits, Integer requiredCredits) {
            this.type = type;
            this.savedCount = savedCount;
            this.costPaid = costPaid;
            this.remainingCredits = remainingCredits;
            this.userCredits = userCredits;
            this.requiredCredits = requiredCredits;
        }
        
        public SaveCountResult withSaveData(int savedCount, int costPaid, int remainingCredits) {
            return new SaveCountResult(this.type, savedCount, costPaid, remainingCredits, null, null);
        }
        
        public SaveCountResult withCreditData(int userCredits, int requiredCredits) {
            return new SaveCountResult(this.type, null, null, null, userCredits, requiredCredits);
        }
        
        public SaveType getType() { return type; }
        public Integer getSavedCount() { return savedCount; }
        public Integer getCostPaid() { return costPaid; }
        public Integer getRemainingCredits() { return remainingCredits; }
        public Integer getUserCredits() { return userCredits; }
        public Integer getRequiredCredits() { return requiredCredits; }
        
        public boolean isSuccess() { return type == SaveType.SUCCESS; }
    }
} 