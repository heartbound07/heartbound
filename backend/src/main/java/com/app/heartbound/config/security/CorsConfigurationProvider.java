package com.app.heartbound.config.security;
 
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsConfiguration;

import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
/**
 * CorsConfigurationProvider
 * 
 * Provides secure, dynamic CORS configuration with origin validation,
 * pattern matching, and environment-specific policies.
 * 
 * Security Features:
 * - Origin validation with whitelist and pattern matching
 * - No wildcard usage in production
 * - Environment-specific configuration
 * - Caching for performance
 * - Comprehensive logging for security monitoring
 */
@Component
public class CorsConfigurationProvider {
    
    private static final Logger logger = LoggerFactory.getLogger(CorsConfigurationProvider.class);
    
    // Configuration properties
    @Value("${cors.allowed-origins}")
    private String allowedOrigins;
    
    @Value("${cors.allowed-origins.production:}")
    private String productionAllowedOrigins;
    
    @Value("${cors.allowed-origins.staging:}")
    private String stagingAllowedOrigins;
    
    @Value("${cors.allowed-headers}")
    private String allowedHeaders;
    
    @Value("${cors.exposed-headers}")
    private String exposedHeaders;
    
    @Value("${cors.max-age:3600}")
    private Long maxAge;
    
    @Value("${cors.allow-credentials:true}")
    private Boolean allowCredentials;
    
    @Value("${cors.origin.validation.enabled:true}")
    private Boolean originValidationEnabled;
    
    @Value("${cors.origin.pattern.enabled:true}")
    private Boolean patternMatchingEnabled;
    
    @Value("${cors.origin.patterns:}")
    private String originPatterns;
    
    @Value("${spring.profiles.active:dev}")
    private String activeProfile;
    
    // Caches for performance
    private final Map<String, Boolean> originValidationCache = new ConcurrentHashMap<>();
    private final Map<String, Pattern> compiledPatterns = new ConcurrentHashMap<>();
    
    // Pre-compiled lists
    private List<String> validOrigins;
    private List<String> validHeaders;
    private List<String> validExposedHeaders;
    private List<Pattern> originPatternList;
    
    @PostConstruct
    public void initialize() {
        logger.info("Initializing CorsConfigurationProvider for profile: {}", activeProfile);
        
        // Initialize origin lists based on environment
        initializeOrigins();
        
        // Initialize header lists
        initializeHeaders();
        
        // Initialize pattern matching
        initializePatterns();
        
        logger.info("CORS configuration initialized with {} origins, {} headers, {} patterns", 
                   validOrigins.size(), validHeaders.size(), originPatternList.size());
    }
    
    /**
     * Create a secure CORS configuration
     */
    public CorsConfiguration createCorsConfiguration() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        // Set allowed origins (never use wildcards in production)
        configuration.setAllowedOrigins(validOrigins);
        
        // Set allowed methods
        configuration.setAllowedMethods(Arrays.asList(
            "GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"
        ));
        
        // Set allowed headers (explicit list, no wildcards)
        configuration.setAllowedHeaders(validHeaders);
        
        // Set exposed headers
        if (!validExposedHeaders.isEmpty()) {
            configuration.setExposedHeaders(validExposedHeaders);
        }
        
        // Set credentials
        configuration.setAllowCredentials(allowCredentials);
        
        // Set max age for preflight caching
        configuration.setMaxAge(maxAge);
        
        logger.debug("Created CORS configuration: Origins={}, Headers={}, MaxAge={}", 
                    validOrigins.size(), validHeaders.size(), maxAge);
        
