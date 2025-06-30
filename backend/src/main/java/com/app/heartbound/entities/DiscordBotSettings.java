package com.app.heartbound.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "discord_bot_settings")
@Getter @Setter
public class DiscordBotSettings {
    
    @Id
    private Long id = 1L; // Single row configuration
    
    private Boolean activityEnabled = true;
    private Integer creditsToAward = 5;
    private Integer messageThreshold = 5;
    private Integer timeWindowMinutes = 60;
    private Integer cooldownSeconds = 30;
    private Integer minMessageLength = 15;
    
    private Boolean levelingEnabled = true;
    private Integer xpToAward = 15;
    private Integer baseXp = 100;
    private Integer levelMultiplier = 50;
    private Integer levelExponent = 2;
    private Integer levelFactor = 5;
    private Integer creditsPerLevel = 50;

    private String level5RoleId = "1161732022704816250";
    private String level15RoleId = "1162632126068437063";
    private String level30RoleId = "1162628059296432148";
    private String level40RoleId = "1162628114195697794";
    private String level50RoleId = "1166539666674167888";
    private String level70RoleId = "1170429914185465906";
    private String level100RoleId = "1162628179043823657";

    // Starter role given to new users (removed when they reach first level milestone)
    private String starterRoleId = "1303106353014771773";

    // Role Multipliers Configuration - format: "roleId1:multiplier1,roleId2:multiplier2"
    private String roleMultipliers;
    private Boolean roleMultipliersEnabled = false;

    // Inactivity channel where users don't accumulate voice time
    private String inactivityChannelId;

    // Counting Game Configuration
    private Boolean countingGameEnabled = false;
    private String countingChannelId;
    private String countingTimeoutRoleId;
    private Integer creditsPerCount = 1;
    private Integer countingLives = 3;

    // Auto Slowmode Configuration
    private Boolean autoSlowmodeEnabled = false;
    private String slowmodeChannelIds; // Comma-separated channel IDs
    private Integer activityThreshold = 10; // Messages per time window
    private Integer slowmodeTimeWindow = 5; // Minutes to monitor
    private Integer slowmodeDuration = 30; // Seconds of slowmode to apply
    private Integer slowmodeCooldown = 10; // Minutes before re-evaluation

