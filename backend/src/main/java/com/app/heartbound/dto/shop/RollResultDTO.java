package com.app.heartbound.dto.shop;

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
    private UUID caseId;
    private String caseName;
    private ShopDTO wonItem;
    private Integer rollValue;        // Roll value 0-100 for future animation sync
    private LocalDateTime rolledAt;
    private boolean alreadyOwned;     // If user already had this item
} 