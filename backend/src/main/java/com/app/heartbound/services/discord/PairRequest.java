package com.app.heartbound.services.discord;

import java.time.Instant;

public record PairRequest(
    String requesterId,
    String targetId,
    long messageId,
    Instant createdAt
) {
    public boolean isExpired() {
        // Requests expire after 5 minutes
        return createdAt.isBefore(Instant.now().minusSeconds(300));
    }
} 