    // Getters and setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Boolean getActivityEnabled() {
        return activityEnabled;
    }

    public void setActivityEnabled(Boolean activityEnabled) {
        this.activityEnabled = activityEnabled;
    }

    // Include all other getters and setters following the same pattern
    // ...

    public Integer getCreditsToAward() {
        return creditsToAward;
    }

    public void setCreditsToAward(Integer creditsToAward) {
        this.creditsToAward = creditsToAward;
    }

    public Integer getMessageThreshold() {
        return messageThreshold;
    }

    public void setMessageThreshold(Integer messageThreshold) {
        this.messageThreshold = messageThreshold;
    }

    public Integer getTimeWindowMinutes() {
        return timeWindowMinutes;
    }

    public void setTimeWindowMinutes(Integer timeWindowMinutes) {
        this.timeWindowMinutes = timeWindowMinutes;
    }

    public Integer getCooldownSeconds() {
        return cooldownSeconds;
    }

    public void setCooldownSeconds(Integer cooldownSeconds) {
        this.cooldownSeconds = cooldownSeconds;
    }

    public Integer getMinMessageLength() {
        return minMessageLength;
    }

    public void setMinMessageLength(Integer minMessageLength) {
        this.minMessageLength = minMessageLength;
    }

    public Boolean getLevelingEnabled() {
        return levelingEnabled;
    }

    public void setLevelingEnabled(Boolean levelingEnabled) {
        this.levelingEnabled = levelingEnabled;
    }

    public Integer getXpToAward() {
        return xpToAward;
    }

    public void setXpToAward(Integer xpToAward) {
        this.xpToAward = xpToAward;
    }

    public Integer getBaseXp() {
        return baseXp;
    }

    public void setBaseXp(Integer baseXp) {
        this.baseXp = baseXp;
    }

    public Integer getLevelMultiplier() {
        return levelMultiplier;
    }

    public void setLevelMultiplier(Integer levelMultiplier) {
        this.levelMultiplier = levelMultiplier;
    }

    public Integer getLevelExponent() {
        return levelExponent;
    }

    public void setLevelExponent(Integer levelExponent) {
        this.levelExponent = levelExponent;
    }

    public Integer getLevelFactor() {
        return levelFactor;
    }

    public void setLevelFactor(Integer levelFactor) {
        this.levelFactor = levelFactor;
    }

    public Integer getCreditsPerLevel() {
        return creditsPerLevel;
    }

    public void setCreditsPerLevel(Integer creditsPerLevel) {
        this.creditsPerLevel = creditsPerLevel;
    }

    public String getLevel5RoleId() {
        return level5RoleId;
    }

    public void setLevel5RoleId(String level5RoleId) {
        this.level5RoleId = level5RoleId;
    }

    public String getLevel15RoleId() {
        return level15RoleId;
    }

    public void setLevel15RoleId(String level15RoleId) {
        this.level15RoleId = level15RoleId;
    }

    public String getLevel30RoleId() {
        return level30RoleId;
    }

    public void setLevel30RoleId(String level30RoleId) {
        this.level30RoleId = level30RoleId;
    }

    public String getLevel40RoleId() {
        return level40RoleId;
    }

    public void setLevel40RoleId(String level40RoleId) {
        this.level40RoleId = level40RoleId;
    }

    public String getLevel50RoleId() {
        return level50RoleId;
    }

    public void setLevel50RoleId(String level50RoleId) {
        this.level50RoleId = level50RoleId;
    }

    public String getLevel70RoleId() {
        return level70RoleId;
    }

    public void setLevel70RoleId(String level70RoleId) {
        this.level70RoleId = level70RoleId;
    }

    public String getLevel100RoleId() {
        return level100RoleId;
    }

    public void setLevel100RoleId(String level100RoleId) {
        this.level100RoleId = level100RoleId;
    }

    public String getStarterRoleId() {
        return starterRoleId;
    }

    public void setStarterRoleId(String starterRoleId) {
        this.starterRoleId = starterRoleId;
    }

    public String getInactivityChannelId() {
        return inactivityChannelId;
    }

    public void setInactivityChannelId(String inactivityChannelId) {
        this.inactivityChannelId = inactivityChannelId;
    }

    // Counting Game getters and setters
    public Boolean getCountingGameEnabled() {
        return countingGameEnabled;
    }

    public void setCountingGameEnabled(Boolean countingGameEnabled) {
        this.countingGameEnabled = countingGameEnabled;
    }

    public String getCountingChannelId() {
        return countingChannelId;
    }

    public void setCountingChannelId(String countingChannelId) {
        this.countingChannelId = countingChannelId;
    }

    public String getCountingTimeoutRoleId() {
        return countingTimeoutRoleId;
    }

    public void setCountingTimeoutRoleId(String countingTimeoutRoleId) {
        this.countingTimeoutRoleId = countingTimeoutRoleId;
    }

    public Integer getCreditsPerCount() {
        return creditsPerCount;
    }

    public void setCreditsPerCount(Integer creditsPerCount) {
        this.creditsPerCount = creditsPerCount;
    }

    public Integer getCountingLives() {
        return countingLives;
    }

    public void setCountingLives(Integer countingLives) {
        this.countingLives = countingLives;
    }

    // Auto Slowmode getters and setters
    public Boolean getAutoSlowmodeEnabled() {
        return autoSlowmodeEnabled;
    }

    public void setAutoSlowmodeEnabled(Boolean autoSlowmodeEnabled) {
        this.autoSlowmodeEnabled = autoSlowmodeEnabled;
    }

    public String getSlowmodeChannelIds() {
        return slowmodeChannelIds;
    }

    public void setSlowmodeChannelIds(String slowmodeChannelIds) {
        this.slowmodeChannelIds = slowmodeChannelIds;
    }

    public Integer getActivityThreshold() {
        return activityThreshold;
    }

    public void setActivityThreshold(Integer activityThreshold) {
        this.activityThreshold = activityThreshold;
    }

    public Integer getSlowmodeTimeWindow() {
        return slowmodeTimeWindow;
    }

    public void setSlowmodeTimeWindow(Integer slowmodeTimeWindow) {
        this.slowmodeTimeWindow = slowmodeTimeWindow;
    }

    public Integer getSlowmodeDuration() {
        return slowmodeDuration;
    }

    public void setSlowmodeDuration(Integer slowmodeDuration) {
        this.slowmodeDuration = slowmodeDuration;
    }

    public Integer getSlowmodeCooldown() {
        return slowmodeCooldown;
    }

    public void setSlowmodeCooldown(Integer slowmodeCooldown) {
        this.slowmodeCooldown = slowmodeCooldown;
    }

    // Role Multipliers getters and setters
    public String getRoleMultipliers() {
        return roleMultipliers;
    }

    public void setRoleMultipliers(String roleMultipliers) {
        this.roleMultipliers = roleMultipliers;
    }

    public Boolean getRoleMultipliersEnabled() {
        return roleMultipliersEnabled;
    }

    public void setRoleMultipliersEnabled(Boolean roleMultipliersEnabled) {
        this.roleMultipliersEnabled = roleMultipliersEnabled;
    }
} 