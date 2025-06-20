package com.app.heartbound.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DailyActivityDataDTO {
    private String date; // Format: YYYY-MM-DD
    private Long count;
} 