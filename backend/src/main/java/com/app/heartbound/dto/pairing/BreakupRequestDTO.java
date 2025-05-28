package com.app.heartbound.dto.pairing;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * BreakupRequestDTO
 * 
 * DTO for handling pairing breakups.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BreakupRequestDTO {

    @NotBlank(message = "Initiator ID is required")
    private String initiatorId;

    private String reason;

    private boolean mutualBreakup;
} 