package com.app.heartbound.dto.discord;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimedOutUserDTO {
    
    private String userId;
    private String username;
    private String avatar;
    private Integer timeoutLevel;
    private LocalDateTime timeoutExpiry;
    private Integer livesRemaining;
    private Long totalCorrectCounts;
    private Long totalMistakes;
    private Integer bestCount;
    
    // Calculated fields
    private Long timeoutHoursRemaining;
    private String timeoutDuration; // Human-readable duration like "2 days, 3 hours"
} 