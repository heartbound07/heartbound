package com.app.heartbound.services.oauth;

public interface DiscordCodeStore {
    /**
     * Stores a single-use code associated with a user ID.
     * @param code The single-use code.
     * @param userId The user ID to associate.
     */
    void storeCode(String code, String userId);

    /**
     * Validates the code, consumes it (marks as used/removes), and returns the associated user ID.
     * Returns null if the code is invalid, expired, or already used.
     * @param code The single-use code to validate.
     * @return The associated user ID or null if invalid.
     */
    String consumeCode(String code);
} 