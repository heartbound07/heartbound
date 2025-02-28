package com.app.heartbound.dto.lfg;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Standardized DTO for WebSocket events related to LFG parties.
 * This DTO is used to broadcast events such as party creation, updates,
 * deletion, joining, leaving, and other party state changes.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LFGPartyEventDTO {
    
    // Event type values: PARTY_CREATED, PARTY_UPDATED, PARTY_DELETED, PARTY_JOINED, PARTY_LEFT, etc.
    private String eventType;
    
    // The LFG party details, if applicable for the event.
    // This may be null for events where party details are not required (e.g., deletion events).
    private LFGPartyResponseDTO party;
    
    // Optional human-readable message to provide additional context or notifications.
    private String message;
}
