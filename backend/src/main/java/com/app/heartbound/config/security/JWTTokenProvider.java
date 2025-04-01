package com.app.heartbound.config.security;

import com.app.heartbound.enums.Role;
import com.app.heartbound.exceptions.InvalidTokenException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.SignatureException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Date;
import java.util.HashMap;
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

@Component
public class JWTTokenProvider {

    private static final Logger logger = LoggerFactory.getLogger(JWTTokenProvider.class);

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.access-token-expiration-ms}")
    private long jwtExpirationInMs;

    // Now using a dedicated refresh secret property
    @Value("${jwt.refresh-secret}")
    private String refreshTokenSecret;

    @Value("${jwt.refresh-token-expiration-ms}")
    private long refreshTokenExpiryInMs;

    private final Map<String, Long> usedRefreshTokens = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    @PostConstruct
    public void init() {
        // Schedule cleanup of expired blacklisted tokens every hour
        scheduler.scheduleAtFixedRate(this::cleanupUsedTokens, 1, 1, TimeUnit.HOURS);
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdown();
    }

    /**
     * Generates a JWT token for the provided user details including roles and credits.
     *
     * @param userId   the user identifier
     * @param username the username
     * @param email    the email
     * @param avatar   the avatar URL
     * @param roles    the user's roles
     * @param credits  the user's credits
     * @return the generated JWT token
     */
    public String generateToken(String userId, String username, String email, String avatar, Set<Role> roles, Integer credits) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationInMs);
        
        logger.debug("Generating JWT token for user ID: {} with expiry: {}", userId, expiryDate);
        
        // Convert roles to string list for JWT claims
        List<String> roleStrings = roles != null ? 
                roles.stream().map(Enum::name).collect(Collectors.toList()) : 
                Collections.singletonList(Role.USER.name());
        
        // Build token with subject and claims
        return Jwts.builder()
                .setSubject(userId)
                .claim("username", username)
                .claim("email", email)
                .claim("avatar", avatar)
                .claim("roles", roleStrings)
                .claim("credits", credits != null ? credits : 0)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .setId(UUID.randomUUID().toString())
                .signWith(SignatureAlgorithm.HS512, jwtSecret)
                .compact();
    }
    
    /**
     * Maintains backward compatibility with older code that doesn't pass credits
     */
    public String generateToken(String userId, String username, String email, String avatar, Set<Role> roles) {
        return generateToken(userId, username, email, avatar, roles, 0);
    }

    /**
     * Generates a refresh token for the provided user id.
     *
     * @param userId the user identifier
     * @param roles  the user's roles
     * @return the generated refresh token
     */
    public String generateRefreshToken(String userId, Set<Role> roles) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + refreshTokenExpiryInMs);
        
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        
        // Include roles in refresh token
        List<String> roleStrings = roles != null ? 
                roles.stream().map(Enum::name).collect(Collectors.toList()) : 
                Collections.singletonList(Role.USER.name());
        claims.put("roles", roleStrings);
        
        // Add a unique token ID and creation timestamp for better tracking
        claims.put("jti", UUID.randomUUID().toString());
        claims.put("created", now.getTime());
        
        return Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(SignatureAlgorithm.HS512, refreshTokenSecret)
                .compact();
    }
    
    /**
     * Overloaded method for backward compatibility.
     */
    public String generateRefreshToken(String userId) {
        return generateRefreshToken(userId, Collections.singleton(Role.USER));
    }

    /**
     * Extracts the user id from the JWT token.
     */
    public String getUserIdFromJWT(String token) {
        Claims claims = Jwts.parser()
                .setSigningKey(jwtSecret)
                .parseClaimsJws(token)
                .getBody();
        
        String userId = claims.getSubject();
        logger.debug("Extracted user id from JWT token: {}", userId);
        return userId;
    }
    
    /**
     * Extracts the roles from the JWT token.
     *
     * @param token the JWT token
     * @return the set of roles
     */
    @SuppressWarnings("unchecked")
    public Set<Role> getRolesFromJWT(String token) {
        Claims claims = Jwts.parser()
                .setSigningKey(jwtSecret)
                .parseClaimsJws(token)
                .getBody();
        
        List<String> roleStrings = claims.get("roles", List.class);
        
        if (roleStrings == null || roleStrings.isEmpty()) {
            return Collections.singleton(Role.USER);
        }
        
        return roleStrings.stream()
                .map(role -> Role.valueOf(role))
                .collect(Collectors.toSet());
    }

    /**
     * Extracts the userId (subject) from the refresh token.
     *
     * @param token the refresh token
     * @return the userId stored in the token
     */
    public String getUserIdFromRefreshToken(String token) {
        Claims claims = Jwts.parser()
                .setSigningKey(refreshTokenSecret)
                .parseClaimsJws(token)
                .getBody();
        logger.debug("Extracted user id from refresh token: {}", claims.get("userId"));
        return claims.get("userId", String.class);
    }

    /**
     * Validates the JWT token.
     *
     * @param token the JWT token
     * @return true if valid
     * @throws InvalidTokenException if token is invalid
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser().setSigningKey(jwtSecret).parseClaimsJws(token);
            logger.debug("JWT token validated successfully.");
            return true;
        } catch (SignatureException ex) {
            logger.error("Invalid JWT signature: {}", ex.getMessage());
            throw new InvalidTokenException("Invalid token signature");
        } catch (MalformedJwtException ex) {
            logger.error("Invalid JWT token: {}", ex.getMessage());
            throw new InvalidTokenException("Malformed token");
        } catch (ExpiredJwtException ex) {
            logger.error("Expired JWT token: {}", ex.getMessage());
            throw new InvalidTokenException("Token has expired");
        } catch (UnsupportedJwtException ex) {
            logger.error("Unsupported JWT token: {}", ex.getMessage());
            throw new InvalidTokenException("Unsupported token format");
        } catch (IllegalArgumentException ex) {
            logger.error("JWT token compact of handler are invalid: {}", ex.getMessage());
            throw new InvalidTokenException("Token is invalid");
        }
    }

    public long getTokenExpiryInMs() {
        return jwtExpirationInMs;
    }

    /**
     * Extracts the credits from the JWT token.
     *
     * @param token the JWT token
     * @return the credits value
     */
    public Integer getCreditsFromJWT(String token) {
        Claims claims = Jwts.parser()
                .setSigningKey(jwtSecret)
                .parseClaimsJws(token)
                .getBody();
        
        Integer credits = claims.get("credits", Integer.class);
        return credits != null ? credits : 0;
    }

    /**
     * Adds a refresh token to the used tokens list to prevent reuse
     * 
     * @param token the refresh token to invalidate
     */
    public void invalidateRefreshToken(String token) {
        String tokenId = getTokenIdFromRefreshToken(token);
        if (tokenId != null) {
            // Store with expiration time to allow cleanup
            usedRefreshTokens.put(tokenId, System.currentTimeMillis() + refreshTokenExpiryInMs);
            logger.debug("Refresh token invalidated: {}", tokenId);
        }
    }

    /**
     * Checks if a refresh token has been used before
     * 
     * @param token the refresh token to check
     * @return true if the token has been used, false otherwise
     */
    public boolean isRefreshTokenUsed(String token) {
        String tokenId = getTokenIdFromRefreshToken(token);
        return tokenId != null && usedRefreshTokens.containsKey(tokenId);
    }

    /**
     * Gets the token ID from a refresh token
     * 
     * @param token the refresh token
     * @return the token ID or null if not present/invalid
     */
    private String getTokenIdFromRefreshToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .setSigningKey(refreshTokenSecret)
                    .parseClaimsJws(token)
                    .getBody();
            return claims.getId(); // jti claim
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Cleans up expired tokens from the used tokens list
     */
    private void cleanupUsedTokens() {
        long now = System.currentTimeMillis();
        usedRefreshTokens.entrySet().removeIf(entry -> entry.getValue() < now);
        logger.debug("Cleaned up expired tokens. Remaining: {}", usedRefreshTokens.size());
    }
}
