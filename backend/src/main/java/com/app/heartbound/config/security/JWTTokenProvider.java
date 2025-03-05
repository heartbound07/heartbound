package com.app.heartbound.config.security;

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

    /**
     * Generates a JWT token for the provided user details.
     *
     * @param userId   the user identifier (for example, Discord user id)
     * @param username the username to be included as a claim
     * @param email    the email to be included as a claim
     * @param avatar   the avatar URL to be included as a claim
     * @return the generated JWT token
     */
    public String generateToken(String userId, String username, String email, String avatar) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationInMs);
        
        logger.debug("Generating JWT token for user ID: {} with expiry: {}", userId, expiryDate);
        
        // Build token with subject first, then add additional claims individually
        return Jwts.builder()
                .setSubject(userId)
                .claim("username", username)
                .claim("email", email)
                .claim("avatar", avatar)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(SignatureAlgorithm.HS512, jwtSecret)
                .compact();
    }

    /**
     * Generates a refresh token for the provided user id.
     *
     * @param userId the user identifier
     * @return the generated refresh token
     */
    public String generateRefreshToken(String userId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + refreshTokenExpiryInMs);
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        return Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(SignatureAlgorithm.HS512, refreshTokenSecret)
                .compact();
    }

    /**
     * Extracts the user id from the JWT token.
     *
     * @param token the JWT token
     * @return the user id extracted from the token
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
     * @param token the JWT token to validate
     * @return true if the token is valid, false otherwise
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser().setSigningKey(jwtSecret).parseClaimsJws(token);
            logger.debug("JWT token validated successfully.");
            return true;
        } catch (SignatureException ex) {
            logger.error("Invalid JWT signature: {}", ex.getMessage());
        } catch (MalformedJwtException ex) {
            logger.error("Invalid JWT token: {}", ex.getMessage());
        } catch (ExpiredJwtException ex) {
            logger.error("Expired JWT token: {}", ex.getMessage());
        } catch (UnsupportedJwtException ex) {
            logger.error("Unsupported JWT token: {}", ex.getMessage());
        } catch (IllegalArgumentException ex) {
            logger.error("JWT token compact of handler are invalid: {}", ex.getMessage());
        }
        return false;
    }

    public long getTokenExpiryInMs() {
        return jwtExpirationInMs;
    }
}
