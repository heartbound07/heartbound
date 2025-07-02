package com.app.heartbound.config.security;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Map;

/**
 * CorsValidationFilter
 * 
 * Provides additional CORS validation, security monitoring, and logging.
 * This filter works in conjunction with Spring's CORS configuration to
 * provide enhanced security features and monitoring capabilities.
 * 
 * Security Features:
 * - Origin validation and sanitization
 * - Suspicious pattern detection
 * - Rate limiting for CORS violations
 * - Comprehensive security logging
 * - Integration with security headers
 */
@Component
@Order(2) // Execute after SecurityHeadersFilter but before other filters
public class CorsValidationFilter implements Filter {
    
    private static final Logger logger = LoggerFactory.getLogger(CorsValidationFilter.class);
    private static final Logger securityLogger = LoggerFactory.getLogger("SECURITY.CORS");
    
    private final CorsConfigurationProvider corsConfigurationProvider;
    
    // Monitoring and rate limiting
    private final ConcurrentHashMap<String, AtomicInteger> rejectedOriginCounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> lastRejectionTime = new ConcurrentHashMap<>();
    
    @Value("${cors.validation.max-rejections-per-hour:10}")
    private int maxRejectionsPerHour;
    
    @Value("${cors.validation.alert-threshold:5}")
    private int alertThreshold;
    
    @Value("${cors.validation.enabled:true}")
    private boolean validationEnabled;
    
    @Autowired
    public CorsValidationFilter(CorsConfigurationProvider corsConfigurationProvider) {
        this.corsConfigurationProvider = corsConfigurationProvider;
    }
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        if (!validationEnabled) {
            chain.doFilter(request, response);
            return;
        }
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        
        // Only validate cross-origin requests
        if (isCrossOriginRequest(httpRequest)) {
            validateCorsRequest(httpRequest, httpResponse);
        }
        
        // Add CORS-specific security headers
        addCorsSecurityHeaders(httpRequest, httpResponse);
        
