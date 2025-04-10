package com.app.heartbound.dto.riot;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class RiotMatchInfoDto {
    private long gameCreation; // Timestamp
    private long gameDuration; // In seconds
    private long gameLengthMillis; // More precise duration
    private long gameStartMillis;
    private String gameMode; // e.g., "/Game/GameModes/Bomb/BombGameMode.BombGameMode_C"
    private String gameVersion;
    private boolean isCompleted; // You might need to derive this or check gameLengthMillis > 0
    private String mapId; // e.g., "/Game/Maps/Ascent/Ascent"
    private String queueId; // e.g., "unrated", "competitive"
    // Add other fields like provisioningFlowId, seasonId if needed
} 