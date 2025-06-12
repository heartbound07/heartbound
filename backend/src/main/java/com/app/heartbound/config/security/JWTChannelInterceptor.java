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

@Component
public class JWTChannelInterceptor implements ChannelInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(JWTChannelInterceptor.class);

    private final JWTTokenProvider jwtTokenProvider;

    public JWTChannelInterceptor(JWTTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
        logger.debug("JWTChannelInterceptor initialized");
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        // Wrap the message to access STOMP headers
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);

        // Only validate the token during the CONNECT phase
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            long startTime = System.nanoTime();
            
            try {
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
                
                // **PERFORMANCE CRITICAL**: Use optimized authentication method
                // This combines validation and user details extraction in a single cached operation
                JWTUserDetails userDetails;
                try {
                    userDetails = jwtTokenProvider.authenticateTokenOptimized(token);
                } catch (InvalidTokenException e) {
                    logger.error("Invalid JWT token received during STOMP CONNECT: {}", e.getMessage());
                    throw new IllegalArgumentException("Invalid JWT token: " + e.getMessage(), e);
                }
                
                // Convert roles to Spring Security GrantedAuthorities
                List<GrantedAuthority> authorities = userDetails.getRoles().stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                        .collect(Collectors.toList());
                    
                // Create authentication with authorities
                Principal user = new UsernamePasswordAuthenticationToken(userDetails.getUserId(), null, authorities);
                accessor.setUser(user);
                
                long duration = System.nanoTime() - startTime;
                logger.debug("WebSocket JWT authentication completed in {} microseconds for user: {}", 
                        duration / 1000, userDetails.getUserId());
                
            } catch (Exception e) {
                long duration = System.nanoTime() - startTime;
                logger.error("WebSocket JWT authentication failed in {} microseconds: {}", 
                        duration / 1000, e.getMessage());
                throw e;
            }
        }
        
        return message;
    }
}
