package com.app.heartbound.controllers.shop;

import com.app.heartbound.config.security.RateLimited;
import com.app.heartbound.config.security.Views;
import com.app.heartbound.entities.Shop;
import com.app.heartbound.dto.shop.ShopDTO;
import com.app.heartbound.dto.shop.PurchaseResponseDTO;
import com.app.heartbound.dto.shop.CaseContentsDTO;
import com.app.heartbound.dto.shop.CaseItemDTO;
import com.app.heartbound.dto.shop.RollResultDTO;
import com.app.heartbound.enums.RateLimitKeyType;
import com.app.heartbound.enums.ShopCategory;
import com.app.heartbound.exceptions.ResourceNotFoundException;
import com.app.heartbound.exceptions.shop.CaseNotFoundException;
import com.app.heartbound.exceptions.shop.CaseNotOwnedException;
import com.app.heartbound.exceptions.shop.EmptyCaseException;
import com.app.heartbound.exceptions.shop.InvalidCaseContentsException;
import com.app.heartbound.exceptions.shop.ItemDeletionException;
import com.app.heartbound.exceptions.shop.ItemReferencedInCasesException;
import com.app.heartbound.services.shop.ShopService;
import com.app.heartbound.services.shop.CaseService;
import com.app.heartbound.repositories.shop.ShopRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.MappingJacksonValue;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/shop")
public class ShopController {
    
    private final ShopService shopService;
    private final CaseService caseService;
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
        
    public ShopController(ShopService shopService, CaseService caseService, ShopRepository shopRepository) {
        this.shopService = shopService;
        this.caseService = caseService;
    }
    
    /**
     * Get all available shop items
     * @param category Optional category filter
     * @param authentication Optional authentication for checking item ownership
     * @return List of shop items
     */
    @GetMapping("/items")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<MappingJacksonValue> getShopItems(
        @RequestParam(required = false) String category,
        Authentication authentication
    ) {
        String userId = authentication != null ? authentication.getName() : null;
        List<ShopDTO> items = shopService.getAvailableShopItems(userId, category);
        
        MappingJacksonValue mappingJacksonValue = new MappingJacksonValue(items);
        
        boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
            .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
            
        if (isAdmin) {
            mappingJacksonValue.setSerializationView(Views.Admin.class);
        } else {
            mappingJacksonValue.setSerializationView(Views.Public.class);
        }
        
        return ResponseEntity.ok(mappingJacksonValue);
    }
    
    /**
     * Get shop layout with featured and daily items in a single call
     * @param authentication Authentication containing user ID
     * @return Shop layout with featured and daily items
     */
    @GetMapping("/layout")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ShopLayoutResponse> getShopLayout(Authentication authentication) {
        String userId = authentication.getName();
        
        try {
            List<ShopDTO> featuredItems = shopService.getFeaturedItems(userId);
            List<ShopDTO> dailyItems = shopService.getDailyItems(userId);
            
            ShopLayoutResponse response = new ShopLayoutResponse(featuredItems, dailyItems);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error retrieving shop layout for user {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
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
     * @param request Purchase request containing optional quantity
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
        @RequestBody(required = false) PurchaseRequest request,
        Authentication authentication
    ) {
        String userId = authentication.getName();
        
        // Extract quantity from request, default to 1
        Integer quantity = (request != null && request.getQuantity() != null) ? request.getQuantity() : 1;
        
        // Validate quantity limits
        if (quantity < 1 || quantity > 10) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("Quantity must be between 1 and 10"));
        }
        
        try {
            PurchaseResponseDTO purchaseResponse = shopService.purchaseItem(userId, itemId, quantity);
            logger.info("Successful purchase by user {} for item {} (quantity: {})", userId, itemId, quantity);
            return ResponseEntity.ok(purchaseResponse);
        } catch (Exception e) {
            logger.error("Error processing purchase for user {} and item {}: {}", userId, itemId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("An error occurred while processing your purchase"));
        }
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
     * Admin endpoint to update an item's price
     */
    @PatchMapping("/admin/items/{itemId}/price")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateItemPrice(
        @PathVariable UUID itemId,
        @RequestBody Map<String, Object> payload
    ) {
        try {
            if (!payload.containsKey("price")) {
                return ResponseEntity.badRequest().body(new ErrorResponse("Payload must contain 'price' key."));
            }
            int newPrice = (Integer) payload.get("price");
            shopService.updateItemPrice(itemId, newPrice);
            return ResponseEntity.ok(new SuccessResponse("Price updated successfully."));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            logger.error("Error updating price for item {}: {}", itemId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("An error occurred while updating the price."));
        }
    }

    /**
     * Admin endpoint to update an item's active status
     */
    @PatchMapping("/admin/items/{itemId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateItemStatus(
        @PathVariable UUID itemId,
        @RequestBody Map<String, Object> payload
    ) {
        try {
            if (!payload.containsKey("active")) {
                return ResponseEntity.badRequest().body(new ErrorResponse("Payload must contain 'active' key."));
            }
            boolean newStatus = (Boolean) payload.get("active");
            shopService.updateItemStatus(itemId, newStatus);
            return ResponseEntity.ok(new SuccessResponse("Status updated successfully."));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            logger.error("Error updating status for item {}: {}", itemId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("An error occurred while updating the status."));
        }
    }
    
    /**
     * Admin endpoint to delete a shop item
     */
    @DeleteMapping("/admin/items/{itemId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteShopItem(@PathVariable UUID itemId) {
        try {
            shopService.deleteShopItem(itemId);
            return ResponseEntity.noContent().build();
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(e.getMessage()));
        } catch (ItemReferencedInCasesException e) {
            // For now, we perform cascade deletion automatically
            // In the future, this could be used to show a confirmation dialog
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new CascadeDeletionResponse(
                    "Item was referenced in cases and has been removed from them during deletion",
                    e.getReferencingCaseIds()
                ));
        } catch (ItemDeletionException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            logger.error("Unexpected error deleting shop item {}: {}", itemId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("An unexpected error occurred while deleting the item"));
        }
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
    
