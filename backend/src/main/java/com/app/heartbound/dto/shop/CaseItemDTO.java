package com.app.heartbound.dto.shop;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
    private Double dropRate;       // Drop rate percentage (1-100)
} 