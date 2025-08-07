package com.app.heartbound.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ServerStatsDTO {
    
    // Total server-wide statistics
    private long totalMessages;
    private long totalVoiceMinutes;
    private long totalFishCaught;
    
    // Time-windowed message statistics
    private long messagesToday;
    private long messagesThisWeek;
    private long messagesThisTwoWeeks;
    
    // Time-windowed voice statistics
    private long voiceMinutesToday;
    private long voiceMinutesThisWeek;
    private long voiceMinutesThisTwoWeeks;
    
    // Top users
    private String topMessageUser;
    private String topVoiceUser;
    
    // Daily activity for charts
    private List<DailyActivity> dailyActivity;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyActivity {
        private String date;
        private long messages;
        private long voiceMinutes;
    }
} 