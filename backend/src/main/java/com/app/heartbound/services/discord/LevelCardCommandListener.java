package com.app.heartbound.services.discord;

import com.app.heartbound.dto.UserProfileDTO;
import com.app.heartbound.services.UserProfileService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.utils.FileUpload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Service
public class LevelCardCommandListener extends ListenerAdapter {
    
    private static final Logger logger = LoggerFactory.getLogger(LevelCardCommandListener.class);
    
    @Autowired
    private UserProfileService userProfileService;
    
    @Value("${htmlcsstoimage.user_id}")
    private String htmlCssToImageUserId;
    
    @Value("${htmlcsstoimage.api_key}")
    private String htmlCssToImageApiKey;
    
    @Value("${frontend.base.url}")
    private String frontendBaseUrl;
    
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!"me".equals(event.getName())) {
            return;
        }

        // Immediately defer the reply to prevent timeout
        event.deferReply().queue();

        try {
            // Get user's Discord ID and fetch their profile
            String userId = event.getUser().getId();
            UserProfileDTO userProfile = userProfileService.getUserProfile(userId);
            
                    if (userProfile == null) {
            event.getHook().sendMessage("You are not currently registered, make sure to register at " + frontendBaseUrl)
                .setEphemeral(true).queue();
            return;
        }

            // Generate the HTML content
            String htmlContent = generateCardHtml(userProfile);
            String cssContent = generateCardCss();
            
            // Call HTML/CSS to Image API
            String imageUrl = generateImageWithApi(htmlContent, cssContent);
            
            // Download the image
            byte[] imageBytes = downloadImage(imageUrl);
            
            // Send the image to Discord
            event.getHook().sendFiles(FileUpload.fromData(imageBytes, "level-card.png"))
                .queue();
            
        } catch (Exception e) {
            logger.error("Error generating level card for user: " + event.getUser().getId(), e);
            event.getHook().sendMessage("❌ **Error generating your profile card!** Please try again later.")
                .setEphemeral(true).queue();
        }
    }

    /**
     * Calls the HTML/CSS to Image API to generate the image
     */
    private String generateImageWithApi(String html, String css) throws IOException {
        try {
            // Prepare the request payload
            Map<String, Object> payload = new HashMap<>();
            payload.put("html", html);
            payload.put("css", css);
            payload.put("google_fonts", "Inter:wght@400;500;600;700;800");
            payload.put("device_scale", 2); // For high-quality images
            payload.put("ms_delay", 1000); // Wait for fonts to load
            payload.put("viewport_width", 450); // Set exact width
            payload.put("viewport_height", 200); // Set height to match content size better
            
            // Set up authentication headers
            String auth = htmlCssToImageUserId + ":" + htmlCssToImageApiKey;
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Basic " + encodedAuth);
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
            
            // Make the API call
            ResponseEntity<String> response = restTemplate.postForEntity(
                "https://hcti.io/v1/image", entity, String.class);  
            
            if (response.getStatusCode() == HttpStatus.OK) {
                JsonNode jsonResponse = objectMapper.readTree(response.getBody());
                return jsonResponse.get("url").asText();
            } else {
                throw new IOException("API returned status: " + response.getStatusCode());
            }
            
        } catch (Exception e) {
            logger.error("Error calling HTML/CSS to Image API", e);
            throw new IOException("Failed to generate image", e);
        }
    }

    /**
     * Downloads the image from the provided URL
     */
    private byte[] downloadImage(String imageUrl) throws IOException {
        try {
            ResponseEntity<byte[]> response = restTemplate.getForEntity(imageUrl, byte[].class);
            if (response.getStatusCode() == HttpStatus.OK) {
                return response.getBody();
            } else {
                throw new IOException("Failed to download image, status: " + response.getStatusCode());
            }
        } catch (Exception e) {
            logger.error("Error downloading image from URL: " + imageUrl, e);
            throw new IOException("Failed to download generated image", e);
        }
    }

    /**
     * Generates the HTML content for the level card matching LevelCard.tsx structure exactly
     */
    private String generateCardHtml(UserProfileDTO profile) {
        // Calculate XP progress exactly like the frontend
        int current = profile.getExperience() != null ? profile.getExperience() : 0;
        int required = profile.getXpForNextLevel() != null ? profile.getXpForNextLevel() : 0;
        double percentage = required > 0 ? Math.min((current / (double) required) * 100, 100) : 0;
        
        // Format numbers exactly like frontend formatNumber function
        String formattedCredits = formatNumber(profile.getCredits() != null ? profile.getCredits() : 0);
        String formattedMessages = formatNumber(profile.getMessageCount() != null ? profile.getMessageCount().intValue() : 0);
        String formattedVoiceTime = formatVoiceTime(profile.getVoiceTimeMinutesTotal() != null ? profile.getVoiceTimeMinutesTotal() : 0);
        
        // Get user info
        String displayName = profile.getDisplayName() != null ? profile.getDisplayName() : 
                           (profile.getUsername() != null ? profile.getUsername() : "User");
        String username = profile.getUsername() != null ? "@" + profile.getUsername() : "";
        String avatarUrl = profile.getAvatar() != null ? profile.getAvatar() : "/default-avatar.png";
        String bannerUrl = profile.getBannerUrl();
        int level = profile.getLevel() != null ? profile.getLevel() : 1;
        
        // Create banner background style if banner exists
        String bannerStyle = bannerUrl != null && !bannerUrl.isEmpty() ? 
            String.format("background-image: url('%s');", bannerUrl) : "";
        String bannerClass = bannerUrl != null && !bannerUrl.isEmpty() ? " has-banner" : "";
        
        // SVG Icons matching the frontend
        String coinsIcon = "<svg viewBox='0 0 24 24' fill='currentColor' style='width: 16px; height: 16px;'><path d='M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1.41 16.09V20h-2.67v-1.93c-1.71-.36-3.16-1.46-3.27-3.4h1.96c.1 1.05.82 1.87 2.65 1.87 1.96 0 2.4-.98 2.4-1.59 0-.83-.44-1.61-2.67-2.14-2.48-.6-4.18-1.62-4.18-3.67 0-1.72 1.39-2.84 3.11-3.21V4h2.67v1.95c1.86.45 2.79 1.86 2.85 3.39H14.3c-.05-1.11-.64-1.87-2.22-1.87-1.5 0-2.4.68-2.4 1.64 0 .84.65 1.39 2.67 1.91 2.28.6 4.18 1.77 4.18 3.84 0 1.77-1.21 2.85-3.12 3.18z'/></svg>";
        String messageIcon = "<svg viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='2' style='width: 16px; height: 16px;'><path d='M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z'/></svg>";
        String voiceIcon = "<svg viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='2' style='width: 16px; height: 16px;'><path d='M11 5L6 9H2v6h4l5 4V5zM19.07 4.93a10 10 0 0 1 0 14.14M15.54 8.46a5 5 0 0 1 0 7.07'/></svg>";

        return String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <link rel="preconnect" href="https://fonts.googleapis.com">
                <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
                <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800&display=swap" rel="stylesheet">
            </head>
            <body>
                <div class="level-card-wrapper">
                    <div class="level-card">
                        <!-- Level Display with User Info -->
                        <div class="level-display-section%s" style="%s">
                            <div class="user-info-left">
                                <div class="avatar">
                                    <img src="%s" alt="%s" />
                                </div>
                                <div class="user-text">
                                    <div class="display-name">%s</div>
                                    %s
                                </div>
                            </div>
                            <div class="level-info-right">
                                <div class="level-number">%d</div>
                                <div class="level-text">LEVEL</div>
                            </div>
                        </div>

                        <!-- Progress Bars -->
                        <div class="progress-bars-section">
                            <div class="progress-bar-item">
                                <div class="progress-bar-container">
                                    <div class="progress-bar-track">
                                        <div class="progress-bar-fill" style="width: %.1f%%"></div>
                                    </div>
                                    <div class="progress-bar-label">%d / %d</div>
                                </div>
                            </div>
                        </div>

                        <!-- Stats Grid -->
                        <div class="stats-grid-section">
                            <div class="stat-grid-item">
                                <div class="stat-icon">%s</div>
                                <div class="stat-label">CRD:</div>
                                <div class="stat-value">%s</div>
                            </div>
                            <div class="stat-grid-item">
                                <div class="stat-icon">%s</div>
                                <div class="stat-label">MSG:</div>
                                <div class="stat-value">%s</div>
                            </div>
                            <div class="stat-grid-item">
                                <div class="stat-icon">%s</div>
                                <div class="stat-label">VT:</div>
                                <div class="stat-value">%s</div>
                            </div>
                        </div>
                    </div>
                </div>
            </body>
            </html>
            """,
            bannerClass,
            bannerStyle,
            avatarUrl,
            displayName,
            displayName,
            username.isEmpty() ? "" : String.format("<div class=\"username\">%s</div>", username),
            level,
            percentage,
            current,
            required,
            coinsIcon,
            formattedCredits,
            messageIcon,
            formattedMessages,
            voiceIcon,
            formattedVoiceTime
        );
    }

    /**
     * Format numbers exactly like the frontend formatNumber function
     */
    private String formatNumber(int num) {
        if (num >= 1000000) {
            return String.format("%.1fM", num / 1000000.0);
        }
        if (num >= 1000) {
            return String.format("%.1fk", num / 1000.0);
        }
        return String.valueOf(num);
    }

    /**
     * Format voice time exactly like the frontend formatVoiceTime function
     */
    private String formatVoiceTime(int minutes) {
        if (minutes == 0) return "0m";
        if (minutes < 60) {
            return minutes + "m";
        }
        int hours = minutes / 60;
        int remainingMinutes = minutes % 60;
        if (remainingMinutes == 0) {
            return hours + "h";
        }
        return hours + "h " + remainingMinutes + "m";
    }

    /**
     * Generates the CSS styles exactly matching LevelCard.css
     */
    private String generateCardCss() {
        return """
            /* Root variables - maintaining existing theme */
            :root {
                --level-card-accent: #ff4655;
                --level-card-bg-primary: #0f1923;
                --level-card-bg-secondary: #1f2731;
                --level-card-border: rgba(255, 255, 255, 0.05);
                --level-card-radius: 0.75rem;
                --level-card-transition: all 0.3s ease;
            }
            
            * {
                margin: 0;
                padding: 0;
                box-sizing: border-box;
            }
            
            html, body {
                margin: 0;
                padding: 0;
                width: 450px;
                height: fit-content;
                min-height: fit-content;
                max-height: fit-content;
                background: #0a0e13;
                font-family: 'Inter', -apple-system, BlinkMacSystemFont, sans-serif;
                overflow: hidden;
            }
            
                         /* WRAPPER & CONTAINER */
             .level-card-wrapper {
                 all: unset;
                 position: relative;
                 z-index: 1;
                 display: block;
                 width: 100%;
                 max-width: 450px;
                 margin: 0;
                 padding: 0;
                 font-family: inherit;
                 line-height: inherit;
                 color: white !important;
                 height: fit-content;
             }
            
            .level-card-wrapper * {
                color: inherit;
            }
            
            .level-card {
                background: rgba(31, 39, 49, 0.3) !important;
                backdrop-filter: blur(8px) !important;
                border: 1px solid rgba(255, 255, 255, 0.05) !important;
                border-radius: var(--level-card-radius);
                padding: 1.5rem;
                transition: var(--level-card-transition);
                position: relative;
                overflow: hidden;
                color: white !important;
            }
            
            .level-card::before {
                content: "";
                position: absolute;
                top: 0;
                left: 0;
                right: 0;
                bottom: 0;
                background: linear-gradient(135deg, rgba(255, 255, 255, 0.02) 0%, transparent 50%);
                pointer-events: none;
                z-index: 0;
            }
            
            .level-card > * {
                position: relative;
                z-index: 1;
            }
            
            /* LEVEL DISPLAY SECTION WITH USER INFO */
            .level-display-section {
                display: flex;
                align-items: center;
                justify-content: space-between;
                margin: -1.5rem -1.5rem 1.5rem -1.5rem;
                padding: 1.5rem 1.5rem 1rem 1.5rem;
                border-bottom: 1px solid rgba(255, 255, 255, 0.08);
                position: relative;
                overflow: hidden;
                border-radius: var(--level-card-radius) var(--level-card-radius) 0 0;
            }
            
            .level-display-section.has-banner {
                background-size: cover;
                background-position: center;
                background-repeat: no-repeat;
            }
            
            .level-display-section.has-banner::before {
                content: "";
                position: absolute;
                top: 0;
                left: 0;
                right: 0;
                bottom: 0px;
                background: linear-gradient(
                    135deg,
                    rgba(0, 0, 0, 0.4) 0%,
                    rgba(0, 0, 0, 0.3) 50%,
                    rgba(0, 0, 0, 0.5) 100%
                );
                z-index: 1;
                pointer-events: none;
            }
            
            .level-display-section > * {
                position: relative;
                z-index: 2;
            }
            
            .user-info-left {
                display: flex;
                align-items: center;
                gap: 1rem;
                flex: 1;
                min-width: 0;
            }
            
            .avatar {
                position: relative;
                flex-shrink: 0;
            }
            
            .avatar img {
                width: 48px;
                height: 48px;
                border-radius: 50%;
                object-fit: cover;
                border: 2px solid rgba(0, 0, 0, 0.8);
                box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.1);
            }
            
            .user-text {
                flex: 1;
                min-width: 0;
            }
            
            .display-name {
                font-size: 1.25rem;
                font-weight: 600;
                color: white;
                margin-bottom: 0.25rem;
                white-space: nowrap;
                overflow: hidden;
                text-overflow: ellipsis;
                letter-spacing: -0.01em;
                text-shadow: 1px 1px 2px rgba(0, 0, 0, 0.8), -1px -1px 2px rgba(0, 0, 0, 0.8), 1px -1px 2px rgba(0, 0, 0, 0.8), -1px 1px 2px rgba(0, 0, 0, 0.8);
            }
            
            .username {
                font-size: 0.875rem;
                color: rgba(255, 255, 255, 0.4);
                font-weight: 400;
                text-shadow: 1px 1px 2px rgba(0, 0, 0, 0.8), -1px -1px 2px rgba(0, 0, 0, 0.8), 1px -1px 2px rgba(0, 0, 0, 0.8), -1px 1px 2px rgba(0, 0, 0, 0.8);
            }
            
            .level-info-right {
                text-align: center;
                flex-shrink: 0;
                margin-left: 0.25rem;
            }
            
            .level-number {
                font-size: 3rem;
                font-weight: 800;
                color: white;
                line-height: 1;
                letter-spacing: -0.02em;
                margin-bottom: 0.25rem;
                text-shadow: 2px 2px 4px rgba(0, 0, 0, 0.9), -2px -2px 4px rgba(0, 0, 0, 0.9), 2px -2px 4px rgba(0, 0, 0, 0.9), -2px 2px 4px rgba(0, 0, 0, 0.9);
            }
            
            .level-text {
                font-size: 0.875rem;
                font-weight: 600;
                color: rgba(255, 255, 255, 0.7);
                letter-spacing: 0.1em;
                text-transform: uppercase;
                text-shadow: 1px 1px 2px rgba(0, 0, 0, 0.8), -1px -1px 2px rgba(0, 0, 0, 0.8), 1px -1px 2px rgba(0, 0, 0, 0.8), -1px 1px 2px rgba(0, 0, 0, 0.8);
            }
            
            /* PROGRESS BARS SECTION */
            .progress-bars-section {
                margin-bottom: 1.5rem;
            }
            
            .progress-bar-item {
                margin-bottom: 0.75rem;
            }
            
            .progress-bar-container {
                display: flex;
                align-items: center;
                gap: 0.75rem;
            }
            
            .progress-bar-track {
                flex: 1;
                height: 8px;
                background: rgba(31, 39, 49, 0.8);
                border-radius: 4px;
                overflow: hidden;
                border: 1px solid rgba(255, 255, 255, 0.1);
            }
            
            .progress-bar-fill {
                height: 100%;
                background: linear-gradient(90deg, var(--level-card-accent) 0%, rgba(255, 70, 85, 0.8) 100%);
                border-radius: 3px;
                transition: width 0.5s ease-in-out;
            }
            
            .progress-bar-label {
                font-size: 0.75rem;
                color: rgba(255, 255, 255, 0.7);
                font-weight: 500;
                min-width: 80px;
                text-align: right;
            }
            
            /* STATS GRID SECTION */
            .stats-grid-section {
                display: grid;
                grid-template-columns: 1fr 1fr 1fr;
                gap: 0.75rem;
            }
            
            .stat-grid-item {
                display: flex;
                align-items: center;
                gap: 0.5rem;
                padding: 0.75rem;
                background: rgba(31, 39, 49, 0.4);
                border: 1px solid rgba(255, 255, 255, 0.05);
                border-radius: 8px;
                transition: var(--level-card-transition);
            }
            
            .stat-icon {
                display: flex;
                align-items: center;
                justify-content: center;
                width: 20px;
                height: 20px;
                font-size: 0.875rem;
                color: white;
                flex-shrink: 0;
            }
            
            .stat-label {
                font-size: 0.75rem;
                color: rgba(255, 255, 255, 0.6);
                font-weight: 600;
                text-transform: uppercase;
                letter-spacing: 0.05em;
            }
            
            .stat-value {
                font-size: 0.875rem;
                font-weight: 700;
                color: white;
                margin-left: auto;
            }
            """;
    }
} 