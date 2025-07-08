package com.app.heartbound.config.security;

import com.app.heartbound.enums.Role;
import com.app.heartbound.exceptions.InvalidTokenException;
import com.app.heartbound.exceptions.JwtException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Date;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.Collections;
import java.util.stream.Collectors;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import java.util.Objects;

@Component
public class JWTTokenProvider {

    private static final Logger logger = LoggerFactory.getLogger(JWTTokenProvider.class);

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.refresh-secret}")
    private String jwtRefreshSecret;

    @Value("${jwt.access-token-expiration-ms}")
    private long jwtExpirationInMs;

    @Value("${jwt.refresh-token-expiration-ms}")
    private long jwtRefreshExpirationInMs;

    @Value("${jwt.cache.enabled:true}")
    private boolean cacheEnabled;

    public static final String TOKEN_TYPE_ACCESS = "ACCESS";
    public static final String TOKEN_TYPE_REFRESH = "REFRESH";

    private SecretKey key;
    private SecretKey refreshKey;
    private final Set<String> usedRefreshTokens = ConcurrentHashMap.newKeySet();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    
    // JWT Cache Configuration - Autowired after construction
    private JWTCacheConfig jwtCacheConfig;

    @PostConstruct
    public void init() {
        if (jwtSecret == null || jwtSecret.trim().isEmpty()) {
            logger.error("JWT access secret is not configured. Please set 'jwt.secret' in application properties.");
            throw new IllegalStateException("JWT access secret is not configured.");
        }
        this.key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        logger.info("JWT Access Secret Key initialized.");

        if (jwtRefreshSecret == null || jwtRefreshSecret.trim().isEmpty()) {
            logger.error("JWT refresh secret is not configured. Please set 'jwt.refresh-secret' in application properties.");
            throw new IllegalStateException("JWT refresh secret is not configured.");
        }
        if (jwtRefreshSecret.equals(jwtSecret)) {
            logger.error("JWT access secret and refresh secret must be different.");
            throw new IllegalStateException("JWT access and refresh secrets cannot be the same.");
        }
        this.refreshKey = Keys.hmacShaKeyFor(jwtRefreshSecret.getBytes(StandardCharsets.UTF_8));
        logger.info("JWT Refresh Secret Key initialized.");

        logger.debug("JWT Access Token Expiration (ms): {}", jwtExpirationInMs);
        logger.debug("JWT Refresh Token Expiration (ms): {}", jwtRefreshExpirationInMs);
        logger.info("JWT Caching enabled: {}", cacheEnabled);
        scheduler.scheduleAtFixedRate(this::cleanupUsedTokens, 1, 1, TimeUnit.HOURS);
    }

    @Autowired
    public void setJwtCacheConfig(JWTCacheConfig jwtCacheConfig) {
        this.jwtCacheConfig = jwtCacheConfig;
        logger.debug("JWT Cache Configuration injected successfully");
        
        // **PERFORMANCE CRITICAL**: Verify cache is working properly
        verifyCacheOperation();
    }
    
    /**
     * **PERFORMANCE CRITICAL**: Verify that caching is operational during startup.
     * This prevents runtime discovery of cache issues during WebSocket connections.
     */
    private void verifyCacheOperation() {
        if (cacheEnabled && jwtCacheConfig != null) {
            try {
                // Test cache operation with a dummy key
                String testKey = "cache_test_" + System.currentTimeMillis();
                jwtCacheConfig.getTokenValidationCache().put(testKey, true);
                Boolean result = jwtCacheConfig.getTokenValidationCache().getIfPresent(testKey);
                
                if (result != null && result) {
                    logger.info("JWT Cache verification successful - caching is operational");
                } else {
                    logger.error("JWT Cache verification failed - caching may not be working properly");
                    // Don't fail startup but log the issue
                }
                
                // Clean up test entry
                jwtCacheConfig.getTokenValidationCache().invalidate(testKey);
                
            } catch (Exception e) {
                logger.error("JWT Cache verification failed with exception: {}", e.getMessage());
                // Don't fail startup but ensure we know about cache issues
            }
        } else {
            logger.warn("JWT Caching is disabled or JWTCacheConfig not available - performance may be degraded");
        }
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdown();
    }

    public String generateToken(Authentication authentication) {
        String username = authentication.getName();
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationInMs);
        Set<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        return Jwts.builder()
                .subject(username)
                .claim("roles", roles)
                .claim("type", TOKEN_TYPE_ACCESS)
                .issuedAt(now)
                .expiration(expiryDate)
                .id(UUID.randomUUID().toString())
                .signWith(key, Jwts.SIG.HS512)
                .compact();
    }

    public String generateToken(String userId, String username, String email, String avatar, Set<Role> roles, Integer credits) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationInMs);
        String jti = UUID.randomUUID().toString();

        Set<String> roleNames = roles.stream().map(Role::name).collect(Collectors.toSet());

        return Jwts.builder()
                .subject(userId)
                .claim("username", username)
                .claim("avatar", avatar)
                .claim("roles", roleNames)
                .claim("credits", credits != null ? credits : 0)
                .claim("type", TOKEN_TYPE_ACCESS)
                .issuedAt(now)
                .expiration(expiryDate)
                .id(jti)
                .signWith(key, Jwts.SIG.HS512)
                .compact();
    }

    public String generateToken(String userId, Set<Role> roles) {
        return generateTokenWithDetails(userId, null, null, null, roles, null, TOKEN_TYPE_ACCESS, jwtExpirationInMs);
    }

    public String generateRefreshToken(String userId, Set<Role> roles) {
        return generateTokenWithDetails(userId, null, null, null, roles, null, TOKEN_TYPE_REFRESH, jwtRefreshExpirationInMs);
    }
    
    private String generateTokenWithDetails(String userId, String username, String email, String avatar, Set<Role> roles, Integer credits, String tokenType, long expirationMs) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationMs);
        String jti = UUID.randomUUID().toString();

        Set<String> roleNames = roles != null ? roles.stream().map(Role::name).collect(Collectors.toSet()) : Collections.emptySet();

        SecretKey signingKey;
        if (TOKEN_TYPE_REFRESH.equals(tokenType)) {
            signingKey = this.refreshKey;
        } else {
            signingKey = this.key;
        }

        return Jwts.builder()
                .subject(userId)
                .claim("roles", roleNames)
                .claim("type", tokenType)
                .claim("username", username)
                .claim("avatar", avatar)
                .claim("credits", credits)
                .issuedAt(now)
                .expiration(expiryDate)
                .id(jti)
                .signWith(signingKey, Jwts.SIG.HS512)
                .compact();
    }

    public String getUserIdFromJWT(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return claims.getSubject();
    }

    @SuppressWarnings("unchecked")
    public Set<Role> getRolesFromJWT(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        List<String> roleNames = claims.get("roles", List.class);
        if (roleNames == null) {
            logger.warn("No roles claim found in JWT for token: {}", token);
            return Collections.emptySet();
        }
        return roleNames.stream()
                .map(roleName -> {
                    try {
                        if (roleName.startsWith("ROLE_")) {
                            return Role.valueOf(roleName.substring(5).toUpperCase());
                        }
                        return Role.valueOf(roleName.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        logger.warn("Invalid role name '{}' in JWT token.", roleName);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }
    
    public String getTokenTypeFromJWT(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return claims.get("type", String.class);
        } catch (JwtException | IllegalArgumentException e) {
            logger.error("Could not get token type from JWT: {}", e.getMessage());
            return null;
        }
    }

    public boolean validateToken(String authToken) {
        try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(authToken);
            return true;
        } catch (SignatureException ex) {
            logger.error("Invalid JWT signature: {}", ex.getMessage());
            throw new InvalidTokenException("Invalid JWT signature", ex);
        } catch (MalformedJwtException ex) {
            logger.error("Invalid JWT token: {}", ex.getMessage());
            throw new InvalidTokenException("Invalid JWT token", ex);
        } catch (ExpiredJwtException ex) {
            logger.debug("Expired JWT token: {}", ex.getMessage());
            throw new InvalidTokenException("Expired JWT token", ex);
        } catch (UnsupportedJwtException ex) {
            logger.error("Unsupported JWT token: {}", ex.getMessage());
            throw new InvalidTokenException("Unsupported JWT token", ex);
        } catch (IllegalArgumentException ex) {
            logger.error("JWT claims string is empty: {}", ex.getMessage());
            throw new InvalidTokenException("JWT claims string is empty", ex);
        } catch (JwtException ex) {
            logger.error("JWT processing error: {}", ex.getMessage());
            throw new InvalidTokenException("Error processing JWT", ex);
        }
    }

    public Claims getAllClaimsFromToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isTokenExpired(String token) {
        try {
            final Date expiration = getExpirationDateFromToken(token);
            return expiration.before(new Date());
        } catch (ExpiredJwtException e) {
            return true;
        } catch (Exception e) {
            logger.warn("Could not determine token expiration due to parsing error: {}", e.getMessage());
            return true;
        }
    }

    public Date getExpirationDateFromToken(String token) {
        return getAllClaimsFromToken(token).getExpiration();
    }

    public String getJtiFromToken(String token) {
        try {
            Claims claims = Jwts.parser()
                            .verifyWith(this.refreshKey)
                            .build()
                            .parseSignedClaims(token)
                            .getPayload();
            return claims.getId();
        } catch (JwtException e) {
            logger.error("Could not get JTI from refresh token: {}", e.getMessage());
            throw new InvalidTokenException("Invalid refresh token, cannot extract JTI.", e);
        }
    }

    public void markRefreshTokenAsUsed(String jti) {
        if (jti != null) {
            usedRefreshTokens.add(jti);
            logger.debug("Refresh token JTI marked as used: {}", jti);
        }
    }

    public boolean isRefreshTokenUsed(String jti) {
        if (jti == null) return true;
        boolean isUsed = usedRefreshTokens.contains(jti);
        if (isUsed) {
            logger.warn("Attempt to reuse refresh token with JTI: {}", jti);
        }
        return isUsed;
    }
    
    public String generateCustomToken(String subject, Map<String, Object> customClaims, long expirationMs) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .subject(subject)
                .claims(customClaims)
                .issuedAt(now)
                .expiration(expiryDate)
                .id(UUID.randomUUID().toString())
                .signWith(key, Jwts.SIG.HS512)
                .compact();
    }

    public long getTokenExpiryInMs() {
        return jwtExpirationInMs;
    }

    public Integer getCreditsFromJWT(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        
        Integer credits = claims.get("credits", Integer.class);
        return credits != null ? credits : 0;
    }

    public String getUserIdFromRefreshToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(this.refreshKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        String tokenType = claims.get("type", String.class);
        if (!TOKEN_TYPE_REFRESH.equals(tokenType)) {
            logger.warn("Attempted to get UserID from a non-refresh token. Actual type: {}", tokenType);
            throw new InvalidTokenException("Expected refresh token but found type: " + tokenType);
        }
        return claims.getSubject();
    }

    public void invalidateRefreshToken(String token) {
        String jti = getJtiFromToken(token);
        if (jti != null) {
            markRefreshTokenAsUsed(jti);
            logger.info("Refresh token with JTI {} has been invalidated.", jti);
        } else {
            logger.warn("Could not invalidate refresh token: JTI was null or token was invalid.");
        }
    }

    private void cleanupUsedTokens() {
        long now = System.currentTimeMillis();
        usedRefreshTokens.removeIf(jti -> {
            boolean isExpired = jti.split(":").length > 1 && Long.parseLong(jti.split(":")[1]) < now;
            if (isExpired) {
                logger.debug("Cleaned up expired token: {}", jti);
            }
            return isExpired;
        });
        logger.debug("Cleaned up expired tokens. Remaining: {}", usedRefreshTokens.size());
    }

    /**
     * Parses and validates an access token, returning its claims.
     * Uses the access token secret key for verification.
     *
     * @param token The JWT access token string.
     * @return The Claims object from the token.
     * @throws InvalidTokenException if the token is invalid, expired, or malformed.
     */
    public Claims parseAndValidateAccessToken(String token) throws InvalidTokenException {
        try {
            return Jwts.parser()
                    .verifyWith(this.key) // Use access token key
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (SignatureException ex) {
            logger.error("Invalid JWT signature: {}", ex.getMessage());
            throw new InvalidTokenException("Invalid JWT signature", ex);
        } catch (MalformedJwtException ex) {
            logger.error("Invalid JWT token: {}", ex.getMessage());
            throw new InvalidTokenException("Invalid JWT token", ex);
        } catch (ExpiredJwtException ex) {
            logger.debug("Expired JWT token: {}", ex.getMessage());
            throw new InvalidTokenException("Expired JWT token", ex);
        } catch (UnsupportedJwtException ex) {
            logger.error("Unsupported JWT token: {}", ex.getMessage());
            throw new InvalidTokenException("Unsupported JWT token", ex);
        } catch (IllegalArgumentException ex) {
            logger.error("JWT claims string is empty or invalid: {}", ex.getMessage());
            throw new InvalidTokenException("JWT claims string is empty or invalid", ex);
        } catch (io.jsonwebtoken.JwtException ex) { // Catch-all for other JWT related issues
            logger.error("JWT processing error: {}", ex.getMessage());
            throw new InvalidTokenException("Error processing JWT", ex);
        }
    }

    /**
     * Extracts roles from a Claims object.
     *
     * @param claims The Claims object.
     * @return A Set of Roles.
     */
    @SuppressWarnings("unchecked")
    public Set<Role> getRolesFromClaims(Claims claims) {
        List<String> roleNames = claims.get("roles", List.class);
        if (roleNames == null || roleNames.isEmpty()) {
            logger.warn("No roles claim found in JWT claims.");
            return Collections.emptySet();
        }
        return roleNames.stream()
                .map(roleName -> {
                    try {
                        if (roleName.startsWith("ROLE_")) {
                            return Role.valueOf(roleName.substring(5).toUpperCase());
                        }
                        return Role.valueOf(roleName.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        logger.warn("Invalid role name '{}' in JWT claims.", roleName);
                        return null; // Skip invalid role
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    // ============================= PERFORMANCE OPTIMIZED METHODS =============================

    /**
     * **PERFORMANCE CRITICAL**: Optimized token validation with caching.
     * This method eliminates redundant token parsing by caching validation results.
     * Used primarily by WebSocket connections where performance is crucial.
     *
     * @param token The JWT token to validate
     * @return true if token is valid, false otherwise
     * @throws InvalidTokenException if token is invalid
     */
    public boolean validateTokenOptimized(String token) throws InvalidTokenException {
        if (token == null || token.trim().isEmpty()) {
            throw new InvalidTokenException("Token cannot be null or empty");
        }

        // Check cache first if caching is enabled
        if (cacheEnabled && jwtCacheConfig != null) {
            String cacheKey = jwtCacheConfig.generateTokenCacheKey(token);
            Boolean cachedResult = jwtCacheConfig.getTokenValidationCache().getIfPresent(cacheKey);
            
            if (cachedResult != null) {
                logger.debug("Token validation cache HIT - returning cached result: {}", cachedResult);
                if (!cachedResult) {
                    throw new InvalidTokenException("Token is invalid (cached result)");
                }
                return true;
            }
            logger.debug("Token validation cache MISS - performing validation");
        }

        // Perform actual validation
        boolean isValid;
        try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            isValid = true;
            logger.debug("Token validation successful");
        } catch (SignatureException ex) {
            logger.error("Invalid JWT signature: {}", ex.getMessage());
            isValid = false;
        } catch (MalformedJwtException ex) {
            logger.error("Invalid JWT token: {}", ex.getMessage());
            isValid = false;
        } catch (ExpiredJwtException ex) {
            logger.debug("Expired JWT token: {}", ex.getMessage());
            isValid = false;
        } catch (UnsupportedJwtException ex) {
            logger.error("Unsupported JWT token: {}", ex.getMessage());
            isValid = false;
        } catch (IllegalArgumentException ex) {
            logger.error("JWT claims string is empty: {}", ex.getMessage());
            isValid = false;
        } catch (io.jsonwebtoken.JwtException ex) {
            logger.error("JWT processing error: {}", ex.getMessage());
            isValid = false;
        }

        // Cache the result if caching is enabled
        if (cacheEnabled && jwtCacheConfig != null) {
            String cacheKey = jwtCacheConfig.generateTokenCacheKey(token);
            jwtCacheConfig.getTokenValidationCache().put(cacheKey, isValid);
            logger.debug("Token validation result cached: {}", isValid);
        }

        if (!isValid) {
            throw new InvalidTokenException("Token validation failed");
        }

        return true;
    }

    /**
     * **PERFORMANCE CRITICAL**: Get Claims from token with caching.
     * This method caches parsed Claims objects to avoid repeated parsing operations.
     *
     * @param token The JWT token
     * @return Claims object
     * @throws InvalidTokenException if token is invalid
     */
    public Claims getClaimsOptimized(String token) throws InvalidTokenException {
        if (token == null || token.trim().isEmpty()) {
            throw new InvalidTokenException("Token cannot be null or empty");
        }

        // Check cache first if caching is enabled
        if (cacheEnabled && jwtCacheConfig != null) {
            String cacheKey = jwtCacheConfig.generateTokenCacheKey(token);
            Claims cachedClaims = jwtCacheConfig.getClaimsCache().getIfPresent(cacheKey);
            
            if (cachedClaims != null) {
                // Verify cached claims are still valid (not expired)
                if (cachedClaims.getExpiration().after(new Date())) {
                    logger.debug("Claims cache HIT - returning cached claims");
                    return cachedClaims;
                } else {
                    // Remove expired entry from cache
                    jwtCacheConfig.getClaimsCache().invalidate(cacheKey);
                    logger.debug("Expired claims removed from cache");
                }
            }
            logger.debug("Claims cache MISS - parsing token");
        }

        // Parse claims from token
        Claims claims;
        try {
            claims = parseAndValidateAccessToken(token);
            logger.debug("Token claims parsed successfully");
        } catch (InvalidTokenException e) {
            logger.error("Failed to parse token claims: {}", e.getMessage());
            throw e;
        }

        // Cache the result if caching is enabled
        if (cacheEnabled && jwtCacheConfig != null && claims != null) {
            String cacheKey = jwtCacheConfig.generateTokenCacheKey(token);
            jwtCacheConfig.getClaimsCache().put(cacheKey, claims);
            logger.debug("Claims cached successfully");
        }

        return claims;
    }

    /**
     * **PERFORMANCE CRITICAL**: Get user details from token with comprehensive caching.
     * This method extracts and caches all user-related information from a JWT token
     * to eliminate the need for multiple parsing operations.
     *
     * @param token The JWT token
     * @return JWTUserDetails containing all user information
     * @throws InvalidTokenException if token is invalid
     */
    public JWTUserDetails getUserDetailsOptimized(String token) throws InvalidTokenException {
        if (token == null || token.trim().isEmpty()) {
            throw new InvalidTokenException("Token cannot be null or empty");
        }

        // Check cache first if caching is enabled
        if (cacheEnabled && jwtCacheConfig != null) {
            String cacheKey = jwtCacheConfig.generateTokenCacheKey(token);
            JWTUserDetails cachedDetails = jwtCacheConfig.getUserDetailsCache().getIfPresent(cacheKey);
            
            if (cachedDetails != null && cachedDetails.isValid()) {
                logger.debug("User details cache HIT - returning cached details for user: {}", cachedDetails.getUserId());
                return cachedDetails;
            } else if (cachedDetails != null) {
                // Remove expired entry from cache
                jwtCacheConfig.getUserDetailsCache().invalidate(cacheKey);
                logger.debug("Expired user details removed from cache");
            }
            logger.debug("User details cache MISS - extracting details");
        }

        // Extract user details from token
        Claims claims = getClaimsOptimized(token);
        
        JWTUserDetails userDetails = JWTUserDetails.builder()
                .userId(claims.getSubject())
                .username(claims.get("username", String.class))
                .email(null) // Email no longer available from Discord OAuth
                .avatar(claims.get("avatar", String.class))
                .roles(getRolesFromClaims(claims))
                .credits(claims.get("credits", Integer.class))
                .tokenType(claims.get("type", String.class))
                .expirationTime(claims.getExpiration().getTime())
                .issuedAt(claims.getIssuedAt() != null ? claims.getIssuedAt().getTime() : System.currentTimeMillis())
                .jti(claims.getId())
                .build();

        // Cache the result if caching is enabled
        if (cacheEnabled && jwtCacheConfig != null) {
            String cacheKey = jwtCacheConfig.generateTokenCacheKey(token);
            jwtCacheConfig.getUserDetailsCache().put(cacheKey, userDetails);
            logger.debug("User details cached successfully for user: {}", userDetails.getUserId());
        }

        return userDetails;
    }

    /**
     * **PERFORMANCE CRITICAL**: Optimized method for WebSocket authentication.
     * Combines validation and user details extraction in a single operation.
     * This is the primary method used by JWTChannelInterceptor for WebSocket connections.
     *
     * @param token The JWT token
     * @return JWTUserDetails if token is valid
     * @throws InvalidTokenException if token is invalid
     */
    public JWTUserDetails authenticateTokenOptimized(String token) throws InvalidTokenException {
        long startTime = System.nanoTime();
        
        if (token == null || token.trim().isEmpty()) {
            throw new InvalidTokenException("Token cannot be null or empty");
        }
        
        try {
            // **CACHE VERIFICATION**: Check if caching is working as expected
            boolean cacheWorking = (cacheEnabled && jwtCacheConfig != null);
            if (!cacheWorking && logger.isWarnEnabled()) {
                logger.warn("JWT caching not available - authentication will be slower");
            }
            
            // This method combines validation and details extraction efficiently
            JWTUserDetails userDetails = getUserDetailsOptimized(token);
            
            long duration = System.nanoTime() - startTime;
            
            // **PERFORMANCE MONITORING**: Log slow authentication operations
            if (duration > 50_000_000) { // 50ms threshold
                logger.warn("Slow JWT authentication detected: {} ms for user: {} (caching: {})", 
                        duration / 1_000_000, userDetails.getUserId(), cacheWorking);
            } else {
                logger.debug("Token authentication completed in {} microseconds for user: {} (cached: {})", 
                        duration / 1000, userDetails.getUserId(), cacheWorking);
            }
            
            return userDetails;
        } catch (InvalidTokenException e) {
            long duration = System.nanoTime() - startTime;
            logger.warn("Token authentication failed in {} microseconds: {}", duration / 1000, e.getMessage());
            throw e;
        }
    }

    /**
     * Invalidates all cached data for a specific token.
     * Use this when a token is revoked, blacklisted, or when user permissions change.
     *
     * @param token The token to invalidate
     */
    public void invalidateTokenCache(String token) {
        if (cacheEnabled && jwtCacheConfig != null && token != null) {
            jwtCacheConfig.invalidateToken(token);
            logger.info("Token cache invalidated for security reasons");
        }
    }

    /**
     * Gets cache statistics for monitoring and performance analysis.
     *
     * @return Cache statistics or null if caching is disabled
     */
    public JWTCacheConfig.CacheStats getCacheStatistics() {
        if (cacheEnabled && jwtCacheConfig != null) {
            return jwtCacheConfig.getCacheStats();
        }
        return null;
    }
}
