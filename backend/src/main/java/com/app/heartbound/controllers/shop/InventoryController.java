package com.app.heartbound.controllers.shop;

import com.app.heartbound.config.security.RateLimited;
import com.app.heartbound.dto.UserProfileDTO;
import com.app.heartbound.enums.RateLimitKeyType;
import com.app.heartbound.services.UserInventoryService;
import com.app.heartbound.enums.FishingRodPart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/inventory")
public class InventoryController {

    private static final Logger logger = LoggerFactory.getLogger(InventoryController.class);
    private final UserInventoryService userInventoryService;

    public InventoryController(UserInventoryService userInventoryService) {
        this.userInventoryService = userInventoryService;
    }

    @PostMapping("/rod/{rodInstanceId}/repair")
    @PreAuthorize("isAuthenticated()")
    @RateLimited(
            requestsPerMinute = 10,
            requestsPerHour = 60,
            keyType = RateLimitKeyType.USER,
            keyPrefix = "rod-repair"
    )
    public ResponseEntity<UserProfileDTO> repairFishingRod(
            @PathVariable UUID rodInstanceId,
            @RequestBody RepairRequest request,
            Authentication authentication) {
        String userId = authentication.getName();
        logger.info("User {} is attempting to repair rod {} with part {}", userId, rodInstanceId, request.getPartInstanceId());
        UserProfileDTO updatedProfile = userInventoryService.repairFishingRod(userId, rodInstanceId, request.getPartInstanceId());
        return ResponseEntity.ok(updatedProfile);
    }

    @PostMapping("/rod/{rodInstanceId}/unequip-part")
    @PreAuthorize("isAuthenticated()")
    @RateLimited(
            requestsPerMinute = 10,
            requestsPerHour = 60,
            keyType = RateLimitKeyType.USER,
            keyPrefix = "rod-unequip"
    )
    public ResponseEntity<UserProfileDTO> unequipRodPart(
            @PathVariable UUID rodInstanceId,
            @RequestBody UnequipRequest request,
            Authentication authentication) {
        String userId = authentication.getName();
        logger.info("User {} is attempting to unequip part type {} from rod {}", userId, request.getPartType(), rodInstanceId);
        UserProfileDTO updatedProfile = userInventoryService.unequipRodPart(userId, rodInstanceId, request.getPartType());
        return ResponseEntity.ok(updatedProfile);
    }

    public static class RepairRequest {
        private UUID partInstanceId;

        public UUID getPartInstanceId() {
            return partInstanceId;
        }

        public void setPartInstanceId(UUID partInstanceId) {
            this.partInstanceId = partInstanceId;
        }
    }

    public static class UnequipRequest {
        private FishingRodPart partType;

        public FishingRodPart getPartType() {
            return partType;
        }

        public void setPartType(FishingRodPart partType) {
            this.partType = partType;
        }
    }
} 