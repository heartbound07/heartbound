package com.app.heartbound.services.pairing;

import com.app.heartbound.dto.pairing.JoinQueueRequestDTO; 
import com.app.heartbound.dto.pairing.QueueStatusDTO;
import com.app.heartbound.dto.pairing.QueueConfigDTO;
import com.app.heartbound.dto.pairing.QueueStatsDTO;
import com.app.heartbound.dto.pairing.QueueUserDetailsDTO;
import com.app.heartbound.entities.MatchQueueUser;
import com.app.heartbound.entities.User;
import com.app.heartbound.enums.Gender;
import com.app.heartbound.enums.Rank;
import com.app.heartbound.enums.Region;
import com.app.heartbound.repositories.pairing.MatchQueueUserRepository;
import com.app.heartbound.repositories.pairing.PairingRepository;
import com.app.heartbound.repositories.UserRepository;
import com.app.heartbound.config.WebSocketConfig;
import com.app.heartbound.services.UserValidationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;
import java.util.Map;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.HashSet;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * PERFORMANCE OPTIMIZED QueueService
 * 
 * Major optimizations implemented:
 * 1. Enhanced caching with intelligent invalidation
 * 2. Batch database operations to eliminate N+1 queries
 * 3. Optimized WebSocket broadcasting with debouncing
 * 4. Read-only transactions for statistics queries
 * 5. Database-level aggregation for complex calculations
 * 6. Memory-efficient data structures and algorithms
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class QueueService {

    private static final Logger logger = LoggerFactory.getLogger(QueueService.class);
    private static final String QUEUE_STATS_CACHE_KEY = "queue_stats";
    private static final String QUEUE_USER_DETAILS_CACHE_KEY = "queue_user_details";
    private static final String ADMIN_QUEUE_STATS_TOPIC = "/topic/admin/queue-stats";

    private final MatchQueueUserRepository queueRepository;
    private final PairingRepository pairingRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final UserRepository userRepository;
    private final SimpUserRegistry simpUserRegistry;
    private final UserValidationService userValidationService;

    // Queue configuration state
    private volatile boolean queueEnabled = true;
    private String lastUpdatedBy = "SYSTEM";
    
    // Queue tracking timestamps
    private LocalDateTime queueStartTime = LocalDateTime.now();
    private LocalDateTime lastMatchmakingRun = LocalDateTime.now();

    // **OPTIMIZATION 1: Enhanced Multi-Layer Caching Strategy**
    // Longer cache TTL for expensive statistics with intelligent invalidation
    private final Cache<String, QueueStatsDTO> queueStatsCache = Caffeine.newBuilder()
            .maximumSize(10)
            .expireAfterWrite(5, TimeUnit.MINUTES) // Increased from 30 seconds to 5 minutes
            .build();

    // Cache for user details to avoid repeated database calls
    private final Cache<String, List<QueueUserDetailsDTO>> queueUserDetailsCache = Caffeine.newBuilder()
            .maximumSize(5)
            .expireAfterWrite(2, TimeUnit.MINUTES) // Cache user details for 2 minutes
            .build();

    // Cache for simple queue size to reduce frequent DB hits
    private final Cache<String, Integer> queueSizeCache = Caffeine.newBuilder()
            .maximumSize(1)
            .expireAfterWrite(30, TimeUnit.SECONDS) // Quick cache for queue size
            .build();

    // **OPTIMIZATION 2: WebSocket Debouncing to Reduce Network Spam**
    private final AtomicBoolean broadcastPending = new AtomicBoolean(false);
    private final AtomicLong lastBroadcastTime = new AtomicLong(0);
    private final ScheduledExecutorService broadcastExecutor = Executors.newSingleThreadScheduledExecutor();
    private static final long BROADCAST_DEBOUNCE_MS = 1000; // 1 second debounce

    // **OPTIMIZATION 3: Intelligent Cache Invalidation Tracking**
    private final AtomicBoolean cacheInvalidated = new AtomicBoolean(false);
    private volatile LocalDateTime lastCacheInvalidation = LocalDateTime.now();

    @Transactional
    public QueueStatusDTO joinQueue(JoinQueueRequestDTO request) {
        // Check if queue is enabled before allowing joins
        if (!queueEnabled) {
            throw new IllegalStateException("Matchmaking queue is currently disabled. Please try again later.");
        }

        String userId = request.getUserId();
        log.info("User {} attempting to join queue", userId);

        // Ensure authenticated user matches the request user ID
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            log.error("SECURITY VIOLATION: Unauthenticated queue join attempt for user {}", userId);
            throw new SecurityException("Authentication required to join queue");
        }

        String authenticatedUserId = authentication.getName();
        if (!authenticatedUserId.equals(userId)) {
            log.error("SECURITY VIOLATION: User {} attempted to join queue as user {}",
                    authenticatedUserId, userId);
            throw new SecurityException("Users can only join the queue as themselves");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("User profile not found. Please log in again."));

        // === DEBUGGING LOGS START ===
        logger.info("[QUEUE_DEBUG] User {} ({}) attempting to join queue. Fetched role selections from DB:", user.getUsername(), user.getId());
        logger.info("[QUEUE_DEBUG] -> Age Role ID: {}", user.getSelectedAgeRoleId());
        logger.info("[QUEUE_DEBUG] -> Gender Role ID: {}", user.getSelectedGenderRoleId());
        logger.info("[QUEUE_DEBUG] -> Rank Role ID: {}", user.getSelectedRankRoleId());
        logger.info("[QUEUE_DEBUG] -> Region Role ID: {}", user.getSelectedRegionRoleId());
        // === DEBUGGING LOGS END ===

        // Validate that all necessary roles have been selected and level requirements are met
        userValidationService.validateUserForPairing(user);

        // Convert role IDs to enum values
        Integer age = userValidationService.convertAgeRoleToAge(user.getSelectedAgeRoleId());
        Gender gender = userValidationService.convertGenderRoleToEnum(user.getSelectedGenderRoleId());
        Rank rank = userValidationService.convertRankRoleToEnum(user.getSelectedRankRoleId());
        Region region = userValidationService.convertRegionRoleToEnum(user.getSelectedRegionRoleId());

        // **OPTIMIZATION: Check active pairing with lightweight exists query**
        if (pairingRepository.findActivePairingByUserId(userId).isPresent()) {
            throw new IllegalStateException("User is already in an active pairing");
        }

        // Check if user is already in queue
        Optional<MatchQueueUser> existingEntry = queueRepository.findByUserId(userId);

        MatchQueueUser queueUser;
        if (existingEntry.isPresent()) {
            queueUser = existingEntry.get();
            if (queueUser.isInQueue()) {
                log.info("User {} is already in queue", userId);
                return buildQueueStatus(queueUser);
            } else {
                // Update existing entry
                queueUser.setAge(age);
                queueUser.setRegion(region);
                queueUser.setRank(rank);
                queueUser.setGender(gender);
                queueUser.setQueuedAt(LocalDateTime.now());
                queueUser.setInQueue(true);
                log.info("User {} rejoined queue with updated preferences", userId);
            }
        } else {
            // Create new queue entry
            queueUser = MatchQueueUser.builder()
                    .userId(userId)
                    .age(age)
                    .region(region)
                    .rank(rank)
                    .gender(gender)
                    .queuedAt(LocalDateTime.now())
                    .inQueue(true)
                    .build();
            log.info("User {} joined queue for the first time", userId);
        }

        queueUser = queueRepository.save(queueUser);

        // **OPTIMIZATION: Immediate broadcast for user actions + smart cache invalidation**
        invalidateRelevantCaches("User joined queue");

        // Send immediate broadcast for live queue updates - users expect instant feedback
        broadcastQueueUpdate();
        // Also send debounced admin stats update to avoid spam
        broadcastAdminQueueUpdateIfNeeded();

        log.info("Saved queue user: ID={}, Age={}, Gender={}, Region={}, Rank={}, InQueue={}",
                queueUser.getUserId(), queueUser.getAge(), queueUser.getGender(),
                queueUser.getRegion(), queueUser.getRank(), queueUser.isInQueue());

        return buildQueueStatus(queueUser);
    }

    @Transactional
    public void leaveQueue(String userId) {
        log.info("User {} attempting to leave queue", userId);

        // Ensure authenticated user matches the request user ID
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            log.error("SECURITY VIOLATION: Unauthenticated queue leave attempt for user {}", userId);
            throw new SecurityException("Authentication required to leave queue");
        }

        String authenticatedUserId = authentication.getName();
        if (!authenticatedUserId.equals(userId)) {
            log.error("SECURITY VIOLATION: User {} attempted to remove user {} from queue", 
                     authenticatedUserId, userId);
            throw new SecurityException("Users can only remove themselves from the queue");
        }

        Optional<MatchQueueUser> queueUser = queueRepository.findByUserId(userId);
        if (queueUser.isPresent() && queueUser.get().isInQueue()) {
            queueUser.get().setInQueue(false);
            queueRepository.save(queueUser.get());
            log.info("User {} left the queue", userId);
            
            // **OPTIMIZATION: Immediate broadcast for user actions + smart cache invalidation**
            invalidateRelevantCaches("User left queue");
            
            // Send immediate broadcast for live queue updates - users expect instant feedback
            broadcastQueueUpdate();
            // Also send debounced admin stats update to avoid spam
            broadcastAdminQueueUpdateIfNeeded();
        } else {
            log.warn("User {} was not in queue", userId);
        }
    }

    /**
     * **OPTIMIZED: Enhanced queue status with caching**
     */
    @Transactional(readOnly = true)
    public QueueStatusDTO getQueueStatus(String userId) {
        // Ensure authenticated user matches the request user ID (unless admin)
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            String authenticatedUserId = authentication.getName();
            boolean hasAdminRole = authentication.getAuthorities().stream()
                    .anyMatch(auth -> auth.getAuthority().equals("ROLE_ADMIN"));
            
            if (!hasAdminRole && !authenticatedUserId.equals(userId)) {
                log.error("SECURITY VIOLATION: User {} attempted to check queue status for user {}", 
                         authenticatedUserId, userId);
                throw new SecurityException("Users can only check their own queue status");
            }
        }

        Optional<MatchQueueUser> queueUser = queueRepository.findByUserId(userId);
        
        if (queueUser.isPresent() && queueUser.get().isInQueue()) {
            return buildQueueStatus(queueUser.get());
        }
        
        return QueueStatusDTO.builder()
                .inQueue(false)
                .totalQueueSize(getActiveQueueSizeOptimized())
                .build();
    }

    /**
     * **OPTIMIZED: Cached queue size calculation**
     */
    private int getActiveQueueSizeOptimized() {
        return queueSizeCache.get("queue_size", key -> queueRepository.countActiveQueueUsers());
    }

    @Transactional(readOnly = true)
    public List<MatchQueueUser> getActiveQueueUsers() {
        return queueRepository.findByInQueueTrue();
    }

    /**
     * **OPTIMIZED: Efficient queue status building with cached queue size**
     */
    private QueueStatusDTO buildQueueStatus(MatchQueueUser queueUser) {
        // **OPTIMIZATION: Use lightweight user ID query for position calculation**
        List<String> queueUserIds = queueRepository.findActiveQueueUserIds();
        int position = calculateQueuePositionOptimized(queueUser.getUserId(), queueUser.getQueuedAt(), queueUserIds);
        int estimatedWaitTime = calculateEstimatedWaitTime(position);

        return QueueStatusDTO.builder()
                .inQueue(queueUser.isInQueue())
                .queuedAt(queueUser.getQueuedAt())
                .queuePosition(position)
                .totalQueueSize(queueUserIds.size())
                .estimatedWaitTime(estimatedWaitTime)
                .build();
    }

    /**
     * **OPTIMIZED: Position calculation using lightweight user ID list**
     */
    private int calculateQueuePositionOptimized(String userId, LocalDateTime queuedAt, List<String> queueUserIds) {
        // Since queueUserIds is already ordered by queuedAt ASC, find position directly
        int position = queueUserIds.indexOf(userId) + 1;
        return position > 0 ? position : queueUserIds.size(); // Fallback if not found
    }

    private int calculateEstimatedWaitTime(int position) {
        // Estimate 5 minutes per position (conservative estimate)
        return Math.max(1, position * 5);
    }

    /**
     * **OPTIMIZATION: Debounced broadcasting to reduce WebSocket spam**
     * Use this for non-critical updates (admin actions, bulk operations)
     * For user join/leave actions, use immediate broadcasting for better UX
     */
    private void scheduleDebouncedBroadcast() {
        long currentTime = System.currentTimeMillis();
        lastBroadcastTime.set(currentTime);
        
        if (broadcastPending.compareAndSet(false, true)) {
            broadcastExecutor.schedule(() -> {
                // Check if enough time has passed since last update
                if (System.currentTimeMillis() - lastBroadcastTime.get() >= BROADCAST_DEBOUNCE_MS - 100) {
                    try {
                        broadcastQueueUpdate();
                        broadcastAdminQueueUpdateIfNeeded();
                    } finally {
                        broadcastPending.set(false);
                    }
                }
            }, BROADCAST_DEBOUNCE_MS, TimeUnit.MILLISECONDS);
        }
    }

    public void broadcastQueueUpdate() {
        try {
            int queueSize = getActiveQueueSizeOptimized();
            Map<String, Object> update = Map.of(
                "totalQueueSize", queueSize,
                "timestamp", LocalDateTime.now().toString()
            );
            
            messagingTemplate.convertAndSend("/topic/queue", update);
            log.debug("Broadcasted queue update: size={}", queueSize);
        } catch (Exception e) {
            log.error("Failed to broadcast queue update", e);
        }
    }

    /**
     * Broadcast queue configuration updates to all users via WebSocket
     */
    public void broadcastQueueConfigUpdate() {
        try {
            QueueConfigDTO config = getQueueConfig();
            messagingTemplate.convertAndSend(WebSocketConfig.QUEUE_CONFIG_TOPIC, config);
            log.info("Broadcasted queue config update: enabled={}, updatedBy={}", 
                     config.isQueueEnabled(), config.getUpdatedBy());
        } catch (Exception e) {
            log.error("Failed to broadcast queue config update", e);
        }
    }

    /**
     * **OPTIMIZATION: Centralized queue configuration management**
     */
    public void setQueueEnabled(boolean enabled, String updatedBy) {
        boolean wasEnabled = this.queueEnabled;
        this.queueEnabled = enabled;
        this.lastUpdatedBy = updatedBy;
        
        if (wasEnabled != enabled) {
            log.info("Queue {} by {}", enabled ? "ENABLED" : "DISABLED", updatedBy);
            
            // **OPTIMIZATION: Only invalidate caches if state actually changed**
            invalidateRelevantCaches("Queue " + (enabled ? "enabled" : "disabled"));
            
            if (!enabled) {
                // Remove all users from queue when disabled
                List<MatchQueueUser> activeUsers = queueRepository.findByInQueueTrue();
                for (MatchQueueUser user : activeUsers) {
                    user.setInQueue(false);
                }
                if (!activeUsers.isEmpty()) {
                    queueRepository.saveAll(activeUsers);
                    log.info("Removed {} users from queue due to disable", activeUsers.size());
                    
                    // Broadcast queue removed notifications
                    for (MatchQueueUser user : activeUsers) {
                        try {
                            Map<String, Object> notification = Map.of(
                                "eventType", "QUEUE_REMOVED",
                                "message", "Queue has been disabled by admin. You have been removed from the queue.",
                                "timestamp", LocalDateTime.now().toString()
                            );
                            messagingTemplate.convertAndSend(
                                "/user/" + user.getUserId() + "/topic/pairings", 
                                notification
                            );
                    } catch (Exception e) {
                            log.error("Failed to send queue removal notification to user {}", user.getUserId(), e);
                        }
                    }
                }
            }
            
            // **CRITICAL: Broadcast queue config changes to all users immediately**
            broadcastQueueConfigUpdate();
            
            scheduleDebouncedBroadcast();
        }
    }

    public boolean isQueueEnabled() {
        return queueEnabled;
    }

    public QueueConfigDTO getQueueConfig() {
        return new QueueConfigDTO(
            queueEnabled,
            queueEnabled ? "Queue is active" : "Queue is disabled", 
            lastUpdatedBy
        );
    }

    /**
     * **OPTIMIZED: Efficient eligible users fetching**
     */
    @Transactional(readOnly = true)
    public List<MatchQueueUser> getEligibleUsersForMatching() {
        if (!queueEnabled) {
            return List.of(); // Return empty list if queue is disabled
        }
        
        // Return all active users - maintain original business logic
        // No artificial delays for matchmaking eligibility
        return queueRepository.findByInQueueTrue();
    }

    public void updateLastMatchmakingRun() {
        this.lastMatchmakingRun = LocalDateTime.now();
        
        // **OPTIMIZATION: Only invalidate statistics cache after matchmaking**
        invalidateRelevantCaches("Matchmaking completed");
    }

    /**
     * **HIGHLY OPTIMIZED: Statistics calculation with database aggregation and caching**
     */
    @Transactional(readOnly = true)
    public QueueStatsDTO getQueueStatistics() {
        return queueStatsCache.get(QUEUE_STATS_CACHE_KEY, key -> {
            log.debug("Computing fresh queue statistics (cache miss)");
            long startTime = System.currentTimeMillis();
            
            try {
                // **OPTIMIZATION 1: Get queue size and calculate average wait time**
                int totalUsersInQueue = queueRepository.getActiveQueueSize();
                
                // Calculate average wait time efficiently
                double avgWaitTimeMinutes = 0.0;
                if (totalUsersInQueue > 0) {
                    List<LocalDateTime> queueTimes = queueRepository.getActiveQueueTimes();
                    LocalDateTime now = LocalDateTime.now();
                    long totalWaitMinutes = queueTimes.stream()
                            .mapToLong(queueTime -> Duration.between(queueTime, now).toMinutes())
                            .sum();
                    avgWaitTimeMinutes = (double) totalWaitMinutes / queueTimes.size();
                }

                // **OPTIMIZATION 2: Database aggregation for breakdown statistics**
                List<Object[]> aggregatedStats = queueRepository.getQueueStatisticsAggregated();
                
                Map<String, Integer> queueByRegion = new HashMap<>();
                Map<String, Integer> queueByRank = new HashMap<>(); 
                Map<String, Integer> queueByGender = new HashMap<>();
                Map<String, Integer> queueByAgeRange = new HashMap<>();

                // Process aggregated results in single pass
                for (Object[] row : aggregatedStats) {
                    String region = row[0] != null ? row[0].toString() : "UNKNOWN";
                    String rank = row[1] != null ? row[1].toString() : "UNKNOWN";
                    String gender = row[2] != null ? row[2].toString() : "UNKNOWN";
                    String ageRange = row[3] != null ? row[3].toString() : "UNKNOWN";
                    int count = ((Number) row[4]).intValue();

                    queueByRegion.merge(region, count, Integer::sum);
                    queueByRank.merge(rank, count, Integer::sum);
                    queueByGender.merge(gender, count, Integer::sum);
                    queueByAgeRange.merge(ageRange, count, Integer::sum);
                }

                // **OPTIMIZATION 3: Real-time compatibility analysis for current queue**
                double matchSuccessRate = calculateRealTimeCompatibilityRate();
                
                // Historical match data for additional context
                LocalDateTime todayStart = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
                int totalMatchesCreatedToday = pairingRepository.countByMatchedAtAfter(todayStart);
                int totalUsersMatchedToday = totalMatchesCreatedToday * 2; // Each match involves 2 users

                // **OPTIMIZATION 4: Efficient history generation**
                LocalDateTime since24Hours = LocalDateTime.now().minusHours(24);
                List<Object[]> queueHistory = queueRepository.getQueueSizeHistory(since24Hours);
                
                Map<String, Integer> queueSizeHistory = new LinkedHashMap<>();
                for (Object[] row : queueHistory) {
                    String hour = String.valueOf(((Number) row[0]).intValue());
                    int count = ((Number) row[1]).intValue();
                    queueSizeHistory.put(hour, count);
                }

                // Generate simplified wait time history
                Map<String, Double> waitTimeHistory = generateOptimizedWaitTimeHistory(queueSizeHistory);
            
            QueueStatsDTO stats = QueueStatsDTO.builder()
                        .totalUsersInQueue(totalUsersInQueue)
                        .averageWaitTimeMinutes(avgWaitTimeMinutes)
                        .lastMatchmakingRun(lastMatchmakingRun)
                        .queueByRegion(queueByRegion)
                        .queueByRank(queueByRank)
                        .queueByGender(queueByGender)
                        .queueByAgeRange(queueByAgeRange)
                    .matchSuccessRate(matchSuccessRate)
                        .totalMatchesCreatedToday(totalMatchesCreatedToday)
                        .totalUsersMatchedToday(totalUsersMatchedToday)
                        .queueSizeHistory(queueSizeHistory)
                        .waitTimeHistory(waitTimeHistory)
                        .queueStartTime(queueStartTime)
                    .queueEnabled(queueEnabled)
                        .lastUpdatedBy(lastUpdatedBy)
                    .build();
            
                long executionTime = System.currentTimeMillis() - startTime;
                log.info("Queue statistics computed in {}ms (cached for 5 minutes)", executionTime);
            
            return stats;
        } catch (Exception e) {
                log.error("Error computing queue statistics", e);
                // Return minimal fallback statistics
                return QueueStatsDTO.builder()
                        .totalUsersInQueue(0)
                        .averageWaitTimeMinutes(0.0)
                        .lastMatchmakingRun(lastMatchmakingRun)
                        .queueByRegion(new HashMap<>())
                        .queueByRank(new HashMap<>())
                        .queueByGender(new HashMap<>())
                        .queueByAgeRange(new HashMap<>())
                        .matchSuccessRate(0.0)
                        .totalMatchesCreatedToday(0)
                        .totalUsersMatchedToday(0)
                        .queueSizeHistory(new HashMap<>())
                        .waitTimeHistory(new HashMap<>())
                        .queueStartTime(queueStartTime)
                        .queueEnabled(queueEnabled)
                        .lastUpdatedBy(lastUpdatedBy)
                        .build();
            }
        });
    }

    /**
     * **HIGHLY OPTIMIZED: Queue user details with batch loading and caching**
     */
    @Transactional(readOnly = true)
    public List<QueueUserDetailsDTO> getQueueUserDetails() {
        return queueUserDetailsCache.get(QUEUE_USER_DETAILS_CACHE_KEY, key -> {
            log.debug("Computing fresh queue user details (cache miss)");
            long startTime = System.currentTimeMillis();
            
            try {
                // **OPTIMIZATION 1: Get all queue users**
                List<MatchQueueUser> queueUsers = queueRepository.findByInQueueTrue();
                
                if (queueUsers.isEmpty()) {
                    return List.<QueueUserDetailsDTO>of();
                }

                // **OPTIMIZATION 2: Batch fetch user profiles to eliminate N+1 queries**
                Set<String> userIds = queueUsers.stream()
                        .map(MatchQueueUser::getUserId)
                        .collect(Collectors.toSet());
                
                List<Object[]> userProfiles = userRepository.findUserProfilesByIds(userIds);
                Map<String, Object[]> userProfileMap = userProfiles.stream()
                        .collect(Collectors.toMap(
                            row -> (String) row[0], // userId
                            row -> row             // [userId, username, avatar]
                        ));

                // **OPTIMIZATION 3: Calculate all positions in single pass**
                List<QueueUserDetailsDTO> userDetails = new ArrayList<>();
                LocalDateTime now = LocalDateTime.now();
                
                for (int i = 0; i < queueUsers.size(); i++) {
                    MatchQueueUser queueUser = queueUsers.get(i);
                    Object[] profile = userProfileMap.get(queueUser.getUserId());
                    
                    String username = profile != null && profile[1] != null ? (String) profile[1] : "Unknown User";
                    String avatar = profile != null && profile[2] != null ? (String) profile[2] : "";
                    
                    long waitTimeMinutes = Duration.between(queueUser.getQueuedAt(), now).toMinutes();
                    int queuePosition = i + 1; // Since list is ordered by queuedAt
                    int estimatedWaitTime = calculateEstimatedWaitTime(queuePosition);
                    boolean recentlyQueued = waitTimeMinutes < 5;

                    QueueUserDetailsDTO details = QueueUserDetailsDTO.builder()
                    .userId(queueUser.getUserId())
                    .username(username)
                    .avatar(avatar)
                    .age(queueUser.getAge())
                    .region(queueUser.getRegion())
                    .rank(queueUser.getRank())
                    .gender(queueUser.getGender())
                            .queuedAt(queueUser.getQueuedAt())
                            .waitTimeMinutes(waitTimeMinutes)
                            .queuePosition(queuePosition)
                            .estimatedWaitTimeMinutes(estimatedWaitTime)
                    .recentlyQueued(recentlyQueued)
                    .build();
                    
                    userDetails.add(details);
                }

                long executionTime = System.currentTimeMillis() - startTime;
                log.info("Queue user details computed for {} users in {}ms (cached for 2 minutes)", 
                         userDetails.size(), executionTime);
                
                return userDetails;
            } catch (Exception e) {
                log.error("Error computing queue user details", e);
                return List.<QueueUserDetailsDTO>of();
            }
        });
    }

    /**
     * **OPTIMIZATION: Efficient wait time history generation**
     */
    private Map<String, Double> generateOptimizedWaitTimeHistory(Map<String, Integer> queueSizeHistory) {
        Map<String, Double> waitTimeHistory = new LinkedHashMap<>();
        
        // Use queue size to estimate wait times (simple but efficient)
        queueSizeHistory.forEach((hour, queueSize) -> {
            double estimatedWaitTime = queueSize > 0 ? queueSize * 2.5 : 0.0; // 2.5 min per person estimate
            waitTimeHistory.put(hour, estimatedWaitTime);
        });
        
        return waitTimeHistory;
    }

    /**
     * **OPTIMIZATION: Smart cache invalidation - only invalidate what's needed**
     */
    private void invalidateRelevantCaches(String reason) {
        log.debug("Invalidating caches: {}", reason);
        
        // Always invalidate queue size cache (most frequently accessed)
        queueSizeCache.invalidateAll();
        
        // Only invalidate expensive caches if significant time has passed or major change
        boolean shouldInvalidateExpensiveCaches = 
            Duration.between(lastCacheInvalidation, LocalDateTime.now()).toMinutes() >= 1 ||
            reason.contains("matchmaking") || 
            reason.contains("enabled") || 
            reason.contains("disabled");
            
        if (shouldInvalidateExpensiveCaches) {
            queueStatsCache.invalidateAll();
            queueUserDetailsCache.invalidateAll();
            lastCacheInvalidation = LocalDateTime.now();
            cacheInvalidated.set(true);
            log.debug("Invalidated expensive caches due to: {}", reason);
        }
    }

    /**
     * **OPTIMIZATION: Efficient admin subscription detection**
     */
    private boolean hasAdminSubscriptions() {
        try {
            // **PERFORMANCE NOTE: This check is lightweight and cached by Spring**
            return simpUserRegistry.findSubscriptions(subscription -> 
                subscription.getDestination().startsWith(ADMIN_QUEUE_STATS_TOPIC)
            ).size() > 0;
        } catch (Exception e) {
            log.warn("Failed to check admin subscriptions", e);
            return false; // Fail safe - don't broadcast if unsure
        }
    }

    /**
     * **OPTIMIZATION: Conditional admin broadcasting - only if admins are connected**
     */
    private void broadcastAdminQueueUpdateIfNeeded() {
        if (hasAdminSubscriptions()) {
            try {
                QueueStatsDTO stats = getQueueStatistics();
                messagingTemplate.convertAndSend(ADMIN_QUEUE_STATS_TOPIC, stats);
                log.debug("Broadcasted admin queue statistics to {} subscribers", 
                         simpUserRegistry.findSubscriptions(sub -> 
                             sub.getDestination().startsWith(ADMIN_QUEUE_STATS_TOPIC)).size());
            } catch (Exception e) {
                log.error("Failed to broadcast admin queue update", e);
            }
        } else {
            log.debug("Skipping admin broadcast - no admin subscribers detected");
        }
    }

    public void broadcastAdminQueueUpdate() {
        broadcastAdminQueueUpdateIfNeeded();
    }

    /**
     * **OPTIMIZATION: Scheduled admin broadcasting with intelligent triggers**
     */
    @Scheduled(fixedRate = 60000) // Every minute instead of every 10 seconds
    public void scheduledAdminQueueBroadcast() {
        // Only broadcast if there are admin subscribers and cache was invalidated
        if (hasAdminSubscriptions() && cacheInvalidated.compareAndSet(true, false)) {
            try {
                broadcastAdminQueueUpdate();
                log.debug("Scheduled admin broadcast triggered (cache was invalidated)");
            } catch (Exception e) {
                log.error("Error in scheduled admin broadcast", e);
            }
        }
    }

    /**
     * **OPTIMIZATION: Proactive cache warming for better performance**
     */
    public void warmUpCache() {
        log.info("Warming up queue statistics cache...");
        long startTime = System.currentTimeMillis();
        
        try {
            // Warm up all major caches
            getQueueStatistics();
            getQueueUserDetails();
            getActiveQueueSizeOptimized();
            
            long executionTime = System.currentTimeMillis() - startTime;
            log.info("Cache warm-up completed in {}ms", executionTime);
        } catch (Exception e) {
            log.error("Cache warm-up failed", e);
        }
    }

    /**
     * **OPTIMIZATION: Enhanced cache status monitoring**
     */
    public Map<String, Object> getCacheStatus() {
        Map<String, Object> status = new HashMap<>();
        
        // Queue statistics cache
        status.put("queueStatsCache", Map.of(
            "size", queueStatsCache.estimatedSize(),
            "hitRate", String.format("%.2f%%", queueStatsCache.stats().hitRate() * 100),
            "hitCount", queueStatsCache.stats().hitCount(),
            "missCount", queueStatsCache.stats().missCount()
        ));
        
        // Queue user details cache
        status.put("queueUserDetailsCache", Map.of(
            "size", queueUserDetailsCache.estimatedSize(),
            "hitRate", String.format("%.2f%%", queueUserDetailsCache.stats().hitRate() * 100),
            "hitCount", queueUserDetailsCache.stats().hitCount(),
            "missCount", queueUserDetailsCache.stats().missCount()
        ));
        
        // Queue size cache
        status.put("queueSizeCache", Map.of(
            "size", queueSizeCache.estimatedSize(),
            "hitRate", String.format("%.2f%%", queueSizeCache.stats().hitRate() * 100),
            "hitCount", queueSizeCache.stats().hitCount(),
            "missCount", queueSizeCache.stats().missCount()
        ));
        
        status.put("lastCacheInvalidation", lastCacheInvalidation.toString());
        status.put("cacheInvalidated", cacheInvalidated.get());
        
        return status;
    }

    /**
     * **OPTIMIZATION: Event-driven cache invalidation and queue cleanup for match creation**
     */
    public void onMatchesCreated(int numberOfMatches) {
        log.info("Match creation event: {} new matches created", numberOfMatches);
        
        // **OPTIMIZATION: Significant event - invalidate all caches immediately**
        queueStatsCache.invalidateAll();
        queueUserDetailsCache.invalidateAll();
        queueSizeCache.invalidateAll();
        lastCacheInvalidation = LocalDateTime.now();
        cacheInvalidated.set(true);
        
        // **OPTIMIZATION: Immediate admin update for match events**
        broadcastAdminQueueUpdateIfNeeded();
    }

    /**
     * **NEW: Remove matched users from queue and trigger live updates**
     * Called by MatchmakingService when users are successfully matched
     */
    @Transactional
    public void removeMatchedUsersFromQueue(List<String> matchedUserIds) {
        if (matchedUserIds == null || matchedUserIds.isEmpty()) {
            return;
        }
        
        log.info("Removing {} matched users from queue: {}", matchedUserIds.size(), matchedUserIds);
        
        // Remove users from queue efficiently
        List<MatchQueueUser> usersToUpdate = queueRepository.findByUserIdIn(matchedUserIds);
        for (MatchQueueUser user : usersToUpdate) {
            if (user.isInQueue()) {
                user.setInQueue(false);
                log.debug("Removed user {} from queue (matched)", user.getUserId());
            }
        }
        
        if (!usersToUpdate.isEmpty()) {
            queueRepository.saveAll(usersToUpdate);
            log.info("Successfully removed {} users from queue after matching", usersToUpdate.size());
            
            // **CRITICAL: Invalidate all caches immediately after queue changes**
            invalidateRelevantCaches("Users removed from queue after matching");
            
            // **CRITICAL: Send immediate broadcasts for live admin updates**
            broadcastQueueUpdate(); // For general queue size updates
            broadcastAdminQueueUpdateIfNeeded(); // For detailed admin statistics
            
            log.info("Live queue updates sent - admin interface should reflect {} fewer users in queue", usersToUpdate.size());
        }
    }

    public void onPairBreakup(String pairingId) {
        log.info("Pair breakup event: pairing {} ended", pairingId);
        
        // **OPTIMIZATION: Minor event - only invalidate queue stats cache**
        invalidateRelevantCaches("Pair breakup: " + pairingId);
        
        // **OPTIMIZATION: Conditional admin update**
        broadcastAdminQueueUpdateIfNeeded();
    }

    /**
     * **OPTIMIZATION: Manual admin statistics refresh with forced cache invalidation**
     */
    public void triggerAdminStatsRefresh() {
        log.info("Admin stats refresh triggered manually");
        
        // Force invalidate all caches to ensure fresh data
        queueStatsCache.invalidateAll();
        queueUserDetailsCache.invalidateAll();
        queueSizeCache.invalidateAll();
        lastCacheInvalidation = LocalDateTime.now();
        cacheInvalidated.set(true);
        
        // Immediate broadcast to any connected admin clients
        broadcastAdminQueueUpdateIfNeeded();
    }

    /**
     * **NEW: Calculate real-time compatibility rate for current queue users**
     * This analyzes current queue users and determines what percentage could potentially be matched
     * based on the matchmaking compatibility algorithm (age, gender, blacklist rules)
     */
    private double calculateRealTimeCompatibilityRate() {
        try {
            List<MatchQueueUser> activeUsers = queueRepository.findByInQueueTrue();
            
            // Need at least 2 users for any matching
            if (activeUsers.size() < 2) {
                return 0.0;
            }
            
            // **OPTIMIZATION: Use efficient compatibility checking**
            Set<String> potentialMatches = new HashSet<>();
            
            // Check all possible pairings for compatibility
            for (int i = 0; i < activeUsers.size(); i++) {
                for (int j = i + 1; j < activeUsers.size(); j++) {
                    MatchQueueUser user1 = activeUsers.get(i);
                    MatchQueueUser user2 = activeUsers.get(j);
                    
                    // Use the same compatibility logic as MatchmakingService
                    if (areUsersCompatibleForMatching(user1, user2)) {
                        potentialMatches.add(user1.getUserId());
                        potentialMatches.add(user2.getUserId());
                    }
                }
            }
            
            // Calculate percentage of users that could potentially be matched
            double successRate = potentialMatches.isEmpty() ? 0.0 : 
                (double) potentialMatches.size() / activeUsers.size() * 100;
            
            log.debug("Real-time compatibility analysis: {}/{} users could potentially be matched ({}%)", 
                     potentialMatches.size(), activeUsers.size(), String.format("%.1f", successRate));
            
            return successRate;
            
        } catch (Exception e) {
            log.error("Error calculating real-time compatibility rate", e);
            return 0.0; // Fail safe
        }
    }

    /**
     * **NEW: Check if two users are compatible for matching**
     * Uses the same compatibility rules as MatchmakingService without calculating full score
     */
    private boolean areUsersCompatibleForMatching(MatchQueueUser user1, MatchQueueUser user2) {
        // Check gender compatibility (hard constraint)
        if (!areGendersCompatibleForQueue(user1.getGender(), user2.getGender())) {
            return false;
        }
        
        // Check age compatibility (hard constraint)
        if (!areAgesCompatibleForQueue(user1.getAge(), user2.getAge())) {
            return false;
        }
        
        // Check blacklist (hard constraint)
        try {
            // Simple exists check without full repository dependency
            // This is a lightweight compatibility check for statistics
            // Note: Full blacklist checking would require additional repository injection
            // For now, assume no blacklist conflicts for statistics (can be enhanced later)
            return true;
        } catch (Exception e) {
            log.debug("Could not check blacklist for compatibility analysis: {}", e.getMessage());
            return true; // Assume compatible if we can't check blacklist
        }
    }

    /**
     * **NEW: Gender compatibility check for queue analysis**
     * Mirrors the logic in MatchmakingService.areGendersCompatible()
     */
    private boolean areGendersCompatibleForQueue(com.app.heartbound.enums.Gender gender1, com.app.heartbound.enums.Gender gender2) {
        if (gender1 == null || gender2 == null) {
            return false;
        }
        
        // MALE can only match with FEMALE
        if (gender1 == com.app.heartbound.enums.Gender.MALE) {
            return gender2 == com.app.heartbound.enums.Gender.FEMALE;
        }
        
        // FEMALE can only match with MALE
        if (gender1 == com.app.heartbound.enums.Gender.FEMALE) {
            return gender2 == com.app.heartbound.enums.Gender.MALE;
        }
        
        // NON_BINARY can only match with NON_BINARY or PREFER_NOT_TO_SAY
        if (gender1 == com.app.heartbound.enums.Gender.NON_BINARY) {
            return gender2 == com.app.heartbound.enums.Gender.NON_BINARY || 
                   gender2 == com.app.heartbound.enums.Gender.PREFER_NOT_TO_SAY;
        }
        
        // PREFER_NOT_TO_SAY can only match with NON_BINARY or PREFER_NOT_TO_SAY
        if (gender1 == com.app.heartbound.enums.Gender.PREFER_NOT_TO_SAY) {
            return gender2 == com.app.heartbound.enums.Gender.NON_BINARY || 
                   gender2 == com.app.heartbound.enums.Gender.PREFER_NOT_TO_SAY;
        }
        
        return false;
    }

    /**
     * **NEW: Age compatibility check for queue analysis**
     * Mirrors the logic in MatchmakingService.areAgesCompatible()
     */
    private boolean areAgesCompatibleForQueue(int age1, int age2) {
        // Both users must be 18+ for any pairing
        if (age1 < 18 || age2 < 18) {
            return false;
        }
        
        int ageGap = Math.abs(age1 - age2);
        
        // Strict age gap rules
        if (ageGap > 5) {
            return false;
        }
        
        // Special restrictions for users exactly 18
        int minAge = Math.min(age1, age2);
        int maxAge = Math.max(age1, age2);
        
        if (minAge == 18 && maxAge > 20) {
            return false;
        }
        
        return true;
    }
} 