package com.app.heartbound.entities;

import com.app.heartbound.services.HtmlSanitizationService;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Entity listener for User entities that automatically sanitizes content before persistence.
 * This provides an additional layer of security by ensuring all data is sanitized at the database level.
 */
@Component
public class UserEntityListener {
    
    private static final Logger logger = LoggerFactory.getLogger(UserEntityListener.class);
    
    @Autowired
    private HtmlSanitizationService htmlSanitizationService;
    
    /**
     * Called before persisting a new User entity
     */
    @PrePersist
    public void beforePersist(User user) {
        sanitizeUserContent(user, "persist");
    }
    
    /**
     * Called before updating an existing User entity
     */
    @PreUpdate
    public void beforeUpdate(User user) {
        sanitizeUserContent(user, "update");
    }
    
    /**
     * Sanitize all user-generated content in the User entity
     */
    private void sanitizeUserContent(User user, String operation) {
        if (user == null) {
            return;
        }
        
        try {
            // Sanitize displayName with STRICT policy (no HTML, alphanumeric + basic punctuation only)
            if (user.getDisplayName() != null) {
                String originalDisplayName = user.getDisplayName();
                String sanitizedDisplayName = htmlSanitizationService.sanitizeStrict(originalDisplayName);
                
                // Enforce length constraints at entity level
                if (sanitizedDisplayName.length() > 50) {
                    sanitizedDisplayName = sanitizedDisplayName.substring(0, 50).trim();
                }
                
                if (!originalDisplayName.equals(sanitizedDisplayName)) {
                    logger.info("User displayName sanitized during {}: '{}' -> '{}'", 
                               operation, truncateForLogging(originalDisplayName), truncateForLogging(sanitizedDisplayName));
                    user.setDisplayName(sanitizedDisplayName);
                }
            }
            
            // Sanitize pronouns with STRICT policy
            if (user.getPronouns() != null) {
                String originalPronouns = user.getPronouns();
                String sanitizedPronouns = htmlSanitizationService.sanitizeStrict(originalPronouns);
                
                // Enforce length constraints at entity level
                if (sanitizedPronouns.length() > 20) {
                    sanitizedPronouns = sanitizedPronouns.substring(0, 20).trim();
                }
                
                if (!originalPronouns.equals(sanitizedPronouns)) {
                    logger.info("User pronouns sanitized during {}: '{}' -> '{}'", 
                               operation, truncateForLogging(originalPronouns), truncateForLogging(sanitizedPronouns));
                    user.setPronouns(sanitizedPronouns);
                }
            }
            
            // Sanitize about with BASIC policy (strip HTML, preserve text)
            if (user.getAbout() != null) {
                String originalAbout = user.getAbout();
                String sanitizedAbout = htmlSanitizationService.sanitizeBasic(originalAbout);
                
                // Enforce length constraints at entity level
                if (sanitizedAbout.length() > 200) {
                    sanitizedAbout = sanitizedAbout.substring(0, 200).trim();
                }
                
                if (!originalAbout.equals(sanitizedAbout)) {
                    logger.info("User about section sanitized during {}: '{}' -> '{}'", 
                               operation, truncateForLogging(originalAbout), truncateForLogging(sanitizedAbout));
                    user.setAbout(sanitizedAbout);
                }
            }
            
            // Sanitize avatar URL
            if (user.getAvatar() != null) {
                String originalAvatarUrl = user.getAvatar();
                
                // AVATAR FALLBACK FIX: Skip sanitization for special Discord avatar marker
                if (!"USE_DISCORD_AVATAR".equals(originalAvatarUrl)) {
                    String sanitizedAvatarUrl = htmlSanitizationService.sanitizeUrl(originalAvatarUrl);
                    
                    if (!originalAvatarUrl.equals(sanitizedAvatarUrl)) {
                        logger.warn("User avatar URL sanitized during {}: '{}' -> '{}'", 
                                   operation, originalAvatarUrl, sanitizedAvatarUrl);
                        user.setAvatar(sanitizedAvatarUrl);
                    }
                }
            }
            
            // Sanitize Discord avatar URL cache
            if (user.getDiscordAvatarUrl() != null) {
                String originalDiscordAvatarUrl = user.getDiscordAvatarUrl();
                String sanitizedDiscordAvatarUrl = htmlSanitizationService.sanitizeUrl(originalDiscordAvatarUrl);
                
                if (!originalDiscordAvatarUrl.equals(sanitizedDiscordAvatarUrl)) {
                    logger.warn("User Discord avatar URL sanitized during {}: '{}' -> '{}'", 
                               operation, originalDiscordAvatarUrl, sanitizedDiscordAvatarUrl);
                    user.setDiscordAvatarUrl(sanitizedDiscordAvatarUrl);
                }
            }
            
            // Sanitize banner URL
            if (user.getBannerUrl() != null) {
                String originalBannerUrl = user.getBannerUrl();
                String sanitizedBannerUrl = htmlSanitizationService.sanitizeUrl(originalBannerUrl);
                
                if (!originalBannerUrl.equals(sanitizedBannerUrl)) {
                    logger.warn("User banner URL sanitized during {}: '{}' -> '{}'", 
                               operation, originalBannerUrl, sanitizedBannerUrl);
                    user.setBannerUrl(sanitizedBannerUrl);
                }
            }
            
            // Validate and sanitize banner color
            if (user.getBannerColor() != null && !user.getBannerColor().isEmpty()) {
                String bannerColor = user.getBannerColor().trim();
                
                // Check if it's a valid hex color or Tailwind class
                if (!isValidBannerColor(bannerColor)) {
                    logger.warn("Invalid banner color format during {}: '{}'. Setting to default.", 
                               operation, bannerColor);
                    user.setBannerColor("bg-white/10"); // Default color
                }
            }
            
        } catch (Exception e) {
            logger.error("Error during content sanitization in entity listener for user {}: {}", 
                        user.getId(), e.getMessage(), e);
            // Don't throw exception to avoid breaking the persistence operation
        }
    }
    
    /**
     * Validate banner color format (hex color or Tailwind CSS class)
     * @param color The color value to validate
     * @return true if valid, false otherwise
     */
    private boolean isValidBannerColor(String color) {
        if (color == null || color.trim().isEmpty()) {
            return true; // Empty is valid
        }
        
        String trimmed = color.trim();
        
        // Check if it's a valid hex color format (#RRGGBB or #RRGGBBAA)
        if (trimmed.matches("^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{8})$")) {
            return true;
        }
        
        // Check if it's a valid Tailwind CSS background color class
        if (trimmed.matches("^(bg-[a-z]+-[0-9]+(/[0-9]+)?|bg-white/[0-9]+)$")) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Truncate content for safe logging
     */
    private String truncateForLogging(String content) {
        if (content == null) return "null";
        if (content.length() <= 50) return content;
        return content.substring(0, 50) + "...";
    }
} 