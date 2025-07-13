package com.app.heartbound.services;

import com.app.heartbound.repositories.PendingRoleSelectionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service for cleaning up old pending role selections.
 * Runs scheduled cleanup tasks to prevent database bloat.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PendingRoleSelectionCleanupService {
    
    private final PendingRoleSelectionRepository repository;
    
    /**
     * Clean up pending role selections older than 30 days.
     * Runs daily at 2 AM.
     */
    @Scheduled(cron = "0 0 2 * * ?") // Daily at 2 AM
    @Transactional
    public void cleanupOldPendingRoleSelections() {
        try {
            LocalDateTime cutoff = LocalDateTime.now().minusDays(30);
            List<String> oldSelectionIds = repository.findByUpdatedAtBefore(cutoff)
                    .stream()
                    .map(selection -> selection.getDiscordUserId())
                    .toList();
            
            if (!oldSelectionIds.isEmpty()) {
                repository.deleteAllById(oldSelectionIds);
                log.info("Cleaned up {} old pending role selections (older than 30 days)", oldSelectionIds.size());
            } else {
                log.debug("No old pending role selections found for cleanup");
            }
        } catch (Exception e) {
            log.error("Error during pending role selection cleanup: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Get statistics about pending role selections.
     * Useful for monitoring and debugging.
     */
    public PendingRoleSelectionStats getStats() {
        try {
            LocalDateTime cutoff30Days = LocalDateTime.now().minusDays(30);
            LocalDateTime cutoff7Days = LocalDateTime.now().minusDays(7);
            LocalDateTime cutoff1Day = LocalDateTime.now().minusDays(1);
            
            long totalCount = repository.count();
            long olderThan30Days = repository.countByUpdatedAtBefore(cutoff30Days);
            long olderThan7Days = repository.countByUpdatedAtBefore(cutoff7Days);
            long olderThan1Day = repository.countByUpdatedAtBefore(cutoff1Day);
            
            return PendingRoleSelectionStats.builder()
                    .totalCount(totalCount)
                    .olderThan30Days(olderThan30Days)
                    .olderThan7Days(olderThan7Days)
                    .olderThan1Day(olderThan1Day)
                    .build();
        } catch (Exception e) {
            log.error("Error getting pending role selection stats: {}", e.getMessage(), e);
            return PendingRoleSelectionStats.builder()
                    .totalCount(0L)
                    .olderThan30Days(0L)
                    .olderThan7Days(0L)
                    .olderThan1Day(0L)
                    .build();
        }
    }
    
    /**
     * Statistics data structure for pending role selections.
     */
    @lombok.Builder
    @lombok.Data
    public static class PendingRoleSelectionStats {
        private final long totalCount;
        private final long olderThan30Days;
        private final long olderThan7Days;
        private final long olderThan1Day;
    }
} 