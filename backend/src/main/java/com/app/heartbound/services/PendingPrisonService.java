package com.app.heartbound.services;

import com.app.heartbound.config.CacheConfig;
import com.app.heartbound.entities.PendingPrison;
import com.app.heartbound.repositories.PendingPrisonRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Service for managing pending prison records for unregistered Discord users.
 * Provides caching support for improved performance.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PendingPrisonService {

    private final PendingPrisonRepository repository;
    private final CacheConfig cacheConfig;

    /**
     * Find a pending prison record by Discord user ID.
     *
     * @param discordUserId The user's Discord ID.
     * @return An Optional containing the PendingPrison record if found.
     */
    public Optional<PendingPrison> findByDiscordUserId(String discordUserId) {
        log.debug("Fetching pending prison for Discord user: {}", discordUserId);
        // Attempt to get from cache first
        PendingPrison cached = (PendingPrison) cacheConfig.getPendingPrisonCache().getIfPresent(discordUserId);
        if (cached != null) {
            log.debug("Found pending prison for user {} in cache.", discordUserId);
            return Optional.of(cached);
        }
        
        log.debug("No pending prison in cache for user {}. Fetching from repository.", discordUserId);
        Optional<PendingPrison> fromDb = repository.findById(discordUserId);
        fromDb.ifPresent(p -> cacheConfig.getPendingPrisonCache().put(discordUserId, p));
        return fromDb;
    }

    /**
     * Creates or updates a pending prison record for a user.
     *
     * @param userId      The user's Discord ID.
     * @param roleIds     The original roles of the user.
     * @param releaseAt   The scheduled release time (can be null).
     * @return The saved PendingPrison entity.
     */
    @Transactional
    public PendingPrison prisonUser(String userId, List<String> roleIds, LocalDateTime releaseAt) {
        log.debug("Creating or updating pending prison for user {}", userId);

        PendingPrison pendingPrison = repository.findById(userId)
                .orElse(PendingPrison.builder().discordUserId(userId).build());
        
        pendingPrison.setOriginalRoleIds(roleIds);
        pendingPrison.setPrisonedAt(LocalDateTime.now());
        pendingPrison.setPrisonReleaseAt(releaseAt);

        PendingPrison saved = repository.save(pendingPrison);
        cacheConfig.getPendingPrisonCache().put(userId, saved);
        log.info("User {} has been imprisoned in pending prison.", userId);
        return saved;
    }

    /**
     * Deletes a pending prison record for a user upon release.
     *
     * @param userId The user's Discord ID.
     */
    @Transactional
    public void releaseUser(String userId) {
        log.debug("Releasing user {} from pending prison", userId);
        repository.deleteById(userId);
        cacheConfig.invalidatePendingPrisonCache(userId);
        log.info("User {} has been released from pending prison. Record deleted.", userId);
    }

    /**
     * Deletes a pending prison record by its ID and evicts it from the cache.
     * Used during data migration to a permanent User record.
     *
     * @param discordUserId The user's Discord ID.
     */
    @Transactional
    public void deleteByDiscordUserId(String discordUserId) {
        log.debug("Deleting pending prison record for Discord user: {}", discordUserId);
        repository.deleteById(discordUserId);
        cacheConfig.invalidatePendingPrisonCache(discordUserId);
        log.info("Deleted pending prison record for Discord user: {}", discordUserId);
    }
} 