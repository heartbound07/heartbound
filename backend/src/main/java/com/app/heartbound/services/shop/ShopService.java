package com.app.heartbound.services.shop;

import com.app.heartbound.dto.UserProfileDTO;
import com.app.heartbound.dto.shop.ShopDTO;
import com.app.heartbound.dto.shop.UserInventoryDTO;
import com.app.heartbound.dto.shop.CaseContentsDTO;
import com.app.heartbound.dto.shop.CaseItemDTO;
import com.app.heartbound.dto.shop.RollResultDTO;
import com.app.heartbound.entities.Shop;
import com.app.heartbound.entities.User;
import com.app.heartbound.entities.CaseItem;
import com.app.heartbound.entities.UserInventoryItem;
import com.app.heartbound.enums.ShopCategory;
import com.app.heartbound.enums.ItemRarity;
import com.app.heartbound.exceptions.ResourceNotFoundException;
import com.app.heartbound.exceptions.shop.InsufficientCreditsException;
import com.app.heartbound.exceptions.shop.ItemAlreadyOwnedException;
import com.app.heartbound.exceptions.shop.ItemNotEquippableException;
import com.app.heartbound.exceptions.shop.RoleRequirementNotMetException;
import com.app.heartbound.exceptions.shop.CaseNotFoundException;
import com.app.heartbound.exceptions.shop.CaseNotOwnedException;
import com.app.heartbound.exceptions.shop.EmptyCaseException;
import com.app.heartbound.exceptions.shop.InvalidCaseContentsException;
import com.app.heartbound.exceptions.shop.ItemDeletionException;
import com.app.heartbound.exceptions.shop.ItemReferencedInCasesException;
import com.app.heartbound.repositories.UserRepository;
import com.app.heartbound.repositories.shop.ShopRepository;
import com.app.heartbound.repositories.shop.CaseItemRepository;
import com.app.heartbound.repositories.UserInventoryItemRepository;
import com.app.heartbound.repositories.RollAuditRepository;
import com.app.heartbound.entities.RollAudit;
import com.app.heartbound.services.UserService;
import com.app.heartbound.services.discord.DiscordService;
import com.app.heartbound.services.HtmlSanitizationService;
import com.app.heartbound.services.SecureRandomService;
import com.app.heartbound.services.RollVerificationService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.cache.annotation.Cacheable;

import java.util.*;
import java.util.stream.Collectors;
import java.util.Objects;
import java.time.LocalDateTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

@Service
public class ShopService {
    
    private final ShopRepository shopRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final DiscordService discordService;
    private final CaseItemRepository caseItemRepository;
    private final HtmlSanitizationService htmlSanitizationService;
    private final SecureRandomService secureRandomService;
    private final RollAuditRepository rollAuditRepository;
    private final RollVerificationService rollVerificationService;
    private final UserInventoryItemRepository userInventoryItemRepository;
    private static final Logger logger = LoggerFactory.getLogger(ShopService.class);
    
    @Autowired
    public ShopService(
        ShopRepository shopRepository,
        UserRepository userRepository,
        UserService userService,
        DiscordService discordService,
        CaseItemRepository caseItemRepository,
        HtmlSanitizationService htmlSanitizationService,
        SecureRandomService secureRandomService,
        RollAuditRepository rollAuditRepository,
        RollVerificationService rollVerificationService,
        UserInventoryItemRepository userInventoryItemRepository
    ) {
        this.shopRepository = shopRepository;
        this.userRepository = userRepository;
        this.userService = userService;
        this.discordService = discordService;
        this.caseItemRepository = caseItemRepository;
        this.htmlSanitizationService = htmlSanitizationService;
        this.secureRandomService = secureRandomService;
        this.rollAuditRepository = rollAuditRepository;
        this.rollVerificationService = rollVerificationService;
        this.userInventoryItemRepository = userInventoryItemRepository;
    }
    
    /**
     * Get all available shop items
     * @param userId Optional user ID to check ownership status
     * @param categoryStr Optional category filter as string
     * @return List of shop items
     */
    @Transactional(readOnly = true)
    public List<ShopDTO> getAvailableShopItems(String userId, String categoryStr) {
        List<Shop> items;
        LocalDateTime now = LocalDateTime.now();
        
        // Filter by category if provided
        if (categoryStr != null && !categoryStr.isEmpty()) {
            try {
                ShopCategory category = ShopCategory.valueOf(categoryStr);
                // Get active items that either have no expiry or haven't expired yet
                items = shopRepository.findByCategoryAndIsActiveTrue(category)
                    .stream()
                    .filter(item -> item.getExpiresAt() == null || item.getExpiresAt().isAfter(now))
                    .collect(Collectors.toList());
            } catch (IllegalArgumentException e) {
                // Invalid category string, return empty list
                logger.warn("Invalid category filter: {}", categoryStr);
                return Collections.emptyList();
            }
        } else {
            // Get all active items that either have no expiry or haven't expired yet
            items = shopRepository.findByIsActiveTrue()
                .stream()
                .filter(item -> item.getExpiresAt() == null || item.getExpiresAt().isAfter(now))
                .collect(Collectors.toList());
        }
        
        // Convert to DTOs and check ownership if userId is provided
        User user = null;
        if (userId != null) {
            user = userRepository.findById(userId).orElse(null);
        }
        
        final User finalUser = user;
        return items.stream()
            .map(item -> mapToShopDTO(item, finalUser))
            .collect(Collectors.toList());
    }
    
