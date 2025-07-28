package com.app.heartbound.services.discord;

import com.app.heartbound.dto.discord.DiscordBotSettingsDTO;
import com.app.heartbound.entities.DiscordBotSettings;
import com.app.heartbound.repositories.DiscordBotSettingsRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@Slf4j
@RequiredArgsConstructor
public class DiscordBotSettingsService {
    
    private final DiscordBotSettingsRepository repository;
    private final ChatActivityListener chatActivityListener;
    private final UserVoiceActivityService userVoiceActivityService;
    private final CountingGameService countingGameService;
    
    @Autowired
    @Lazy
    private DiscordBotSettingsService self;
    
    // Import default values from application.properties
    @Value("${discord.activity.enabled:true}")
    private boolean defaultActivityEnabled;
    
    @Value("${discord.activity.credits-to-award:5}")
    private int defaultCreditsToAward;
    
    @Value("${discord.activity.message-threshold:5}")
    private int defaultMessageThreshold;
    
    @Value("${discord.activity.time-window-minutes:60}")
    private int defaultTimeWindowMinutes;
    
    @Value("${discord.activity.cooldown-seconds:30}")
    private int defaultCooldownSeconds;
    
    @Value("${discord.activity.min-message-length:15}")
    private int defaultMinMessageLength;
    
    @Value("${discord.leveling.enabled:true}")
    private boolean defaultLevelingEnabled;
    
    @Value("${discord.leveling.xp-to-award:15}")
    private int defaultXpToAward;
    
    @Value("${discord.leveling.base-xp:100}")
    private int defaultBaseXp;
    
    @Value("${discord.leveling.level-multiplier:50}")
    private int defaultLevelMultiplier;
    
    @Value("${discord.leveling.level-exponent:2}")
    private int defaultLevelExponent;
    
    @Value("${discord.leveling.level-factor:5}")
    private int defaultLevelFactor;
    
    @Value("${discord.leveling.credits-per-level:50}")
    private int defaultCreditsPerLevel;
    
    @Value("${discord.leveling.starter-role-id:1303106353014771773}")
    private String defaultStarterRoleId;
    
    @PostConstruct
    public void init() {
        initializeSettings();
    }
    
    @Transactional
    public void initializeSettings() {
        // Check if settings exist, if not create with defaults
        if (repository.count() == 0) {
            log.info("Initializing Discord bot settings with defaults");
            DiscordBotSettings settings = new DiscordBotSettings();
            
            // Default role IDs
            settings.setLevel5RoleId("1161732022704816250");
            settings.setLevel15RoleId("1162632126068437063");
            settings.setLevel30RoleId("1162628059296432148");
            settings.setLevel40RoleId("1162628114195697794");
            settings.setLevel50RoleId("1166539666674167888");
            settings.setLevel70RoleId("1170429914185465906");
            settings.setLevel100RoleId("1162628179043823657");
            
            // Set default starter role ID
            settings.setStarterRoleId(defaultStarterRoleId);

            // Initialize self-assignable roles to prevent nulls
            settings.setAge15RoleId("");
            settings.setAge16To17RoleId("");
            settings.setAge18PlusRoleId("");
            settings.setGenderSheHerRoleId("");
            settings.setGenderHeHimRoleId("");
            settings.setGenderAskRoleId("");
            settings.setRankIronRoleId("");
            settings.setRankBronzeRoleId("");
            settings.setRankSilverRoleId("");
            settings.setRankGoldRoleId("");
            settings.setRankPlatinumRoleId("");
            settings.setRankDiamondRoleId("");
            
            // Initialize verified rank roles
            settings.setRankAscendantRoleId("");
            settings.setRankImmortalRoleId("");
            settings.setRankRadiantRoleId("");

            // Initialize thumbnail URLs
            settings.setAgeRolesThumbnailUrl("");
            settings.setGenderRolesThumbnailUrl("");
            settings.setRankRolesThumbnailUrl("");
            settings.setRegionNaRoleId("");
            settings.setRegionEuRoleId("");
            settings.setRegionSaRoleId("");
            settings.setRegionApRoleId("");
            settings.setRegionOceRoleId("");
            settings.setRegionRolesThumbnailUrl("");

            // Initialize credit drop settings
            settings.setCreditDropEnabled(false);
            settings.setCreditDropChannelId("");
            settings.setCreditDropMinAmount(1);
            settings.setCreditDropMaxAmount(1000);

            // Initialize fishing game settings with defaults
            settings.setFishingMaxCatches(300);
            settings.setFishingCooldownHours(6);
            settings.setFishingLimitWarningThreshold(0.9);
            settings.setFishingPenaltyCredits(50);

            repository.save(settings);
        }
        
        // Apply current settings to the ChatActivityListener
        applyCurrentSettings();
    }
    
