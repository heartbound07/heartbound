package com.app.heartbound.controllers.shop;

import com.app.heartbound.dto.UserProfileDTO;
import com.app.heartbound.dto.shop.ShopDTO;
import com.app.heartbound.dto.shop.UserInventoryDTO;
import com.app.heartbound.exceptions.ResourceNotFoundException;
import com.app.heartbound.exceptions.shop.InsufficientCreditsException;
import com.app.heartbound.exceptions.shop.ItemAlreadyOwnedException;
import com.app.heartbound.exceptions.shop.RoleRequirementNotMetException;
import com.app.heartbound.services.shop.ShopService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/shop")
public class ShopController {
    
    private final ShopService shopService;
    
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
