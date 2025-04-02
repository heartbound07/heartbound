package com.app.heartbound.config.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(RateLimitingFilter.class);

    // Configurable properties from application.properties
    @Value("${rate.limit.max-requests:100}")
    private int maxRequests;

    @Value("${rate.limit.window-minutes:1}")
    private int windowMinutes;

    // Map to track requests by IP address
    private final Map<String, ConcurrentLinkedQueue<Instant>> requestTracker = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        // Skip rate limiting for non-auth endpoints
        String requestURI = request.getRequestURI();
        if (shouldSkipRateLimiting(requestURI)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Get client IP address
        String clientIp = getClientIpAddress(request);
        
        // Check if client has exceeded rate limit
        if (isRateLimited(clientIp)) {
            logger.warn("Rate limit exceeded for IP: {} on URI: {}", clientIp, requestURI);
            sendRateLimitResponse(response);
            return;
        }

        // Proceed with the request if within rate limits
        logger.debug("Request from IP: {} to URI: {} within rate limits", clientIp, requestURI);
        filterChain.doFilter(request, response);
    }

    /**
     * Determines if the given URI should bypass rate limiting
     */
    private boolean shouldSkipRateLimiting(String uri) {
        // Skip static resources and websocket endpoints
        return uri.contains("/ws/") || 
               uri.contains("/swagger-ui/") || 
               uri.contains("/v3/api-docs/") ||
               uri.contains("/error");
    }

    /**
     * Checks if the client has exceeded the rate limit
     */
    private boolean isRateLimited(String clientIp) {
        Instant now = Instant.now();
        Instant cutoff = now.minus(Duration.ofMinutes(windowMinutes));

        // Get or create the request queue for this IP
        ConcurrentLinkedQueue<Instant> requestTimestamps = requestTracker.computeIfAbsent(
                clientIp, k -> new ConcurrentLinkedQueue<>());

        // Clean up old entries first
        requestTimestamps.removeIf(timestamp -> timestamp.isBefore(cutoff));
        
        // Check if under the limit
        if (requestTimestamps.size() < maxRequests) {
            // Add current request timestamp
            requestTimestamps.add(now);
            return false; // Not rate limited
        }
        
        return true; // Rate limited
    }

    /**
     * Extracts client IP address from the request, handling proxies
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // Get the first IP in case of multiple proxies
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * Sends a 429 Too Many Requests response
     */
    private void sendRateLimitResponse(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Retry-After", String.valueOf(windowMinutes * 60)); // Retry after window time in seconds
        
        String errorJson = "{\"error\":\"Too Many Requests\",\"message\":\"Rate limit exceeded. Please try again later.\"}";
        response.getWriter().write(errorJson);
    }

    /**
     * Cleans up the request tracker to prevent memory leaks
     * Called periodically to remove old entries for IPs that are no longer active
     */
    public void cleanupRequestTracker() {
        Instant cutoff = Instant.now().minus(Duration.ofMinutes(windowMinutes));
        
        requestTracker.forEach((ip, timestamps) -> {
            timestamps.removeIf(timestamp -> timestamp.isBefore(cutoff));
            
            // Remove IP from tracker if no recent requests
            if (timestamps.isEmpty()) {
                requestTracker.remove(ip);
            }
        });
        
        logger.debug("Cleaned up rate limiting request tracker. Current tracked IPs: {}", requestTracker.size());
    }
} 