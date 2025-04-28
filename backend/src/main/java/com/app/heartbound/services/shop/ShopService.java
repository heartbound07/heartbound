package com.app.heartbound.services.shop;

import com.app.heartbound.dto.UserProfileDTO;
import com.app.heartbound.dto.shop.ShopDTO;
import com.app.heartbound.dto.shop.UserInventoryDTO;
import com.app.heartbound.entities.Shop;
import com.app.heartbound.entities.User;
import com.app.heartbound.exceptions.ResourceNotFoundException;
import com.app.heartbound.exceptions.InsufficientCreditsException;
import com.app.heartbound.exceptions.ItemAlreadyOwnedException;
import com.app.heartbound.exceptions.RoleRequirementNotMetException;
import com.app.heartbound.repositories.UserRepository;
import com.app.heartbound.repositories.shop.ShopRepository;
import com.app.heartbound.services.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class ShopService {
    
    private final ShopRepository shopRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private static final Logger logger = LoggerFactory.getLogger(ShopService.class);
    
    @Autowired
    public ShopService(
        ShopRepository shopRepository,
        UserRepository userRepository,
        UserService userService
    ) {
        this.shopRepository = shopRepository;
        this.userRepository = userRepository;
        this.userService = userService;
    }
    
    /**
     * Get all available shop items
     * @param userId Optional user ID to check ownership status
     * @param category Optional category filter
     * @return List of shop items
     */
    public List<ShopDTO> getAvailableShopItems(String userId, String category) {
        List<Shop> items;
        
        // Filter by category if provided
        if (category != null && !category.isEmpty()) {
            items = shopRepository.findByCategoryAndIsActiveTrue(category);
        } else {
            items = shopRepository.findByIsActiveTrue();
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
     * Get a user's inventory
     * @param userId User ID
     * @return Set of shop items owned by the user
     */
    public UserInventoryDTO getUserInventory(String userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));
        
        Set<ShopDTO> items = user.getInventory()
            .stream()
            .map(item -> mapToShopDTO(item, user))
            .collect(Collectors.toSet());
        
        return UserInventoryDTO.builder()
            .userId(userId)
            .items(items)
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
            .build();
    }
}
