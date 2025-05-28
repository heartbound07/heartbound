package com.app.heartbound.dto.pairing;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * PairingUpdateEvent
 * 
 * DTO for WebSocket pairing update events.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PairingUpdateEvent {
    private String eventType; // MATCH_FOUND, PAIRING_ENDED
    private PairingDTO pairing;
    private String message;
    private LocalDateTime timestamp;
} 