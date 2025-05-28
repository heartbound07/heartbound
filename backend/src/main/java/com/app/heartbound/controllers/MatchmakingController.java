package com.app.heartbound.controllers;

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
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.HashMap;

import com.app.heartbound.dto.pairing.JoinQueueRequestDTO;
import com.app.heartbound.dto.pairing.QueueStatusDTO;
import com.app.heartbound.services.pairing.QueueService;

@RestController
@RequestMapping("/matchmaking")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Matchmaking", description = "Endpoints for managing matchmaking queue")
@PreAuthorize("hasRole('USER')")
public class MatchmakingController {

    private final QueueService queueService;

    @Operation(summary = "Get queue status for user", description = "Check if user is currently in matchmaking queue")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Queue status retrieved successfully")
    })
    @GetMapping("/status")
    public ResponseEntity<QueueStatusDTO> getQueueStatus(
            @Parameter(description = "User ID to check queue status for", required = true)
            @RequestParam String userId) {
        
        log.info("Getting queue status for user: {}", userId);
        
        try {
            QueueStatusDTO status = queueService.getQueueStatus(userId);
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            log.error("Error getting queue status: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "Join matchmaking queue", description = "Add user to the matchmaking queue")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully joined queue"),
            @ApiResponse(responseCode = "400", description = "Invalid request data"),
            @ApiResponse(responseCode = "409", description = "User already in queue")
    })
    @PostMapping("/join")
    public ResponseEntity<QueueStatusDTO> joinQueue(@Valid @RequestBody JoinQueueRequestDTO request) {
        log.info("User {} joining matchmaking queue with preferences - Age: {}, Region: {}, Rank: {}", 
                 request.getUserId(), request.getAge(), request.getRegion(), request.getRank());
        
        try {
            QueueStatusDTO status = queueService.joinQueue(request);
            return ResponseEntity.ok(status);
        } catch (IllegalStateException e) {
            log.error("Conflict when joining queue: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        } catch (Exception e) {
            log.error("Error joining queue: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "Leave matchmaking queue", description = "Remove user from the matchmaking queue")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully left queue"),
            @ApiResponse(responseCode = "400", description = "User not in queue")
    })
    @PostMapping("/leave")
    public ResponseEntity<Map<String, Object>> leaveQueue(@RequestParam String userId) {
        log.info("User {} leaving matchmaking queue", userId);
        
        try {
            queueService.leaveQueue(userId);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Left matchmaking queue");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error leaving queue: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
} 