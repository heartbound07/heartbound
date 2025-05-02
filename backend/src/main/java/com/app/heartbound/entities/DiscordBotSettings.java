package com.app.heartbound.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "discord_bot_settings")
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
} 