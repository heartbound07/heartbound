package com.app.heartbound.dto;

import com.app.heartbound.enums.ItemRarity;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class EconomyStatsDTO {
    private long totalCredits;
    private long totalUsers;
    private double averageCreditsPerUser;
    private long itemsInCirculation;
    private Map<ItemRarity, String> recommendedPriceRanges;
} 