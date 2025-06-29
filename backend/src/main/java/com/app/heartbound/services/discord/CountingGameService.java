package com.app.heartbound.services.discord;

import com.app.heartbound.entities.CountingGameState;
import com.app.heartbound.entities.CountingUserData;
import com.app.heartbound.entities.User;
import com.app.heartbound.repositories.CountingGameStateRepository;
import com.app.heartbound.repositories.CountingUserDataRepository;
import com.app.heartbound.services.UserService;
import com.app.heartbound.config.CacheConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final CacheConfig cacheConfig;
    private final DiscordService discordService;
    
    public CountingGameService(
            CountingGameStateRepository gameStateRepository,
            CountingUserDataRepository userDataRepository,
            UserService userService,
            CacheConfig cacheConfig,
            @Lazy DiscordService discordService) {
        this.gameStateRepository = gameStateRepository;
        this.userDataRepository = userDataRepository;
        this.userService = userService;
        this.cacheConfig = cacheConfig;
        this.discordService = discordService;
    }
    
    private String discordServerId;
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
                    .build();
            gameStateRepository.save(initialState);
            log.info("Initialized counting game state");
        }
    }
    
    /**
     * Update settings from DiscordBotSettings
     */
    public void updateSettings(String discordServerId, String countingChannelId, String timeoutRoleId, 
                              Integer creditsPerCount, Integer countingLives, boolean countingGameEnabled) {
        this.discordServerId = discordServerId;
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
     * Process a counting attempt and return the result
     */
    @Transactional
    public CountingResult processCount(String userId, int attemptedNumber) {
        if (!isGameActive()) {
            return CountingResult.GAME_DISABLED;
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
        return handleCorrectCount(userId, userData, gameState, attemptedNumber);
    }
    
    @Transactional
    protected CountingResult handleCorrectCount(String userId, CountingUserData userData, 
                                               CountingGameState gameState, int number) {
        // Update game state
        gameState.setCurrentCount(number);
        gameState.setLastUserId(userId);
        if (number > gameState.getHighestCount()) {
            gameState.setHighestCount(number);
        }
        gameStateRepository.save(gameState);
        
        // Update user stats
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
        // Update user stats
        userData.setTotalMistakes(userData.getTotalMistakes() + 1);
        userData.setLivesRemaining(userData.getLivesRemaining() - 1);
        
        // Reset game state
        int currentCount = gameState.getCurrentCount();
        gameState.setCurrentCount(0);
        gameState.setLastUserId(null);
        gameState.setTotalResets(gameState.getTotalResets() + 1);
        gameStateRepository.save(gameState);
        
        // Check if user ran out of lives BEFORE applying timeout
        boolean willBeTimedOut = userData.getLivesRemaining() <= 0;
        int timeoutHours = 0;
        
        if (willBeTimedOut) {
            // Calculate timeout hours before applying timeout (which will increment timeout level)
            timeoutHours = (userData.getTimeoutLevel() + 1) * 24;
            applyTimeout(userData);
        }
        
        userDataRepository.save(userData);
        
        // Invalidate cache
        cacheConfig.invalidateCountingGameCache();
        
        log.info("User {} made mistake at count {} (attempted {}), type: {}, lives remaining: {}", 
                userData.getUserId(), currentCount, attemptedNumber, mistakeType, userData.getLivesRemaining());
        
        // Determine what to show based on timeout status
        int livesToShow = willBeTimedOut ? 0 : userData.getLivesRemaining();
        
        return mistakeType.withMistakeData(currentCount, livesToShow, timeoutHours);
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
        try {
            User user = userService.getUserById(userId);
            if (user != null) {
                user.setCredits((user.getCredits() != null ? user.getCredits() : 0) + credits);
                userService.updateUser(user);
                log.debug("Awarded {} credits to user {}", credits, userId);
            }
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
     * Result class for counting attempts
     */
    public static class CountingResult {
        public static final CountingResult CORRECT = new CountingResult(Type.CORRECT, null, null, null, null);
        public static final CountingResult GAME_DISABLED = new CountingResult(Type.GAME_DISABLED, null, null, null, null);
        public static final CountingResult USER_TIMED_OUT = new CountingResult(Type.USER_TIMED_OUT, null, null, null, null);
        public static final CountingResult WRONG_NUMBER = new CountingResult(Type.WRONG_NUMBER, null, null, null, null);
        public static final CountingResult CONSECUTIVE_COUNT = new CountingResult(Type.CONSECUTIVE_COUNT, null, null, null, null);
        public static final CountingResult WRONG_NUMBER_WARNING = new CountingResult(Type.WRONG_NUMBER_WARNING, null, null, null, null);
        
        public enum Type {
            CORRECT, GAME_DISABLED, USER_TIMED_OUT, WRONG_NUMBER, CONSECUTIVE_COUNT, WRONG_NUMBER_WARNING
        }
        
        private final Type type;
        private final Integer currentCount;
        private final Integer expectedNumber;
        private final Integer livesRemaining;
        private final Integer timeoutHours;
        
        private CountingResult(Type type, Integer currentCount, Integer expectedNumber, Integer livesRemaining, Integer timeoutHours) {
            this.type = type;
            this.currentCount = currentCount;
            this.expectedNumber = expectedNumber;
            this.livesRemaining = livesRemaining;
            this.timeoutHours = timeoutHours;
        }
        
        public CountingResult withMistakeData(int currentCount, int livesRemaining, int timeoutHours) {
            return new CountingResult(this.type, currentCount, currentCount + 1, livesRemaining, timeoutHours);
        }
        
        public CountingResult withWarningData(int expectedNumber) {
            return new CountingResult(this.type, 0, expectedNumber, null, null);
        }
        
        public Type getType() { return type; }
        public Integer getCurrentCount() { return currentCount; }
        public Integer getExpectedNumber() { return expectedNumber; }
        public Integer getLivesRemaining() { return livesRemaining; }
        public Integer getTimeoutHours() { return timeoutHours; }
        
        public boolean isSuccess() { return type == Type.CORRECT; }
        public boolean isMistake() { return type == Type.WRONG_NUMBER || type == Type.CONSECUTIVE_COUNT; }
        public boolean isTimedOut() { return timeoutHours != null && timeoutHours > 0; }
    }
} 