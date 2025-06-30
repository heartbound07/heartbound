package com.app.heartbound.services;

import com.app.heartbound.entities.Giveaway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * GiveawaySchedulerService
 * 
 * Scheduled service to automatically complete expired giveaways.
 * Runs every 5 minutes to check for and process expired giveaways.
 */
@Service
public class GiveawaySchedulerService {

    private static final Logger logger = LoggerFactory.getLogger(GiveawaySchedulerService.class);
    
    private final GiveawayService giveawayService;

    @Autowired
    public GiveawaySchedulerService(GiveawayService giveawayService) {
        this.giveawayService = giveawayService;
    }

    /**
     * Scheduled task to process expired giveaways.
     * Runs every 5 minutes.
     */
    @Scheduled(fixedRate = 300000) // 5 minutes in milliseconds
    public void processExpiredGiveaways() {
        try {
            logger.debug("Checking for expired giveaways...");
            
            List<Giveaway> expiredGiveaways = giveawayService.processExpiredGiveaways();
            
            if (!expiredGiveaways.isEmpty()) {
                logger.info("Processed {} expired giveaways", expiredGiveaways.size());
                
                // Log completed giveaways for monitoring
                for (Giveaway giveaway : expiredGiveaways) {
                    logger.info("Completed expired giveaway: {} - Prize: {}", 
                               giveaway.getId(), giveaway.getPrize());
                }
            } else {
                logger.debug("No expired giveaways found");
            }
            
        } catch (Exception e) {
            logger.error("Error processing expired giveaways", e);
        }
    }

    /**
     * Manual trigger for processing expired giveaways.
     * Can be called from admin endpoints if needed.
     */
    public int manualProcessExpiredGiveaways() {
        logger.info("Manual processing of expired giveaways triggered");
        
        try {
            List<Giveaway> expiredGiveaways = giveawayService.processExpiredGiveaways();
            logger.info("Manually processed {} expired giveaways", expiredGiveaways.size());
            return expiredGiveaways.size();
        } catch (Exception e) {
            logger.error("Error in manual processing of expired giveaways", e);
            throw e;
        }
    }
} 