        return configuration;
    }
    
    /**
     * Validate if an origin is allowed
     */
    public boolean isOriginAllowed(String origin) {
        if (origin == null || origin.trim().isEmpty()) {
            return false;
        }
        
        // Check cache first
        Boolean cached = originValidationCache.get(origin);
        if (cached != null) {
            return cached;
        }
        
        boolean allowed = validateOrigin(origin);
        
        // Cache the result (with size limit)
        if (originValidationCache.size() < 1000) {
            originValidationCache.put(origin, allowed);
        }
        
        if (!allowed) {
            logger.warn("CORS: Rejected origin: {}", origin);
        }
        
        return allowed;
    }
    
    /**
     * Initialize origins based on environment
     */
    private void initializeOrigins() {
        Set<String> origins = new HashSet<>();
        
        // Add profile-specific origins
        switch (activeProfile.toLowerCase()) {
            case "prod":
            case "production":
                if (!productionAllowedOrigins.isEmpty()) {
                    origins.addAll(parseOrigins(productionAllowedOrigins));
                }
                break;
            case "staging":
                if (!stagingAllowedOrigins.isEmpty()) {
                    origins.addAll(parseOrigins(stagingAllowedOrigins));
                }
                break;
            default:
                // Development - include all origins
                origins.addAll(parseOrigins(allowedOrigins));
                if (!productionAllowedOrigins.isEmpty()) {
                    origins.addAll(parseOrigins(productionAllowedOrigins));
                }
                if (!stagingAllowedOrigins.isEmpty()) {
                    origins.addAll(parseOrigins(stagingAllowedOrigins));
                }
        }
        
        // Validate all origins
        this.validOrigins = origins.stream()
                .filter(this::isValidOriginFormat)
                .distinct()
                .sorted()
                .toList();
                
        logger.info("Initialized {} valid origins for profile '{}'", validOrigins.size(), activeProfile);
    }
    
    /**
     * Initialize header lists
     */
    private void initializeHeaders() {
        // Parse allowed headers
        this.validHeaders = parseHeaders(allowedHeaders);
        
        // Parse exposed headers
        this.validExposedHeaders = parseHeaders(exposedHeaders);
        
        logger.debug("Initialized headers - Allowed: {}, Exposed: {}", 
                    validHeaders.size(), validExposedHeaders.size());
    }
    
    /**
     * Initialize pattern matching
     */
    private void initializePatterns() {
        this.originPatternList = new ArrayList<>();
        
        if (patternMatchingEnabled && !originPatterns.isEmpty()) {
            String[] patterns = originPatterns.split(",");
            for (String pattern : patterns) {
                try {
                    String trimmedPattern = pattern.trim();
                    if (!trimmedPattern.isEmpty()) {
                        // Convert wildcard pattern to regex
                        String regex = convertWildcardToRegex(trimmedPattern);
                        Pattern compiled = Pattern.compile(regex);
                        originPatternList.add(compiled);
                        compiledPatterns.put(trimmedPattern, compiled);
                        logger.debug("Compiled origin pattern: {} -> {}", trimmedPattern, regex);
                    }
                } catch (Exception e) {
                    logger.error("Failed to compile origin pattern: {}", pattern, e);
                }
            }
        }
        
        logger.info("Initialized {} origin patterns", originPatternList.size());
    }
    
    /**
     * Validate origin format and security
     */
    private boolean validateOrigin(String origin) {
        // Check against whitelist first
        if (validOrigins.contains(origin)) {
            return true;
        }
        
        // Check against patterns if enabled
        if (patternMatchingEnabled && !originPatternList.isEmpty()) {
            for (Pattern pattern : originPatternList) {
                if (pattern.matcher(origin).matches()) {
                    logger.debug("Origin '{}' matched pattern: {}", origin, pattern.pattern());
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * Validate origin format
     */
    private boolean isValidOriginFormat(String origin) {
        if (origin == null || origin.trim().isEmpty()) {
            return false;
        }
        
        try {
            URI uri = new URI(origin);
            
            // Must have a scheme
            if (uri.getScheme() == null) {
                logger.warn("Invalid origin format (no scheme): {}", origin);
                return false;
            }
            
            // Must have a host
            if (uri.getHost() == null) {
                logger.warn("Invalid origin format (no host): {}", origin);
                return false;
            }
            
            // In production, only allow HTTPS (except localhost)
            if ("production".equals(activeProfile) || "prod".equals(activeProfile)) {
                if (!"https".equals(uri.getScheme()) && !uri.getHost().contains("localhost")) {
                    logger.warn("Production environment requires HTTPS origins: {}", origin);
                    return false;
                }
            }
            
            // No path, query, or fragment allowed
            if (uri.getPath() != null && !uri.getPath().isEmpty() && !"/".equals(uri.getPath())) {
                logger.warn("Origin cannot contain path: {}", origin);
                return false;
            }
            
            return true;
            
        } catch (URISyntaxException e) {
            logger.warn("Invalid origin URI format: {}", origin);
            return false;
        }
    }
    
    /**
     * Parse comma-separated origins
     */
    private List<String> parseOrigins(String origins) {
        if (origins == null || origins.trim().isEmpty()) {
            return new ArrayList<>();
        }
        
        return Arrays.stream(origins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
    
    /**
     * Parse comma-separated headers
     */
    private List<String> parseHeaders(String headers) {
        if (headers == null || headers.trim().isEmpty()) {
            return new ArrayList<>();
        }
        
        return Arrays.stream(headers.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
    
    /**
     * Convert wildcard pattern to regex
     */
    private String convertWildcardToRegex(String pattern) {
        // Escape special regex characters except *
        String escaped = pattern
                .replace(".", "\\.")
                .replace("+", "\\+")
                .replace("^", "\\^")
                .replace("$", "\\$")
                .replace("(", "\\(")
                .replace(")", "\\)")
                .replace("[", "\\[")
                .replace("]", "\\]")
                .replace("{", "\\{")
                .replace("}", "\\}")
                .replace("|", "\\|")
                .replace("?", "\\?");
        
        // Convert * to regex equivalent
        escaped = escaped.replace("*", "[^.]*");
        
        // Anchor the pattern
        return "^" + escaped + "$";
    }
    
    /**
     * Get current validation statistics
     */
    public Map<String, Object> getValidationStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("validOrigins", validOrigins.size());
        stats.put("validHeaders", validHeaders.size());
        stats.put("validExposedHeaders", validExposedHeaders.size());
        stats.put("originPatterns", originPatternList.size());
        stats.put("cacheSize", originValidationCache.size());
        stats.put("activeProfile", activeProfile);
        return stats;
    }
    
    /**
     * Clear validation cache
     */
    public void clearCache() {
        originValidationCache.clear();
        logger.info("CORS validation cache cleared");
    }
} 