    /**
     * Get featured shop items for the main layout
     * @param userId User ID to check ownership status
     * @return List of featured shop items
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "featuredItems", key = "#userId")
    public List<ShopDTO> getFeaturedItems(String userId) {
        logger.debug("Getting featured items for user {}", userId);
        
        LocalDateTime now = LocalDateTime.now();
        
        // Get featured items that are active and not expired
        List<Shop> items = shopRepository.findByIsFeaturedTrueAndIsActiveTrueOrderByCreatedAtDesc()
            .stream()
            .filter(item -> item.getExpiresAt() == null || item.getExpiresAt().isAfter(now))
            .collect(Collectors.toList());
        
        // Get user for ownership checking
        User user = null;
        if (userId != null) {
            user = userRepository.findById(userId).orElse(null);
        }
        
        final User finalUser = user;
        return items.stream()
            .map(item -> mapToShopDTO(item, finalUser))
            .collect(Collectors.toList());
    }
    
    /**
     * Get daily shop items for the main layout
     * @param userId User ID to check ownership status
     * @return List of daily shop items
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "dailyItems", key = "#userId")
    public List<ShopDTO> getDailyItems(String userId) {
        logger.debug("Getting daily items for user {}", userId);
        
        LocalDateTime now = LocalDateTime.now();
        
        // Get daily items that are active and not expired
        List<Shop> items = shopRepository.findByIsDailyTrueAndIsActiveTrueOrderByCreatedAtDesc()
            .stream()
            .filter(item -> item.getExpiresAt() == null || item.getExpiresAt().isAfter(now))
            .collect(Collectors.toList());
        
        // Get user for ownership checking
        User user = null;
        if (userId != null) {
            user = userRepository.findById(userId).orElse(null);
        }
        
        final User finalUser = user;
        return items.stream()
            .map(item -> mapToShopDTO(item, finalUser))
            .collect(Collectors.toList());
    }
    
    /**
     * Get a shop item by ID
     * @param itemId Item ID
     * @param userId Optional user ID to check ownership status
     * @return ShopDTO
     */
    public ShopDTO getShopItemById(UUID itemId, String userId) {
        Shop item = shopRepository.findById(itemId)
            .orElseThrow(() -> new ResourceNotFoundException("Shop item not found with ID: " + itemId));
        
        User user = null;
        if (userId != null) {
            user = userRepository.findById(userId).orElse(null);
        }
        
        return mapToShopDTO(item, user);
    }
    
    /**
     * Purchase an item for a user
     * @param userId User ID
     * @param itemId Item ID
     * @return Updated UserProfileDTO
     */
    @Transactional
    public UserProfileDTO purchaseItem(String userId, UUID itemId) {
        return purchaseItem(userId, itemId, 1);
    }
    
