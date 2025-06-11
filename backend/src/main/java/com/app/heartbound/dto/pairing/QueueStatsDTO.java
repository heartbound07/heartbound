package com.app.heartbound.dto.pairing;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * QueueStatsDTO
 * 
 * Response DTO for queue statistics available to admin users.
 * Provides comprehensive analytics about the matchmaking queue.
 */
@Data
@Builder
public class QueueStatsDTO {
    
    // Total statistics
    private int totalUsersInQueue;
    private double averageWaitTimeMinutes;
    private LocalDateTime lastMatchmakingRun;
    
    // Breakdown by region
    private Map<String, Integer> queueByRegion;
    
    // Breakdown by rank
    private Map<String, Integer> queueByRank;
    
    // Breakdown by gender
    private Map<String, Integer> queueByGender;
    
    // Breakdown by age ranges
    private Map<String, Integer> queueByAgeRange;
    
    // Queue health metrics
    private double matchSuccessRate;
    private int totalMatchesCreatedToday;
    private int totalUsersMatchedToday;
    
    // Historical data
    private Map<String, Integer> queueSizeHistory; // Hour -> Size
    private Map<String, Double> waitTimeHistory; // Hour -> Avg Wait Time
    
    // Queue timing info
    private LocalDateTime queueStartTime; // When the queue system was started
    private boolean queueEnabled;
    private String lastUpdatedBy;
} 