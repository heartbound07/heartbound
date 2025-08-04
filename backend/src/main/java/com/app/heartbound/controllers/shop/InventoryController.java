package com.app.heartbound.controllers.shop;

import com.app.heartbound.dto.UserProfileDTO;
import com.app.heartbound.dto.shop.UserInventoryDTO;
import com.app.heartbound.enums.ShopCategory;
import com.app.heartbound.services.UserInventoryService;
import com.app.heartbound.config.security.RateLimited;
import com.app.heartbound.enums.RateLimitKeyType;
import com.app.heartbound.exceptions.ResourceNotFoundException;
import com.app.heartbound.exceptions.shop.ItemNotEquippableException;
import com.app.heartbound.exceptions.shop.ItemNotOwnedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/inventory")
public class InventoryController {

    private static final Logger logger = LoggerFactory.getLogger(InventoryController.class);
    private final UserInventoryService userInventoryService;

    public InventoryController(UserInventoryService userInventoryService) {
        this.userInventoryService = userInventoryService;
    }

    /**
     * Get user's inventory
     * @param authentication Authentication containing user ID
     * @return User's inventory
     */
    @GetMapping("")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserInventoryDTO> getUserInventory(Authentication authentication) {
        String userId = authentication.getName();
        UserInventoryDTO inventory = userInventoryService.getFullUserInventory(userId);
        return ResponseEntity.ok(inventory);
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
            UserProfileDTO updatedProfile = userInventoryService.equipItem(userId, itemId);
            return ResponseEntity.ok(updatedProfile);
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(e.getMessage()));
        } catch (ItemNotOwnedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponse(e.getMessage()));
        } catch (ItemNotEquippableException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(e.getMessage()));
        } catch (UnsupportedOperationException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("An error occurred while equipping the item"));
        }
    }

    /**
     * Equip an item by its instance ID.
     * @param instanceId The UUID of the ItemInstance to equip.
     * @param authentication Authentication containing user ID
     * @return Updated user profile
     */
    @RateLimited(
        requestsPerMinute = 30,
        requestsPerHour = 200,
        keyType = RateLimitKeyType.USER,
        keyPrefix = "equip-instance",
        burstCapacity = 35
    )
    @PostMapping("/equip/instance/{instanceId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> equipItemInstance(
        @PathVariable UUID instanceId,
        Authentication authentication
    ) {
        String userId = authentication.getName();
        
        try {
            UserProfileDTO updatedProfile = userInventoryService.equipItemInstance(userId, instanceId);
            return ResponseEntity.ok(updatedProfile);
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(e.getMessage()));
        } catch (ItemNotOwnedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
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
     * Equip multiple items in a single atomic transaction
     * @param request Batch equip request containing list of item IDs
     * @param authentication Authentication containing user ID
     * @return Updated user profile
     */
    @RateLimited(
        requestsPerMinute = 20,
        requestsPerHour = 100,
        keyType = RateLimitKeyType.USER,
        keyPrefix = "batch-equip",
        burstCapacity = 25
    )
    @PostMapping("/equip/batch")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> equipMultipleItems(
        @RequestBody BatchEquipRequest request,
        Authentication authentication
    ) {
        String userId = authentication.getName();
        
        try {
            if (request.getItemIds() == null || request.getItemIds().isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(new ErrorResponse("Item IDs list cannot be empty"));
            }
            
            UserProfileDTO updatedProfile = userInventoryService.equipBatch(userId, request.getItemIds());
            return ResponseEntity.ok(updatedProfile);
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(e.getMessage()));
        } catch (ItemNotOwnedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponse(e.getMessage()));
        } catch (ItemNotEquippableException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            logger.error("Error in batch equip for user {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("An error occurred while equipping items"));
        }
    }

    /**
     * Unequip an item by category
     * @param category Category of item to unequip
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
            UserProfileDTO updatedProfile = userInventoryService.unequipItem(userId, category);
            return ResponseEntity.ok(updatedProfile);
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(e.getMessage()));
        } catch (UnsupportedOperationException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("An error occurred while unequipping the item"));
        }
    }

    /**
     * Unequip multiple items in a single atomic transaction
     * @param request Batch unequip request containing list of item IDs
     * @param authentication Authentication containing user ID
     * @return Updated user profile
     */
    @RateLimited(
        requestsPerMinute = 20,
        requestsPerHour = 100,
        keyType = RateLimitKeyType.USER,
        keyPrefix = "batch-unequip",
        burstCapacity = 25
    )
    @PostMapping("/unequip/batch")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> unequipMultipleItems(
        @RequestBody BatchUnequipRequest request,
        Authentication authentication
    ) {
        String userId = authentication.getName();
        
        try {
            if (request.getItemIds() == null || request.getItemIds().isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(new ErrorResponse("Item IDs list cannot be empty"));
            }
            
            UserProfileDTO updatedProfile = userInventoryService.unequipBatch(userId, request.getItemIds());
            return ResponseEntity.ok(updatedProfile);
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            logger.error("Error in batch unequip for user {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("An error occurred while unequipping items"));
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
            UserProfileDTO updatedProfile = userInventoryService.unequipBadge(userId, badgeId);
            return ResponseEntity.ok(updatedProfile);
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(e.getMessage()));
        } catch (ItemNotEquippableException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("An error occurred while unequipping the badge"));
        }
    }

    @GetMapping("/rod/{rodInstanceId}/repair-cost")
    @PreAuthorize("isAuthenticated()")
    @RateLimited(
            requestsPerMinute = 20,
            keyType = RateLimitKeyType.USER,
            keyPrefix = "rod-repair-cost"
    )
    public ResponseEntity<Map<String, Integer>> getRepairCost(
            @PathVariable UUID rodInstanceId,
            Authentication authentication) {
        String userId = authentication.getName();
        int cost = userInventoryService.getRepairCost(userId, rodInstanceId);
        return ResponseEntity.ok(Map.of("repairCost", cost));
    }

    @PostMapping("/rod/{rodInstanceId}/repair")
    @PreAuthorize("isAuthenticated()")
    @RateLimited(
            requestsPerMinute = 10,
            requestsPerHour = 60,
            keyType = RateLimitKeyType.USER,
            keyPrefix = "rod-repair"
    )
    public ResponseEntity<UserProfileDTO> repairRod(
            @PathVariable UUID rodInstanceId,
            Authentication authentication) {
        String userId = authentication.getName();
        UserProfileDTO profile = userInventoryService.repairFishingRod(userId, rodInstanceId);
        return ResponseEntity.ok(profile);
    }

    @GetMapping("/part/{partInstanceId}/repair-cost")
    @PreAuthorize("isAuthenticated()")
    @RateLimited(
            requestsPerMinute = 20,
            keyType = RateLimitKeyType.USER,
            keyPrefix = "part-repair-cost"
    )
    public ResponseEntity<Map<String, Integer>> getPartRepairCost(
            @PathVariable UUID partInstanceId,
            Authentication authentication) {
        int cost = userInventoryService.getPartRepairCost(partInstanceId);
        return ResponseEntity.ok(Map.of("repairCost", cost));
    }

    @PostMapping("/part/{partInstanceId}/repair")
    @PreAuthorize("isAuthenticated()")
    @RateLimited(
            requestsPerMinute = 10,
            requestsPerHour = 60,
            keyType = RateLimitKeyType.USER,
            keyPrefix = "part-repair"
    )
    public ResponseEntity<UserProfileDTO> repairPart(
            @PathVariable UUID partInstanceId,
            Authentication authentication) {
        String userId = authentication.getName();
        UserProfileDTO profile = userInventoryService.repairFishingRodPart(userId, partInstanceId);
        return ResponseEntity.ok(profile);
    }

    @PostMapping("/rod/{rodInstanceId}/equip-part")
    @PreAuthorize("isAuthenticated()")
    @RateLimited(
            requestsPerMinute = 10,
            requestsPerHour = 60,
            keyType = RateLimitKeyType.USER,
            keyPrefix = "rod-equip"
    )
    public ResponseEntity<UserProfileDTO> equipAndRepairRodPart(
            @PathVariable UUID rodInstanceId,
            @RequestBody EquipRequest request,
            Authentication authentication) {
        String userId = authentication.getName();
        UserProfileDTO profile = userInventoryService.equipAndRepairFishingRodPart(userId, rodInstanceId, request.getPartInstanceId());
        return ResponseEntity.ok(profile);
    }

    public static class EquipRequest {
        private UUID partInstanceId;

        public UUID getPartInstanceId() {
            return partInstanceId;
        }

        public void setPartInstanceId(UUID partInstanceId) {
            this.partInstanceId = partInstanceId;
        }
    }

    @PostMapping("/rod/{rodInstanceId}/unequip-part")
    @PreAuthorize("isAuthenticated()")
    @RateLimited(
            requestsPerMinute = 10,
            keyType = RateLimitKeyType.USER,
            keyPrefix = "rod-unequip-part"
    )
    public ResponseEntity<UserProfileDTO> unequipBrokenPart(
            @PathVariable UUID rodInstanceId,
            @RequestBody UnequipPartRequest request,
            Authentication authentication) {
        String userId = authentication.getName();
        UserProfileDTO profile = userInventoryService.unequipAndRemoveBrokenPart(userId, rodInstanceId, request.getPartInstanceId());
        return ResponseEntity.ok(profile);
    }

    @PostMapping("/rod/{rodInstanceId}/unequip-part-keep")
    @PreAuthorize("isAuthenticated()")
    @RateLimited(
            requestsPerMinute = 15,
            requestsPerHour = 100,
            keyType = RateLimitKeyType.USER,
            keyPrefix = "rod-unequip-part-keep"
    )
    public ResponseEntity<UserProfileDTO> unequipFishingRodPart(
            @PathVariable UUID rodInstanceId,
            @RequestBody UnequipPartRequest request,
            Authentication authentication) {
        String userId = authentication.getName();
        UserProfileDTO profile = userInventoryService.unequipFishingRodPart(userId, rodInstanceId, request.getPartInstanceId());
        return ResponseEntity.ok(profile);
    }

    public static class UnequipPartRequest {
        private UUID partInstanceId;

        public UUID getPartInstanceId() {
            return partInstanceId;
        }

        public void setPartInstanceId(UUID partInstanceId) {
            this.partInstanceId = partInstanceId;
        }
    }

    // Inner classes for request/response DTOs
    public static class BatchEquipRequest {
        private List<UUID> itemIds;

        public List<UUID> getItemIds() {
            return itemIds;
        }

        public void setItemIds(List<UUID> itemIds) {
            this.itemIds = itemIds;
        }
    }

    public static class BatchUnequipRequest {
        private List<UUID> itemIds;

        public List<UUID> getItemIds() {
            return itemIds;
        }

        public void setItemIds(List<UUID> itemIds) {
            this.itemIds = itemIds;
        }
    }

    public static class ErrorResponse {
        private final String message;

        public ErrorResponse(String message) {
            this.message = message;
        }

        @SuppressWarnings("unused")
        public String getMessage() {
            return message;
        }
    }
} 