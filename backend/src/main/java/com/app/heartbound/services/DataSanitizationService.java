package com.app.heartbound.services;

import com.app.heartbound.entities.Shop;
import com.app.heartbound.repositories.shop.ShopRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * DataSanitizationService
 * 
 * Service for cleaning and migrating existing data to ensure it complies with
 * the new security standards. This service can be run as a one-time migration
 * or as needed to clean up any potentially dangerous content.
 */
@Service
public class DataSanitizationService {
    
    private static final Logger logger = LoggerFactory.getLogger(DataSanitizationService.class);
    
    @Autowired
    private ShopRepository shopRepository;
    
    @Autowired
    private HtmlSanitizationService htmlSanitizationService;
    
    /**
     * Sanitize all existing shop items
     * This is a comprehensive migration that cleans all shop data
     * 
     * @return Migration results
     */
    @Async
    @Transactional
    public CompletableFuture<MigrationResult> sanitizeAllShopItems() {
        logger.info("Starting comprehensive shop data sanitization migration");
        
        AtomicInteger totalProcessed = new AtomicInteger(0);
        AtomicInteger totalModified = new AtomicInteger(0);
        AtomicInteger totalErrors = new AtomicInteger(0);
        
        try {
            // Get all shop items
            List<Shop> allShopItems = shopRepository.findAll();
            logger.info("Found {} shop items to process", allShopItems.size());
            
            for (Shop shop : allShopItems) {
                try {
                    boolean wasModified = sanitizeShopItem(shop);
                    if (wasModified) {
                        shopRepository.save(shop);
                        totalModified.incrementAndGet();
                        logger.debug("Sanitized and saved shop item: {} (ID: {})", shop.getName(), shop.getId());
                    }
                    totalProcessed.incrementAndGet();
                    
                    // Log progress every 100 items
                    if (totalProcessed.get() % 100 == 0) {
                        logger.info("Migration progress: {}/{} items processed, {} modified", 
                                   totalProcessed.get(), allShopItems.size(), totalModified.get());
                    }
                    
                } catch (Exception e) {
                    totalErrors.incrementAndGet();
                    logger.error("Error sanitizing shop item {} (ID: {}): {}", 
                               shop.getName(), shop.getId(), e.getMessage(), e);
                }
            }
            
            MigrationResult result = new MigrationResult(
                totalProcessed.get(),
                totalModified.get(),
                totalErrors.get(),
                true,
                "Shop data sanitization completed successfully"
            );
            
            logger.info("Shop data sanitization migration completed. Processed: {}, Modified: {}, Errors: {}",
                       result.getProcessedCount(), result.getModifiedCount(), result.getErrorCount());
            
            return CompletableFuture.completedFuture(result);
            
        } catch (Exception e) {
            logger.error("Failed to complete shop data sanitization migration: {}", e.getMessage(), e);
            return CompletableFuture.completedFuture(new MigrationResult(
                totalProcessed.get(),
                totalModified.get(),
                totalErrors.get(),
                false,
                "Migration failed: " + e.getMessage()
            ));
        }
    }
    
    /**
     * Sanitize a single shop item and return whether it was modified
     */
    private boolean sanitizeShopItem(Shop shop) {
        boolean wasModified = false;
        
        // Sanitize name
        if (shop.getName() != null) {
            String originalName = shop.getName();
            String sanitizedName = htmlSanitizationService.sanitizeStrict(originalName);
            
            // Enforce length constraints
            if (sanitizedName.length() > 100) {
                sanitizedName = sanitizedName.substring(0, 100).trim();
            }
            
            if (!originalName.equals(sanitizedName)) {
                shop.setName(sanitizedName);
                wasModified = true;
                logger.debug("Sanitized shop name: '{}' -> '{}'", 
                           truncateForLogging(originalName), truncateForLogging(sanitizedName));
            }
        }
        
        // Sanitize description
        if (shop.getDescription() != null) {
            String originalDescription = shop.getDescription();
            String sanitizedDescription = htmlSanitizationService.sanitizeBasic(originalDescription);
            
            // Enforce length constraints
            if (sanitizedDescription.length() > 500) {
                sanitizedDescription = sanitizedDescription.substring(0, 500).trim();
            }
            
            if (!originalDescription.equals(sanitizedDescription)) {
                shop.setDescription(sanitizedDescription);
                wasModified = true;
                logger.debug("Sanitized shop description for item: {}", shop.getName());
            }
        }
        
        // Sanitize URLs
        if (shop.getImageUrl() != null) {
            String originalImageUrl = shop.getImageUrl();
            String sanitizedImageUrl = htmlSanitizationService.sanitizeUrl(originalImageUrl);
            
            if (!originalImageUrl.equals(sanitizedImageUrl)) {
                shop.setImageUrl(sanitizedImageUrl);
                wasModified = true;
                logger.debug("Sanitized imageUrl for shop item: {}", shop.getName());
            }
        }
        
        if (shop.getThumbnailUrl() != null) {
            String originalThumbnailUrl = shop.getThumbnailUrl();
            String sanitizedThumbnailUrl = htmlSanitizationService.sanitizeUrl(originalThumbnailUrl);
            
            if (!originalThumbnailUrl.equals(sanitizedThumbnailUrl)) {
                shop.setThumbnailUrl(sanitizedThumbnailUrl);
                wasModified = true;
                logger.debug("Sanitized thumbnailUrl for shop item: {}", shop.getName());
            }
        }
        
        // Validate Discord role ID
        if (shop.getDiscordRoleId() != null && !shop.getDiscordRoleId().isEmpty()) {
            String discordRoleId = shop.getDiscordRoleId().trim();
            if (!discordRoleId.matches("^\\d+$")) {
                shop.setDiscordRoleId("");
                wasModified = true;
                logger.debug("Cleaned invalid Discord role ID for shop item: {}", shop.getName());
            }
        }
        
        return wasModified;
    }
    
