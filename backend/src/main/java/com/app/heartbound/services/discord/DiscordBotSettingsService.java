package com.app.heartbound.services.discord;

import com.app.heartbound.dto.discord.DiscordBotSettingsDTO;
import com.app.heartbound.entities.DiscordBotSettings;
import com.app.heartbound.repositories.DiscordBotSettingsRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DiscordBotSettingsService {
    
    private static final Logger logger = LoggerFactory.getLogger(DiscordBotSettingsService.class);
    
    @Autowired
    private DiscordBotSettingsRepository repository;
    
    @Autowired
    private ChatActivityListener chatActivityListener;
    
    @Autowired
    private Environment environment;
    
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
    
    @PostConstruct
    public void init() {
        initializeSettings();
    }
    
    @Transactional
    public void initializeSettings() {
        // Check if settings exist, if not create with defaults
        if (repository.count() == 0) {
            logger.info("Initializing Discord bot settings with defaults");
            DiscordBotSettings settings = new DiscordBotSettings();
            // Set all default values
            settings.setActivityEnabled(defaultActivityEnabled);
            settings.setCreditsToAward(defaultCreditsToAward);
            settings.setMessageThreshold(defaultMessageThreshold);
            settings.setTimeWindowMinutes(defaultTimeWindowMinutes);
            settings.setCooldownSeconds(defaultCooldownSeconds);
            settings.setMinMessageLength(defaultMinMessageLength);
            
            settings.setLevelingEnabled(defaultLevelingEnabled);
            settings.setXpToAward(defaultXpToAward);
            settings.setBaseXp(defaultBaseXp);
            settings.setLevelMultiplier(defaultLevelMultiplier);
            settings.setLevelExponent(defaultLevelExponent);
            settings.setLevelFactor(defaultLevelFactor);
            settings.setCreditsPerLevel(defaultCreditsPerLevel);
            
            repository.save(settings);
        }
        
        // Apply current settings to the ChatActivityListener
        applyCurrentSettings();
    }
    
    @Transactional(readOnly = true)
    public DiscordBotSettingsDTO getCurrentSettings() {
        DiscordBotSettings settings = repository.findById(1L)
                .orElseThrow(() -> new RuntimeException("Discord bot settings not found"));
        
        DiscordBotSettingsDTO dto = new DiscordBotSettingsDTO();
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
        
        return dto;
    }
    
    @Transactional
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
        
        repository.save(settings);
        
        // Apply the updated settings to the ChatActivityListener
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
            settings.getCreditsPerLevel()
        );
        
        logger.info("Discord bot settings updated successfully");
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
                    settings.getCreditsPerLevel()
                );
                logger.info("Applied Discord bot settings from database");
            }
        } catch (Exception e) {
            logger.error("Error applying Discord bot settings: " + e.getMessage(), e);
        }
    }
} 