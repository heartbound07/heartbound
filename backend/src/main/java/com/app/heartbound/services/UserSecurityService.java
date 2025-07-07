package com.app.heartbound.services;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * UserSecurityService
 * 
 * Centralized service for user-related security validations.
 * Provides common security checks to prevent code duplication and ensure consistent security enforcement.
 */
@Service
public class UserSecurityService {
    
    private static final Logger logger = LoggerFactory.getLogger(UserSecurityService.class);
    
    /**
     * Checks if the authenticated user can access the specified user's data.
     * Users can access their own data, admins can access any user's data.
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
        return hasAdminRole(authentication);
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