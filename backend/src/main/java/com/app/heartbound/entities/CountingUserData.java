package com.app.heartbound.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "counting_user_data")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CountingUserData {
    
    @Id
    private String userId; // Discord user ID
    
    private Integer livesRemaining;
    private Integer timeoutLevel = 0; // Track progressive timeout durations (0 = never timed out)
    private LocalDateTime timeoutExpiry; // When the current timeout expires (null if not timed out)
    
    // Statistics
    private Long totalCorrectCounts = 0L;
    private Long totalMistakes = 0L;
    private Integer bestCount = 0; // Highest number they've successfully counted to
} 