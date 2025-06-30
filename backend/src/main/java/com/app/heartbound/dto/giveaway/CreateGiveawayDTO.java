package com.app.heartbound.dto.giveaway;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateGiveawayDTO {
    
    @NotBlank(message = "Prize is required")
    @Size(max = 255, message = "Prize description cannot exceed 255 characters")
    private String prize;
    
    @NotNull(message = "Number of winners is required")
    @Min(value = 1, message = "Number of winners must be at least 1")
    private Integer numberOfWinners;
    
    @NotBlank(message = "Duration is required")
    private String duration; // "1 day", "2 days", etc.
    
    // Restrictions
    private Boolean boostersOnly = false;
    private Boolean levelRestricted = false; // Level 5+ users only
    private Boolean noRestrictions = false;
    
    // Entry configuration
    @Min(value = 1, message = "Max entries per user must be at least 1 if specified")
    private Integer maxEntriesPerUser; // null = unlimited
    
    @Min(value = 0, message = "Entry price cannot be negative")
    private Integer entryPrice = 0; // Credits cost per entry, 0 = free
    
    // Validation helper
    public boolean hasValidRestrictions() {
        int restrictionCount = 0;
        if (Boolean.TRUE.equals(boostersOnly)) restrictionCount++;
        if (Boolean.TRUE.equals(levelRestricted)) restrictionCount++;
        if (Boolean.TRUE.equals(noRestrictions)) restrictionCount++;
        return restrictionCount == 1; // Exactly one restriction type must be selected
    }
} 