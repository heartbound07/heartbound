package com.app.heartbound.dto.shop;

import com.app.heartbound.config.security.Views;
import com.fasterxml.jackson.annotation.JsonView;
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
    @JsonView(Views.Public.class)
    private UUID id;
    
    @JsonView(Views.Public.class)
    private UUID caseId;
    
    @JsonView(Views.Public.class)
    private ShopDTO containedItem;  // The actual item that can be won
    
    @JsonView(Views.Admin.class)
    private BigDecimal dropRate; // Drop rate percentage (e.g., 50.0, 0.05)
} 