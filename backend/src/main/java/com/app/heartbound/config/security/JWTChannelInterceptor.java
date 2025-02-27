package com.app.heartbound.config.security;

import com.app.heartbound.config.security.JWTTokenProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.Collections;
import java.util.List;

@Component
public class JWTChannelInterceptor implements ChannelInterceptor {

    private final JWTTokenProvider jwtTokenProvider;

    @Autowired
    public JWTChannelInterceptor(JWTTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        // Wrap the message to access STOMP headers
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);

        // Only validate the token during the CONNECT phase
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            List<String> authHeaders = accessor.getNativeHeader("Authorization");
            if (authHeaders == null || authHeaders.isEmpty()) {
                throw new IllegalArgumentException("Missing Authorization header");
            }
            
            // Expect the Authorization header to contain a Bearer token
            String token = authHeaders.get(0);
            if (token.startsWith("Bearer ")) {
                token = token.substring(7);
            }
            
            // Validate the token using the JWTTokenProvider
            if (!jwtTokenProvider.validateToken(token)) {
                throw new IllegalArgumentException("Invalid JWT token");
            }
            
            // Retrieve the user identifier and set it as the Principal
            String userId = jwtTokenProvider.getUserIdFromJWT(token);
            Principal user = new UsernamePasswordAuthenticationToken(userId, null, Collections.emptyList());
            accessor.setUser(user);
        }
        
        return message;
    }
}
