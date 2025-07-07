package com.app.heartbound.services;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import com.app.heartbound.services.pairing.PairingService;
import com.app.heartbound.dto.pairing.PairingDTO;
import java.util.List;
import java.util.Optional;

/**
 * UserSecurityService
 * 
 * Centralized service for user-related security validations.
 * Provides common security checks to prevent code duplication and ensure consistent security enforcement.
 */
@Service
public class UserSecurityService {
    
    private static final Logger logger = LoggerFactory.getLogger(UserSecurityService.class);
    
    @Autowired
    private PairingService pairingService;
    
    /**
     * Checks if the authenticated user can access the specified user's data.
     * Users can access their own data, admins can access any user's data.
     * Users can also access data of users they are paired with or users in public active pairings.
     * 
     * @param authentication the authentication context
     * @param targetUserId the user ID being accessed
     * @return true if access is allowed, false otherwise
     */
    public boolean canAccessUserData(Authentication authentication, String targetUserId) {
        if (authentication == null || authentication.getName() == null) {
            logger.warn("Authentication is null or has no name");
            return false;
        }
        
        String authenticatedUserId = authentication.getName();
        
        // Users can always access their own data
        if (authenticatedUserId.equals(targetUserId)) {
            return true;
        }
        
        // Check if user has admin role
        if (hasAdminRole(authentication)) {
            return true;
        }
        
        // Allow access for pairing-related use cases
        return canAccessForPairing(authenticatedUserId, targetUserId);
    }
    
    /**
     * Checks if a user can access another user's data for pairing-related purposes.
     * This includes:
     * - Users currently paired together
     * - Users in active pairings (public information for "Current Matches")
     * 
     * @param authenticatedUserId the authenticated user ID
     * @param targetUserId the target user ID being accessed
     * @return true if access is allowed for pairing purposes, false otherwise
     */
    private boolean canAccessForPairing(String authenticatedUserId, String targetUserId) {
        try {
            // Check if users are currently paired together
            Optional<PairingDTO> currentPairingOpt = pairingService.getCurrentPairing(authenticatedUserId);
            if (currentPairingOpt.isPresent()) {
                PairingDTO currentPairing = currentPairingOpt.get();
                boolean isPaired = currentPairing.getUser1Id().equals(targetUserId) || 
                                 currentPairing.getUser2Id().equals(targetUserId);
                if (isPaired) {
                    logger.debug("Allowing profile access: users {} and {} are currently paired", 
                        authenticatedUserId, targetUserId);
                    return true;
                }
            }
            
            // Check if target user is in any active pairing (public information)
            List<PairingDTO> allActivePairings = pairingService.getAllActivePairings();
            boolean isInActivePairing = allActivePairings.stream()
                .anyMatch(pairing -> 
                    pairing.getUser1Id().equals(targetUserId) || 
                    pairing.getUser2Id().equals(targetUserId));
            
            if (isInActivePairing) {
                logger.debug("Allowing profile access: user {} is in an active pairing (public information)", 
                    targetUserId);
                return true;
            }
            
            return false;
        } catch (Exception e) {
            logger.warn("Error checking pairing access for users {} -> {}: {}", 
                authenticatedUserId, targetUserId, e.getMessage());
            return false;
        }
    }
    
    /**
     * Checks if the authenticated user has admin role.
     * 
     * @param authentication the authentication context
     * @return true if user has admin role, false otherwise
     */
    public boolean hasAdminRole(Authentication authentication) {
        if (authentication == null) {
            return false;
        }
        
        return authentication.getAuthorities().stream()
            .anyMatch(auth -> auth.getAuthority().equals("ROLE_ADMIN"));
    }
    
    /**
     * Checks if the authenticated user has moderator or admin role.
     * 
     * @param authentication the authentication context
     * @return true if user has moderator or admin role, false otherwise
     */
    public boolean hasModeratorOrAdminRole(Authentication authentication) {
        if (authentication == null) {
            return false;
        }
        
        return authentication.getAuthorities().stream()
            .anyMatch(auth -> auth.getAuthority().equals("ROLE_ADMIN") || 
                             auth.getAuthority().equals("ROLE_MODERATOR"));
    }
    
    /**
     * Validates that a Discord user ID is properly formatted.
     * Discord user IDs are numeric strings between 17-20 characters.
     * 
     * @param userId the user ID to validate
     * @return true if valid, false otherwise
     */
    public boolean isValidDiscordUserId(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            return false;
        }
        
        return userId.length() >= 17 && userId.length() <= 20 && userId.matches("\\d+");
    }
    
    /**
     * Sanitizes a user ID by ensuring it's a valid Discord ID format.
     * 
     * @param userId the user ID to sanitize
     * @return sanitized user ID or null if invalid
     */
    public String sanitizeUserId(String userId) {
        if (!isValidDiscordUserId(userId)) {
            logger.warn("Invalid user ID format detected: {}", userId);
            return null;
        }
        
        return userId.trim();
    }
    
    /**
     * Validates batch operation size to prevent DoS attacks.
     * 
     * @param batchSize the size of the batch operation
     * @param maxSize the maximum allowed size
     * @param operationName the name of the operation for logging
     * @param userId the user performing the operation
     * @return true if valid, false if exceeds limit
     */
    public boolean validateBatchSize(int batchSize, int maxSize, String operationName, String userId) {
        if (batchSize > maxSize) {
            logger.warn("User {} attempted {} operation exceeding batch limit: {} (max: {})", 
                userId, operationName, batchSize, maxSize);
            return false;
        }
        
        return true;
    }
} 