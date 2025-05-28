package com.app.heartbound.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/matchmaking")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Matchmaking", description = "Endpoints for managing matchmaking queue")
@PreAuthorize("hasRole('USER')")
public class MatchmakingController {

    @Operation(summary = "Get queue status for user", description = "Check if user is currently in matchmaking queue")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Queue status retrieved successfully")
    })
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getQueueStatus(
            @Parameter(description = "User ID to check queue status for", required = true)
            @RequestParam String userId) {
        
        log.info("Getting queue status for user: {}", userId);
        
        // For now, return default status (not in queue)
        // TODO: Implement actual queue status checking
        Map<String, Object> status = new HashMap<>();
        status.put("inQueue", false);
        status.put("estimatedWaitTime", null);
        status.put("queuePosition", null);
        
        return ResponseEntity.ok(status);
    }

    @Operation(summary = "Join matchmaking queue", description = "Add user to the matchmaking queue")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully joined queue"),
            @ApiResponse(responseCode = "400", description = "Invalid request data"),
            @ApiResponse(responseCode = "409", description = "User already in queue")
    })
    @PostMapping("/join")
    public ResponseEntity<Map<String, Object>> joinQueue(@RequestBody Map<String, Object> request) {
        
        String userId = (String) request.get("userId");
        Integer age = (Integer) request.get("age");
        String region = (String) request.get("region");
        String rank = (String) request.get("rank");
        
        log.info("User {} joining matchmaking queue with preferences - Age: {}, Region: {}, Rank: {}", 
                 userId, age, region, rank);
        
        // TODO: Implement actual queue joining logic
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Joined matchmaking queue");
        response.put("estimatedWaitTime", "2-5 minutes");
        
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Leave matchmaking queue", description = "Remove user from the matchmaking queue")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully left queue"),
            @ApiResponse(responseCode = "400", description = "User not in queue")
    })
    @PostMapping("/leave")
    public ResponseEntity<Map<String, Object>> leaveQueue(@RequestParam String userId) {
        
        log.info("User {} leaving matchmaking queue", userId);
        
        // TODO: Implement actual queue leaving logic
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Left matchmaking queue");
        
        return ResponseEntity.ok(response);
    }
} 