        chain.doFilter(request, response);
    }
    
    /**
     * Validate CORS request and monitor for security violations
     */
    private void validateCorsRequest(HttpServletRequest request, HttpServletResponse response) {
        String origin = request.getHeader("Origin");
        String requestMethod = request.getMethod();
        String requestPath = request.getRequestURI();
        
        if (origin != null) {
            // Sanitize origin header
            origin = sanitizeOrigin(origin);
            
            // Validate origin format
            if (!isValidOriginFormat(origin)) {
                logSecurityViolation("INVALID_ORIGIN_FORMAT", origin, request);
                recordRejection(origin);
                return;
            }
            
            // Check if origin is allowed
            if (!corsConfigurationProvider.isOriginAllowed(origin)) {
                logSecurityViolation("UNAUTHORIZED_ORIGIN", origin, request);
                recordRejection(origin);
                
                // Check for suspicious patterns
                detectSuspiciousPatterns(origin, request);
                return;
            }
            
            // Log successful CORS request for monitoring
            logger.debug("CORS: Allowed origin '{}' for {} {}", origin, requestMethod, requestPath);
            
            // Special validation for preflight requests
            if (HttpMethod.OPTIONS.matches(requestMethod)) {
                validatePreflightRequest(request);
            }
        }
    }
    
    /**
     * Add CORS-specific security headers
     */
    private void addCorsSecurityHeaders(HttpServletRequest request, HttpServletResponse response) {
        // Add Vary header for proper caching with CORS
        String varyHeader = response.getHeader("Vary");
        if (varyHeader == null) {
            response.setHeader("Vary", "Origin, Access-Control-Request-Method, Access-Control-Request-Headers");
        } else if (!varyHeader.contains("Origin")) {
            response.setHeader("Vary", varyHeader + ", Origin");
        }
        
        // Add timing attack protection
        response.setHeader("X-Content-Type-Options", "nosniff");
        
        // Ensure CORS requests are not cached inappropriately
        if (isCrossOriginRequest(request)) {
            response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        }
    }
    
    /**
     * Validate preflight request headers
     */
    private void validatePreflightRequest(HttpServletRequest request) {
        String requestMethod = request.getHeader("Access-Control-Request-Method");
        String requestHeaders = request.getHeader("Access-Control-Request-Headers");
        String origin = request.getHeader("Origin");
        
        logger.debug("CORS Preflight: Origin={}, Method={}, Headers={}", 
                    origin, requestMethod, requestHeaders);
        
        // Check for suspicious preflight patterns
        if (requestHeaders != null && requestHeaders.length() > 1000) {
            logSecurityViolation("EXCESSIVE_PREFLIGHT_HEADERS", origin, request);
        }
        
        // Validate requested method
        if (requestMethod != null && !isAllowedMethod(requestMethod)) {
            logSecurityViolation("INVALID_PREFLIGHT_METHOD", origin, request);
        }
    }
    
    /**
     * Detect suspicious patterns in CORS requests
     */
    private void detectSuspiciousPatterns(String origin, HttpServletRequest request) {
        // Check for common attack patterns
        if (origin.contains("javascript:") || origin.contains("data:") || origin.contains("file:")) {
            logSecurityViolation("SUSPICIOUS_ORIGIN_SCHEME", origin, request);
            return;
        }
        
        // Check for potential XSS attempts in origin
        if (origin.contains("<script") || origin.contains("javascript") || origin.contains("onload=")) {
            logSecurityViolation("XSS_ATTEMPT_IN_ORIGIN", origin, request);
            return;
        }
        
        // Check for IP address origins (potentially suspicious)
        if (origin.matches("https?://\\d+\\.\\d+\\.\\d+\\.\\d+.*")) {
            logSecurityViolation("IP_ADDRESS_ORIGIN", origin, request);
            return;
        }
        
        // Check for non-standard ports (potentially suspicious)
        if (origin.matches(".*:\\d{5}.*")) {
            logSecurityViolation("HIGH_PORT_ORIGIN", origin, request);
        }
    }
    
    /**
     * Sanitize origin header to prevent injection attacks
     */
    private String sanitizeOrigin(String origin) {
        if (origin == null) {
            return null;
        }
        
        // Remove potential null bytes and other control characters
        origin = origin.replaceAll("[\\x00-\\x1F\\x7F]", "");
        
        // Trim whitespace
        origin = origin.trim();
        
        // Limit length to prevent DoS
        if (origin.length() > 200) {
            origin = origin.substring(0, 200);
            logger.warn("CORS: Truncated overly long origin header");
        }
        
        return origin;
    }
    
    /**
     * Validate origin format
     */
    private boolean isValidOriginFormat(String origin) {
        if (origin == null || origin.isEmpty()) {
            return false;
        }
        
        // Basic URL format validation
        if (!origin.matches("^https?://[^\\s/$.?#].[^\\s]*$")) {
            return false;
        }
        
        // Check for double slashes (potential bypass attempt)
        if (origin.contains("//") && !origin.startsWith("http://") && !origin.startsWith("https://")) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Check if request is cross-origin
     */
    private boolean isCrossOriginRequest(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        return origin != null && !origin.isEmpty();
    }
    
    /**
     * Check if HTTP method is allowed
     */
    private boolean isAllowedMethod(String method) {
        return method != null && (
            "GET".equals(method) ||
            "POST".equals(method) ||
            "PUT".equals(method) ||
            "DELETE".equals(method) ||
            "PATCH".equals(method) ||
            "OPTIONS".equals(method)
        );
    }
    
    /**
     * Record rejection for monitoring and rate limiting
     */
    private void recordRejection(String origin) {
        long currentTime = System.currentTimeMillis();
        
        // Clean up old entries (older than 1 hour)
        cleanupOldRejections(currentTime);
        
        // Increment rejection count
        AtomicInteger count = rejectedOriginCounts.computeIfAbsent(origin, k -> new AtomicInteger(0));
        int rejections = count.incrementAndGet();
        lastRejectionTime.put(origin, currentTime);
        
        // Alert if threshold exceeded
        if (rejections >= alertThreshold) {
            securityLogger.warn("CORS SECURITY ALERT: Origin '{}' has been rejected {} times in the last hour", 
                              origin, rejections);
        }
        
        // Rate limit if too many rejections
        if (rejections >= maxRejectionsPerHour) {
            securityLogger.error("CORS RATE LIMIT: Origin '{}' has exceeded maximum rejections ({}) per hour", 
                               origin, maxRejectionsPerHour);
        }
    }
    
    /**
     * Clean up old rejection records
     */
    private void cleanupOldRejections(long currentTime) {
        long oneHourAgo = currentTime - (60 * 60 * 1000);
        
        lastRejectionTime.entrySet().removeIf(entry -> {
            if (entry.getValue() < oneHourAgo) {
                rejectedOriginCounts.remove(entry.getKey());
                return true;
            }
            return false;
        });
    }
    
    /**
     * Log security violation with detailed information
     */
    private void logSecurityViolation(String violationType, String origin, HttpServletRequest request) {
        String clientIp = getClientIpAddress(request);
        String userAgent = request.getHeader("User-Agent");
        String requestPath = request.getRequestURI();
        String method = request.getMethod();
        
        securityLogger.warn("CORS VIOLATION [{}]: Origin='{}', IP='{}', Path='{}', Method='{}', UserAgent='{}'",
                          violationType, origin, clientIp, requestPath, method, userAgent);
        
        // Also log to main logger for general monitoring
        logger.warn("CORS security violation: {} from origin '{}' (IP: {})", 
                   violationType, origin, clientIp);
    }
    
    /**
     * Extract client IP address considering proxies
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
    
    /**
     * Get current monitoring statistics
     */
    public CorsValidationStats getValidationStats() {
        return new CorsValidationStats(
            rejectedOriginCounts.size(),
            rejectedOriginCounts.values().stream().mapToInt(AtomicInteger::get).sum(),
            corsConfigurationProvider.getValidationStats()
        );
    }
    
    /**
     * Clear monitoring data
     */
    public void clearMonitoringData() {
        rejectedOriginCounts.clear();
        lastRejectionTime.clear();
        logger.info("CORS validation monitoring data cleared");
    }
    
    /**
     * Statistics class for monitoring
     */
    public static class CorsValidationStats {
        private final int uniqueRejectedOrigins;
        private final int totalRejections;
        private final Map<String, Object> configStats;
        
        public CorsValidationStats(int uniqueRejectedOrigins, int totalRejections, Map<String, Object> configStats) {
            this.uniqueRejectedOrigins = uniqueRejectedOrigins;
            this.totalRejections = totalRejections;
            this.configStats = configStats;
        }
        
        public int getUniqueRejectedOrigins() { return uniqueRejectedOrigins; }
        public int getTotalRejections() { return totalRejections; }
        public Map<String, Object> getConfigStats() { return configStats; }
    }
} 