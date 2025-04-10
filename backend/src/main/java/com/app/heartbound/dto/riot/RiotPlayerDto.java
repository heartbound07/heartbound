package com.app.heartbound.dto.riot;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class RiotPlayerDto {
    private String puuid;
    private String gameName;
    private String tagLine;
    private String teamId; // "Blue" or "Red"
    // Add stats, characterId etc. if needed later
} 