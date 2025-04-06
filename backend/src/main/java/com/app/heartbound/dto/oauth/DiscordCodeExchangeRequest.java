package com.app.heartbound.dto.oauth;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DiscordCodeExchangeRequest {
    private String code;
    // Optionally include state if needed for validation here, though primary validation happens on frontend
    // private String state;
} 