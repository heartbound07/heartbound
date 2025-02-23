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

    /**
     * Generates a JWT token for the provided user details.
     *
     * @param userId   the user identifier (for example, Discord user id)
     * @param username the username to be included as a claim
     * @param email    the email to be included as a claim
     * @return the generated JWT token
     */
    public String generateToken(String userId, String username, String email) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationInMs);

        // Create claims map and add additional user details
        Map<String, Object> claims = new HashMap<>();
        claims.put("username", username);
        claims.put("email", email);
        // Future claims can be added here (e.g., roles, permissions, etc.)

        logger.info("Generating JWT token for user id: {} with username: {}", userId, username);

        String token = Jwts.builder()
                .setClaims(claims)
                .setSubject(userId)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(SignatureAlgorithm.HS512, jwtSecret)
                .compact();

        logger.info("JWT token generated successfully for user id: {}", userId);
        return token;
    }

    /**
     * Extracts the userId (subject) from the JWT token.
     *
     * @param token the JWT token
     * @return the userId stored in the token
     */
    public String getUserIdFromJWT(String token) {
        Claims claims = Jwts.parser()
                .setSigningKey(jwtSecret)
                .parseClaimsJws(token)
                .getBody();
        logger.debug("Extracted user id from JWT token: {}", claims.getSubject());
        return claims.getSubject();
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
}
