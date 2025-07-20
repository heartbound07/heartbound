package com.app.heartbound.config.security;

import com.app.heartbound.entities.Shop;
import com.app.heartbound.repositories.shop.ShopRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class ShopExpirationScheduler {
    
    private final ShopRepository shopRepository;
    private static final Logger logger = LoggerFactory.getLogger(ShopExpirationScheduler.class);
    
    public ShopExpirationScheduler(ShopRepository shopRepository) {
        this.shopRepository = shopRepository;
    }
    
    @Scheduled(fixedRate = 3600000) // Run every hour (in milliseconds)
    @Transactional
    public void checkAndUpdateExpiredItems() {
        LocalDateTime now = LocalDateTime.now();
        List<Shop> expiredItems = shopRepository.findByIsActiveTrueAndExpiresAtBeforeAndExpiresAtIsNotNull(now);
        
        if (!expiredItems.isEmpty()) {
            int count = 0;
            for (Shop item : expiredItems) {
                item.setIsActive(false);
                shopRepository.save(item);
                count++;
                logger.info("Marked item as expired: {} (ID: {})", item.getName(), item.getId());
            }
            logger.info("Updated {} expired shop items", count);
        } else {
            logger.debug("No expired shop items found");
        }
    }
}
