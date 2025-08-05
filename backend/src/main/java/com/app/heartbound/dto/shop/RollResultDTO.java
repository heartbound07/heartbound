package com.app.heartbound.dto.shop;

import com.app.heartbound.config.security.Views;
import com.fasterxml.jackson.annotation.JsonView;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * RollResultDTO
 * 
 * Represents the result of opening a case, including the won item
 * and metadata for potential future animation features.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RollResultDTO {
    @JsonView(Views.Public.class)
    private UUID caseId;
    
    @JsonView(Views.Public.class)
    private String caseName;
    
    @JsonView(Views.Public.class)
    private ShopDTO wonItem;
    
    @JsonView(Views.Admin.class)
    private Integer rollValue;        // Roll value 0-999,999 for internal use only
    
    @JsonView(Views.Public.class)
    private LocalDateTime rolledAt;
    
    @JsonView(Views.Public.class)
    private boolean alreadyOwned;     // If user already had this item
    
    // Duplicate compensation fields
    @JsonView(Views.Public.class)
    private boolean compensationAwarded;  // Whether compensation was given for duplicate
    
    @JsonView(Views.Public.class)
    private Integer compensatedCredits;   // Credits awarded for duplicate item
    
    @JsonView(Views.Public.class)
    private Integer compensatedXp;        // XP awarded for duplicate item
} 