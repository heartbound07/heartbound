package com.app.heartbound.controllers;

import com.app.heartbound.dto.discord.DiscordBotSettingsDTO;
import com.app.heartbound.services.discord.DiscordBotSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/discord/api")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class DiscordController {

    private final DiscordBotSettingsService discordBotSettingsService;

    @GetMapping("/bot-settings")
    public ResponseEntity<DiscordBotSettingsDTO> getDiscordBotSettings() {
        DiscordBotSettingsDTO settings = discordBotSettingsService.getDiscordBotSettingsAsDTO();
        return ResponseEntity.ok(settings);
    }
} 