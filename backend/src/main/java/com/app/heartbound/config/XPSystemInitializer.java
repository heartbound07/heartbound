package com.app.heartbound.config;

import com.app.heartbound.services.pairing.AchievementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * XPSystemInitializer
 * 
 * Initializes the XP system with default achievements when the application starts.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class XPSystemInitializer implements ApplicationRunner {

    private final AchievementService achievementService;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("Initializing XP system...");
        
        try {
            // Initialize default achievements
            achievementService.initializeDefaultAchievements();
            log.info("XP system initialization completed successfully");
        } catch (Exception e) {
            log.error("Failed to initialize XP system: {}", e.getMessage(), e);
            // Don't fail application startup, just log the error
        }
    }
} 