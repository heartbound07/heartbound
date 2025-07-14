package com.app.heartbound.services.pairing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Scheduler for automatic matchmaking.
 * Runs every 30 seconds ONLY when the queue is enabled.
 * Prevents concurrent runs and logs all executions for audit.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MatchmakingSchedulerService {

    private final MatchmakingService matchmakingService;
    private final QueueService queueService;

    // Local lock to prevent concurrent matchmaking runs
    private final ReentrantLock matchmakingLock = new ReentrantLock();

    /**
     * Scheduled matchmaking task.
     * Runs every 30 seconds if the queue is enabled.
     */
    @Scheduled(fixedRate = 30000)
    public void scheduledMatchmaking() {
        if (!queueService.isQueueEnabled()) {
            log.debug("[MatchmakingScheduler] Queue is disabled. Skipping matchmaking run.");
            return;
        }
        if (!matchmakingLock.tryLock()) {
            log.warn("[MatchmakingScheduler] Previous matchmaking run still in progress. Skipping this cycle.");
            return;
        }
        try {
            log.info("[MatchmakingScheduler] Starting scheduled matchmaking run...");
            matchmakingService.performMatchmaking();
            log.info("[MatchmakingScheduler] Matchmaking run completed.");
        } catch (Exception e) {
            log.error("[MatchmakingScheduler] Error during scheduled matchmaking: {}", e.getMessage(), e);
        } finally {
            matchmakingLock.unlock();
        }
    }
} 