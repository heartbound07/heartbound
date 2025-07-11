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

    // Self-Assignable Roles Configuration
    private String age15RoleId;
    private String age16To17RoleId;
    private String age18PlusRoleId;
    private String genderSheHerRoleId;
    private String genderHeHimRoleId;
    private String genderAskRoleId;
    private String rankIronRoleId;
    private String rankBronzeRoleId;
    private String rankSilverRoleId;
    private String rankGoldRoleId;
    private String rankPlatinumRoleId;
    private String rankDiamondRoleId;

    // Verified Rank Roles (Admin/Mod assigned)
    private String rankAscendantRoleId;
    private String rankImmortalRoleId;
    private String rankRadiantRoleId;

    // Thumbnail URLs for Self-Assignable Roles
    private String ageRolesThumbnailUrl;
    private String genderRolesThumbnailUrl;
    private String rankRolesThumbnailUrl;

    private String regionNaRoleId;
    private String regionEuRoleId;
    private String regionSaRoleId;
    private String regionApRoleId;
    private String regionOceRoleId;
    private String regionRolesThumbnailUrl;

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

    public String getAge15RoleId() {
        return age15RoleId;
    }

    public void setAge15RoleId(String age15RoleId) {
        this.age15RoleId = age15RoleId;
    }

    public String getAge16To17RoleId() {
        return age16To17RoleId;
    }

    public void setAge16To17RoleId(String age16To17RoleId) {
        this.age16To17RoleId = age16To17RoleId;
    }

    public String getAge18PlusRoleId() {
        return age18PlusRoleId;
    }

    public void setAge18PlusRoleId(String age18PlusRoleId) {
        this.age18PlusRoleId = age18PlusRoleId;
    }

    public String getGenderSheHerRoleId() {
        return genderSheHerRoleId;
    }

    public void setGenderSheHerRoleId(String genderSheHerRoleId) {
        this.genderSheHerRoleId = genderSheHerRoleId;
    }

    public String getGenderHeHimRoleId() {
        return genderHeHimRoleId;
    }

    public void setGenderHeHimRoleId(String genderHeHimRoleId) {
        this.genderHeHimRoleId = genderHeHimRoleId;
    }

    public String getGenderAskRoleId() {
        return genderAskRoleId;
    }

    public void setGenderAskRoleId(String genderAskRoleId) {
        this.genderAskRoleId = genderAskRoleId;
    }

    public String getRankIronRoleId() {
        return rankIronRoleId;
    }

    public void setRankIronRoleId(String rankIronRoleId) {
        this.rankIronRoleId = rankIronRoleId;
    }

    public String getRankBronzeRoleId() {
        return rankBronzeRoleId;
    }

    public void setRankBronzeRoleId(String rankBronzeRoleId) {
        this.rankBronzeRoleId = rankBronzeRoleId;
    }

    public String getRankSilverRoleId() {
        return rankSilverRoleId;
    }

    public void setRankSilverRoleId(String rankSilverRoleId) {
        this.rankSilverRoleId = rankSilverRoleId;
    }

    public String getRankGoldRoleId() {
        return rankGoldRoleId;
    }

    public void setRankGoldRoleId(String rankGoldRoleId) {
        this.rankGoldRoleId = rankGoldRoleId;
    }

    public String getRankPlatinumRoleId() {
        return rankPlatinumRoleId;
    }

    public void setRankPlatinumRoleId(String rankPlatinumRoleId) {
        this.rankPlatinumRoleId = rankPlatinumRoleId;
    }

    public String getRankDiamondRoleId() {
        return rankDiamondRoleId;
    }

    public void setRankDiamondRoleId(String rankDiamondRoleId) {
        this.rankDiamondRoleId = rankDiamondRoleId;
    }

    public String getRankAscendantRoleId() {
        return rankAscendantRoleId;
    }

    public void setRankAscendantRoleId(String rankAscendantRoleId) {
        this.rankAscendantRoleId = rankAscendantRoleId;
    }

    public String getRankImmortalRoleId() {
        return rankImmortalRoleId;
    }

    public void setRankImmortalRoleId(String rankImmortalRoleId) {
        this.rankImmortalRoleId = rankImmortalRoleId;
    }

    public String getRankRadiantRoleId() {
        return rankRadiantRoleId;
    }

    public void setRankRadiantRoleId(String rankRadiantRoleId) {
        this.rankRadiantRoleId = rankRadiantRoleId;
    }

    public String getAgeRolesThumbnailUrl() {
        return ageRolesThumbnailUrl;
    }

    public void setAgeRolesThumbnailUrl(String ageRolesThumbnailUrl) {
        this.ageRolesThumbnailUrl = ageRolesThumbnailUrl;
    }

    public String getGenderRolesThumbnailUrl() {
        return genderRolesThumbnailUrl;
    }

    public void setGenderRolesThumbnailUrl(String genderRolesThumbnailUrl) {
        this.genderRolesThumbnailUrl = genderRolesThumbnailUrl;
    }

    public String getRankRolesThumbnailUrl() {
        return rankRolesThumbnailUrl;
    }

    public void setRankRolesThumbnailUrl(String rankRolesThumbnailUrl) {
        this.rankRolesThumbnailUrl = rankRolesThumbnailUrl;
    }

    public String getRegionNaRoleId() {
        return regionNaRoleId;
    }

    public void setRegionNaRoleId(String regionNaRoleId) {
        this.regionNaRoleId = regionNaRoleId;
    }

    public String getRegionEuRoleId() {
        return regionEuRoleId;
    }

    public void setRegionEuRoleId(String regionEuRoleId) {
        this.regionEuRoleId = regionEuRoleId;
    }

    public String getRegionSaRoleId() {
        return regionSaRoleId;
    }

    public void setRegionSaRoleId(String regionSaRoleId) {
        this.regionSaRoleId = regionSaRoleId;
    }

    public String getRegionApRoleId() {
        return regionApRoleId;
    }

    public void setRegionApRoleId(String regionApRoleId) {
        this.regionApRoleId = regionApRoleId;
    }

    public String getRegionOceRoleId() {
        return regionOceRoleId;
    }

    public void setRegionOceRoleId(String regionOceRoleId) {
        this.regionOceRoleId = regionOceRoleId;
    }

    public String getRegionRolesThumbnailUrl() {
        return regionRolesThumbnailUrl;
    }

    public void setRegionRolesThumbnailUrl(String regionRolesThumbnailUrl) {
        this.regionRolesThumbnailUrl = regionRolesThumbnailUrl;
    }
} 