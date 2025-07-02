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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * SecurityHeadersFilter
 * 
 * Adds security headers to all HTTP responses to prevent XSS, clickjacking, and other attacks.
 * This filter implements multiple layers of browser-based security controls.
 */
@Component
@Order(1) // Execute before other filters
public class SecurityHeadersFilter implements Filter {
    
    private static final Logger logger = LoggerFactory.getLogger(SecurityHeadersFilter.class);
    
    @Value("${security.csp.enabled:true}")
    private boolean cspEnabled;
    
    @Value("${security.csp.report-only:false}")
    private boolean cspReportOnly;
    
    @Value("${security.csp.report-uri:}")
    private String cspReportUri;
    
    @Value("${cors.allowed-origins}")
    private String allowedOrigins;
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        
        // Add security headers
        addSecurityHeaders(httpRequest, httpResponse);
        
        // Continue with the filter chain
        chain.doFilter(request, response);
    }
    
    /**
     * Add comprehensive security headers to prevent various attacks
     */
    private void addSecurityHeaders(HttpServletRequest request, HttpServletResponse response) {
        try {
            // 1. Content Security Policy (CSP) - Primary XSS prevention
            if (cspEnabled) {
                addContentSecurityPolicy(response);
            }
            
            // 2. X-XSS-Protection - Legacy XSS protection for older browsers
            response.setHeader("X-XSS-Protection", "1; mode=block");
            
            // 3. X-Content-Type-Options - Prevent MIME type sniffing
            response.setHeader("X-Content-Type-Options", "nosniff");
            
            // 4. X-Frame-Options - Prevent clickjacking
            response.setHeader("X-Frame-Options", "DENY");
            
            // 5. Referrer Policy - Control referrer information
            response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
            
            // 6. Permissions Policy - Control browser features
            addPermissionsPolicy(response);
            
            // 7. Strict Transport Security (HTTPS only)
            if (request.isSecure()) {
                response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains; preload");
            }
            
            logger.debug("Security headers added to response for: {}", request.getRequestURI());
            
        } catch (Exception e) {
            logger.error("Error adding security headers: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Add Content Security Policy header
     */
    private void addContentSecurityPolicy(HttpServletResponse response) {
        StringBuilder csp = new StringBuilder();
        
        // Default source - only self
        csp.append("default-src 'self'; ");
        
        // Script sources - strict policy
        csp.append("script-src 'self' 'unsafe-inline' 'unsafe-eval' ") // Allow inline for React development
           .append("https://cdnjs.cloudflare.com ")  // Common CDN
           .append("https://cdn.jsdelivr.net; ");     // Common CDN
        
        // Style sources - allow inline styles for CSS-in-JS
        csp.append("style-src 'self' 'unsafe-inline' ")
           .append("https://fonts.googleapis.com; ");
        
        // Font sources
        csp.append("font-src 'self' ")
           .append("https://fonts.gstatic.com ")
           .append("data:; ");
        
        // Image sources - allow data URLs and common image hosting
        csp.append("img-src 'self' data: blob: ")
           .append("https: ")  // Allow HTTPS images from any source (for user avatars, etc.)
           .append("http: ")   // Allow HTTP for development
           .append("*.cloudinary.com ")
           .append("*.imgur.com ")
           .append("*.discord.com ")
           .append("*.discordapp.com; ");
        
        // Media sources
        csp.append("media-src 'self' data: blob:; ");
        
        // Connect sources - for API calls
        csp.append("connect-src 'self' ")
           .append("ws: wss: ")  // WebSocket connections
           .append("https://api.heartbound.net ")
           .append("http://localhost:* "); // Development
        
        // Add allowed origins for connect-src
        if (allowedOrigins != null && !allowedOrigins.isEmpty()) {
            for (String origin : allowedOrigins.split(",")) {
                csp.append(origin.trim()).append(" ");
            }
        }
        csp.append("; ");
        
        // Frame sources - prevent embedding
        csp.append("frame-src 'none'; ");
        
        // Object sources - prevent plugins
        csp.append("object-src 'none'; ");
        
        // Base URI - prevent injection
        csp.append("base-uri 'self'; ");
        
        // Form action - restrict form submissions
        csp.append("form-action 'self'; ");
        
        // Upgrade insecure requests in production
        if (!allowedOrigins.contains("localhost")) {
            csp.append("upgrade-insecure-requests; ");
        }
        
        // Add report URI if configured
        if (cspReportUri != null && !cspReportUri.trim().isEmpty()) {
            csp.append("report-uri ").append(cspReportUri.trim()).append("; ");
        }
        
        // Use report-only mode if configured (for testing)
        String headerName = cspReportOnly ? "Content-Security-Policy-Report-Only" : "Content-Security-Policy";
        response.setHeader(headerName, csp.toString().trim());
        
        logger.debug("CSP header added: {} = {}", headerName, csp.toString().trim());
    }
    
    /**
     * Add Permissions Policy header to control browser features
     */
    private void addPermissionsPolicy(HttpServletResponse response) {
        StringBuilder policy = new StringBuilder();
        
        // Disable potentially dangerous features
        policy.append("camera=(), ")
              .append("microphone=(), ")
              .append("geolocation=(), ")
              .append("payment=(), ")
              .append("usb=(), ")
              .append("magnetometer=(), ")
              .append("gyroscope=(), ")
              .append("accelerometer=(), ")
              .append("encrypted-media=(), ")
              .append("autoplay=()");
        
        response.setHeader("Permissions-Policy", policy.toString());
    }
} 