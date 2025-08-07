package com.app.heartbound.services.discord;

import com.app.heartbound.dto.ServerStatsDTO;
import com.app.heartbound.services.ServerStatsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.utils.FileUpload;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ServerStatsCommandListener extends ListenerAdapter {
    
    private static final long COOLDOWN_DURATION_MS = 120_000; // 2 minutes
    private final ConcurrentHashMap<String, Long> commandCooldowns = new ConcurrentHashMap<>();
    
    @Autowired
    private ServerStatsService serverStatsService;
    
    @Value("${htmlcsstoimage.user_id}")
    private String htmlCssToImageUserId;
    
    @Value("${htmlcsstoimage.api_key}")
    private String htmlCssToImageApiKey;
    
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Override
    public void onSlashCommandInteraction(@Nonnull SlashCommandInteractionEvent event) {
        if (!event.getName().equals("serverstats")) {
            return;
        }
        
        // Check guild context
        if (!event.isFromGuild()) {
            event.reply("This command can only be used in a server.").setEphemeral(true).queue();
            return;
        }
        
        // Get target user (default to command user if not specified)
        OptionMapping userOption = event.getOption("user");
        User targetUser = userOption != null ? userOption.getAsUser() : event.getUser();
        String userId = targetUser.getId();
        String username = targetUser.getName();
        
        // Check cooldown per user
        long remainingCooldown = checkCooldown(userId);
        if (remainingCooldown > 0) {
            long secondsRemaining = remainingCooldown / 1000;
            event.reply(String.format("Please wait %d seconds before using this command again.", secondsRemaining))
                .setEphemeral(true)
                .queue();
            return;
        }
        
        // Defer reply immediately
        event.deferReply().queue();
        
        // Set cooldown
        setCooldown(userId);
        
        try {
            // Fetch user statistics
            ServerStatsDTO userStats = serverStatsService.getUserStats(userId);
            
            // Generate HTML and CSS
            String html = generateServerStatsHtml(userStats, username);
            String css = generateServerStatsCss();
            
            // Generate image using HCTI API
            String imageUrl = generateImageWithApi(html, css);
            
            // Download the image
            byte[] imageBytes = downloadImage(imageUrl);
            
            // Send the image to Discord
            FileUpload file = FileUpload.fromData(imageBytes, "user-stats.png");
            event.getHook().sendFiles(file).queue(
                success -> log.info("Successfully sent user stats image for user: {}", userId),
                error -> {
                    log.error("Failed to send user stats image: {}", error.getMessage());
                    event.getHook().sendMessage("Failed to generate user statistics image. Please try again later.")
                        .queue();
                }
            );
            
        } catch (Exception e) {
            log.error("Error generating user stats for user {}: {}", userId, e.getMessage(), e);
            event.getHook().sendMessage("An error occurred while generating user statistics. Please try again later.")
                .setEphemeral(true)
                .queue();
        }
    }
    
    private long checkCooldown(String guildId) {
        Long lastUsed = commandCooldowns.get(guildId);
        if (lastUsed == null) {
            return 0;
        }
        
        long currentTime = System.currentTimeMillis();
        long timeSinceLastUse = currentTime - lastUsed;
        
        if (timeSinceLastUse < COOLDOWN_DURATION_MS) {
            return COOLDOWN_DURATION_MS - timeSinceLastUse;
        }
        
        return 0;
    }
    
    private void setCooldown(String guildId) {
        commandCooldowns.put(guildId, System.currentTimeMillis());
    }
    
    @Scheduled(fixedRate = 600_000) // 10 minutes
    private void cleanupExpiredCooldowns() {
        long currentTime = System.currentTimeMillis();
        commandCooldowns.entrySet().removeIf(entry -> 
            currentTime - entry.getValue() > COOLDOWN_DURATION_MS * 2
        );
        log.debug("Cleaned up expired server stats command cooldowns. Remaining: {}", commandCooldowns.size());
    }
    
    private String generateImageWithApi(String html, String css) throws IOException {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("html", html);
        requestBody.put("css", css);
        requestBody.put("google_fonts", "Poppins|Inter");
        
        String credentials = htmlCssToImageUserId + ":" + htmlCssToImageApiKey;
        String encodedCredentials = Base64.getEncoder().encodeToString(credentials.getBytes());
        
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.set("Authorization", "Basic " + encodedCredentials);
        headers.set("Content-Type", "application/json");
        
        org.springframework.http.HttpEntity<Map<String, Object>> request = 
            new org.springframework.http.HttpEntity<>(requestBody, headers);
        
        org.springframework.http.ResponseEntity<Map> response = restTemplate.postForEntity(
            "https://hcti.io/v1/image",
            request,
            Map.class
        );
        
        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            return (String) response.getBody().get("url");
        } else {
            throw new IOException("Failed to generate image with HCTI API");
        }
    }
    
    private byte[] downloadImage(String imageUrl) throws IOException {
        org.springframework.http.ResponseEntity<byte[]> response = restTemplate.getForEntity(
            imageUrl,
            byte[].class
        );
        
        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            return response.getBody();
        } else {
            throw new IOException("Failed to download image from URL: " + imageUrl);
        }
    }
    
    private String generateServerStatsHtml(ServerStatsDTO stats, String username) {
        StringBuilder html = new StringBuilder();
        
        html.append("<div class='discord-dashboard'>");
        
        // Main Stats Grid
        html.append("<div class='stats-container'>");
        
        // User Ranking Section
        html.append("<div class='stats-section'>");
        html.append("<div class='section-header'>");
        html.append("<svg class='icon' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='2'>");
        html.append("<path d='M3 12h18M3 6h18M3 18h18'/>");
        html.append("</svg>");
        html.append("<h2>").append(username).append("'s Ranking</h2>");
        html.append("</div>");
        html.append("<div class='rank-cards'>");
        html.append("<div class='rank-card'>");
        html.append("<div class='rank-label'>Messages Rank</div>");
        html.append("<div class='rank-value'>").append(stats.getTopMessageUser() != null ? 
            stats.getTopMessageUser() : "N/A").append("</div>");
        html.append("</div>");
        html.append("<div class='rank-card'>");
        html.append("<div class='rank-label'>Voice Activity Rank</div>");
        html.append("<div class='rank-value'>").append(stats.getTopVoiceUser() != null ? 
            stats.getTopVoiceUser() : "N/A").append("</div>");
        html.append("</div>");
        html.append("</div>");
        html.append("</div>");
        
        // Messages Section
        html.append("<div class='stats-section'>");
        html.append("<div class='section-header'>");
        html.append("<svg class='icon' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='2'>");
        html.append("<path d='M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z'/>");
        html.append("</svg>");
        html.append("<h2>Messages</h2>");
        html.append("</div>");
        html.append("<div class='time-period-cards'>");
        html.append("<div class='time-card'>");
        html.append("<div class='time-period'>1d</div>");
        html.append("<div class='time-value'>").append(formatNumber(stats.getMessagesToday()))
            .append(" <span>messages</span></div>");
        html.append("</div>");
        html.append("<div class='time-card'>");
        html.append("<div class='time-period'>7d</div>");
        html.append("<div class='time-value'>").append(formatNumber(stats.getMessagesThisWeek()))
            .append(" <span>messages</span></div>");
        html.append("</div>");
        html.append("<div class='time-card'>");
        html.append("<div class='time-period'>14d</div>");
        html.append("<div class='time-value'>").append(formatNumber(stats.getMessagesThisTwoWeeks()))
            .append(" <span>messages</span></div>");
        html.append("</div>");
        html.append("</div>");
        html.append("</div>");
        
        // Voice Activity Section
        html.append("<div class='stats-section'>");
        html.append("<div class='section-header'>");
        html.append("<svg class='icon' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='2'>");
        html.append("<path d='M11 5L6 9H2v6h4l5 4V5zM19.07 4.93a10 10 0 0 1 0 14.14M15.54 8.46a5 5 0 0 1 0 7.07'/>");
        html.append("</svg>");
        html.append("<h2>Voice Activity</h2>");
        html.append("</div>");
        html.append("<div class='time-period-cards'>");
        html.append("<div class='time-card'>");
        html.append("<div class='time-period'>1d</div>");
        html.append("<div class='time-value'>").append(formatVoiceTime(stats.getVoiceMinutesToday()))
            .append("</div>");
        html.append("</div>");
        html.append("<div class='time-card'>");
        html.append("<div class='time-period'>7d</div>");
        html.append("<div class='time-value'>").append(formatVoiceTime(stats.getVoiceMinutesThisWeek()))
            .append("</div>");
        html.append("</div>");
        html.append("<div class='time-card'>");
        html.append("<div class='time-period'>14d</div>");
        html.append("<div class='time-value'>").append(formatVoiceTime(stats.getVoiceMinutesThisTwoWeeks()))
            .append("</div>");
        html.append("</div>");
        html.append("</div>");
        html.append("</div>");
        
        html.append("</div>"); // stats-container
        
        // Bottom Section
        html.append("<div class='bottom-section'>");
        
        // Activity Overview
        html.append("<div class='activity-overview'>");
        html.append("<div class='section-header'>");
        html.append("<svg class='icon' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='2'>");
        html.append("<path d='M3 12h18M3 6h18M3 18h18'/>");
        html.append("</svg>");
        html.append("<h2>Activity Overview</h2>");
        html.append("</div>");
        html.append("<div class='activity-summary'>");
        html.append("<div class='activity-item'>");
        html.append("<svg class='icon-small' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='2'>");
        html.append("<path d='M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z'/>");
        html.append("</svg>");
        html.append("<span class='activity-label'>Total Messages</span>");
        html.append("<span class='activity-value'>").append(formatNumber(stats.getTotalMessages()))
            .append(" messages</span>");
        html.append("</div>");
        html.append("<div class='activity-item'>");
        html.append("<svg class='icon-small' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='2'>");
        html.append("<path d='M11 5L6 9H2v6h4l5 4V5zM19.07 4.93a10 10 0 0 1 0 14.14M15.54 8.46a5 5 0 0 1 0 7.07'/>");
        html.append("</svg>");
        html.append("<span class='activity-label'>Total Voice Time</span>");
        html.append("<span class='activity-value'>").append(formatVoiceTime(stats.getTotalVoiceMinutes()))
            .append("</span>");
        html.append("</div>");
        html.append("<div class='activity-item'>");
        html.append("<svg class='icon-small' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='2'>");
        html.append("<path d='M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2z'/>");
        html.append("</svg>");
        html.append("<span class='activity-label'>Total Fish Caught</span>");
        html.append("<span class='activity-value'>").append(formatNumber(stats.getTotalFishCaught()))
            .append(" fish</span>");
        html.append("</div>");
        html.append("</div>");
        html.append("</div>");
        
        // Charts Section
        html.append("<div class='charts-section'>");
        html.append("<div class='charts-section-header'>");
        html.append("<div class='section-header'>");
        html.append("<svg class='icon' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='2'>");
        html.append("<path d='M3 12h18M3 6h18M3 18h18'/>");
        html.append("</svg>");
        html.append("<h2>Charts</h2>");
        html.append("</div>");
        html.append("<div class='chart-legend'>");
        html.append("<div class='legend-item'>");
        html.append("<div class='legend-dot message'></div>");
        html.append("<span>Message</span>");
        html.append("</div>");
        html.append("<div class='legend-item'>");
        html.append("<div class='legend-dot voice'></div>");
        html.append("<span>Voice</span>");
        html.append("</div>");
        html.append("</div>");
        html.append("</div>");
        html.append("<div class='chart-container'>");
        html.append(generateChartHtml(stats.getDailyActivity()));
        html.append("</div>");
        html.append("</div>");
        
        html.append("</div>"); // bottom-section
        
        // Footer
        html.append("<div class='dashboard-footer'>");
        html.append("<span>Statistics for ").append(username).append(" • Generated on ")
            .append(LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM d, yyyy")))
            .append("</span>");
        html.append("</div>");
        
        html.append("</div>"); // discord-dashboard
        
        return html.toString();
    }
    
    private String generateChartHtml(List<ServerStatsDTO.DailyActivity> dailyActivity) {
        if (dailyActivity == null || dailyActivity.isEmpty()) {
            return "<div class='no-data'>No activity data available</div>";
        }
        
        // Find max values for scaling
        long maxMessages = dailyActivity.stream()
            .mapToLong(ServerStatsDTO.DailyActivity::getMessages)
            .max()
            .orElse(1);
        long maxVoice = dailyActivity.stream()
            .mapToLong(ServerStatsDTO.DailyActivity::getVoiceMinutes)
            .max()
            .orElse(1);
        
        StringBuilder chart = new StringBuilder();
        chart.append("<div class='chart-bars'>");
        
        for (ServerStatsDTO.DailyActivity day : dailyActivity) {
            double messageHeight = (day.getMessages() / (double) maxMessages) * 100;
            double voiceHeight = (day.getVoiceMinutes() / (double) maxVoice) * 100;
            
            chart.append("<div class='chart-bar-group'>");
            chart.append("<div class='chart-bar message' style='height: ").append(messageHeight).append("%;'></div>");
            chart.append("<div class='chart-bar voice' style='height: ").append(voiceHeight).append("%;'></div>");
            chart.append("</div>");
        }
        
        chart.append("</div>");
        return chart.toString();
    }
    
    private String formatNumber(long num) {
        if (num >= 1000000) {
            return String.format("%.1fM", num / 1000000.0);
        }
        if (num >= 1000) {
            return String.format("%.1fk", num / 1000.0);
        }
        return String.valueOf(num);
    }
    
    private String formatVoiceTime(long minutes) {
        if (minutes == 0) return "0 mins";
        
        long hours = minutes / 60;
        long remainingMinutes = minutes % 60;
        
        if (hours == 0) {
            return remainingMinutes + " mins";
        } else if (remainingMinutes == 0) {
            return hours + (hours == 1 ? " hour" : " hours");
        } else {
            String hourText = hours == 1 ? "hour" : "hours";
            return String.format("%d.%d %s", hours, Math.round((remainingMinutes / 60.0) * 10), hourText);
        }
    }
    
    private String generateServerStatsCss() {
        return """
            * {
                margin: 0;
                padding: 0;
                box-sizing: border-box;
            }
            
            body {
                font-family: 'Inter', 'Poppins', sans-serif;
                background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                padding: 20px;
            }
            
            .discord-dashboard {
                max-width: 1200px;
                margin: 0 auto;
            }
            
            .stats-container {
                display: grid;
                grid-template-columns: repeat(3, 1fr);
                gap: 20px;
                margin-bottom: 20px;
            }
            
            .stats-section {
                background: rgba(30, 31, 34, 0.95);
                border-radius: 12px;
                padding: 20px;
                border: 1px solid rgba(255, 255, 255, 0.1);
            }
            
            .section-header {
                display: flex;
                align-items: center;
                gap: 8px;
                margin-bottom: 16px;
                color: rgba(255, 255, 255, 0.9);
            }
            
            .section-header h2 {
                font-size: 14px;
                font-weight: 600;
                text-transform: uppercase;
                letter-spacing: 0.5px;
            }
            
            .icon {
                width: 20px;
                height: 20px;
                color: rgba(255, 255, 255, 0.7);
            }
            
            .icon-small {
                width: 16px;
                height: 16px;
                color: rgba(255, 255, 255, 0.5);
            }
            
            .rank-cards {
                display: flex;
                flex-direction: column;
                gap: 12px;
            }
            
            .rank-card {
                background: rgba(47, 49, 54, 0.8);
                padding: 12px 16px;
                border-radius: 8px;
                display: flex;
                justify-content: space-between;
                align-items: center;
            }
            
            .rank-label {
                color: rgba(255, 255, 255, 0.6);
                font-size: 13px;
            }
            
            .rank-value {
                color: #ffd700;
                font-size: 18px;
                font-weight: 600;
            }
            
            .time-period-cards {
                display: flex;
                flex-direction: column;
                gap: 10px;
            }
            
            .time-card {
                background: rgba(47, 49, 54, 0.8);
                padding: 10px 14px;
                border-radius: 8px;
                display: flex;
                align-items: center;
                gap: 12px;
            }
            
            .time-period {
                background: rgba(114, 137, 218, 0.2);
                color: #7289da;
                padding: 4px 8px;
                border-radius: 4px;
                font-size: 12px;
                font-weight: 600;
                min-width: 30px;
                text-align: center;
            }
            
            .time-value {
                color: rgba(255, 255, 255, 0.9);
                font-size: 15px;
                font-weight: 500;
            }
            
            .time-value span {
                color: rgba(255, 255, 255, 0.5);
                font-size: 13px;
            }
            
            .bottom-section {
                display: grid;
                grid-template-columns: 1fr 2fr;
                gap: 20px;
                margin-bottom: 20px;
            }
            
            .activity-overview {
                background: rgba(30, 31, 34, 0.95);
                border-radius: 12px;
                padding: 20px;
                border: 1px solid rgba(255, 255, 255, 0.1);
            }
            
            .activity-summary {
                display: flex;
                flex-direction: column;
                gap: 14px;
            }
            
            .activity-item {
                display: flex;
                align-items: center;
                gap: 10px;
                padding: 10px;
                background: rgba(47, 49, 54, 0.6);
                border-radius: 8px;
            }
            
            .activity-label {
                flex: 1;
                color: rgba(255, 255, 255, 0.6);
                font-size: 13px;
            }
            
            .activity-value {
                color: rgba(255, 255, 255, 0.9);
                font-size: 15px;
                font-weight: 600;
            }
            
            .charts-section {
                background: rgba(30, 31, 34, 0.95);
                border-radius: 12px;
                padding: 20px;
                border: 1px solid rgba(255, 255, 255, 0.1);
            }
            
            .charts-section-header {
                display: flex;
                justify-content: space-between;
                align-items: center;
                margin-bottom: 20px;
            }
            
            .chart-legend {
                display: flex;
                gap: 16px;
            }
            
            .legend-item {
                display: flex;
                align-items: center;
                gap: 6px;
                color: rgba(255, 255, 255, 0.7);
                font-size: 13px;
            }
            
            .legend-dot {
                width: 10px;
                height: 10px;
                border-radius: 50%;
            }
            
            .legend-dot.message {
                background: #7289da;
            }
            
            .legend-dot.voice {
                background: #43b581;
            }
            
            .chart-container {
                height: 200px;
                background: rgba(47, 49, 54, 0.4);
                border-radius: 8px;
                padding: 15px;
                display: flex;
                align-items: flex-end;
            }
            
            .chart-bars {
                display: flex;
                width: 100%;
                height: 100%;
                align-items: flex-end;
                gap: 2px;
            }
            
            .chart-bar-group {
                flex: 1;
                display: flex;
                gap: 1px;
                height: 100%;
                align-items: flex-end;
            }
            
            .chart-bar {
                flex: 1;
                border-radius: 2px 2px 0 0;
                min-height: 2px;
                transition: all 0.3s ease;
            }
            
            .chart-bar.message {
                background: #7289da;
                opacity: 0.8;
            }
            
            .chart-bar.voice {
                background: #43b581;
                opacity: 0.8;
            }
            
            .no-data {
                text-align: center;
                color: rgba(255, 255, 255, 0.5);
                padding: 40px;
            }
            
            .dashboard-footer {
                text-align: center;
                padding: 16px;
                color: rgba(255, 255, 255, 0.5);
                font-size: 12px;
                background: rgba(30, 31, 34, 0.6);
                border-radius: 8px;
            }
            """;
    }
} 