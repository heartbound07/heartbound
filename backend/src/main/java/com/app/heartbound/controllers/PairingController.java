package com.app.heartbound.controllers;

import com.app.heartbound.dto.pairing.*;
import com.app.heartbound.services.pairing.MatchmakingService;
import com.app.heartbound.services.pairing.PairingService;
import com.app.heartbound.services.pairing.QueueService;
import com.app.heartbound.services.security.PairingSecurityService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Optional;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * PairingController
 * 
 * REST controller for managing user pairings in the "Don't Catch Feelings Challenge".
 */
@RestController
@RequestMapping("/pairings")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Pairings", description = "Endpoints for managing user pairings")
@Validated
public class PairingController {

    private final PairingService pairingService;
    private final MatchmakingService matchmakingService;
    private final QueueService queueService;
    private final PairingSecurityService pairingSecurityService;

    @Operation(summary = "Create a new pairing", description = "Create a pairing between two users")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Pairing created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request data"),
            @ApiResponse(responseCode = "409", description = "Users already paired or blacklisted")
    })
    @PostMapping
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<PairingDTO> createPairing(@Valid @RequestBody CreatePairingRequestDTO request) {
        log.info("Creating pairing request received");
        
        try {
            PairingDTO createdPairing = pairingService.createPairing(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(createdPairing);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid pairing request: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (IllegalStateException e) {
            log.warn("Pairing conflict: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error creating pairing", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to create pairing");
        }
    }

    @Operation(summary = "Get current active pairing for a user", description = "Retrieve the active pairing for a specific user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Pairing found"),
            @ApiResponse(responseCode = "404", description = "No active pairing found")
    })
    @GetMapping("/current")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<PairingDTO> getCurrentPairing(
            @Parameter(description = "User ID", required = true)
            @RequestParam @NotBlank String userId) {
        
        try {
            Optional<PairingDTO> pairing = pairingService.getCurrentPairing(userId);
            return pairing.map(ResponseEntity::ok)
                         .orElse(ResponseEntity.notFound().build());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid user ID for current pairing: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            log.error("Error fetching current pairing for user: {}", userId, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to fetch current pairing");
        }
    }

    @Operation(summary = "Update pairing activity", description = "Update activity metrics for a pairing")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Activity updated successfully"),
            @ApiResponse(responseCode = "404", description = "Pairing not found"),
            @ApiResponse(responseCode = "400", description = "Invalid request data")
    })
    @PatchMapping("/{id}/activity")
    public ResponseEntity<PairingDTO> updatePairingActivity(
            @Parameter(description = "Pairing ID", required = true)
            @PathVariable Long id,
            @Valid @RequestBody UpdatePairingActivityDTO request) {
        
        log.info("Updating activity for pairing ID: {}", id);
        
        try {
            PairingDTO updatedPairing = pairingService.updatePairingActivity(id, request);
            return ResponseEntity.ok(updatedPairing);
        } catch (IllegalArgumentException e) {
            log.error("Pairing not found: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            log.error("Invalid state for activity update: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    @Operation(summary = "Handle pairing breakup", description = "Process a breakup for a pairing")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Breakup processed successfully"),
            @ApiResponse(responseCode = "404", description = "Pairing not found"),
            @ApiResponse(responseCode = "400", description = "Invalid request data"),
            @ApiResponse(responseCode = "403", description = "User not authorized to break up this pairing")
    })
    @PostMapping("/{pairingId}/breakup")
    @PreAuthorize("hasRole('USER') and @pairingSecurityService.isUserInPairing(authentication, #pairingId)")
    public ResponseEntity<PairingDTO> breakupPairing(
            @PathVariable @Positive Long pairingId,
            @Valid @RequestBody BreakupRequestDTO request,
            Authentication authentication) {
        
        // Additional security check - ensure the initiator matches the authenticated user
        String authenticatedUserId = authentication.getName();
        if (!authenticatedUserId.equals(request.getInitiatorId())) {
            log.warn("Authenticated user {} does not match initiator ID {} for pairing breakup", 
                    authenticatedUserId, request.getInitiatorId());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Initiator ID must match authenticated user");
        }
        
        try {
            log.info("Processing breakup for pairing {} initiated by user {}", pairingId, authenticatedUserId);
            PairingDTO updatedPairing = pairingService.breakupPairing(pairingId, request);
            log.info("Successfully processed breakup for pairing {}", pairingId);
            return ResponseEntity.ok(updatedPairing);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid breakup request for pairing {}: {}", pairingId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (IllegalStateException e) {
            log.warn("Invalid state for breakup on pairing {}: {}", pairingId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            log.error("Error processing breakup for pairing: {}", pairingId, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to process breakup");
        }
    }

    @Operation(summary = "Get all active pairings", description = "Retrieve all currently active pairings")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Active pairings retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Authentication required")
    })
    @GetMapping("/active")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<PublicPairingDTO>> getAllActivePairings() {
        log.info("Getting all active pairings (public data only)");
        
        List<PublicPairingDTO> activePairings = pairingService.getAllActivePairingsPublic();
        return ResponseEntity.ok(activePairings);
    }

    @Operation(summary = "Get pairing history for a user", description = "Retrieve the pairing history for a specific user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Pairing history retrieved successfully")
    })
    @GetMapping("/history")
    public ResponseEntity<List<PairingDTO>> getPairingHistory(
            @Parameter(description = "User ID to get history for", required = true)
            @RequestParam String userId) {
        
        log.info("Getting pairing history for user: {}", userId);
        
        try {
            List<PairingDTO> history = pairingService.getPairingHistory(userId);
            return ResponseEntity.ok(history);
        } catch (Exception e) {
            log.error("Error getting pairing history for user {}: {}", userId, e.getMessage());
            return ResponseEntity.ok(Collections.emptyList());
        }
    }

    @Operation(summary = "Check if users are blacklisted", description = "Check if a pair of users is blacklisted from being matched")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Blacklist status checked successfully")
    })
    @GetMapping("/is-blacklisted")
    public ResponseEntity<BlacklistStatusDTO> checkBlacklistStatus(
            @Parameter(description = "First user ID", required = true)
            @RequestParam String user1Id,
            @Parameter(description = "Second user ID", required = true)
            @RequestParam String user2Id) {
        
        log.info("Checking blacklist status for users {} and {}", user1Id, user2Id);
        
        BlacklistStatusDTO status = pairingService.checkBlacklistStatus(user1Id, user2Id);
        return ResponseEntity.ok(status);
    }

    @Operation(summary = "Perform automatic matchmaking", description = "Run the automatic matchmaking algorithm for users in queue")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Matchmaking completed successfully")
    })
    @PostMapping("/matchmake")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<PairingDTO>> performMatchmaking() {
        log.info("=== ADMIN MATCHMAKING TRIGGERED ===");
        try {
            List<PairingDTO> newPairings = matchmakingService.performMatchmaking();
            log.info("Matchmaking completed. Created {} new pairings", newPairings.size());
            log.info("=== ADMIN MATCHMAKING COMPLETE ===");
            return ResponseEntity.ok(newPairings);
        } catch (Exception e) {
            log.error("Error during matchmaking process", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Matchmaking failed");
        }
    }

    @Operation(summary = "Delete all active pairings", description = "Admin function to delete all active pairings")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "All pairings deleted successfully")
    })
    @DeleteMapping("/admin/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> deleteAllPairings() {
        log.info("Admin requesting to delete all active pairings");
        
        int deletedCount = pairingService.deleteAllPairings();
        
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Successfully deleted all active pairings");
        response.put("deletedCount", deletedCount);
        
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Admin unpair users", description = "Admin function to end an active pairing without removing blacklist entry")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Pairing ended successfully"),
            @ApiResponse(responseCode = "404", description = "Pairing not found"),
            @ApiResponse(responseCode = "403", description = "Admin access required")
    })
    @PostMapping("/admin/{pairingId}/unpair")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> unpairUsers(
            @Parameter(description = "Pairing ID to unpair", required = true)
            @PathVariable Long pairingId,
            Authentication authentication) {
        
        log.info("Admin {} requesting to unpair pairing ID: {}", authentication.getName(), pairingId);
        
        try {
            pairingService.unpairUsers(pairingId, authentication.getName());
            
            Map<String, String> response = new HashMap<>();
            response.put("message", "Users unpaired successfully");
            
            log.info("Pairing {} unpaired by admin {}", pairingId, authentication.getName());
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            log.warn("Pairing not found for unpair: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (IllegalStateException e) {
            log.warn("Invalid state for unpair: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            log.error("Error unpairing pairing {}", pairingId, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to unpair users");
        }
    }

    @Operation(summary = "Permanently delete a pairing record", description = "Admin function to permanently delete a pairing and its blacklist entry")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Pairing permanently deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Pairing not found"),
            @ApiResponse(responseCode = "403", description = "Admin access required")
    })
    @DeleteMapping("/admin/history/{pairingId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> deletePairingPermanently(
            @Parameter(description = "Pairing ID to delete", required = true)
            @PathVariable Long pairingId,
            Authentication authentication) {
        
        log.info("Admin {} requesting permanent deletion of pairing ID: {}", authentication.getName(), pairingId);
        
        try {
            pairingService.deletePairingPermanently(pairingId);
            
            Map<String, String> response = new HashMap<>();
            response.put("message", "Pairing permanently deleted successfully");
            
            log.info("Pairing {} permanently deleted by admin {}", pairingId, authentication.getName());
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            log.warn("Pairing not found for deletion: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (Exception e) {
            log.error("Error permanently deleting pairing {}", pairingId, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to delete pairing permanently");
        }
    }

    @Operation(summary = "Delete all inactive pairings", description = "Admin function to permanently delete all inactive pairings and their blacklist entries")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "All inactive pairings deleted successfully"),
            @ApiResponse(responseCode = "403", description = "Admin access required")
    })
    @DeleteMapping("/admin/history/all-inactive")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> deleteAllInactivePairings(Authentication authentication) {
        log.info("Admin {} requesting deletion of all inactive pairings", authentication.getName());
        
        try {
            long deletedCount = pairingService.deleteAllInactivePairings();
            
            Map<String, Object> response = new HashMap<>();
            response.put("message", "All inactive pairings permanently deleted successfully");
            response.put("deletedCount", deletedCount);
            
            log.info("Admin {} deleted {} inactive pairings", authentication.getName(), deletedCount);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error deleting all inactive pairings", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to delete inactive pairings");
        }
    }

    @PostMapping("/admin/queue/enable")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<QueueConfigDTO> enableQueue(Authentication authentication) {
        try {
            String username = authentication.getName();
            queueService.setQueueEnabled(true, username);
            QueueConfigDTO config = queueService.getQueueConfig();
            
            log.info("Queue enabled by admin: {}", username);
            return ResponseEntity.ok(config);
        } catch (Exception e) {
            log.error("Error enabling queue", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new QueueConfigDTO(false, "Failed to enable queue: " + e.getMessage(), authentication.getName()));
        }
    }

    @PostMapping("/admin/queue/disable")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<QueueConfigDTO> disableQueue(Authentication authentication) {
        try {
            String username = authentication.getName();
            queueService.setQueueEnabled(false, username);
            QueueConfigDTO config = queueService.getQueueConfig();
            
            log.info("Queue disabled by admin: {}", username);
            return ResponseEntity.ok(config);
        } catch (Exception e) {
            log.error("Error disabling queue", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new QueueConfigDTO(true, "Failed to disable queue: " + e.getMessage(), authentication.getName()));
        }
    }

    @GetMapping("/admin/queue/config")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<QueueConfigDTO> getQueueConfig() {
        try {
            QueueConfigDTO config = queueService.getQueueConfig();
            return ResponseEntity.ok(config);
        } catch (Exception e) {
            log.error("Error fetching queue config", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/queue/status")
    @Operation(summary = "Get public queue status", description = "Get the current queue enabled/disabled status (accessible to all users)")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Queue status retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<QueueConfigDTO> getPublicQueueStatus() {
        try {
            QueueConfigDTO config = queueService.getQueueConfig();
            return ResponseEntity.ok(config);
        } catch (Exception e) {
            log.error("Error fetching public queue status", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "Get queue statistics", description = "Admin endpoint to get comprehensive queue analytics and statistics")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Queue statistics retrieved successfully"),
        @ApiResponse(responseCode = "403", description = "Admin access required")
    })
    @GetMapping("/admin/queue/statistics")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<QueueStatsDTO> getQueueStatistics() {
        try {
            log.info("Admin requesting queue statistics");
            QueueStatsDTO stats = queueService.getQueueStatistics();
            log.info("Successfully retrieved queue statistics with {} users in queue", stats.getTotalUsersInQueue());
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Error fetching queue statistics: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to fetch queue statistics: " + e.getMessage());
        }
    }

    @Operation(summary = "Warm up cache", description = "Admin endpoint to pre-compute and cache queue statistics")
    @PostMapping("/admin/queue/cache/warmup")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> warmUpCache() {
        try {
            log.info("Admin requesting cache warm-up");
            queueService.warmUpCache();
            Map<String, String> response = Map.of(
                "status", "success", 
                "message", "Queue statistics cache warmed up successfully"
            );
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error warming up cache: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("status", "error", "message", "Failed to warm up cache: " + e.getMessage()));
        }
    }

    @Operation(summary = "Get cache status", description = "Admin endpoint to check cache health and statistics")
    @GetMapping("/admin/queue/cache/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getCacheStatus() {
        try {
            Map<String, Object> status = queueService.getCacheStatus();
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            log.error("Error fetching cache status: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to fetch cache status: " + e.getMessage()));
        }
    }

    @Operation(summary = "Get queue user details", description = "Admin endpoint to get detailed information about users currently in queue")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Queue user details retrieved successfully"),
        @ApiResponse(responseCode = "403", description = "Admin access required")
    })
    @GetMapping("/admin/queue/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<QueueUserDetailsDTO>> getQueueUserDetails() {
        try {
            log.info("Admin requesting queue user details");
            List<QueueUserDetailsDTO> userDetails = queueService.getQueueUserDetails();
            log.info("Successfully retrieved {} user details from queue", userDetails.size());
            return ResponseEntity.ok(userDetails);
        } catch (Exception e) {
            log.error("Error fetching queue user details: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to fetch queue user details: " + e.getMessage());
        }
    }

    @Operation(summary = "Trigger admin stats refresh", description = "Admin endpoint to manually trigger a refresh of queue statistics")
    @PostMapping("/admin/queue/statistics/refresh")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<QueueStatsDTO> triggerStatsRefresh() {
        try {
            log.info("Admin triggering manual stats refresh");
            queueService.triggerAdminStatsRefresh();
            QueueStatsDTO stats = queueService.getQueueStatistics();
            log.info("Manual stats refresh completed successfully");
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Error during manual stats refresh: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to refresh statistics: " + e.getMessage());
        }
    }


} 