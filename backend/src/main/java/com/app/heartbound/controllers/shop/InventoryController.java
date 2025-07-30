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
        logger.info("User {} is requesting repair cost for rod {}", userId, rodInstanceId);
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
        logger.info("User {} is attempting to repair rod {}", userId, rodInstanceId);
        UserProfileDTO updatedProfile = userInventoryService.repairFishingRod(userId, rodInstanceId);
        return ResponseEntity.ok(updatedProfile);
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
        String userId = authentication.getName();
        logger.info("User {} is requesting repair cost for part {}", userId, partInstanceId);
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
        logger.info("User {} is attempting to repair part {}", userId, partInstanceId);
        UserProfileDTO updatedProfile = userInventoryService.repairFishingRodPart(userId, partInstanceId);
        return ResponseEntity.ok(updatedProfile);
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