package com.app.heartbound.controllers.shop;

import com.app.heartbound.config.security.RateLimited;
import com.app.heartbound.dto.UserProfileDTO;
import com.app.heartbound.entities.Shop;
import com.app.heartbound.dto.shop.ShopDTO;
import com.app.heartbound.dto.shop.UserInventoryDTO;
import com.app.heartbound.dto.shop.CaseContentsDTO;
import com.app.heartbound.dto.shop.CaseItemDTO;
import com.app.heartbound.dto.shop.RollResultDTO;
import com.app.heartbound.enums.RateLimitKeyType;
import com.app.heartbound.enums.ShopCategory;
import com.app.heartbound.exceptions.ResourceNotFoundException;
import com.app.heartbound.exceptions.shop.InsufficientCreditsException;
import com.app.heartbound.exceptions.shop.ItemAlreadyOwnedException;
import com.app.heartbound.exceptions.shop.ItemNotEquippableException;
import com.app.heartbound.exceptions.shop.RoleRequirementNotMetException;
import com.app.heartbound.exceptions.shop.CaseNotFoundException;
import com.app.heartbound.exceptions.shop.CaseNotOwnedException;
import com.app.heartbound.exceptions.shop.EmptyCaseException;
import com.app.heartbound.exceptions.shop.InvalidCaseContentsException;
import com.app.heartbound.services.shop.ShopService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/shop")
public class ShopController {
    
    private final ShopService shopService;
    private static final Logger logger = LoggerFactory.getLogger(ShopController.class);
    
    // Rate limiting configuration values
    @Value("${rate.limit.purchase.per-minute:5}")
    private int purchaseRatePerMinute;
    
    @Value("${rate.limit.purchase.per-hour:20}")
    private int purchaseRatePerHour;
    
    @Value("${rate.limit.purchase.burst-capacity:6}")
    private int purchaseBurstCapacity;
    
    @Value("${rate.limit.case-open.per-minute:10}")
    private int caseOpenRatePerMinute;
    
    @Value("${rate.limit.case-open.per-hour:50}")
    private int caseOpenRatePerHour;
    
    @Value("${rate.limit.case-open.burst-capacity:12}")
    private int caseOpenBurstCapacity;
    
    @Value("${rate.limit.equip.per-minute:30}")
    private int equipRatePerMinute;
    
    @Value("${rate.limit.equip.per-hour:200}")
    private int equipRatePerHour;
    
    @Value("${rate.limit.equip.burst-capacity:35}")
    private int equipBurstCapacity;
        
    @Autowired
    public ShopController(ShopService shopService) {
        this.shopService = shopService;
    }
    
    /**
     * Get all available shop items
     * @param category Optional category filter
     * @param authentication Optional authentication for checking item ownership
     * @return List of shop items
     */
    @GetMapping("/items")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<ShopDTO>> getShopItems(
        @RequestParam(required = false) String category,
        Authentication authentication
    ) {
        String userId = authentication != null ? authentication.getName() : null;
        List<ShopDTO> items = shopService.getAvailableShopItems(userId, category);
        return ResponseEntity.ok(items);
    }
    
    /**
     * Get a specific shop item by ID
     * @param itemId Item ID
     * @param authentication Optional authentication for checking item ownership
     * @return Shop item details
     */
    @GetMapping("/items/{itemId}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ShopDTO> getShopItem(
        @PathVariable UUID itemId,
        Authentication authentication
    ) {
        String userId = authentication != null ? authentication.getName() : null;
        ShopDTO item = shopService.getShopItemById(itemId, userId);
        return ResponseEntity.ok(item);
    }
    
