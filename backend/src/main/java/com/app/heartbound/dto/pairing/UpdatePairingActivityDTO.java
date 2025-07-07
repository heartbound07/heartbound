package com.app.heartbound.dto.pairing;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * UpdatePairingActivityDTO
 * 
 * DTO for updating pairing activity metrics with realistic rate limits.
 * Limits are based on Discord rate limits and human behavior patterns.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdatePairingActivityDTO {

    @Min(value = 0, message = "Message increment must be non-negative")
    @Max(value = 50, message = "Message increment must not exceed 50 per update (realistic burst limit)")
    private int messageIncrement;

    @Min(value = 0, message = "Word increment must be non-negative")
    @Max(value = 1000, message = "Word increment must not exceed 1,000 per update (avg 20 words per message)")
    private int wordIncrement;

    @Min(value = 0, message = "Emoji increment must be non-negative")
    @Max(value = 20, message = "Emoji increment must not exceed 20 per update (realistic usage)")
    private int emojiIncrement;

    // Optional: Direct update to active days if calculated externally
    @Min(value = 0, message = "Active days must be non-negative")
    @Max(value = 1, message = "Active days must not exceed 1 per update (realistic daily increment)")
    private Integer activeDays;
    
    // Admin-only fields for direct metric updates
    @Min(value = 0, message = "User1 message count must be non-negative")
    @Max(value = 10000000, message = "User1 message count must not exceed 10,000,000")
    private Integer user1MessageCount;
    
    @Min(value = 0, message = "User2 message count must be non-negative")
    @Max(value = 10000000, message = "User2 message count must not exceed 10,000,000")
    private Integer user2MessageCount;
    
    @Min(value = 0, message = "Voice time minutes must be non-negative")
    @Max(value = 100000, message = "Voice time minutes must not exceed 100,000")
    private Integer voiceTimeMinutes;
} 