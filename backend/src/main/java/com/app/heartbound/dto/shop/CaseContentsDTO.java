package com.app.heartbound.dto.shop;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * CaseContentsDTO
 * 
 * Represents the complete contents of a case including all possible items
 * and their drop rates.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CaseContentsDTO {
    private UUID caseId;
    private String caseName;
    private List<CaseItemDTO> items;
    private java.math.BigDecimal totalDropRate;  // Should be 100 for valid cases
    private Integer itemCount;      // Number of different items in the case
} 