    /**
     * Purchase an item
     * @param itemId Item ID
     * @param authentication Authentication containing user ID
     * @return Updated user profile
     */
    @RateLimited(
        requestsPerMinute = 5,
        requestsPerHour = 20,
        keyType = RateLimitKeyType.USER,
        keyPrefix = "purchase",
        burstCapacity = 6
    )
    @PostMapping("/purchase/{itemId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> purchaseItem(
        @PathVariable UUID itemId,
        Authentication authentication
    ) {
        String userId = authentication.getName();
        
        try {
            UserProfileDTO updatedProfile = shopService.purchaseItem(userId, itemId);
            return ResponseEntity.ok(updatedProfile);
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(e.getMessage()));
        } catch (InsufficientCreditsException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(e.getMessage()));
        } catch (ItemAlreadyOwnedException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(e.getMessage()));
        } catch (RoleRequirementNotMetException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("An error occurred while processing your purchase"));
        }
    }
    
    /**
     * Get a user's inventory
     * @param authentication Authentication containing user ID
     * @return User's inventory
     */
    @GetMapping("/inventory")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserInventoryDTO> getUserInventory(Authentication authentication) {
        String userId = authentication.getName();
        UserInventoryDTO inventory = shopService.getUserInventory(userId);
        return ResponseEntity.ok(inventory);
    }
    
    /**
     * Admin endpoint to create a new shop item
     */
    @PostMapping("/admin/items")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ShopDTO> createShopItem(@Valid @RequestBody ShopDTO shopDTO) {
        logger.debug("Received create shop item request with active status: {}", shopDTO.isActive());
        Shop newItem = shopService.createShopItem(shopDTO);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(shopService.getShopItemById(newItem.getId(), null));
    }
    
    /**
     * Admin endpoint to update an existing shop item
     */
    @PutMapping("/admin/items/{itemId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ShopDTO> updateShopItem(
        @PathVariable UUID itemId,
        @Valid @RequestBody ShopDTO shopDTO
    ) {
        logger.debug("Received update shop item request for ID {} with active status: {}", itemId, shopDTO.isActive());
        Shop updatedItem = shopService.updateShopItem(itemId, shopDTO);
        return ResponseEntity.ok(shopService.getShopItemById(updatedItem.getId(), null));
    }
    
    /**
     * Admin endpoint to delete a shop item
     */
    @DeleteMapping("/admin/items/{itemId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteShopItem(@PathVariable UUID itemId) {
        shopService.deleteShopItem(itemId);
        return ResponseEntity.noContent().build();
    }
    
    /**
     * Admin endpoint to get all shop items (including inactive)
     */
    @GetMapping("/admin/items")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ShopDTO>> getAllShopItems() {
        List<ShopDTO> items = shopService.getAllShopItems();
        return ResponseEntity.ok(items);
    }
    
    /**
     * Get all distinct shop categories
     * @return List of category names as strings
     */
    @GetMapping("/categories")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<String>> getShopCategories() {
        try {
            // Option 1: Get categories from active items
            List<String> categories = shopService.getShopCategories();
            
            // Option 2: If we want to always return all possible categories,
            // regardless of whether they have active items
            if (categories.isEmpty()) {
                categories = Arrays.stream(ShopCategory.values())
                    .map(ShopCategory::name)
                    .collect(Collectors.toList());
            }
            
            return ResponseEntity.ok(categories);
        } catch (Exception e) {
            // Log the error
            logger.error("Error retrieving shop categories", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Equip an item
     * @param itemId Item ID
     * @param authentication Authentication containing user ID
     * @return Updated user profile
     */
    @RateLimited(
        requestsPerMinute = 30,
        requestsPerHour = 200,
        keyType = RateLimitKeyType.USER,
        keyPrefix = "equip",
        burstCapacity = 35
    )
    @PostMapping("/equip/{itemId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> equipItem(
        @PathVariable UUID itemId,
        Authentication authentication
    ) {
        String userId = authentication.getName();
        
        try {
            UserProfileDTO updatedProfile = shopService.equipItem(userId, itemId);
            return ResponseEntity.ok(updatedProfile);
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(e.getMessage()));
        } catch (ItemAlreadyOwnedException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(e.getMessage()));
        } catch (ItemNotEquippableException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("An error occurred while equipping the item"));
        }
    }
    
    /**
     * Unequip an item by category
     * @param category Category to unequip
     * @param authentication Authentication containing user ID
     * @return Updated user profile
     */
    @RateLimited(
        requestsPerMinute = 30,
        requestsPerHour = 200,
        keyType = RateLimitKeyType.USER,
        keyPrefix = "unequip",
        burstCapacity = 35
    )
    @PostMapping("/unequip/{category}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> unequipItem(
        @PathVariable ShopCategory category,
        Authentication authentication
    ) {
        String userId = authentication.getName();
        
        try {
            UserProfileDTO updatedProfile = shopService.unequipItem(userId, category);
            return ResponseEntity.ok(updatedProfile);
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("An error occurred while unequipping the item"));
        }
    }
    
    /**
     * Unequip a specific badge
     * @param badgeId Badge ID to unequip
     * @param authentication Authentication containing user ID
     * @return Updated user profile
     */
    @RateLimited(
        requestsPerMinute = 30,
        requestsPerHour = 200,
        keyType = RateLimitKeyType.USER,
        keyPrefix = "unequip-badge",
        burstCapacity = 35
    )
    @PostMapping("/unequip/badge/{badgeId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> unequipBadge(
        @PathVariable UUID badgeId,
        Authentication authentication
    ) {
        String userId = authentication.getName();
        
        try {
            // Validate UUID format
            if (badgeId == null) {
                return ResponseEntity.badRequest()
                    .body(new ErrorResponse("Invalid badge ID format"));
            }
            
            UserProfileDTO updatedProfile = shopService.unequipBadge(userId, badgeId);
            return ResponseEntity.ok(updatedProfile);
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            logger.error("Error unequipping badge: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("An error occurred while unequipping the badge"));
        }
    }
    
    // ===== CASE-SPECIFIC ENDPOINTS =====
    
    /**
     * Get case contents
     * @param caseId Case ID
     * @return Case contents with drop rates
     */
    @GetMapping("/cases/{caseId}/contents")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> getCaseContents(@PathVariable UUID caseId) {
        try {
            CaseContentsDTO contents = shopService.getCaseContents(caseId);
            return ResponseEntity.ok(contents);
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            logger.error("Error retrieving case contents for case {}: {}", caseId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("An error occurred while retrieving case contents"));
        }
    }
    
    /**
     * Open a case and receive a random item
     * @param caseId Case ID to open
     * @param authentication Authentication containing user ID
     * @return RollResultDTO with the won item details
     */
    @RateLimited(
        requestsPerMinute = 10,
        requestsPerHour = 50,
        keyType = RateLimitKeyType.USER,
        keyPrefix = "case-open",
        burstCapacity = 12
    )
    @PostMapping("/cases/{caseId}/open")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> openCase(
        @PathVariable UUID caseId,
        Authentication authentication
    ) {
        String userId = authentication.getName();
        
        try {
            RollResultDTO result = shopService.openCase(userId, caseId);
            return ResponseEntity.ok(result);
        } catch (CaseNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(e.getMessage()));
        } catch (CaseNotOwnedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponse(e.getMessage()));
        } catch (EmptyCaseException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(e.getMessage()));
        } catch (InvalidCaseContentsException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(e.getMessage()));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            logger.error("Error opening case {} for user {}: {}", caseId, userId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("An error occurred while opening the case"));
        }
    }
    
    /**
     * Admin endpoint to update case contents
     * @param caseId Case ID
     * @param caseItems List of items with drop rates
     * @return Success response
     */
    @PostMapping("/admin/cases/{caseId}/contents")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateCaseContents(
        @PathVariable UUID caseId,
        @RequestBody List<CaseItemDTO> caseItems
    ) {
        try {
            logger.debug("Updating case {} with {} items", caseId, caseItems.size());
            shopService.updateCaseContents(caseId, caseItems);
            return ResponseEntity.ok(new SuccessResponse("Case contents updated successfully"));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            logger.error("Error updating case contents for case {}: {}", caseId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("An error occurred while updating case contents"));
        }
    }
    
    /**
     * Validate case contents
     * @param caseId Case ID
     * @return Validation result
     */
    @GetMapping("/admin/cases/{caseId}/validate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> validateCaseContents(@PathVariable UUID caseId) {
        try {
            boolean isValid = shopService.validateCaseContents(caseId);
            return ResponseEntity.ok(new ValidationResponse(isValid, 
                isValid ? "Case contents are valid" : "Case contents are invalid - drop rates must total 100%"));
        } catch (Exception e) {
            logger.error("Error validating case contents for case {}: {}", caseId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("An error occurred while validating case contents"));
        }
    }
    
    /**
     * Simple success response class
     */
    private static class SuccessResponse {
        private final String message;
        
        public SuccessResponse(String message) {
            this.message = message;
        }
        
        public String getMessage() {
            return message;
        }
    }
    
    /**
     * Validation response class
     */
    private static class ValidationResponse {
        private final boolean valid;
        private final String message;
        
        public ValidationResponse(boolean valid, String message) {
            this.valid = valid;
            this.message = message;
        }
        
        public boolean isValid() {
            return valid;
        }
        
        public String getMessage() {
            return message;
        }
    }
    
    /**
     * Simple error response class
     */
    private static class ErrorResponse {
        private final String message;
        
        public ErrorResponse(String message) {
            this.message = message;
        }
        
        public String getMessage() {
            return message;
        }
    }
}
