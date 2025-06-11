package com.app.heartbound.dto.pairing;

import com.app.heartbound.enums.Gender;
import com.app.heartbound.enums.Rank;
import com.app.heartbound.enums.Region;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * QueueUserDetailsDTO
 * 
 * Detailed information about users in the queue for admin monitoring.
 */
@Data
@Builder
public class QueueUserDetailsDTO {
    
    private String userId;
    private String username;
    private String avatar;
    private int age;
    private Region region;
    private Rank rank;
    private Gender gender;
    private LocalDateTime queuedAt;
    private long waitTimeMinutes;
    private int queuePosition;
    private int estimatedWaitTimeMinutes;
    private boolean recentlyQueued; // Queued in last 5 minutes
} 