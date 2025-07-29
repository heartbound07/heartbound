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
        logger.info("User {} is attempting to equip part {} on rod {}", userId, request.getPartInstanceId(), rodInstanceId);
        UserProfileDTO updatedProfile = userInventoryService.equipAndRepairFishingRodPart(userId, rodInstanceId, request.getPartInstanceId());
        return ResponseEntity.ok(updatedProfile);
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
} 