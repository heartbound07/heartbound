package com.app.heartbound.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Utility class for security-related operations
 */
public class SecurityUtils {
    
    private static final Logger logger = LoggerFactory.getLogger(SecurityUtils.class);
    
    /**
     * Gets the current user ID from the security context
     * 
     * @return The user ID or null if not authenticated
     */
    public static String getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return getCurrentUserId(authentication);
    }
    
    /**
     * Gets the user ID from the provided authentication object
     * 
     * @param authentication The authentication object
     * @return The user ID or null if authentication is invalid
     */
    public static String getCurrentUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || 
                authentication.getPrincipal() == null) {
            logger.debug("No valid authentication found in context");
            return null;
        }
        
        // Based on JWTAuthenticationFilter, the principal is set to the user ID
        String userId = authentication.getPrincipal().toString();
        logger.debug("Current authenticated user ID: {}", userId);
        return userId;
    }
    
    /**
     * Checks if the current user has the specified role
     * 
     * @param role The role to check (without the "ROLE_" prefix)
     * @return true if the user has the role, false otherwise
     */
    public static boolean hasRole(String role) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_" + role));
    }
} 