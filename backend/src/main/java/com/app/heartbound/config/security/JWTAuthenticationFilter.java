package com.app.heartbound.config.security;

import java.io.IOException;
import java.util.Collections;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class JWTAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JWTAuthenticationFilter.class);
    private final JWTTokenProvider jwtTokenProvider;

    public JWTAuthenticationFilter(JWTTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, 
                                    @NonNull HttpServletResponse response, 
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        // Skip JWT authentication for WebSocket handshake
        if (request.getRequestURI().contains("/ws/")) {
            logger.debug("Skipping JWT filter for WebSocket handshake endpoint: {}", request.getRequestURI());
            filterChain.doFilter(request, response);
            return;
        }

        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            logger.debug("JWT token extracted from Authorization header.");
            if (jwtTokenProvider.validateToken(token)) {
                String userId = jwtTokenProvider.getUserIdFromJWT(token);
                if (userId != null && !userId.isEmpty()) {
                    logger.debug("JWT token validated. Setting authentication for user id: {}", userId);
                    UsernamePasswordAuthenticationToken authentication = 
                        new UsernamePasswordAuthenticationToken(userId, null, Collections.emptyList());
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                } else {
                    logger.warn("JWT token validated but no user ID was found.");
                }
            } else {
                logger.warn("Invalid JWT token provided in header.");
            }
        } else {
            logger.debug("No Authorization header with Bearer token found.");
        }
        filterChain.doFilter(request, response);
    }
}
