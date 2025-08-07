package com.app.heartbound.services.shop;

import com.app.heartbound.repositories.shop.UserDailyShopItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Service for cleaning up old user daily shop item selection records.
 * Runs a scheduled task to prevent database bloat from stale daily selections.
 * 
 * Daily shop selections are only relevant for 24 hours, so records older than
 * a few days can be safely removed to maintain database performance.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserDailyShopItemCleanupService {

    private final UserDailyShopItemRepository userDailyShopItemRepository;

    /**
     * Periodically cleans up old daily shop item selection records.
     * This task runs daily at 2:30 AM and removes records older than 7 days.
     * 
     * We keep records for 7 days to allow for:
     * - Debugging and analytics purposes
     * - Potential future features that might reference recent selections
     * - Grace period in case of any date/timezone related issues
     */
    @Scheduled(cron = "0 30 2 * * ?") // Daily at 2:30 AM
    @Transactional
    public void cleanupOldDailySelections() {
        try {
            LocalDate cutoffDate = LocalDate.now().minusDays(7);
            
            log.debug("Starting cleanup of daily shop selections older than: {}", cutoffDate);
            
            // Get count before deletion for logging
            long totalBefore = userDailyShopItemRepository.countTotalSelections();
            
            // Perform bulk deletion
            int deletedCount = userDailyShopItemRepository.deleteBySelectionDateBefore(cutoffDate);
            
            if (deletedCount > 0) {
                long totalAfter = userDailyShopItemRepository.countTotalSelections();
                log.info("Cleaned up {} old daily shop selection records (older than {} days). " +
                        "Total records before: {}, after: {}", 
                        deletedCount, 7, totalBefore, totalAfter);
            } else {
                log.debug("No old daily shop selection records found for cleanup (cutoff: {})", cutoffDate);
            }
            
        } catch (Exception e) {
            log.error("Error during daily shop selections cleanup: {}", e.getMessage(), e);
        }
    }

    /**
     * Manual cleanup method that can be called programmatically if needed.
     * Useful for testing or administrative operations.
     * 
     * @param daysToKeep Number of days worth of records to keep
     * @return Number of records deleted
     */
    @Transactional
    public int manualCleanup(int daysToKeep) {
        if (daysToKeep < 1) {
            throw new IllegalArgumentException("Days to keep must be at least 1");
        }
        
        try {
            LocalDate cutoffDate = LocalDate.now().minusDays(daysToKeep);
            int deletedCount = userDailyShopItemRepository.deleteBySelectionDateBefore(cutoffDate);
            
            log.info("Manual cleanup completed: deleted {} daily shop selection records older than {} days", 
                    deletedCount, daysToKeep);
            
            return deletedCount;
        } catch (Exception e) {
            log.error("Error during manual daily shop selections cleanup: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Get statistics about current daily shop selection records.
     * Useful for monitoring and maintenance purposes.
     * 
     * @return Statistics about the daily shop selections
     */
    public DailyShopCleanupStats getCleanupStats() {
        try {
            long totalRecords = userDailyShopItemRepository.countTotalSelections();
            long todayUsers = userDailyShopItemRepository.countUniqueUsersOnDate(LocalDate.now());
            long yesterdayUsers = userDailyShopItemRepository.countUniqueUsersOnDate(LocalDate.now().minusDays(1));
            
            return DailyShopCleanupStats.builder()
                    .totalRecords(totalRecords)
                    .usersWithSelectionsToday(todayUsers)
                    .usersWithSelectionsYesterday(yesterdayUsers)
                    .build();
        } catch (Exception e) {
            log.error("Error getting cleanup stats: {}", e.getMessage(), e);
            return DailyShopCleanupStats.builder()
                    .totalRecords(0)
                    .usersWithSelectionsToday(0)
                    .usersWithSelectionsYesterday(0)
                    .build();
        }
    }

    /**
     * Statistics data structure for monitoring daily shop cleanup operations.
     */
    @lombok.Builder
    @lombok.Data
    public static class DailyShopCleanupStats {
        private final long totalRecords;
        private final long usersWithSelectionsToday;
        private final long usersWithSelectionsYesterday;
    }
} 