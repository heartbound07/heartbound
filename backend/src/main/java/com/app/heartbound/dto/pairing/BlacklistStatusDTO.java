package com.app.heartbound.dto.pairing;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * BlacklistStatusDTO
 * 
 * DTO for blacklist status responses.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BlacklistStatusDTO {
    private boolean blacklisted;
    private String reason;
} 