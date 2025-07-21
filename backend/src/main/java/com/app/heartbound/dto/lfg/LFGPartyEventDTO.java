package com.app.heartbound.dto.lfg;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

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
    
    // The full party details, if applicable
    private LFGPartyResponseDTO party;
    
    // Optional: Minimal party data for lightweight events
    private MinimalPartyDTO minimalParty;
    
    // Optional human-readable message to provide additional context or notifications.
    private String message;
    
    // Add this field
    private String targetUserId;
    
    /**
     * Minimal party DTO for lightweight events.
     * Only includes essential fields to reduce payload size.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MinimalPartyDTO {
        private UUID id;
        private String leaderId;
        // Fields that might change based on event type
        private String status;
        private java.util.Set<String> participants;
        private java.util.Set<String> joinRequests;
        private java.util.Set<String> invitedUsers;
    }
}
