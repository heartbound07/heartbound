package com.app.heartbound.dto.pairing;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JoinQueueRequestDTO {

    @NotBlank(message = "User ID is required")
    private String userId;

} 