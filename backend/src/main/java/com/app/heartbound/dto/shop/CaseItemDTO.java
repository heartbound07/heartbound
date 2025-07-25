package com.app.heartbound.dto.shop;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * CaseItemDTO
 * 
 * Represents an item within a case with its drop rate information.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CaseItemDTO {
    private UUID id;
    private UUID caseId;
    private ShopDTO containedItem;  // The actual item that can be won
    private BigDecimal dropRate; // Drop rate percentage (e.g., 50.0, 0.05)
} 