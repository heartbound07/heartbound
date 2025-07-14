package com.app.heartbound.services;

import com.app.heartbound.entities.PendingPrison;
import com.app.heartbound.repositories.PendingPrisonRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.app.heartbound.config.CacheConfig;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service for cleaning up old pending prison records.
 * Runs a scheduled task to prevent database bloat from stale records.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PendingPrisonCleanupService {

    private final PendingPrisonRepository repository;
    private final CacheConfig cacheConfig;

    /**
     * Periodically cleans up old pending prison records.
     * This task runs daily and removes records for users who were released more than 30 days ago,
     * or whose pending record was last updated more than 30 days ago if they were never released.
     */
    @Scheduled(cron = "0 0 3 * * ?") // Daily at 3 AM
    @Transactional
    public void cleanupOldPendingPrisons() {
        try {
            LocalDateTime cutoff = LocalDateTime.now().minusDays(30);
            List<PendingPrison> oldSelections = repository.findByUpdatedAtBefore(cutoff);
            
            if (!oldSelections.isEmpty()) {
                List<String> idsToDelete = oldSelections.stream()
                        .map(PendingPrison::getDiscordUserId)
                        .toList();
                
                repository.deleteAllById(idsToDelete);

                for (String id : idsToDelete) {
                    cacheConfig.invalidatePendingPrisonCache(id);
                }

                log.info("Cleaned up {} old pending prison records (older than 30 days)", oldSelections.size());
            } else {
                log.debug("No old pending prison records found for cleanup.");
            }
        } catch (Exception e) {
            log.error("Error during pending prison cleanup: {}", e.getMessage(), e);
        }
    }
} 