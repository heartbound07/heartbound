package com.app.heartbound.config.security;

import java.io.IOException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.app.heartbound.exceptions.InvalidTokenException;

import java.util.List;
import java.util.stream.Collectors;

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
            try {
                // **PERFORMANCE OPTIMIZED**: Use cached authentication method
                JWTUserDetails userDetails = jwtTokenProvider.authenticateTokenOptimized(token);
                
                if (userDetails.getUserId() != null && !userDetails.getUserId().isEmpty()) {
                    logger.debug("JWT token validated. Setting authentication for user id: {}", userDetails.getUserId());
                    
                    List<GrantedAuthority> authorities = userDetails.getRoles().stream()
                            .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                            .collect(Collectors.toList());
                    
                    UsernamePasswordAuthenticationToken authentication = 
                        new UsernamePasswordAuthenticationToken(userDetails.getUserId(), null, authorities);
                    
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    logger.debug("User {} authenticated with roles: {}", userDetails.getUserId(), userDetails.getRoles());
                } else {
                    logger.warn("JWT token validated but no user ID was found in user details.");
                }
            } catch (InvalidTokenException e) {
                logger.warn("Invalid JWT token provided in header: {}", e.getMessage());
                // SecurityContextHolder is not populated, request proceeds without authentication
            }
        } else {
            logger.debug("No Authorization header with Bearer token found.");
        }
        filterChain.doFilter(request, response);
    }
}
