package com.app.heartbound.dto.discord;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;
import com.app.heartbound.validation.ValidFishingLimit;

@Getter @Setter
@ValidFishingLimit
public class DiscordBotSettingsDTO {
    
    @NotNull
    private Boolean activityEnabled;
    
    @NotNull
    @Min(1)
    @Max(1000) // Reasonable upper limit
    private Integer creditsToAward;
    
    @NotNull
    @Min(1)
    @Max(100) // Reasonable upper limit
    private Integer messageThreshold;
    
    @NotNull
    @Min(1)
    @Max(1440) // Maximum 24 hours
    private Integer timeWindowMinutes;
    
    @NotNull
    @Min(1)
    @Max(3600) // Maximum 1 hour cooldown
    private Integer cooldownSeconds;
    
    @NotNull
    @Min(1)
    @Max(500) // Reasonable message length limit
    private Integer minMessageLength;
    
    @NotNull
    private Boolean levelingEnabled;
    
    @NotNull
    @Min(1)
    @Max(1000) // Reasonable XP limit
    private Integer xpToAward;
    
    @NotNull
    @Min(1)
    @Max(10000) // Reasonable base XP limit
    private Integer baseXp;
    
    @NotNull
    @Min(1)
    @Max(1000) // Reasonable multiplier limit
    private Integer levelMultiplier;
    
    @NotNull
    @Min(1)
    @Max(5) // Reasonable exponent limit
    private Integer levelExponent;
    
    @NotNull
    @Min(1)
    @Max(100) // Reasonable factor limit
    private Integer levelFactor;
    
    @NotNull
    @Min(1)
    @Max(10000) // Reasonable credits per level limit
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

    // Part Drop Configuration
    @NotNull
    private Boolean partDropEnabled;

    @Pattern(regexp = "^\\d*$", message = "Channel ID must contain only digits")
    private String partDropChannelId;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = false)
    @DecimalMax(value = "1.0")
    private Double partDropChance;

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
    @Max(10000)
    private Integer fishingMinCatches;
    
    @NotNull
    @Min(1)
    @Max(10000)
    private Integer fishingMaxCatches;

    @NotNull
    @Min(1)
    @Max(10000) // Reasonable upper limit to prevent abuse
    private Integer fishingDefaultMaxCatches;

    @NotNull
    @Min(1)
    @Max(168) // Maximum 1 week cooldown to prevent indefinite lockout
    private Integer fishingCooldownHours;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = true)
    @DecimalMax(value = "1.0", inclusive = true)
    private Double fishingLimitWarningThreshold;

    @NotNull
    @Min(0)
    @Max(50000) // Reasonable upper limit to prevent excessive penalties
    private Integer fishingPenaltyCredits;
} 