package com.app.heartbound.dto.discord;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class DiscordBotSettingsDTO {
    
    @NotNull
    private Boolean activityEnabled;
    
    @NotNull
    @Min(1)
    private Integer creditsToAward;
    
    @NotNull
    @Min(1)
    private Integer messageThreshold;
    
    @NotNull
    @Min(1)
    private Integer timeWindowMinutes;
    
    @NotNull
    @Min(1)
    private Integer cooldownSeconds;
    
    @NotNull
    @Min(1)
    private Integer minMessageLength;
    
    @NotNull
    private Boolean levelingEnabled;
    
    @NotNull
    @Min(1)
    private Integer xpToAward;
    
    @NotNull
    @Min(1)
    private Integer baseXp;
    
    @NotNull
    @Min(1)
    private Integer levelMultiplier;
    
    @NotNull
    @Min(1)
    private Integer levelExponent;
    
    @NotNull
    @Min(1)
    private Integer levelFactor;
    
    @NotNull
    @Min(1)
    private Integer creditsPerLevel;
    
    @Pattern(regexp = "^\\d*$", message = "Role ID must contain only digits")
    private String level5RoleId;
    
    @Pattern(regexp = "^\\d*$", message = "Role ID must contain only digits")
    private String level15RoleId;
    
    @Pattern(regexp = "^\\d*$", message = "Role ID must contain only digits")
    private String level30RoleId;
    
    @Pattern(regexp = "^\\d*$", message = "Role ID must contain only digits")
    private String level40RoleId;
    
    @Pattern(regexp = "^\\d*$", message = "Role ID must contain only digits")
    private String level50RoleId;
    
    @Pattern(regexp = "^\\d*$", message = "Role ID must contain only digits")
    private String level70RoleId;
    
    @Pattern(regexp = "^\\d*$", message = "Role ID must contain only digits")
    private String level100RoleId;
    
    @Pattern(regexp = "^\\d*$", message = "Role ID must contain only digits")
    private String starterRoleId;
    
    @Pattern(regexp = "^\\d*$", message = "Channel ID must contain only digits")
    private String inactivityChannelId;
    
    // Counting Game Configuration
    private Boolean countingGameEnabled;
    
    @Pattern(regexp = "^\\d*$", message = "Channel ID must contain only digits")
    private String countingChannelId;
    
    @Pattern(regexp = "^\\d*$", message = "Role ID must contain only digits")
    private String countingTimeoutRoleId;
    
    @Min(1)
    private Integer creditsPerCount;
    
    @Min(1)
    private Integer countingLives;
} 