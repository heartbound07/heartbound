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
    
    // Role Multipliers Configuration
    private String roleMultipliers;
    private Boolean roleMultipliersEnabled;
    
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
    
    // Auto Slowmode Configuration
    private Boolean autoSlowmodeEnabled;
    
    private String slowmodeChannelIds; // Comma-separated channel IDs - will validate in service
    
    @Min(1)
    private Integer activityThreshold;
    
    @Min(1)
    private Integer slowmodeTimeWindow;
    
    @Min(0)
    private Integer slowmodeDuration; // 0 to disable slowmode
    
    @Min(1)
    private Integer slowmodeCooldown;

    // Credit Drop Configuration
    @NotNull
    private Boolean creditDropEnabled;

    @Pattern(regexp = "^\\d*$", message = "Channel ID must contain only digits")
    private String creditDropChannelId;

    @NotNull
    @Min(1)
    private Integer creditDropMinAmount;

    @NotNull
    @Min(1)
    private Integer creditDropMaxAmount;

    // Self-Assignable Roles Configuration
    @Pattern(regexp = "^\\d*$", message = "Role ID must contain only digits")
    private String age15RoleId;
    @Pattern(regexp = "^\\d*$", message = "Role ID must contain only digits")
    private String age16To17RoleId;
    @Pattern(regexp = "^\\d*$", message = "Role ID must contain only digits")
    private String age18PlusRoleId;
    @Pattern(regexp = "^\\d*$", message = "Role ID must contain only digits")
    private String genderSheHerRoleId;
    @Pattern(regexp = "^\\d*$", message = "Role ID must contain only digits")
    private String genderHeHimRoleId;
    @Pattern(regexp = "^\\d*$", message = "Role ID must contain only digits")
    private String genderAskRoleId;
    @Pattern(regexp = "^\\d*$", message = "Role ID must contain only digits")
    private String rankIronRoleId;
    @Pattern(regexp = "^\\d*$", message = "Role ID must contain only digits")
    private String rankBronzeRoleId;
    @Pattern(regexp = "^\\d*$", message = "Role ID must contain only digits")
    private String rankSilverRoleId;
    @Pattern(regexp = "^\\d*$", message = "Role ID must contain only digits")
    private String rankGoldRoleId;
    @Pattern(regexp = "^\\d*$", message = "Role ID must contain only digits")
    private String rankPlatinumRoleId;
    @Pattern(regexp = "^\\d*$", message = "Role ID must contain only digits")
    private String rankDiamondRoleId;

    // Verified Rank Roles (Admin/Mod assigned)
    @Pattern(regexp = "^\\d*$", message = "Role ID must contain only digits")
    private String rankAscendantRoleId;
    @Pattern(regexp = "^\\d*$", message = "Role ID must contain only digits")
    private String rankImmortalRoleId;
    @Pattern(regexp = "^\\d*$", message = "Role ID must contain only digits")
    private String rankRadiantRoleId;

    // Thumbnail URLs for Self-Assignable Roles
    private String ageRolesThumbnailUrl;
    private String genderRolesThumbnailUrl;
    private String rankRolesThumbnailUrl;

    @Pattern(regexp = "^\\d*$", message = "Role ID must contain only digits")
    private String regionNaRoleId;
    @Pattern(regexp = "^\\d*$", message = "Role ID must contain only digits")
    private String regionEuRoleId;
    @Pattern(regexp = "^\\d*$", message = "Role ID must contain only digits")
    private String regionSaRoleId;
    @Pattern(regexp = "^\\d*$", message = "Role ID must contain only digits")
    private String regionApRoleId;
    @Pattern(regexp = "^\\d*$", message = "Role ID must contain only digits")
    private String regionOceRoleId;
    private String regionRolesThumbnailUrl;

    // Fishing Game Configuration
    @NotNull
    @Min(1)
    private Integer fishingMaxCatches;

    @NotNull
    @Min(1)
    private Integer fishingCooldownHours;

    @NotNull
    @Min(0)
    private Double fishingLimitWarningThreshold;

    @NotNull
    @Min(0)
    private Integer fishingPenaltyCredits;
} 