    /**
     * Purchase an item for a user with quantity
     * @param userId User ID
     * @param itemId Item ID
     * @param quantity Quantity to purchase (for cases)
     * @return Updated UserProfileDTO
     */
    @Transactional
    public UserProfileDTO purchaseItem(String userId, UUID itemId, Integer quantity) {
        logger.debug("Processing purchase of item {} for user {} with quantity {}", itemId, userId, quantity);
        
        // Validate quantity
        if (quantity == null || quantity < 1 || quantity > 10) {
            throw new IllegalArgumentException("Quantity must be between 1 and 10");
        }
        
        // Get user and item
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));
        
        Shop item = shopRepository.findById(itemId)
            .orElseThrow(() -> new ResourceNotFoundException("Shop item not found with ID: " + itemId));
        
        // Validation checks
        if (!item.getIsActive()) {
            throw new ResourceNotFoundException("Item is not available for purchase");
        }
        
        // For non-case items, enforce ownership check
        if (item.getCategory() != ShopCategory.CASE && user.hasItem(itemId)) {
            throw new ItemAlreadyOwnedException("User already owns this item");
        }
        
        // For cases, check quantity-based ownership for proper inventory management
        if (item.getCategory() == ShopCategory.CASE) {
            // Cases can be purchased multiple times, no ownership check needed
            logger.debug("Purchasing {} cases for user {}", quantity, userId);
        }
        
        // For non-case items, enforce quantity = 1
        if (item.getCategory() != ShopCategory.CASE && quantity > 1) {
            throw new IllegalArgumentException("Non-case items can only be purchased with quantity 1");
        }
        
        // Calculate total cost
        int totalCost = item.getPrice() * quantity;
        
        if (user.getCredits() < totalCost) {
            throw new InsufficientCreditsException(
                "Insufficient credits. Required: " + totalCost + ", Available: " + user.getCredits()
            );
        }
        
        if (item.getRequiredRole() != null && !user.hasRole(item.getRequiredRole())) {
            throw new RoleRequirementNotMetException(
                "This item requires the " + item.getRequiredRole() + " role"
            );
        }
        
        // Process purchase
        user.setCredits(user.getCredits() - totalCost);
        
        try {
            // Add items to inventory - use quantity-based system for cases
            if (item.getCategory() == ShopCategory.CASE) {
                logger.debug("Adding {} cases to user inventory using quantity-based system", quantity);
                user.addItemWithQuantity(item, quantity);
            } else {
                logger.debug("Adding {} instances of item {} to user inventory", quantity, itemId);
                for (int i = 0; i < quantity; i++) {
                    user.addItem(item);
                }
            }
            
            // Save user with updated inventory
            logger.debug("Saving user {} with updated inventory", userId);
            user = userRepository.save(user);
            
            logger.info("User {} successfully purchased {} x{} (total cost: {})", userId, itemId, quantity, totalCost);
            
            // Return updated profile
            return userService.mapToProfileDTO(user);
        } catch (Exception e) {
            logger.error("Error during purchase process for user {} and item {}: {}", userId, itemId, e.getMessage(), e);
            throw new RuntimeException("An error occurred while processing your purchase", e);
        }
    }
    
    /**
     * Equips an item for a user
     * @param userId User ID
     * @param itemId Item ID
     * @return Updated UserProfileDTO
     */
    @Transactional
    public UserProfileDTO equipItem(String userId, UUID itemId) {
        logger.debug("Equipping item {} for user {}", itemId, userId);
        
        // Get user and item
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));
        
        Shop item = shopRepository.findById(itemId)
            .orElseThrow(() -> new ResourceNotFoundException("Shop item not found with ID: " + itemId));
        
        // Check if user owns the item
        if (!user.hasItem(itemId)) {
            throw new ItemAlreadyOwnedException("You don't own this item");
        }
        
        // Set the item as equipped based on its category
        ShopCategory category = item.getCategory();
        if (category == null) {
            throw new ItemNotEquippableException("This item cannot be equipped");
        }
        
        // Special handling for BADGE category - allows multiple equipped items
        if (category == ShopCategory.BADGE) {
            // Check if badge is already equipped
            if (user.isBadgeEquipped(itemId)) {
                logger.debug("Badge {} is already equipped for user {}", itemId, userId);
                return userService.mapToProfileDTO(user);
            }
            
            // Add badge to equipped badges
            user.addEquippedBadge(itemId);
            logger.debug("Added badge {} to user {}'s equipped badges", itemId, userId);
            
            // Apply Discord role if applicable
            if (item.getDiscordRoleId() != null && !item.getDiscordRoleId().isEmpty()) {
                logger.debug("Adding Discord role {} for user {} for badge", 
                           item.getDiscordRoleId(), userId);
                boolean grantSuccess = discordService.grantRole(userId, item.getDiscordRoleId());
                if (!grantSuccess) {
                    logger.warn("Failed to grant Discord role {} to user {} for badge", 
                              item.getDiscordRoleId(), userId);
                }
            }
        } else if (category == ShopCategory.USER_COLOR) {
            // Handle Discord role management for USER_COLOR items
            // Check if there was a previously equipped item of the same category
            UUID previousItemId = user.getEquippedItemIdByCategory(category);
            if (previousItemId != null && !previousItemId.equals(itemId)) {
                // Find the previous item to get its Discord role ID and handle removal synchronously
                shopRepository.findById(previousItemId).ifPresent(previousItem -> {
                    String previousRoleId = previousItem.getDiscordRoleId();
                    if (previousRoleId != null && !previousRoleId.isEmpty()) {
                        logger.debug("Removing previous Discord role {} from user {} before equipping new item", 
                                previousRoleId, userId);
                        
                        // Ensure role removal occurs and log any failures
                        boolean removalSuccess = discordService.removeRole(userId, previousRoleId);
                        if (!removalSuccess) {
                            // Log the issue but continue with equipping the new item
                            logger.warn("Failed to remove previous Discord role {} from user {}. " +
                                    "Continuing with equipping new item.", previousRoleId, userId);
                        } else {
                            logger.debug("Successfully removed previous Discord role {} from user {}", 
                                    previousRoleId, userId);
                        }
                    }
                });
            }
            
            // Now unequip the previous item and set the new one
            user.setEquippedItemIdByCategory(category, itemId);
            
            // Grant the new role if it has a discordRoleId
            String newRoleId = item.getDiscordRoleId();
            if (newRoleId != null && !newRoleId.isEmpty()) {
                logger.debug("Granting Discord role {} to user {} for equipped item", newRoleId, userId);
                boolean grantSuccess = discordService.grantRole(userId, newRoleId);
                if (!grantSuccess) {
                    logger.warn("Failed to grant Discord role {} to user {}", newRoleId, userId);
                }
            }
        } else {
            // For other categories, simply update the equipped item
            user.setEquippedItemIdByCategory(category, itemId);
        }
        
        // Save user changes
        user = userRepository.save(user);
        
        // Return updated profile
        return userService.mapToProfileDTO(user);
    }
    
    /**
     * Unequips an item for a user by category
     * @param userId User ID
     * @param category Shop category to unequip
     * @return Updated UserProfileDTO
     */
    @Transactional
    public UserProfileDTO unequipItem(String userId, ShopCategory category) {
        logger.debug("Unequipping item of category {} for user {}", category, userId);
        
        // Get user
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));
        
        // Badge category requires specific badge ID to unequip
        if (category == ShopCategory.BADGE) {
            throw new UnsupportedOperationException("BADGE category requires a specific badge ID to unequip. Use unequipBadge(userId, badgeId) instead.");
        }
        
        // Get the currently equipped item ID BEFORE unequipping
        UUID currentlyEquippedItemId = user.getEquippedItemIdByCategory(category);
        
        // Unequip the item in the specified category
        user.setEquippedItemIdByCategory(category, null);
        
        // If this is a USER_COLOR category, remove associated Discord role
        if (category == ShopCategory.USER_COLOR && currentlyEquippedItemId != null) {
            shopRepository.findById(currentlyEquippedItemId).ifPresent(equippedItem -> {
                if (equippedItem.getDiscordRoleId() != null && !equippedItem.getDiscordRoleId().isEmpty()) {
                    logger.debug("Removing Discord role {} from user {} for unequipped item", 
                                equippedItem.getDiscordRoleId(), userId);
                    discordService.removeRole(userId, equippedItem.getDiscordRoleId());
                }
            });
        }
        
        // Save user changes
        user = userRepository.save(user);
        
        // Return updated profile
        return userService.mapToProfileDTO(user);
    }
    
    /**
     * Unequips a specific badge for a user
     * @param userId User ID
     * @param badgeId Badge ID to unequip
     * @return Updated UserProfileDTO
     */
    @Transactional
    public UserProfileDTO unequipBadge(String userId, UUID badgeId) {
        logger.debug("Unequipping badge {} for user {}", badgeId, userId);
        
        // Get user
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));
        
        // Check if user has the badge equipped
        if (!user.isBadgeEquipped(badgeId)) {
            logger.debug("Badge {} is not equipped for user {}", badgeId, userId);
            return userService.mapToProfileDTO(user);
        }
        
        // Get badge item for possible Discord role handling
        Shop badge = shopRepository.findById(badgeId)
            .orElseThrow(() -> new ResourceNotFoundException("Badge not found with ID: " + badgeId));
        
        // Check that it's actually a badge
        if (badge.getCategory() != ShopCategory.BADGE) {
            throw new ItemNotEquippableException("Item is not a badge");
        }
        
        // Remove the badge from equipped badges
        user.removeEquippedBadge(badgeId);
        
        // Handle Discord role if applicable
        if (badge.getDiscordRoleId() != null && !badge.getDiscordRoleId().isEmpty()) {
            logger.debug("Removing Discord role {} from user {} for unequipped badge", 
                        badge.getDiscordRoleId(), userId);
            boolean removalSuccess = discordService.removeRole(userId, badge.getDiscordRoleId());
            if (!removalSuccess) {
                logger.warn("Failed to remove Discord role {} from user {} for badge", 
                          badge.getDiscordRoleId(), userId);
            }
        }
        
        // Save user changes
        user = userRepository.save(user);
        
        // Return updated profile
        return userService.mapToProfileDTO(user);
    }
    
    /**
     * Gets a user's inventory with equipped status
     * @param userId User ID
     * @return User's inventory with equipped status
     */
    public UserInventoryDTO getUserInventory(String userId) {
        logger.debug("Getting inventory for user {}", userId);
        
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));
        
        Set<Shop> inventory = user.getInventory();
        Set<UserInventoryItem> inventoryItems = user.getInventoryItems();
        
        Set<ShopDTO> itemDTOs = new HashSet<>();
        
        // Process regular inventory items (non-cases)
        Map<UUID, List<Shop>> itemGroups = inventory.stream()
            .filter(item -> item.getCategory() != ShopCategory.CASE) // Exclude cases from regular inventory
            .collect(Collectors.groupingBy(Shop::getId));
        
        for (Map.Entry<UUID, List<Shop>> entry : itemGroups.entrySet()) {
            Shop item = entry.getValue().get(0);
            int quantity = entry.getValue().size();
            
            ShopDTO dto = mapToShopDTO(item, user);
            dto.setQuantity(quantity);
            
            // Add equipped status
            if (item.getCategory() != null) {
                if (item.getCategory() == ShopCategory.BADGE) {
                    dto.setEquipped(user.isBadgeEquipped(item.getId()));
                } else {
                    UUID equippedItemId = user.getEquippedItemIdByCategory(item.getCategory());
                    dto.setEquipped(equippedItemId != null && equippedItemId.equals(item.getId()));
                }
            }
            
            itemDTOs.add(dto);
        }
        
        // Process quantity-based inventory items (cases)
        if (inventoryItems != null) {
            for (UserInventoryItem invItem : inventoryItems) {
                if (invItem.getQuantity() > 0) {
                    Shop item = invItem.getItem();
                    ShopDTO dto = mapToShopDTO(item, user);
                    dto.setQuantity(invItem.getQuantity());
                    dto.setEquipped(false); // Cases cannot be equipped
                    
                    itemDTOs.add(dto);
                }
            }
        }
        
        return UserInventoryDTO.builder()
            .items(itemDTOs)
            .build();
    }
    
    /**
     * Map a Shop entity to a ShopDTO
     * @param shop Shop entity
     * @param user Optional user to check ownership status
     * @return ShopDTO
     */
    private ShopDTO mapToShopDTO(Shop shop, User user) {
        boolean owned = false;
        
        // For cases, never show as owned since they can be purchased multiple times
        if (shop.getCategory() != ShopCategory.CASE && user != null) {
            // This accesses the lazy-loaded inventory, which works when inside @Transactional
            owned = user.getInventory().stream()
                .anyMatch(item -> item.getId().equals(shop.getId()));
        }
        
        // Check if this is a case and get contents count
        boolean isCase = shop.getCategory() == ShopCategory.CASE;
        Integer caseContentsCount = 0;
        if (isCase) {
            caseContentsCount = Math.toIntExact(caseItemRepository.countByCaseShopItem(shop));
        }
        
        return ShopDTO.builder()
            .id(shop.getId())
            .name(shop.getName())
            .description(shop.getDescription())
            .price(shop.getPrice())
            .category(shop.getCategory())
            .imageUrl(shop.getImageUrl())
            .thumbnailUrl(shop.getThumbnailUrl())
            .requiredRole(shop.getRequiredRole())
            .owned(owned)
            .expiresAt(shop.getExpiresAt())
            .discordRoleId(shop.getDiscordRoleId())
            .rarity(shop.getRarity() != null ? shop.getRarity() : ItemRarity.COMMON)
            .isCase(isCase)
            .caseContentsCount(caseContentsCount)
            .isFeatured(shop.getIsFeatured())
            .isDaily(shop.getIsDaily())
            .build();
    }
    
    /**
     * Create a new shop item
     * @param shopDTO Shop item DTO
     * @return Created shop item
     */
    @Transactional
    public Shop createShopItem(ShopDTO shopDTO) {
        logger.debug("Creating new shop item: {} with active status: {}", shopDTO.getName(), shopDTO.isActive());
        
        // Sanitize input data before creating entity
        String sanitizedName = htmlSanitizationService.sanitizeStrict(shopDTO.getName());
        String sanitizedDescription = htmlSanitizationService.sanitizeBasic(shopDTO.getDescription());
        
        // Handle imageUrl sanitization based on category
        String sanitizedImageUrl;
        if (shopDTO.getCategory() == ShopCategory.USER_COLOR) {
            // For USER_COLOR items, imageUrl contains a hex color value, not a URL
            sanitizedImageUrl = sanitizeColorValue(shopDTO.getImageUrl());
        } else {
            // For other categories, treat as URL
            sanitizedImageUrl = htmlSanitizationService.sanitizeUrl(shopDTO.getImageUrl());
        }
        
        String sanitizedThumbnailUrl = htmlSanitizationService.sanitizeUrl(shopDTO.getThumbnailUrl());
        
        // Validate required fields after sanitization
        if (sanitizedName == null || sanitizedName.trim().isEmpty()) {
            throw new IllegalArgumentException("Item name cannot be empty after sanitization");
        }
        
        if (sanitizedName.length() > 100) {
            throw new IllegalArgumentException("Item name exceeds maximum length of 100 characters after sanitization");
        }
        
        if (sanitizedDescription != null && sanitizedDescription.length() > 500) {
            throw new IllegalArgumentException("Description exceeds maximum length of 500 characters after sanitization");
        }
        
        // Log sanitization if content was modified
        if (!sanitizedName.equals(shopDTO.getName())) {
            logger.info("Shop item name sanitized during creation: '{}' -> '{}'", shopDTO.getName(), sanitizedName);
        }
        if (shopDTO.getDescription() != null && !sanitizedDescription.equals(shopDTO.getDescription())) {
            logger.info("Shop item description sanitized during creation");
        }
        
        Shop newItem = Shop.builder()
            .name(sanitizedName)
            .description(sanitizedDescription)
            .price(shopDTO.getPrice())
            .category(shopDTO.getCategory())
            .imageUrl(sanitizedImageUrl)
            .requiredRole(shopDTO.getRequiredRole())
            .isActive(shopDTO.isActive())
            .expiresAt(shopDTO.getExpiresAt())
            .discordRoleId(shopDTO.getDiscordRoleId())
            .rarity(shopDTO.getRarity() != null ? shopDTO.getRarity() : ItemRarity.COMMON)
            .thumbnailUrl(sanitizedThumbnailUrl)
            .isFeatured(shopDTO.getIsFeatured())
            .isDaily(shopDTO.getIsDaily())
            .build();
        
        logger.debug("Creating new shop item with sanitized content");
        
        return shopRepository.save(newItem);
    }
    
    /**
     * Update an existing shop item
     * @param itemId Item ID
     * @param shopDTO Updated shop item data
     * @return Updated shop item
     */
    @Transactional
    public Shop updateShopItem(UUID itemId, ShopDTO shopDTO) {
        logger.debug("Updating shop item {}: {} with active status: {}", itemId, shopDTO.getName(), shopDTO.isActive());
        
        Shop existingItem = shopRepository.findById(itemId)
            .orElseThrow(() -> new ResourceNotFoundException("Shop item not found with ID: " + itemId));
        
        // Sanitize input data before updating entity
        String sanitizedName = htmlSanitizationService.sanitizeStrict(shopDTO.getName());
        String sanitizedDescription = htmlSanitizationService.sanitizeBasic(shopDTO.getDescription());
        
        // Handle imageUrl sanitization based on category
        String sanitizedImageUrl;
        if (shopDTO.getCategory() == ShopCategory.USER_COLOR) {
            // For USER_COLOR items, imageUrl contains a hex color value, not a URL
            sanitizedImageUrl = sanitizeColorValue(shopDTO.getImageUrl());
        } else {
            // For other categories, treat as URL
            sanitizedImageUrl = htmlSanitizationService.sanitizeUrl(shopDTO.getImageUrl());
        }
        
        String sanitizedThumbnailUrl = htmlSanitizationService.sanitizeUrl(shopDTO.getThumbnailUrl());
        
        // Validate required fields after sanitization
        if (sanitizedName == null || sanitizedName.trim().isEmpty()) {
            throw new IllegalArgumentException("Item name cannot be empty after sanitization");
        }
        
        if (sanitizedName.length() > 100) {
            throw new IllegalArgumentException("Item name exceeds maximum length of 100 characters after sanitization");
        }
        
        if (sanitizedDescription != null && sanitizedDescription.length() > 500) {
            throw new IllegalArgumentException("Description exceeds maximum length of 500 characters after sanitization");
        }
        
        // Log sanitization if content was modified
        if (!sanitizedName.equals(shopDTO.getName())) {
            logger.info("Shop item name sanitized during update: '{}' -> '{}'", shopDTO.getName(), sanitizedName);
        }
        if (shopDTO.getDescription() != null && !sanitizedDescription.equals(shopDTO.getDescription())) {
            logger.info("Shop item description sanitized during update");
        }
        
        // Update fields with sanitized content
        existingItem.setName(sanitizedName);
        existingItem.setDescription(sanitizedDescription);
        existingItem.setPrice(shopDTO.getPrice());
        existingItem.setCategory(shopDTO.getCategory());
        existingItem.setImageUrl(sanitizedImageUrl);
        existingItem.setRequiredRole(shopDTO.getRequiredRole());
        existingItem.setExpiresAt(shopDTO.getExpiresAt());
        existingItem.setIsActive(shopDTO.isActive());
        existingItem.setDiscordRoleId(shopDTO.getDiscordRoleId());
        existingItem.setRarity(shopDTO.getRarity() != null ? shopDTO.getRarity() : ItemRarity.COMMON);
        existingItem.setThumbnailUrl(sanitizedThumbnailUrl);
        existingItem.setIsFeatured(shopDTO.getIsFeatured());
        existingItem.setIsDaily(shopDTO.getIsDaily());
        
        logger.debug("Updating shop item with ID: {} with sanitized content", existingItem.getId());
        
        return shopRepository.save(existingItem);
    }
    
    /**
     * Delete a shop item completely with cascade delete handling
     * @param itemId Item ID
     * @throws ItemDeletionException if the item cannot be deleted
     * @throws ItemReferencedInCasesException if the item is referenced in cases (with cascade info)
     */
    @Transactional
    public void deleteShopItem(UUID itemId) {
        logger.debug("Attempting to delete shop item {}", itemId);
        
        Shop item = shopRepository.findById(itemId)
            .orElseThrow(() -> new ResourceNotFoundException("Shop item not found with ID: " + itemId));
        
        try {
            // Step 1: Check for case references and handle cascade deletion
            handleCaseReferences(item);
            
            // Step 2: Remove from user inventories (both old and new inventory systems)
            cleanupUserInventories(item);
            
            // Step 3: Perform the actual deletion
            shopRepository.delete(item);
            
            logger.info("Successfully deleted shop item {} with cascade cleanup", itemId);
            
        } catch (Exception e) {
            logger.error("Failed to delete shop item {}: {}", itemId, e.getMessage(), e);
            
            // If it's one of our custom exceptions, re-throw it
            if (e instanceof ItemDeletionException) {
                throw e;
            }
            
            // For any other exceptions, wrap in our custom exception
            throw new ItemDeletionException(
                "Failed to delete item due to an unexpected error: " + e.getMessage(), e
            );
        }
    }
    
    /**
     * Handle case references during item deletion
     * @param item The item being deleted
     * @throws ItemReferencedInCasesException if the item has case references that need attention
     */
    private void handleCaseReferences(Shop item) {
        logger.debug("Checking case references for item {}", item.getId());
        
        // Check if this item is a case itself
        if (item.getCategory() == ShopCategory.CASE) {
            List<CaseItem> caseContents = caseItemRepository.findByCaseShopItem(item);
            if (!caseContents.isEmpty()) {
                logger.info("Deleting {} case items for case {}", caseContents.size(), item.getId());
                caseItemRepository.deleteByCaseShopItem(item);
            }
        }
        
        // Check if this item is contained in any cases
        List<CaseItem> containingCases = caseItemRepository.findByContainedItem(item);
        if (!containingCases.isEmpty()) {
            // Extract the case IDs for reporting
            List<UUID> caseIds = containingCases.stream()
                .map(caseItem -> caseItem.getCaseShopItem().getId())
                .distinct()
                .collect(Collectors.toList());
                
            logger.info("Item {} is contained in {} cases. Removing from cases: {}", 
                       item.getId(), caseIds.size(), caseIds);
            
            // Remove the item from all cases that contain it
            caseItemRepository.deleteByContainedItem(item);
            
            // Log the cascade operation for audit purposes
            logger.warn("Cascade deletion: Removed item {} from {} cases during deletion. " +
                       "This may affect case drop rates and should be reviewed.", 
                       item.getId(), caseIds.size());
        }
    }
    
    /**
     * Clean up user inventories for the item being deleted
     * @param item The item being deleted
     */
    private void cleanupUserInventories(Shop item) {
        logger.debug("Cleaning up user inventories for item {}", item.getId());
        
        // Clean up the new UserInventoryItem system
        userInventoryItemRepository.deleteByItem(item);
        
        // Clean up the legacy User.inventory system (if still in use)
        List<User> usersWithItem = userRepository.findByInventoryContaining(item);
        if (!usersWithItem.isEmpty()) {
            logger.info("Removing item {} from {} users' legacy inventory", item.getId(), usersWithItem.size());
            for (User user : usersWithItem) {
                user.getInventory().removeIf(i -> i.getId().equals(item.getId()));
                userRepository.save(user);
            }
        }
    }
    
    /**
     * Sanitize and validate a hex color value for USER_COLOR items
     * @param colorValue The color value to sanitize
     * @return Sanitized color value or null if invalid
     */
    private String sanitizeColorValue(String colorValue) {
        if (colorValue == null || colorValue.trim().isEmpty()) {
            return null;
        }
        
        String trimmed = colorValue.trim();
        
        // Check if it's a valid hex color format
        if (trimmed.matches("^#[0-9A-Fa-f]{6}$")) {
            return trimmed.toUpperCase(); // Normalize to uppercase
        }
        
        // Log invalid color attempts for security monitoring
        logger.warn("Invalid color value rejected for USER_COLOR item: {}", trimmed);
        return null;
    }
    
    /**
     * Get all shop items including inactive ones (admin only)
     * @return List of all shop items
     */
    public List<ShopDTO> getAllShopItems() {
        List<Shop> items = shopRepository.findAll();
        LocalDateTime now = LocalDateTime.now();
        
        return items.stream()
            .map(item -> {
                ShopDTO dto = mapToShopDTO(item, null);
                // Add isActive status to the DTO for admin UI
                dto.setActive(item.getIsActive());
                
                // Determine if item is expired
                boolean expired = item.getExpiresAt() != null && 
                                 item.getExpiresAt().isBefore(now);
                dto.setExpired(expired);
                
                return dto;
            })
            .collect(Collectors.toList());
    }
    
    /**
     * Get all distinct shop categories from active items
     * @return List of unique category names as strings
     */
    public List<String> getShopCategories() {
        logger.debug("Fetching all distinct shop categories");
        
        // Get all active shop items
        List<Shop> items = shopRepository.findByIsActiveTrue();
        
        // Extract unique categories and convert to strings
        return items.stream()
            .map(Shop::getCategory)
            .filter(Objects::nonNull)
            .distinct()
            .map(ShopCategory::name)  // Convert enum to string
            .sorted()  // Optional: sort categories alphabetically
            .collect(Collectors.toList());
    }
    
    // ===== CASE-RELATED METHODS =====
    
    /**
     * Open a case and return a random item based on drop rates
     * @param userId User ID
     * @param caseId Case ID to open
     * @return RollResultDTO with the won item and roll details
     */
    @Transactional
    public RollResultDTO openCase(String userId, UUID caseId) {
        logger.debug("Opening case {} for user {}", caseId, userId);
        long startTime = System.currentTimeMillis();
        
        // 1. Verify user exists
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));
        
        // 2. Verify case exists and is actually a case
        Shop caseItem = shopRepository.findById(caseId)
            .orElseThrow(() -> new CaseNotFoundException("Case not found with ID: " + caseId));
        
        if (caseItem.getCategory() != ShopCategory.CASE) {
            throw new IllegalArgumentException("Item is not a case");
        }
        
        // 3. Verify user owns the case
        if (!user.hasItemWithQuantity(caseId)) {
            throw new CaseNotOwnedException("You do not own this case");
        }
        
        // Check that user has at least one case to open
        int caseQuantity = user.getItemQuantity(caseId);
        if (caseQuantity < 1) {
            throw new CaseNotOwnedException("You do not have any cases of this type to open");
        }
        
        // 4. Get case contents with drop rates and validate
        List<CaseItem> caseItems = caseItemRepository.findByCaseIdOrderByDropRateDesc(caseId);
        
        if (caseItems.isEmpty()) {
            throw new EmptyCaseException("Case has no contents to roll");
        }
        
        // 5. Validate case contents (drop rates should sum to 100)
        Integer totalDropRate = caseItemRepository.sumDropRatesByCaseId(caseId);
        if (totalDropRate == null || totalDropRate != 100) {
            throw new InvalidCaseContentsException(
                "Invalid case contents - drop rates sum to " + totalDropRate + "% instead of 100%"
            );
        }
        
        // 6. Generate secure random seed for this roll
        String rollSeed = secureRandomService.generateRollSeed();
        String rollSeedHash = generateSeedHash(rollSeed);
        
        // 7. Generate secure roll value for animation sync (0-99)
        int rollValue = secureRandomService.getSecureInt(100); // Secure random for animation sync
        
        // 8. Perform secure weighted random selection using the pre-generated roll value
        Shop wonItem = selectItemByDropRateSecureWithRoll(caseItems, rollValue);
        
        // 9. Find the drop rate for the won item
        int wonItemDropRate = caseItems.stream()
            .filter(item -> item.getContainedItem().getId().equals(wonItem.getId()))
            .mapToInt(CaseItem::getDropRate)
            .findFirst()
            .orElse(0);
        
        // 10. Check if user already owns the won item
        boolean alreadyOwned = user.hasItem(wonItem.getId());
        
        // 11. Store credits and XP before operation
        int creditsBefore = user.getCredits();
        int xpBefore = user.getExperience();
        
        // 12. Calculate and award compensation for duplicate items
        boolean compensationAwarded = false;
        int compensatedCredits = 0;
        int compensatedXp = 0;
        
        if (alreadyOwned) {
            // Calculate compensation based on item rarity
            compensatedCredits = calculateCompensationCredits(wonItem.getRarity());
            compensatedXp = calculateCompensationXp(wonItem.getRarity());
            
            // Award compensation
            user.setCredits(user.getCredits() + compensatedCredits);
            user.setExperience(user.getExperience() + compensatedXp);
            compensationAwarded = true;
            
            logger.info("Awarded compensation for duplicate item {} to user {}: {} credits, {} XP", 
                       wonItem.getId(), userId, compensatedCredits, compensatedXp);
        }
        
        // 13. Remove case from user inventory (consume it)
        user.removeItemQuantity(caseId, 1);
        
        // 14. Add won item to user inventory (only if not already owned)
        if (!alreadyOwned) {
            user.addItem(wonItem);
        }
        
        // 15. Save user changes
        user = userRepository.save(user);
        int creditsAfter = user.getCredits();
        
        // 16. Calculate processing time
        long processingTime = System.currentTimeMillis() - startTime;
        
        // 17. Create audit record
        RollAudit auditRecord = new RollAudit(
            userId, caseId, caseItem.getName(), wonItem.getId(), wonItem.getName(),
            rollValue, rollSeedHash, wonItemDropRate, totalDropRate, caseItems.size(),
            alreadyOwned, getClientIp(), getUserAgent(), getSessionId(),
            creditsBefore, creditsAfter
        );
        auditRecord.setProcessingTimeMs(processingTime);
        
        // Set timestamp manually before generating statistical hash
        LocalDateTime now = LocalDateTime.now();
        auditRecord.setRollTimestamp(now);
        auditRecord.setStatisticalHash(rollVerificationService.generateStatisticalHash(auditRecord));
        
        // 18. Save audit record
        rollAuditRepository.save(auditRecord);
        
        logger.info("User {} opened case {} and won item {} (already owned: {}{}) - Roll: {}, Seed: {}", 
                   userId, caseId, wonItem.getId(), alreadyOwned, 
                   compensationAwarded ? ", compensation awarded: " + compensatedCredits + " credits, " + compensatedXp + " XP" : "",
                   rollValue, rollSeedHash.substring(0, 8) + "...");
        
        // 19. Return result
        return RollResultDTO.builder()
            .caseId(caseId)
            .caseName(caseItem.getName())
            .wonItem(mapToShopDTO(wonItem, user))
            .rollValue(rollValue)
            .rolledAt(LocalDateTime.now())
            .alreadyOwned(alreadyOwned)
            .compensationAwarded(compensationAwarded)
            .compensatedCredits(compensationAwarded ? compensatedCredits : null)
            .compensatedXp(compensationAwarded ? compensatedXp : null)
            .build();
    }
    
    /**
     * Calculate credit compensation for duplicate items based on rarity
     * @param rarity Item rarity
     * @return Credit compensation amount
     */
    private int calculateCompensationCredits(ItemRarity rarity) {
        if (rarity == null) {
            rarity = ItemRarity.COMMON;
        }
        
        switch (rarity) {
            case LEGENDARY:
                return 500;
            case EPIC:
                return 300;
            case RARE:
                return 150;
            case UNCOMMON:
                return 100;
            case COMMON:
            default:
                return 50;
        }
    }
    
    /**
     * Calculate XP compensation for duplicate items based on rarity
     * @param rarity Item rarity
     * @return XP compensation amount
     */
    private int calculateCompensationXp(ItemRarity rarity) {
        if (rarity == null) {
            rarity = ItemRarity.COMMON;
        }
        
        switch (rarity) {
            case LEGENDARY:
                return 350;
            case EPIC:
                return 250;
            case RARE:
                return 100;
            case UNCOMMON:
                return 25;
            case COMMON:
            default:
                return 15;
        }
    }
    
    /**
     * Perform secure weighted random selection based on drop rates
     * @param caseItems List of case items with drop rates
     * @return Selected shop item
     */
    private Shop selectItemByDropRateSecure(List<CaseItem> caseItems) {
        // Use secure random service for weighted selection
        return secureRandomService.selectWeightedRandom(
            caseItems,
            100, // Total weight should always be 100 for drop rates
            CaseItem::getDropRate
        ).getContainedItem();
    }
    
    /**
     * Perform secure weighted random selection based on drop rates using a pre-generated roll value
     * This method ensures animation synchronization by using the same roll value for both selection and animation
     * @param caseItems List of case items with drop rates
     * @param rollValue Pre-generated roll value (0-99)
     * @return Selected shop item
     */
    private Shop selectItemByDropRateSecureWithRoll(List<CaseItem> caseItems, int rollValue) {
        // Use secure random service for weighted selection with pre-generated roll value
        return secureRandomService.selectWeightedRandom(
            caseItems,
            100, // Total weight should always be 100 for drop rates
            rollValue, // Use the pre-generated roll value
            CaseItem::getDropRate
        ).getContainedItem();
    }
    
    /**
     * Legacy method - kept for backward compatibility but using secure random
     * @deprecated Use selectItemByDropRateSecure instead
     */
    @Deprecated
    private Shop selectItemByDropRate(List<CaseItem> caseItems) {
        return selectItemByDropRateSecure(caseItems);
    }
    
    /**
     * Get the contents of a case with drop rates
     * @param caseId Case ID
     * @return CaseContentsDTO with all possible items and their drop rates
     */
    @Transactional(readOnly = true)
    public CaseContentsDTO getCaseContents(UUID caseId) {
        logger.debug("Getting contents for case {}", caseId);
        
        Shop caseItem = shopRepository.findById(caseId)
            .orElseThrow(() -> new ResourceNotFoundException("Case not found with ID: " + caseId));
        
        if (caseItem.getCategory() != ShopCategory.CASE) {
            throw new IllegalArgumentException("Item is not a case");
        }
        
        List<CaseItem> caseItems = caseItemRepository.findByCaseIdOrderByDropRateDesc(caseId);
        
        List<CaseItemDTO> itemDTOs = caseItems.stream()
            .map(this::mapToCaseItemDTO)
            .collect(Collectors.toList());
        
        Integer totalDropRate = caseItemRepository.sumDropRatesByCaseId(caseId);
        
        return CaseContentsDTO.builder()
            .caseId(caseId)
            .caseName(caseItem.getName())
            .items(itemDTOs)
            .totalDropRate(totalDropRate != null ? totalDropRate : 0)
            .itemCount(itemDTOs.size())
            .build();
    }
    
    /**
     * Update case contents (admin only)
     * @param caseId Case ID
     * @param caseItems List of items to include in the case with drop rates
     */
    @Transactional
    public void updateCaseContents(UUID caseId, List<CaseItemDTO> caseItems) {
        logger.debug("Updating contents for case {} with {} items", caseId, caseItems.size());
        
        Shop caseItem = shopRepository.findById(caseId)
            .orElseThrow(() -> new ResourceNotFoundException("Case not found with ID: " + caseId));
        
        if (caseItem.getCategory() != ShopCategory.CASE) {
            throw new IllegalArgumentException("Item is not a case");
        }
        
        // Validate drop rates sum to 100
        int totalDropRate = caseItems.stream()
            .mapToInt(CaseItemDTO::getDropRate)
            .sum();
        
        if (totalDropRate != 100) {
            throw new IllegalArgumentException("Drop rates must sum to 100, current sum: " + totalDropRate);
        }
        
        // Validate all contained items exist and are not cases themselves
        for (CaseItemDTO dto : caseItems) {
            Shop containedItem = shopRepository.findById(dto.getContainedItem().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Contained item not found: " + dto.getContainedItem().getId()));
            
            if (containedItem.getCategory() == ShopCategory.CASE) {
                throw new IllegalArgumentException("Cases cannot contain other cases");
            }
        }
        
        // Remove existing case items
        caseItemRepository.deleteByCaseId(caseId);
        
        // Add new case items
        for (CaseItemDTO dto : caseItems) {
            Shop containedItem = shopRepository.findById(dto.getContainedItem().getId()).get();
            
            CaseItem newCaseItem = CaseItem.builder()
                .caseShopItem(caseItem)
                .containedItem(containedItem)
                .dropRate(dto.getDropRate())
                .build();
            
            caseItemRepository.save(newCaseItem);
        }
        
        logger.info("Updated case {} with {} items", caseId, caseItems.size());
    }
    
    /**
     * Validate case contents to ensure drop rates sum to 100
     * @param caseId Case ID
     * @return true if valid, false otherwise
     */
    public boolean validateCaseContents(UUID caseId) {
        Integer totalDropRate = caseItemRepository.sumDropRatesByCaseId(caseId);
        return totalDropRate != null && totalDropRate == 100;
    }
    
    /**
     * Map a CaseItem entity to a CaseItemDTO
     * @param caseItem CaseItem entity
     * @return CaseItemDTO
     */
    private CaseItemDTO mapToCaseItemDTO(CaseItem caseItem) {
        return CaseItemDTO.builder()
            .id(caseItem.getId())
            .caseId(caseItem.getCaseShopItem().getId())
            .containedItem(mapToShopDTO(caseItem.getContainedItem(), null))
            .dropRate(caseItem.getDropRate())
            .build();
    }
    
    // ===== SECURITY HELPER METHODS =====
    
    /**
     * Generate SHA-256 hash of the roll seed for audit trail
     * @param seed The roll seed to hash
     * @return SHA-256 hash of the seed
     */
    private String generateSeedHash(String seed) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(seed.getBytes());
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            logger.error("Failed to generate seed hash: {}", e.getMessage());
            return "";
        }
    }
    
    /**
     * Get client IP address for audit trail
     * TODO: Implement proper IP extraction from HTTP request
     * @return Client IP address or "unknown"
     */
    private String getClientIp() {
        // This would need to be injected from the controller layer
        // For now, return a placeholder
        return "unknown";
    }
    
    /**
     * Get user agent for audit trail
     * TODO: Implement proper user agent extraction from HTTP request
     * @return User agent string or "unknown"
     */
    private String getUserAgent() {
        // This would need to be injected from the controller layer
        // For now, return a placeholder
        return "unknown";
    }
    
    /**
     * Get session ID for audit trail
     * TODO: Implement proper session ID extraction
     * @return Session ID or "unknown"
     */
    private String getSessionId() {
        // This would need to be injected from the controller layer
        // For now, return a placeholder
        return "unknown";
    }
}