    /**
     * Get sanitization statistics for monitoring
     */
    @Transactional(readOnly = true)
    public SanitizationStats getSanitizationStats() {
        List<Shop> allShopItems = shopRepository.findAll();
        
        int totalItems = allShopItems.size();
        int itemsNeedingSanitization = 0;
        int unsafeNames = 0;
        int unsafeDescriptions = 0;
        int unsafeUrls = 0;
        
        for (Shop shop : allShopItems) {
            boolean needsSanitization = false;
            
            // Check name
            if (shop.getName() != null) {
                String sanitized = htmlSanitizationService.sanitizeStrict(shop.getName());
                if (!shop.getName().equals(sanitized)) {
                    unsafeNames++;
                    needsSanitization = true;
                }
            }
            
            // Check description
            if (shop.getDescription() != null) {
                String sanitized = htmlSanitizationService.sanitizeBasic(shop.getDescription());
                if (!shop.getDescription().equals(sanitized)) {
                    unsafeDescriptions++;
                    needsSanitization = true;
                }
            }
            
            // Check URLs
            if (shop.getImageUrl() != null) {
                String sanitized = htmlSanitizationService.sanitizeUrl(shop.getImageUrl());
                if (!shop.getImageUrl().equals(sanitized)) {
                    unsafeUrls++;
                    needsSanitization = true;
                }
            }
            
            if (shop.getThumbnailUrl() != null) {
                String sanitized = htmlSanitizationService.sanitizeUrl(shop.getThumbnailUrl());
                if (!shop.getThumbnailUrl().equals(sanitized)) {
                    unsafeUrls++;
                    needsSanitization = true;
                }
            }
            
            if (needsSanitization) {
                itemsNeedingSanitization++;
            }
        }
        
        return new SanitizationStats(
            totalItems,
            itemsNeedingSanitization,
            unsafeNames,
            unsafeDescriptions,
            unsafeUrls
        );
    }
    
    /**
     * Truncate content for safe logging
     */
    private String truncateForLogging(String content) {
        if (content == null) return "null";
        if (content.length() <= 50) return content;
        return content.substring(0, 50) + "...";
    }
    
    /**
     * Migration result class
     */
    public static class MigrationResult {
        private final int processedCount;
        private final int modifiedCount;
        private final int errorCount;
        private final boolean success;
        private final String message;
        
        public MigrationResult(int processedCount, int modifiedCount, int errorCount, boolean success, String message) {
            this.processedCount = processedCount;
            this.modifiedCount = modifiedCount;
            this.errorCount = errorCount;
            this.success = success;
            this.message = message;
        }
        
        public int getProcessedCount() { return processedCount; }
        public int getModifiedCount() { return modifiedCount; }
        public int getErrorCount() { return errorCount; }
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
    }
    
    /**
     * Sanitization statistics class
     */
    public static class SanitizationStats {
        private final int totalItems;
        private final int itemsNeedingSanitization;
        private final int unsafeNames;
        private final int unsafeDescriptions;
        private final int unsafeUrls;
        
        public SanitizationStats(int totalItems, int itemsNeedingSanitization, int unsafeNames, 
                               int unsafeDescriptions, int unsafeUrls) {
            this.totalItems = totalItems;
            this.itemsNeedingSanitization = itemsNeedingSanitization;
            this.unsafeNames = unsafeNames;
            this.unsafeDescriptions = unsafeDescriptions;
            this.unsafeUrls = unsafeUrls;
        }
        
        public int getTotalItems() { return totalItems; }
        public int getItemsNeedingSanitization() { return itemsNeedingSanitization; }
        public int getUnsafeNames() { return unsafeNames; }
        public int getUnsafeDescriptions() { return unsafeDescriptions; }
        public int getUnsafeUrls() { return unsafeUrls; }
        
        public double getSafetyPercentage() {
            if (totalItems == 0) return 100.0;
            return ((double) (totalItems - itemsNeedingSanitization) / totalItems) * 100.0;
        }
    }
} 