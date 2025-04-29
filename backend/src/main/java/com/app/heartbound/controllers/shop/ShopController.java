package com.app.heartbound.controllers.shop;

import com.app.heartbound.dto.UserProfileDTO;
import com.app.heartbound.entities.Shop;
import com.app.heartbound.dto.shop.ShopDTO;
import com.app.heartbound.dto.shop.UserInventoryDTO;
import com.app.heartbound.exceptions.ResourceNotFoundException;
import com.app.heartbound.exceptions.shop.InsufficientCreditsException;
import com.app.heartbound.exceptions.shop.ItemAlreadyOwnedException;
import com.app.heartbound.exceptions.shop.RoleRequirementNotMetException;
import com.app.heartbound.services.shop.ShopService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/shop")
public class ShopController {
    
    private final ShopService shopService;
    private static final Logger logger = LoggerFactory.getLogger(ShopController.class);
        
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
    public ResponseEntity<ShopDTO> createShopItem(@RequestBody ShopDTO shopDTO) {
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
        @RequestBody ShopDTO shopDTO
    ) {
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
     * @return List of category names
     */
    @GetMapping("/categories")
    public ResponseEntity<List<String>> getShopCategories() {
        try {
            List<String> categories = shopService.getShopCategories();
            return ResponseEntity.ok(categories);
        } catch (Exception e) {
            // Log the error
            logger.error("Error retrieving shop categories", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
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
