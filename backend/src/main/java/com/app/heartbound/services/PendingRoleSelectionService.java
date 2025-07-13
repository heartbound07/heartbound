package com.app.heartbound.services;

import com.app.heartbound.entities.PendingRoleSelection;
import com.app.heartbound.repositories.PendingRoleSelectionRepository;
import com.app.heartbound.config.CacheConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Service for managing pending role selections for unregistered Discord users.
 * Provides caching support for improved performance.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PendingRoleSelectionService {
    
    private final PendingRoleSelectionRepository repository;
    private final CacheConfig cacheConfig;
    
    /**
     * Find pending role selection by Discord user ID with caching.
     */
    @Cacheable(value = "pendingRoleSelectionCache", key = "#discordUserId")
    public Optional<PendingRoleSelection> findByDiscordUserId(String discordUserId) {
        log.debug("Fetching pending role selection for Discord user: {}", discordUserId);
        return repository.findByDiscordUserId(discordUserId);
    }
    
    /**
     * Save or update pending role selection with caching.
     */
    @CachePut(value = "pendingRoleSelectionCache", key = "#pendingRoleSelection.discordUserId")
    @Transactional
    public PendingRoleSelection save(PendingRoleSelection pendingRoleSelection) {
        log.debug("Saving pending role selection for Discord user: {}", pendingRoleSelection.getDiscordUserId());
        PendingRoleSelection saved = repository.save(pendingRoleSelection);
        log.info("Saved pending role selection for Discord user: {}", saved.getDiscordUserId());
        return saved;
    }
    
    /**
     * Create or update pending role selection for a specific category.
     */
    @Transactional
    public PendingRoleSelection updateRoleSelection(String discordUserId, String category, String roleId) {
        log.debug("Updating role selection for Discord user: {} category: {} roleId: {}", 
                discordUserId, category, roleId);
        
        PendingRoleSelection pendingSelection = repository.findByDiscordUserId(discordUserId)
                .orElse(PendingRoleSelection.builder()
                        .discordUserId(discordUserId)
                        .build());
        
        pendingSelection.setRoleIdForCategory(category, roleId);
        PendingRoleSelection saved = repository.save(pendingSelection);
        
        // Update cache
        cacheConfig.getPendingRoleSelectionCache().put(discordUserId, saved);
        
        log.info("Updated role selection for Discord user: {} category: {} roleId: {}", 
                discordUserId, category, roleId);
        return saved;
    }
    
    /**
     * Check if user has already selected a role in a specific category.
     */
    public boolean hasRoleInCategory(String discordUserId, String category) {
        Optional<PendingRoleSelection> pendingSelection = findByDiscordUserId(discordUserId);
        return pendingSelection.map(selection -> selection.hasRoleInCategory(category)).orElse(false);
    }
    
    /**
     * Get the selected role ID for a specific category.
     */
    public String getRoleIdForCategory(String discordUserId, String category) {
        Optional<PendingRoleSelection> pendingSelection = findByDiscordUserId(discordUserId);
        return pendingSelection.map(selection -> selection.getRoleIdForCategory(category)).orElse(null);
    }
    
    /**
     * Delete pending role selection and clear cache.
     */
    @CacheEvict(value = "pendingRoleSelectionCache", key = "#discordUserId")
    @Transactional
    public void deleteByDiscordUserId(String discordUserId) {
        log.debug("Deleting pending role selection for Discord user: {}", discordUserId);
        repository.deleteById(discordUserId);
        log.info("Deleted pending role selection for Discord user: {}", discordUserId);
    }
    
    /**
     * Check if user has selected all required roles.
     */
    public boolean hasAllRequiredRoles(String discordUserId) {
        Optional<PendingRoleSelection> pendingSelection = findByDiscordUserId(discordUserId);
        return pendingSelection.map(PendingRoleSelection::hasAllRequiredRoles).orElse(false);
    }
} 