package com.app.heartbound.dto.shop;

import com.app.heartbound.config.security.Views;
import com.fasterxml.jackson.annotation.JsonView;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
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
    @JsonView(Views.Public.class)
    private UUID caseId;
    
    @JsonView(Views.Public.class)
    private String caseName;
    
    @JsonView(Views.Public.class)
    private List<CaseItemDTO> items;
    
    @JsonView(Views.Public.class)
    private BigDecimal totalDropRate;  // Should be 100 for valid cases
    
    @JsonView(Views.Public.class)
    private Integer itemCount;      // Number of different items in the case
} 