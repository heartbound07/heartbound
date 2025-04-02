package com.app.heartbound.config.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.Refill;
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

@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(RateLimitingFilter.class);

    // Configurable properties from application.properties
    @Value("${rate.limit.max-requests:100}")
    private int maxRequests;

    @Value("${rate.limit.window-minutes:1}")
    private int windowMinutes;
    
    @Value("${rate.limit.burst-capacity:120}")
    private int burstCapacity;
    
    @Value("${rate.limit.cache-maximum-size:100000}")
    private int cacheMaximumSize;

    // Using Caffeine cache instead of ConcurrentHashMap
    private final Cache<String, Bucket> buckets;
    
    public RateLimitingFilter() {
        // Configure Caffeine cache with expiration and size limits
        this.buckets = Caffeine.newBuilder()
                .maximumSize(cacheMaximumSize)
                .expireAfterAccess(Duration.ofHours(6))
                .build();
    }

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
        
        // Get or create bucket for this IP
        Bucket bucket = getBucketForIp(clientIp);
        
        // Try to consume a token from the bucket
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        
        if (probe.isConsumed()) {
            // Add rate limit headers
            response.addHeader("X-Rate-Limit-Remaining", String.valueOf(probe.getRemainingTokens()));
            response.addHeader("X-Rate-Limit-Limit", String.valueOf(maxRequests));
            
            logger.debug("Request from IP: {} to URI: {} within rate limits. Remaining: {}", 
                     clientIp, requestURI, probe.getRemainingTokens());
                     
            filterChain.doFilter(request, response);
        } else {
            // Rate limit exceeded
            logger.warn("Rate limit exceeded for IP: {} on URI: {}", clientIp, requestURI);
            
            // Return 429 Too Many Requests
            sendRateLimitResponse(response, probe.getNanosToWaitForRefill() / 1_000_000_000);
        }
    }

    /**
     * Get or create a token bucket for the given IP address
     */
    private Bucket getBucketForIp(String clientIp) {
        return buckets.get(clientIp, ip -> {
            // Configure the rate limit: [maxRequests] tokens per [windowMinutes] minutes
            // Allow initial burst capacity higher than regular refill rate
            Bandwidth limit = Bandwidth.classic(burstCapacity, 
                Refill.intervally(maxRequests, Duration.ofMinutes(windowMinutes)));
            
            // Updated to use non-deprecated API
            return Bucket.builder()
                .addLimit(limit)
                .build();
        });
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
    private void sendRateLimitResponse(HttpServletResponse response, long retryAfterSeconds) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        
        String errorJson = "{\"error\":\"Too Many Requests\",\"message\":\"Rate limit exceeded. Please try again after " 
            + retryAfterSeconds + " seconds.\"}";
        response.getWriter().write(errorJson);
    }

    /**
     * Method to clean up unused buckets to prevent memory leaks.
     * This can be called periodically from RateLimitingConfig.
     */
    public void cleanupBuckets() {
        // Caffeine handles eviction automatically based on our configuration
        // But we can still manually clean if needed
        buckets.cleanUp();
        logger.info("Triggered manual cleanup of rate limiting buckets cache");
    }
} 