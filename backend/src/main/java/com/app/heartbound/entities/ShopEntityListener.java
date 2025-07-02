package com.app.heartbound.entities;

import com.app.heartbound.services.HtmlSanitizationService;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Entity listener for Shop entities that automatically sanitizes content before persistence.
 * This provides an additional layer of security by ensuring all data is sanitized at the database level.
 */
@Component
public class ShopEntityListener {
    
    private static final Logger logger = LoggerFactory.getLogger(ShopEntityListener.class);
    
    @Autowired
    private HtmlSanitizationService htmlSanitizationService;
    
    /**
     * Called before persisting a new Shop entity
     */
    @PrePersist
    public void beforePersist(Shop shop) {
        sanitizeShopContent(shop, "persist");
    }
    
    /**
     * Called before updating an existing Shop entity
     */
    @PreUpdate
    public void beforeUpdate(Shop shop) {
        sanitizeShopContent(shop, "update");
    }
    
    /**
     * Sanitize all user-generated content in the Shop entity
     */
    private void sanitizeShopContent(Shop shop, String operation) {
        if (shop == null) {
            return;
        }
        
        try {
            // Sanitize name with STRICT policy (no HTML, alphanumeric + basic punctuation only)
            if (shop.getName() != null) {
                String originalName = shop.getName();
                String sanitizedName = htmlSanitizationService.sanitizeStrict(originalName);
                
                // Enforce length constraints at entity level
                if (sanitizedName.length() > 100) {
                    sanitizedName = sanitizedName.substring(0, 100).trim();
                }
                
                if (!originalName.equals(sanitizedName)) {
                    logger.info("Shop name sanitized during {}: '{}' -> '{}'", 
                               operation, truncateForLogging(originalName), truncateForLogging(sanitizedName));
                    shop.setName(sanitizedName);
                }
            }
            
            // Sanitize description with BASIC policy (strip HTML, preserve text)
            if (shop.getDescription() != null) {
                String originalDescription = shop.getDescription();
                String sanitizedDescription = htmlSanitizationService.sanitizeBasic(originalDescription);
                
                // Enforce length constraints at entity level
                if (sanitizedDescription.length() > 500) {
                    sanitizedDescription = sanitizedDescription.substring(0, 500).trim();
                }
                
                if (!originalDescription.equals(sanitizedDescription)) {
                    logger.info("Shop description sanitized during {}: '{}' -> '{}'", 
                               operation, truncateForLogging(originalDescription), truncateForLogging(sanitizedDescription));
                    shop.setDescription(sanitizedDescription);
                }
            }
            
            // Sanitize URLs for safety
            if (shop.getImageUrl() != null) {
                String originalImageUrl = shop.getImageUrl();
                String sanitizedImageUrl = htmlSanitizationService.sanitizeUrl(originalImageUrl);
                
                if (!originalImageUrl.equals(sanitizedImageUrl)) {
                    logger.warn("Shop imageUrl sanitized during {}: '{}' -> '{}'", 
                               operation, originalImageUrl, sanitizedImageUrl);
                    shop.setImageUrl(sanitizedImageUrl);
                }
            }
            
            if (shop.getThumbnailUrl() != null) {
                String originalThumbnailUrl = shop.getThumbnailUrl();
                String sanitizedThumbnailUrl = htmlSanitizationService.sanitizeUrl(originalThumbnailUrl);
                
                if (!originalThumbnailUrl.equals(sanitizedThumbnailUrl)) {
                    logger.warn("Shop thumbnailUrl sanitized during {}: '{}' -> '{}'", 
                               operation, originalThumbnailUrl, sanitizedThumbnailUrl);
                    shop.setThumbnailUrl(sanitizedThumbnailUrl);
                }
            }
            
            // Validate and sanitize discordRoleId (should be numeric only)
            if (shop.getDiscordRoleId() != null && !shop.getDiscordRoleId().isEmpty()) {
                String discordRoleId = shop.getDiscordRoleId().trim();
                if (!discordRoleId.matches("^\\d+$")) {
                    logger.warn("Invalid Discord role ID format during {}: '{}'. Setting to empty.", 
                               operation, discordRoleId);
                    shop.setDiscordRoleId("");
                }
            }
            
        } catch (Exception e) {
            logger.error("Error during content sanitization in entity listener for shop {}: {}", 
                        shop.getId(), e.getMessage(), e);
            // Don't throw exception to avoid breaking the persistence operation
        }
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