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

    @Value("${app.jwtSecret}")
    private String jwtSecret;

    @Value("${app.jwtExpirationInMs}")
    private long jwtExpirationInMs;

    @Value("${app.jwtRefreshExpirationInMs}")
    private long jwtRefreshExpirationInMs;

    public static final String TOKEN_TYPE_ACCESS = "ACCESS";
    public static final String TOKEN_TYPE_REFRESH = "REFRESH";

    private SecretKey key;
    private final Set<String> usedRefreshTokens = ConcurrentHashMap.newKeySet();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    @PostConstruct
    public void init() {
        this.key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        scheduler.scheduleAtFixedRate(this::cleanupUsedTokens, 1, 1, TimeUnit.HOURS);
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
                .claim("email", email)
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

        return Jwts.builder()
                .subject(userId)
                .claim("roles", roleNames)
                .claim("type", tokenType)
                .claim("username", username)
                .claim("email", email)
                .claim("avatar", avatar)
                .claim("credits", credits)
                .issuedAt(now)
                .expiration(expiryDate)
                .id(jti)
                .signWith(key, Jwts.SIG.HS512)
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
            logger.error("Expired JWT token: {}", ex.getMessage());
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
            Claims claims = getAllClaimsFromToken(token);
            return claims.getId();
        } catch (JwtException e) {
            logger.error("Could not get JTI from token: {}", e.getMessage());
            throw new InvalidTokenException("Invalid token, cannot extract JTI.", e);
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
                .verifyWith(key)
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
}
