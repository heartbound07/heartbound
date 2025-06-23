package com.app.heartbound.config.security;

import com.app.heartbound.enums.Role;
import com.app.heartbound.exceptions.InvalidTokenException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Component
public class JWTChannelInterceptor implements ChannelInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(JWTChannelInterceptor.class);
    private static final long AUTHENTICATION_TIMEOUT_MS = 5000; // 5 second timeout

    private final JWTTokenProvider jwtTokenProvider;

    public JWTChannelInterceptor(JWTTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
        logger.debug("JWTChannelInterceptor initialized with caching optimization");
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        // Wrap the message to access STOMP headers
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);

        // Only validate the token during the CONNECT phase
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            long startTime = System.nanoTime();
            
            try {
                // **PERFORMANCE CRITICAL**: Fast-fail validation with timeout
                List<String> authHeaders = accessor.getNativeHeader("Authorization");
                if (authHeaders == null || authHeaders.isEmpty()) {
                    logger.error("Missing Authorization header in STOMP CONNECT message");
                    throw new IllegalArgumentException("Missing Authorization header");
                }
                
                // Extract the token, removing the "Bearer " prefix if present
                String token = authHeaders.get(0);
                if (token.startsWith("Bearer ")) {
                    token = token.substring(7);
                }
                
                // **PERFORMANCE OPTIMIZATION 1**: Use cached authentication with timeout
                JWTUserDetails userDetails = authenticateWithTimeout(token);
                
                // **PERFORMANCE OPTIMIZATION 2**: Pre-compute authorities to avoid repeated processing
                List<GrantedAuthority> authorities = userDetails.getRoles().stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                        .collect(Collectors.toList());
                    
                // Create authentication with authorities
                Principal user = new UsernamePasswordAuthenticationToken(userDetails.getUserId(), null, authorities);
                accessor.setUser(user);
                
                long duration = System.nanoTime() - startTime;
                if (duration > 100_000_000) { // Log if authentication takes longer than 100ms
                    logger.warn("WebSocket JWT authentication took {} ms for user: {} - investigate caching", 
                            duration / 1_000_000, userDetails.getUserId());
                } else {
                    logger.debug("WebSocket JWT authentication completed in {} microseconds for user: {}", 
                            duration / 1000, userDetails.getUserId());
                }
                
            } catch (Exception e) {
                long duration = System.nanoTime() - startTime;
                logger.error("WebSocket JWT authentication failed in {} ms: {}", 
                        duration / 1_000_000, e.getMessage());
                
                // **PERFORMANCE OPTIMIZATION 3**: Fail fast instead of letting connection hang
                throw new IllegalArgumentException("Authentication failed: " + e.getMessage(), e);
            }
        }
        
        return message;
    }
    
    /**
     * **PERFORMANCE CRITICAL**: Authenticate token with timeout to prevent hanging connections.
     * This method ensures WebSocket connections never wait more than the configured timeout.
     */
    private JWTUserDetails authenticateWithTimeout(String token) throws InvalidTokenException {
        try {
            // Use CompletableFuture to add timeout protection
            CompletableFuture<JWTUserDetails> authenticationFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    return jwtTokenProvider.authenticateTokenOptimized(token);
                } catch (InvalidTokenException e) {
                    logger.error("Invalid JWT token received during STOMP CONNECT: {}", e.getMessage());
                    throw new RuntimeException("Invalid JWT token: " + e.getMessage(), e);
                }
            });
            
            // Wait for authentication with timeout
            return authenticationFuture.get(AUTHENTICATION_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            
        } catch (java.util.concurrent.TimeoutException e) {
            logger.error("JWT authentication timeout after {}ms - possible caching issue", AUTHENTICATION_TIMEOUT_MS);
            throw new InvalidTokenException("Authentication timeout - service temporarily unavailable");
        } catch (Exception e) {
            if (e.getCause() instanceof InvalidTokenException) {
                throw (InvalidTokenException) e.getCause();
            }
            logger.error("Unexpected error during JWT authentication: {}", e.getMessage(), e);
            throw new InvalidTokenException("Authentication error: " + e.getMessage());
        }
    }
}
