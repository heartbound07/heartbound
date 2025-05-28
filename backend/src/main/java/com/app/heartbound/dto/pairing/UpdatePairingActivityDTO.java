package com.app.heartbound.dto.pairing;

import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * UpdatePairingActivityDTO
 * 
 * DTO for updating pairing activity metrics.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdatePairingActivityDTO {

    @Min(value = 0, message = "Message increment must be non-negative")
    private int messageIncrement;

    @Min(value = 0, message = "Word increment must be non-negative")
    private int wordIncrement;

    @Min(value = 0, message = "Emoji increment must be non-negative")
    private int emojiIncrement;

    // Optional: Direct update to active days if calculated externally
    private Integer activeDays;
} 