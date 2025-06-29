package com.app.heartbound.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
} 