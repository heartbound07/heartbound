package com.app.heartbound.controllers;

import com.app.heartbound.dto.discord.DiscordBotSettingsDTO;
import com.app.heartbound.dto.discord.TimedOutUserDTO;
import com.app.heartbound.services.discord.DiscordBotSettingsService;
import com.app.heartbound.services.discord.CountingGameService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/discord")
@Tag(name = "Discord Bot Management", description = "APIs for managing Discord bot settings")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Slf4j
public class DiscordSettingsController {

    private final DiscordBotSettingsService discordBotSettingsService;
    private final CountingGameService countingGameService;

    @GetMapping("/settings")
    @Operation(summary = "Get Discord bot settings", description = "Retrieves current Discord bot activity and leveling settings")
    @ApiResponse(responseCode = "200", description = "Settings retrieved successfully")
    @ApiResponse(responseCode = "403", description = "Access denied")
    public ResponseEntity<DiscordBotSettingsDTO> getSettings() {
        log.info("GET request for Discord bot settings");
        return ResponseEntity.ok(discordBotSettingsService.getCurrentSettings());
    }

    @PutMapping("/settings")
    @Operation(summary = "Update Discord bot settings", description = "Updates Discord bot activity and leveling settings")
    @ApiResponse(responseCode = "200", description = "Settings updated successfully")
    @ApiResponse(responseCode = "400", description = "Invalid settings data")
    @ApiResponse(responseCode = "403", description = "Access denied")
    public ResponseEntity<DiscordBotSettingsDTO> updateSettings(@Valid @RequestBody DiscordBotSettingsDTO settings) {
        log.info("PUT request to update Discord bot settings");
        return ResponseEntity.ok(discordBotSettingsService.updateSettings(settings));
    }

    @GetMapping("/counting/timeouts")
    @Operation(summary = "Get timed out users", description = "Retrieves list of users currently timed out from counting game")
    @ApiResponse(responseCode = "200", description = "Timed out users retrieved successfully")
    @ApiResponse(responseCode = "403", description = "Access denied")
    public ResponseEntity<List<TimedOutUserDTO>> getTimedOutUsers() {
        log.info("GET request for timed out users");
        return ResponseEntity.ok(countingGameService.getTimedOutUsers());
    }

    @DeleteMapping("/counting/timeouts/{userId}")
    @Operation(summary = "Remove user timeout", description = "Removes timeout for a specific user (admin action)")
    @ApiResponse(responseCode = "200", description = "Timeout removed successfully")
    @ApiResponse(responseCode = "404", description = "User not found or not timed out")
    @ApiResponse(responseCode = "403", description = "Access denied")
    public ResponseEntity<Void> removeUserTimeout(@PathVariable String userId) {
        log.info("DELETE request to remove timeout for user {}", userId);
        boolean success = countingGameService.removeUserTimeout(userId);
        
        if (success) {
            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }
} 