    // ===== CASE-SPECIFIC ENDPOINTS =====
    
    /**
     * Get case contents
     * @param caseId Case ID
     * @param authentication Authentication containing user information
     * @return Case contents with drop rates (admin only) or without drop rates (regular users)
     */
    @GetMapping("/cases/{caseId}/contents")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> getCaseContents(@PathVariable UUID caseId, Authentication authentication) {
        try {
            CaseContentsDTO contents = caseService.getCaseContents(caseId);
            
            // Create MappingJacksonValue to control JSON serialization based on user role
            MappingJacksonValue mappingJacksonValue = new MappingJacksonValue(contents);
            
            // Check if user has admin role to determine which view to use
            boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
            
            if (isAdmin) {
                // Admin users see all data including drop rates
                mappingJacksonValue.setSerializationView(Views.Admin.class);
            } else {
                // Regular users see data without drop rates
                mappingJacksonValue.setSerializationView(Views.Public.class);
            }
            
            return ResponseEntity.ok(mappingJacksonValue);
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
            RollResultDTO result = caseService.openCase(userId, caseId);
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
        @RequestBody @Valid List<CaseItemDTO> caseItems
    ) {
        try {
            // Additional validation for drop rates
            for (CaseItemDTO item : caseItems) {
                if (item.getDropRate() == null || item.getDropRate().compareTo(java.math.BigDecimal.ZERO) <= 0) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ErrorResponse("Drop rates must be positive values."));
                }
            }

            logger.debug("Updating case {} with {} items", caseId, caseItems.size());
            caseService.updateCaseContents(caseId, caseItems);
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
            boolean isValid = caseService.validateCaseContents(caseId);
            return ResponseEntity.ok()
                .header("Cache-Control", "no-cache, no-store, must-revalidate")
                .header("Pragma", "no-cache")
                .header("Expires", "0")
                .body(new ValidationResponse(isValid, 
                    isValid ? "Case contents are valid" : "Case contents are invalid - drop rates must total 100%"));
        } catch (Exception e) {
            logger.error("Error validating case contents for case {}: {}", caseId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .header("Cache-Control", "no-cache, no-store, must-revalidate")
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
        
        @SuppressWarnings("unused")
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
        
        @SuppressWarnings("unused")
        public boolean isValid() {
            return valid;
        }
        
        @SuppressWarnings("unused")
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
        
        @SuppressWarnings("unused")
        public String getMessage() {
            return message;
        }
    }
    
    /**
     * Purchase request DTO for quantity-based purchases
     */
        public static class PurchaseRequest {
        private Integer quantity;

        public Integer getQuantity() {
            return quantity;
        }

        public void setQuantity(Integer quantity) {
            this.quantity = quantity;
        }
    }
    
    /**
     * Response class for cascade deletion operations
     */
    public static class CascadeDeletionResponse {
        private final String message;
        private final List<UUID> affectedCaseIds;

        public CascadeDeletionResponse(String message, List<UUID> affectedCaseIds) {
            this.message = message;
            this.affectedCaseIds = affectedCaseIds;
        }

        public String getMessage() {
            return message;
        }

        public List<UUID> getAffectedCaseIds() {
            return affectedCaseIds;
        }
    }
    
    /**
     * Response class for shop layout containing featured and daily items
     */
    public static class ShopLayoutResponse {
        private final List<ShopDTO> featuredItems;
        private final List<ShopDTO> dailyItems;

        public ShopLayoutResponse(List<ShopDTO> featuredItems, List<ShopDTO> dailyItems) {
            this.featuredItems = featuredItems;
            this.dailyItems = dailyItems;
        }

        public List<ShopDTO> getFeaturedItems() {
            return featuredItems;
        }

        public List<ShopDTO> getDailyItems() {
            return dailyItems;
        }
    }
}
