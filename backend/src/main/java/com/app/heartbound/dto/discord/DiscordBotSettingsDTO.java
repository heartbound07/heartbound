package com.app.heartbound.dto.discord;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

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

    // Getters and setters
    public Boolean getActivityEnabled() {
        return activityEnabled;
    }

    public void setActivityEnabled(Boolean activityEnabled) {
        this.activityEnabled = activityEnabled;
    }

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
} 