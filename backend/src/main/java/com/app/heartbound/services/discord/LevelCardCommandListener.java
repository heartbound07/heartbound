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
                event.getHook().sendMessage("❌ **Profile not found!** You need to be registered in the Heartbound system first. Try participating in some activities!")
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
                .addContent("🎮 **" + userProfile.getDisplayName() + "'s Profile Card**")
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
            payload.put("viewport_height", 300); // Set approximate height
            
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
     * Generates the HTML content for the level card based on LevelCard.tsx structure
     */
    private String generateCardHtml(UserProfileDTO profile) {
        // Calculate level and progress
        int level = profile.getLevel() != null ? profile.getLevel() : 1;
        long currentXp = profile.getExperience() != null ? profile.getExperience() : 0;
        long xpToNextLevel = calculateXpToNextLevel(level, currentXp);
        double progressPercent = ((double) currentXp / (currentXp + xpToNextLevel)) * 100;
        
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
                <div class="level-card">
                    <!-- Gradient Background -->
                    <div class="card-gradient"></div>
                    
                    <!-- Card Content -->
                    <div class="card-content">
                        <!-- Header Section -->
                        <div class="card-header">
                            <div class="user-info">
                                <div class="avatar">
                                    <div class="avatar-placeholder">%s</div>
                                </div>
                                <div class="user-details">
                                    <h2 class="display-name">%s</h2>
                                    <p class="username">%s</p>
                                </div>
                            </div>
                            <div class="level-badge">
                                <span class="level-number">%d</span>
                                <span class="level-text">LVL</span>
                            </div>
                        </div>
                        
                        <!-- XP Progress Section -->
                        <div class="xp-section">
                            <div class="xp-info">
                                <span class="xp-current">%,d XP</span>
                                <span class="xp-next">%,d to next level</span>
                            </div>
                            <div class="progress-bar">
                                <div class="progress-fill" style="width: %.1f%%"></div>
                                <div class="progress-glow" style="width: %.1f%%"></div>
                            </div>
                        </div>
                        
                        <!-- Stats Grid -->
                        <div class="stats-grid">
                            <div class="stat-item">
                                <div class="stat-icon">💰</div>
                                <div class="stat-content">
                                    <span class="stat-value">%,d</span>
                                    <span class="stat-label">Credits</span>
                                </div>
                            </div>
                            <div class="stat-item">
                                <div class="stat-icon">💬</div>
                                <div class="stat-content">
                                    <span class="stat-value">%,d</span>
                                    <span class="stat-label">Messages</span>
                                </div>
                            </div>
                            <div class="stat-item">
                                <div class="stat-icon">🎤</div>
                                <div class="stat-content">
                                    <span class="stat-value">%sh</span>
                                    <span class="stat-label">Voice Time</span>
                                </div>
                            </div>
                            <div class="stat-item">
                                <div class="stat-icon">🏆</div>
                                <div class="stat-content">
                                    <span class="stat-value">%,d</span>
                                    <span class="stat-label">Achievements</span>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </body>
            </html>
            """,
            profile.getDisplayName().substring(0, 1).toUpperCase(), // Avatar placeholder
            profile.getDisplayName(),
            "@" + profile.getDisplayName(),
            level,
            currentXp,
            xpToNextLevel,
            progressPercent,
            progressPercent,
            profile.getCredits() != null ? profile.getCredits() : 0,
            profile.getMessageCount() != null ? profile.getMessageCount() : 0,
            profile.getVoiceTimeMinutesTotal() != null ? Math.round(profile.getVoiceTimeMinutesTotal() / 60.0) : 0,
            0 // Achievements count - will be implemented later
        );
    }

    /**
     * Calculates XP needed to reach the next level
     */
    private long calculateXpToNextLevel(int currentLevel, long currentXp) {
        // Basic leveling formula - each level requires more XP
        long xpForNextLevel = (long) (1000 * Math.pow(1.2, currentLevel));
        long xpForCurrentLevel = currentLevel > 1 ? (long) (1000 * Math.pow(1.2, currentLevel - 1)) : 0;
        return xpForNextLevel - currentXp;
    }

    /**
     * Generates the CSS styles matching LevelCard.css
     */
    private String generateCardCss() {
        return """
            * {
                margin: 0;
                padding: 0;
                box-sizing: border-box;
            }
            
            html, body {
                margin: 0;
                padding: 0;
                width: 450px;
                height: auto;
                background: #0a0e13;
                font-family: 'Inter', -apple-system, BlinkMacSystemFont, sans-serif;
                overflow: hidden;
            }
            
            .level-card {
                width: 450px;
                height: auto;
                margin: 0;
                background: rgba(31, 39, 49, 0.3);
                backdrop-filter: blur(8px);
                border: 1px solid rgba(255, 255, 255, 0.05);
                border-radius: 12px;
                padding: 24px;
                position: relative;
                overflow: hidden;
                color: white;
                box-sizing: border-box;
            }
            
            .card-gradient {
                position: absolute;
                top: 0;
                left: 0;
                right: 0;
                height: 120px;
                background: linear-gradient(135deg, 
                    rgba(139, 92, 246, 0.15) 0%, 
                    rgba(59, 130, 246, 0.15) 50%, 
                    rgba(16, 185, 129, 0.15) 100%);
                pointer-events: none;
            }
            
            .card-content {
                position: relative;
                z-index: 1;
            }
            
            .card-header {
                display: flex;
                justify-content: space-between;
                align-items: flex-start;
                margin-bottom: 24px;
            }
            
            .user-info {
                display: flex;
                align-items: center;
                gap: 16px;
            }
            
            .avatar {
                width: 64px;
                height: 64px;
                border-radius: 50%;
                background: linear-gradient(135deg, #8b5cf6, #3b82f6);
                display: flex;
                align-items: center;
                justify-content: center;
                font-size: 24px;
                font-weight: 700;
                color: white;
                text-shadow: 0 2px 4px rgba(0, 0, 0, 0.3);
            }
            
            .avatar-placeholder {
                font-size: 24px;
                font-weight: 700;
            }
            
            .user-details {
                display: flex;
                flex-direction: column;
                gap: 4px;
            }
            
            .display-name {
                font-size: 20px;
                font-weight: 700;
                color: #f8fafc;
                margin: 0;
            }
            
            .username {
                font-size: 14px;
                color: #94a3b8;
                margin: 0;
            }
            
            .level-badge {
                background: linear-gradient(135deg, #8b5cf6, #3b82f6);
                border-radius: 12px;
                padding: 8px 16px;
                display: flex;
                flex-direction: column;
                align-items: center;
                min-width: 60px;
                box-shadow: 0 4px 12px rgba(139, 92, 246, 0.3);
            }
            
            .level-number {
                font-size: 24px;
                font-weight: 800;
                color: white;
                line-height: 1;
            }
            
            .level-text {
                font-size: 10px;
                font-weight: 600;
                color: rgba(255, 255, 255, 0.8);
                text-transform: uppercase;
                letter-spacing: 0.5px;
            }
            
            .xp-section {
                margin-bottom: 24px;
            }
            
            .xp-info {
                display: flex;
                justify-content: space-between;
                align-items: center;
                margin-bottom: 8px;
            }
            
            .xp-current {
                font-size: 14px;
                font-weight: 600;
                color: #f8fafc;
            }
            
            .xp-next {
                font-size: 12px;
                color: #94a3b8;
            }
            
            .progress-bar {
                height: 8px;
                background: rgba(31, 39, 49, 0.5);
                border-radius: 4px;
                overflow: hidden;
                position: relative;
            }
            
            .progress-fill {
                height: 100%;
                background: linear-gradient(90deg, #8b5cf6, #3b82f6, #10b981);
                border-radius: 4px;
                transition: width 0.5s ease;
            }
            
            .progress-glow {
                position: absolute;
                top: 0;
                left: 0;
                height: 100%;
                background: linear-gradient(90deg, 
                    rgba(139, 92, 246, 0.5), 
                    rgba(59, 130, 246, 0.5), 
                    rgba(16, 185, 129, 0.5));
                border-radius: 4px;
                filter: blur(4px);
                transition: width 0.5s ease;
            }
            
            .stats-grid {
                display: grid;
                grid-template-columns: 1fr 1fr;
                gap: 16px;
            }
            
            .stat-item {
                background: rgba(31, 39, 49, 0.3);
                border: 1px solid rgba(255, 255, 255, 0.05);
                border-radius: 8px;
                padding: 16px;
                display: flex;
                align-items: center;
                gap: 12px;
                transition: all 0.2s ease;
            }
            
            .stat-item:hover {
                background: rgba(31, 39, 49, 0.5);
                border-color: rgba(139, 92, 246, 0.3);
            }
            
            .stat-icon {
                font-size: 20px;
                width: 32px;
                height: 32px;
                display: flex;
                align-items: center;
                justify-content: center;
                background: rgba(139, 92, 246, 0.1);
                border-radius: 6px;
            }
            
            .stat-content {
                display: flex;
                flex-direction: column;
                gap: 2px;
            }
            
            .stat-value {
                font-size: 18px;
                font-weight: 700;
                color: #f8fafc;
                line-height: 1;
            }
            
            .stat-label {
                font-size: 12px;
                color: #94a3b8;
                text-transform: uppercase;
                letter-spacing: 0.5px;
            }
            """;
    }
} 