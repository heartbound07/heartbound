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
@Table(name = "counting_game_state")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CountingGameState {
    
    @Id
    private Long id = 1L; // Single row configuration
    
    private Integer currentCount = 0;
    private String lastUserId; // Discord ID of user who sent the last correct number
    
    // Reset tracking
    private Long totalResets = 0L;
    private Integer highestCount = 0;
    
    // Save This Count feature
    private Integer saveCost = 200; // Cost to save count, starts at 200, doubles each save
    private LocalDateTime restartDelayUntil; // When counting can restart after failure (5 second delay)
    private Integer lastFailedCount; // The count that failed and can be saved
} 