    @Transactional(readOnly = true)
    @Cacheable(value = "discordBotSettings", key = "#root.methodName")
    public DiscordBotSettings getDiscordBotSettings() {
        return repository.findById(1L).orElse(new DiscordBotSettings());
    }

    @Transactional(readOnly = true)
    public DiscordBotSettingsDTO getDiscordBotSettingsAsDTO() {
        // Try to get from cache first
        DiscordBotSettings settings = self.getDiscordBotSettings();
        
        DiscordBotSettingsDTO dto = new DiscordBotSettingsDTO();
        // Map entity to DTO (this would be cleaner with ModelMapper or MapStruct)
        dto.setActivityEnabled(settings.getActivityEnabled());
        dto.setCreditsToAward(settings.getCreditsToAward());
        dto.setMessageThreshold(settings.getMessageThreshold());
        dto.setTimeWindowMinutes(settings.getTimeWindowMinutes());
        dto.setCooldownSeconds(settings.getCooldownSeconds());
        dto.setMinMessageLength(settings.getMinMessageLength());
        
        dto.setLevelingEnabled(settings.getLevelingEnabled());
        dto.setXpToAward(settings.getXpToAward());
        dto.setBaseXp(settings.getBaseXp());
        dto.setLevelMultiplier(settings.getLevelMultiplier());
        dto.setLevelExponent(settings.getLevelExponent());
        dto.setLevelFactor(settings.getLevelFactor());
        dto.setCreditsPerLevel(settings.getCreditsPerLevel());
        
        // Map role IDs
        dto.setLevel5RoleId(settings.getLevel5RoleId());
        dto.setLevel15RoleId(settings.getLevel15RoleId());
        dto.setLevel30RoleId(settings.getLevel30RoleId());
        dto.setLevel40RoleId(settings.getLevel40RoleId());
        dto.setLevel50RoleId(settings.getLevel50RoleId());
        dto.setLevel70RoleId(settings.getLevel70RoleId());
        dto.setLevel100RoleId(settings.getLevel100RoleId());
        dto.setStarterRoleId(settings.getStarterRoleId());
        
        // Map role multipliers
        dto.setRoleMultipliers(settings.getRoleMultipliers());
        dto.setRoleMultipliersEnabled(settings.getRoleMultipliersEnabled());
        
        // Map inactivity channel ID
        dto.setInactivityChannelId(settings.getInactivityChannelId());
        
        // Map counting game settings
        dto.setCountingGameEnabled(settings.getCountingGameEnabled());
        dto.setCountingChannelId(settings.getCountingChannelId());
        dto.setCountingTimeoutRoleId(settings.getCountingTimeoutRoleId());
        dto.setCreditsPerCount(settings.getCreditsPerCount());
        dto.setCountingLives(settings.getCountingLives());
        
        // Map auto slowmode settings
        dto.setAutoSlowmodeEnabled(settings.getAutoSlowmodeEnabled());
        dto.setSlowmodeChannelIds(settings.getSlowmodeChannelIds());
        dto.setActivityThreshold(settings.getActivityThreshold());
        dto.setSlowmodeTimeWindow(settings.getSlowmodeTimeWindow());
        dto.setSlowmodeDuration(settings.getSlowmodeDuration());
        dto.setSlowmodeCooldown(settings.getSlowmodeCooldown());

        // Map credit drop settings
        dto.setCreditDropEnabled(settings.getCreditDropEnabled());
        dto.setCreditDropChannelId(settings.getCreditDropChannelId());
        dto.setCreditDropMinAmount(settings.getCreditDropMinAmount());
        dto.setCreditDropMaxAmount(settings.getCreditDropMaxAmount());

        // Map self-assignable roles
        dto.setAge15RoleId(settings.getAge15RoleId());
        dto.setAge16To17RoleId(settings.getAge16To17RoleId());
        dto.setAge18PlusRoleId(settings.getAge18PlusRoleId());
        dto.setGenderSheHerRoleId(settings.getGenderSheHerRoleId());
        dto.setGenderHeHimRoleId(settings.getGenderHeHimRoleId());
        dto.setGenderAskRoleId(settings.getGenderAskRoleId());
        dto.setRankIronRoleId(settings.getRankIronRoleId());
        dto.setRankBronzeRoleId(settings.getRankBronzeRoleId());
        dto.setRankSilverRoleId(settings.getRankSilverRoleId());
        dto.setRankGoldRoleId(settings.getRankGoldRoleId());
        dto.setRankPlatinumRoleId(settings.getRankPlatinumRoleId());
        dto.setRankDiamondRoleId(settings.getRankDiamondRoleId());
        
        // Map verified rank roles
        dto.setRankAscendantRoleId(settings.getRankAscendantRoleId());
        dto.setRankImmortalRoleId(settings.getRankImmortalRoleId());
        dto.setRankRadiantRoleId(settings.getRankRadiantRoleId());
        
        // Map thumbnail URLs
        dto.setAgeRolesThumbnailUrl(settings.getAgeRolesThumbnailUrl());
        dto.setGenderRolesThumbnailUrl(settings.getGenderRolesThumbnailUrl());
        dto.setRankRolesThumbnailUrl(settings.getRankRolesThumbnailUrl());

        // Map region roles
        dto.setRegionNaRoleId(settings.getRegionNaRoleId());
        dto.setRegionEuRoleId(settings.getRegionEuRoleId());
        dto.setRegionSaRoleId(settings.getRegionSaRoleId());
        dto.setRegionApRoleId(settings.getRegionApRoleId());
        dto.setRegionOceRoleId(settings.getRegionOceRoleId());
        dto.setRegionRolesThumbnailUrl(settings.getRegionRolesThumbnailUrl());

        // Map fishing game settings
        dto.setFishingMaxCatches(settings.getFishingMaxCatches());
        dto.setFishingCooldownHours(settings.getFishingCooldownHours());
        dto.setFishingLimitWarningThreshold(settings.getFishingLimitWarningThreshold());
        dto.setFishingPenaltyCredits(settings.getFishingPenaltyCredits());

        return dto;
    }
    
