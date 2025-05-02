package com.app.heartbound.controllers;

import com.app.heartbound.dto.discord.DiscordBotSettingsDTO;
import com.app.heartbound.services.discord.DiscordBotSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/discord")
@Tag(name = "Discord Bot Management", description = "APIs for managing Discord bot settings")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class DiscordSettingsController {

    private static final Logger logger = LoggerFactory.getLogger(DiscordSettingsController.class);

    @Autowired
    private DiscordBotSettingsService discordBotSettingsService;

    @GetMapping("/settings")
    @Operation(summary = "Get Discord bot settings", description = "Retrieves current Discord bot activity and leveling settings")
    @ApiResponse(responseCode = "200", description = "Settings retrieved successfully")
    @ApiResponse(responseCode = "403", description = "Access denied")
    public ResponseEntity<DiscordBotSettingsDTO> getSettings() {
        logger.info("GET request for Discord bot settings");
        return ResponseEntity.ok(discordBotSettingsService.getCurrentSettings());
    }

    @PutMapping("/settings")
    @Operation(summary = "Update Discord bot settings", description = "Updates Discord bot activity and leveling settings")
    @ApiResponse(responseCode = "200", description = "Settings updated successfully")
    @ApiResponse(responseCode = "400", description = "Invalid settings data")
    @ApiResponse(responseCode = "403", description = "Access denied")
    public ResponseEntity<DiscordBotSettingsDTO> updateSettings(@Valid @RequestBody DiscordBotSettingsDTO settings) {
        logger.info("PUT request to update Discord bot settings");
        return ResponseEntity.ok(discordBotSettingsService.updateSettings(settings));
    }
} 