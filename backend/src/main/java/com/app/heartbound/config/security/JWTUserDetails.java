package com.app.heartbound.config.security;

import com.app.heartbound.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

/**
 * Cached representation of user details extracted from JWT tokens.
 * This class stores parsed user information to avoid repeated token parsing operations.
 * Optimized for performance and memory usage in JWT validation caching.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JWTUserDetails {

    /**
     * The user's unique identifier (subject from JWT).
     */
    private String userId;

    /**
     * The user's username (if available in token).
     */
    private String username;

    /**
     * The user's email address (if available in token).
     */
    private String email;

    /**
     * The user's avatar URL (if available in token).
     */
    private String avatar;

    /**
     * The user's roles/authorities.
     */
    private Set<Role> roles;

    /**
     * The user's credit balance (if available in token).
     */
    private Integer credits;

    /**
     * Token type (ACCESS or REFRESH).
     */
    private String tokenType;

    /**
     * Token expiration timestamp (for cache invalidation).
     */
    private long expirationTime;

    /**
     * Token issued at timestamp.
     */
    private long issuedAt;

    /**
     * JWT ID (jti) for tracking purposes.
     */
    private String jti;

    /**
     * Checks if the cached user details are still valid based on token expiration.
     * @return true if the token hasn't expired, false otherwise
     */
    public boolean isValid() {
        return System.currentTimeMillis() < expirationTime;
    }

    /**
     * Gets the time remaining until token expiration in milliseconds.
     * @return milliseconds until expiration, or 0 if already expired
     */
    public long getTimeToExpiration() {
        long remaining = expirationTime - System.currentTimeMillis();
        return Math.max(0, remaining);
    }
} 