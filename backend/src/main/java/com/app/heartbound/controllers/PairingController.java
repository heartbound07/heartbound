package com.app.heartbound.controllers;

import com.app.heartbound.dto.pairing.*;
import com.app.heartbound.services.pairing.MatchmakingService;
import com.app.heartbound.services.pairing.PairingService;
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
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

/**
 * PairingController
 * 
 * REST controller for managing user pairings in the "Don't Catch Feelings Challenge".
 */
@RestController
@RequestMapping("/api/pairings")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Pairings", description = "Endpoints for managing user pairings")
public class PairingController {

    private final PairingService pairingService;
    private final MatchmakingService matchmakingService;

    @Operation(summary = "Create a new pairing", description = "Create a pairing between two users")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Pairing created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request data"),
            @ApiResponse(responseCode = "409", description = "Users already paired or blacklisted")
    })
    @PostMapping
    public ResponseEntity<PairingDTO> createPairing(@Valid @RequestBody CreatePairingRequestDTO request) {
        log.info("Creating pairing between users {} and {}", request.getUser1Id(), request.getUser2Id());
        
        try {
            PairingDTO createdPairing = pairingService.createPairing(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(createdPairing);
        } catch (IllegalArgumentException e) {
            log.error("Bad request for pairing creation: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (IllegalStateException e) {
            log.error("Conflict in pairing creation: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    @Operation(summary = "Get current active pairing for a user", description = "Retrieve the active pairing for a specific user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Pairing found"),
            @ApiResponse(responseCode = "404", description = "No active pairing found")
    })
    @GetMapping("/current")
    public ResponseEntity<PairingDTO> getCurrentPairing(
            @Parameter(description = "User ID to search for", required = true)
            @RequestParam String userId) {
        
        log.info("Getting current pairing for user: {}", userId);
        
        Optional<PairingDTO> pairing = pairingService.getCurrentPairing(userId);
        return pairing.map(ResponseEntity::ok)
                     .orElse(ResponseEntity.notFound().build());
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
            @ApiResponse(responseCode = "400", description = "Invalid request data")
    })
    @PostMapping("/{id}/breakup")
    public ResponseEntity<PairingDTO> breakupPairing(
            @Parameter(description = "Pairing ID", required = true)
            @PathVariable Long id,
            @Valid @RequestBody BreakupRequestDTO request) {
        
        log.info("Processing breakup for pairing ID: {} by user: {}", id, request.getInitiatorId());
        
        try {
            PairingDTO brokenUpPairing = pairingService.breakupPairing(id, request);
            return ResponseEntity.ok(brokenUpPairing);
        } catch (IllegalArgumentException e) {
            log.error("Invalid breakup request: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (IllegalStateException e) {
            log.error("Invalid state for breakup: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    @Operation(summary = "Get all active pairings", description = "Retrieve all currently active pairings")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Active pairings retrieved successfully")
    })
    @GetMapping("/active")
    public ResponseEntity<List<PairingDTO>> getAllActivePairings() {
        log.info("Getting all active pairings");
        
        List<PairingDTO> activePairings = pairingService.getAllActivePairings();
        return ResponseEntity.ok(activePairings);
    }

    @Operation(summary = "Get pairing history for a user", description = "Retrieve all past pairings for a specific user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Pairing history retrieved successfully")
    })
    @GetMapping("/history")
    public ResponseEntity<List<PairingDTO>> getPairingHistory(
            @Parameter(description = "User ID to get history for", required = true)
            @RequestParam String userId) {
        
        log.info("Getting pairing history for user: {}", userId);
        
        List<PairingDTO> history = pairingService.getPairingHistory(userId);
        return ResponseEntity.ok(history);
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
    public ResponseEntity<List<PairingDTO>> performMatchmaking() {
        log.info("Starting automatic matchmaking process");
        
        List<PairingDTO> newPairings = matchmakingService.performMatchmaking();
        log.info("Matchmaking completed. Created {} new pairings", newPairings.size());
        
        return ResponseEntity.ok(newPairings);
    }
} 