    @Transactional
    @CacheEvict(value = "discordBotSettings", allEntries = true)
    public DiscordBotSettingsDTO updateSettings(DiscordBotSettingsDTO dto) {
        DiscordBotSettings settings = repository.findById(1L)
                .orElseThrow(() -> new RuntimeException("Discord bot settings not found"));
        
        // Update all settings
        settings.setActivityEnabled(dto.getActivityEnabled());
        settings.setCreditsToAward(dto.getCreditsToAward());
        settings.setMessageThreshold(dto.getMessageThreshold());
        settings.setTimeWindowMinutes(dto.getTimeWindowMinutes());
        settings.setCooldownSeconds(dto.getCooldownSeconds());
        settings.setMinMessageLength(dto.getMinMessageLength());
        
        settings.setLevelingEnabled(dto.getLevelingEnabled());
        settings.setXpToAward(dto.getXpToAward());
        settings.setBaseXp(dto.getBaseXp());
        settings.setLevelMultiplier(dto.getLevelMultiplier());
        settings.setLevelExponent(dto.getLevelExponent());
        settings.setLevelFactor(dto.getLevelFactor());
        settings.setCreditsPerLevel(dto.getCreditsPerLevel());
        
        // Update role IDs
        settings.setLevel5RoleId(dto.getLevel5RoleId());
        settings.setLevel15RoleId(dto.getLevel15RoleId());
        settings.setLevel30RoleId(dto.getLevel30RoleId());
        settings.setLevel40RoleId(dto.getLevel40RoleId());
        settings.setLevel50RoleId(dto.getLevel50RoleId());
        settings.setLevel70RoleId(dto.getLevel70RoleId());
        settings.setLevel100RoleId(dto.getLevel100RoleId());
        settings.setStarterRoleId(dto.getStarterRoleId());
        
        // Update role multipliers
        settings.setRoleMultipliers(dto.getRoleMultipliers());
        settings.setRoleMultipliersEnabled(dto.getRoleMultipliersEnabled());
        
        // Update inactivity channel ID
        settings.setInactivityChannelId(dto.getInactivityChannelId());
        
        // Update counting game settings
        settings.setCountingGameEnabled(dto.getCountingGameEnabled());
        settings.setCountingChannelId(dto.getCountingChannelId());
        settings.setCountingTimeoutRoleId(dto.getCountingTimeoutRoleId());
        settings.setCreditsPerCount(dto.getCreditsPerCount());
        settings.setCountingLives(dto.getCountingLives());
        
        // Update auto slowmode settings
        settings.setAutoSlowmodeEnabled(dto.getAutoSlowmodeEnabled());
        settings.setSlowmodeChannelIds(dto.getSlowmodeChannelIds());
        settings.setActivityThreshold(dto.getActivityThreshold());
        settings.setSlowmodeTimeWindow(dto.getSlowmodeTimeWindow());
        settings.setSlowmodeDuration(dto.getSlowmodeDuration());
        settings.setSlowmodeCooldown(dto.getSlowmodeCooldown());

        // Update credit drop settings
        settings.setCreditDropEnabled(dto.getCreditDropEnabled());
        settings.setCreditDropChannelId(dto.getCreditDropChannelId());
        settings.setCreditDropMinAmount(dto.getCreditDropMinAmount());
        settings.setCreditDropMaxAmount(dto.getCreditDropMaxAmount());

        // Update self-assignable roles
        settings.setAge15RoleId(dto.getAge15RoleId());
        settings.setAge16To17RoleId(dto.getAge16To17RoleId());
        settings.setAge18PlusRoleId(dto.getAge18PlusRoleId());
        settings.setGenderSheHerRoleId(dto.getGenderSheHerRoleId());
        settings.setGenderHeHimRoleId(dto.getGenderHeHimRoleId());
        settings.setGenderAskRoleId(dto.getGenderAskRoleId());
        settings.setRankIronRoleId(dto.getRankIronRoleId());
        settings.setRankBronzeRoleId(dto.getRankBronzeRoleId());
        settings.setRankSilverRoleId(dto.getRankSilverRoleId());
        settings.setRankGoldRoleId(dto.getRankGoldRoleId());
        settings.setRankPlatinumRoleId(dto.getRankPlatinumRoleId());
        settings.setRankDiamondRoleId(dto.getRankDiamondRoleId());
        
        // Update verified rank roles
        settings.setRankAscendantRoleId(dto.getRankAscendantRoleId());
        settings.setRankImmortalRoleId(dto.getRankImmortalRoleId());
        settings.setRankRadiantRoleId(dto.getRankRadiantRoleId());
        
        // Update thumbnail URLs
        settings.setAgeRolesThumbnailUrl(dto.getAgeRolesThumbnailUrl());
        settings.setGenderRolesThumbnailUrl(dto.getGenderRolesThumbnailUrl());
        settings.setRankRolesThumbnailUrl(dto.getRankRolesThumbnailUrl());

        // Update region roles
        settings.setRegionNaRoleId(dto.getRegionNaRoleId());
        settings.setRegionEuRoleId(dto.getRegionEuRoleId());
        settings.setRegionSaRoleId(dto.getRegionSaRoleId());
        settings.setRegionApRoleId(dto.getRegionApRoleId());
        settings.setRegionOceRoleId(dto.getRegionOceRoleId());
        settings.setRegionRolesThumbnailUrl(dto.getRegionRolesThumbnailUrl());

        // Update fishing game settings
        settings.setFishingMaxCatches(dto.getFishingMaxCatches());
        settings.setFishingCooldownHours(dto.getFishingCooldownHours());
        settings.setFishingLimitWarningThreshold(dto.getFishingLimitWarningThreshold());
        settings.setFishingPenaltyCredits(dto.getFishingPenaltyCredits());

        repository.save(settings);
        
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                // Apply the updated settings to the ChatActivityListener and UserVoiceActivityService
                chatActivityListener.updateSettings(
                    settings.getActivityEnabled(),
                    settings.getCreditsToAward(),
                    settings.getMessageThreshold(),
                    settings.getTimeWindowMinutes(),
                    settings.getCooldownSeconds(),
                    settings.getMinMessageLength(),
                    settings.getLevelingEnabled(),
                    settings.getXpToAward(),
                    settings.getBaseXp(),
                    settings.getLevelMultiplier(),
                    settings.getLevelExponent(),
                    settings.getLevelFactor(),
                    settings.getCreditsPerLevel(),
                    settings.getLevel5RoleId(),
                    settings.getLevel15RoleId(),
                    settings.getLevel30RoleId(),
                    settings.getLevel40RoleId(),
                    settings.getLevel50RoleId(),
                    settings.getLevel70RoleId(),
                    settings.getLevel100RoleId(),
                    settings.getStarterRoleId(),
                    settings.getRoleMultipliers(),
                    settings.getRoleMultipliersEnabled()
                );

                // Update voice activity service with inactivity channel setting
                userVoiceActivityService.updateSettings(settings.getInactivityChannelId());

                // Update counting game service with new settings
                countingGameService.updateSettings(
                    settings.getCountingChannelId(),
                    settings.getCountingTimeoutRoleId(),
                    settings.getCreditsPerCount(),
                    settings.getCountingLives(),
                    settings.getCountingGameEnabled() != null ? settings.getCountingGameEnabled() : false
                );
                log.info("Discord bot settings applied to listeners after transaction commit.");
            }
        });
        
        log.info("Discord bot settings updated successfully in database");
        return dto;
    }
    
    private void applyCurrentSettings() {
        try {
            DiscordBotSettings settings = repository.findById(1L).orElse(null);
            
            if (settings != null) {
                chatActivityListener.updateSettings(
                    settings.getActivityEnabled(),
                    settings.getCreditsToAward(),
                    settings.getMessageThreshold(),
                    settings.getTimeWindowMinutes(),
                    settings.getCooldownSeconds(),
                    settings.getMinMessageLength(),
                    settings.getLevelingEnabled(),
                    settings.getXpToAward(),
                    settings.getBaseXp(),
                    settings.getLevelMultiplier(),
                    settings.getLevelExponent(),
                    settings.getLevelFactor(),
                    settings.getCreditsPerLevel(),
                    settings.getLevel5RoleId(),
                    settings.getLevel15RoleId(),
                    settings.getLevel30RoleId(),
                    settings.getLevel40RoleId(),
                    settings.getLevel50RoleId(),
                    settings.getLevel70RoleId(),
                    settings.getLevel100RoleId(),
                    settings.getStarterRoleId(),
                    settings.getRoleMultipliers(),
                    settings.getRoleMultipliersEnabled()
                );
                
                // Apply voice activity settings
                userVoiceActivityService.updateSettings(settings.getInactivityChannelId());
                
                // Apply counting game settings
                countingGameService.updateSettings(
                    settings.getCountingChannelId(),
                    settings.getCountingTimeoutRoleId(),
                    settings.getCreditsPerCount(),
                    settings.getCountingLives(),
                    settings.getCountingGameEnabled() != null ? settings.getCountingGameEnabled() : false
                );
                
                log.info("Applied Discord bot settings from database");
            }
        } catch (Exception e) {
            log.error("Error applying Discord bot settings: " + e.getMessage(), e);
        }
    }
} 