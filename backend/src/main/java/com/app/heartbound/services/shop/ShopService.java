package com.app.heartbound.services.shop;

import com.app.heartbound.dto.UserProfileDTO;
import com.app.heartbound.dto.shop.ShopDTO;
import com.app.heartbound.dto.shop.UserInventoryDTO;
import com.app.heartbound.entities.Shop;
import com.app.heartbound.entities.User;
import com.app.heartbound.enums.ShopCategory;
import com.app.heartbound.enums.ItemRarity;
import com.app.heartbound.exceptions.ResourceNotFoundException;
import com.app.heartbound.exceptions.shop.InsufficientCreditsException;
import com.app.heartbound.exceptions.shop.ItemAlreadyOwnedException;
import com.app.heartbound.exceptions.shop.ItemNotEquippableException;
import com.app.heartbound.exceptions.shop.RoleRequirementNotMetException;
import com.app.heartbound.repositories.UserRepository;
import com.app.heartbound.repositories.shop.ShopRepository;
import com.app.heartbound.services.UserService;
import com.app.heartbound.services.discord.DiscordService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;
import java.util.Objects;
import java.time.LocalDateTime;

@Service
public class ShopService {
    
    private final ShopRepository shopRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final DiscordService discordService;
    private static final Logger logger = LoggerFactory.getLogger(ShopService.class);
    
    @Autowired
    public ShopService(
        ShopRepository shopRepository,
        UserRepository userRepository,
        UserService userService,
        DiscordService discordService
    ) {
        this.shopRepository = shopRepository;
        this.userRepository = userRepository;
        this.userService = userService;
        this.discordService = discordService;
    }
    
    /**
     * Get all available shop items
     * @param userId Optional user ID to check ownership status
     * @param categoryStr Optional category filter as string
     * @return List of shop items
     */
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
        logger.debug("Processing purchase of item {} for user {}", itemId, userId);
        
        // Get user and item
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));
        
        Shop item = shopRepository.findById(itemId)
            .orElseThrow(() -> new ResourceNotFoundException("Shop item not found with ID: " + itemId));
        
        // Validation checks
        if (!item.getIsActive()) {
            throw new ResourceNotFoundException("Item is not available for purchase");
        }
        
        if (user.hasItem(itemId)) {
            throw new ItemAlreadyOwnedException("User already owns this item");
        }
        
        if (user.getCredits() < item.getPrice()) {
            throw new InsufficientCreditsException(
                "Insufficient credits. Required: " + item.getPrice() + ", Available: " + user.getCredits()
            );
        }
        
        if (item.getRequiredRole() != null && !user.hasRole(item.getRequiredRole())) {
            throw new RoleRequirementNotMetException(
                "This item requires the " + item.getRequiredRole() + " role"
            );
        }
        
        // Process purchase
        user.setCredits(user.getCredits() - item.getPrice());
        user.addItem(item);
        
        User savedUser = userRepository.save(user);
        logger.info("User {} successfully purchased item {}", userId, itemId);
        
        // Return updated user profile
        return userService.mapToProfileDTO(savedUser);
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
        
        // Handle Discord role management for USER_COLOR items
        if (category == ShopCategory.USER_COLOR) {
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
     * Gets a user's inventory with equipped status
     * @param userId User ID
     * @return User's inventory with equipped status
     */
    public UserInventoryDTO getUserInventory(String userId) {
        logger.debug("Getting inventory for user {}", userId);
        
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));
        
        Set<Shop> inventory = user.getInventory();
        Set<ShopDTO> itemDTOs = inventory.stream()
            .map(item -> {
                ShopDTO dto = mapToShopDTO(item, user);
                
                // Add equipped status to each item
                if (item.getCategory() != null) {
                    UUID equippedItemId = user.getEquippedItemIdByCategory(item.getCategory());
                    dto.setEquipped(equippedItemId != null && equippedItemId.equals(item.getId()));
                }
                
                return dto;
            })
            .collect(Collectors.toSet());
        
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
        if (user != null && user.getInventory() != null) {
            owned = user.hasItem(shop.getId());
        }
        
        return ShopDTO.builder()
            .id(shop.getId())
            .name(shop.getName())
            .description(shop.getDescription())
            .price(shop.getPrice())
            .category(shop.getCategory())
            .imageUrl(shop.getImageUrl())
            .requiredRole(shop.getRequiredRole())
            .owned(owned)
            .expiresAt(shop.getExpiresAt())
            .discordRoleId(shop.getDiscordRoleId())
            .rarity(shop.getRarity() != null ? shop.getRarity() : ItemRarity.COMMON)
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
        
        Shop newItem = Shop.builder()
            .name(shopDTO.getName())
            .description(shopDTO.getDescription())
            .price(shopDTO.getPrice())
            .category(shopDTO.getCategory())
            .imageUrl(shopDTO.getImageUrl())
            .requiredRole(shopDTO.getRequiredRole())
            .isActive(shopDTO.isActive())
            .expiresAt(shopDTO.getExpiresAt())
            .discordRoleId(shopDTO.getDiscordRoleId())
            .rarity(shopDTO.getRarity() != null ? shopDTO.getRarity() : ItemRarity.COMMON)
            .build();
        
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
        
        // Update fields
        existingItem.setName(shopDTO.getName());
        existingItem.setDescription(shopDTO.getDescription());
        existingItem.setPrice(shopDTO.getPrice());
        existingItem.setCategory(shopDTO.getCategory());
        existingItem.setImageUrl(shopDTO.getImageUrl());
        existingItem.setRequiredRole(shopDTO.getRequiredRole());
        existingItem.setExpiresAt(shopDTO.getExpiresAt());
        existingItem.setIsActive(shopDTO.isActive());
        existingItem.setDiscordRoleId(shopDTO.getDiscordRoleId());
        existingItem.setRarity(shopDTO.getRarity() != null ? shopDTO.getRarity() : ItemRarity.COMMON);
        
        return shopRepository.save(existingItem);
    }
    
    /**
     * Delete a shop item completely (hard delete)
     * @param itemId Item ID
     */
    @Transactional
    public void deleteShopItem(UUID itemId) {
        logger.debug("Hard deleting shop item {}", itemId);
        
        Shop item = shopRepository.findById(itemId)
            .orElseThrow(() -> new ResourceNotFoundException("Shop item not found with ID: " + itemId));
        
        // First, check if item is in any user's inventory and remove it
        List<User> usersWithItem = userRepository.findByInventoryContaining(item);
        for (User user : usersWithItem) {
            user.getInventory().removeIf(i -> i.getId().equals(itemId));
            userRepository.save(user);
        }
        
        // Hard delete the item
        shopRepository.delete(item);
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
}
