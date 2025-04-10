package com.app.heartbound.dto.riot;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class RiotMatchlistEntryDto {
    private String matchId;
    private long gameStartTimeMillis;
    // Add other relevant fields from the matchlist entry if needed
} 