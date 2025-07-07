package com.app.heartbound.services;

import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

/**
 * HtmlSanitizationService
 * 
 * Centralized service for HTML sanitization using OWASP Java HTML Sanitizer.
 * Provides different sanitization policies for different content types.
 * 
 * Security Features:
 * - Prevents all XSS attacks by stripping dangerous HTML
 * - Configurable policies for different content types
 * - Audit logging for security monitoring
 * - Performance metrics for monitoring
 */
@Service
public class HtmlSanitizationService {
    
    private static final Logger logger = LoggerFactory.getLogger(HtmlSanitizationService.class);
    
    // Pre-compiled sanitization policies for performance
    private final PolicyFactory strictPolicy;
    private final PolicyFactory basicPolicy;
    private final PolicyFactory richPolicy;
    
    // Security audit metrics
    private final ConcurrentHashMap<String, AtomicLong> sanitizationMetrics = new ConcurrentHashMap<>();
    
    // Regex patterns for validation
    private static final Pattern ALPHANUMERIC_WITH_PUNCTUATION = Pattern.compile("^[a-zA-Z0-9\\s\\-_.,'!?():/]+$");
    private static final Pattern DANGEROUS_PATTERNS = Pattern.compile(
        "(?i)(javascript:|data:|vbscript:|on\\w+\\s*=|<\\s*script|<\\s*iframe|<\\s*object|<\\s*embed|<\\s*link|<\\s*meta|<\\s*style)"
    );
    
    public HtmlSanitizationService() {
        this.strictPolicy = createStrictPolicy();
        this.basicPolicy = createBasicPolicy();
        this.richPolicy = createRichPolicy();
        
        logger.info("HtmlSanitizationService initialized with security policies");
    }
    
    /**
     * Sanitize text using STRICT policy (for item names, case names)
     * - Strips ALL HTML tags
     * - Allows only alphanumeric + basic punctuation
     * - Maximum security level
     */
    public String sanitizeStrict(String input) {
        return sanitize(input, SanitizationPolicy.STRICT);
    }
    
    /**
     * Sanitize text using BASIC policy (for descriptions)
     * - Strips all HTML tags but preserves text content
     * - Allows newlines (converted to spaces for safety)
     * - Medium security level
     */
    public String sanitizeBasic(String input) {
        return sanitize(input, SanitizationPolicy.BASIC);
    }
    
    /**
     * Sanitize text using RICH policy (future use - rich text descriptions)
     * - Allows safe HTML tags only (b, i, u, br, p)
     * - Strips all attributes except whitelisted classes
     * - Lower security level but allows formatting
     */
    public String sanitizeRich(String input) {
        return sanitize(input, SanitizationPolicy.RICH);
    }
    
    /**
     * Main sanitization method with policy selection
     */
    public String sanitize(String input, SanitizationPolicy policy) {
        if (!StringUtils.hasText(input)) {
            return input;
        }
        
        String originalInput = input;
        
        try {
            // Step 1: Pre-validation for obvious attacks
            if (containsDangerousPatterns(input)) {
                logSecurityEvent("DANGEROUS_PATTERN_DETECTED", originalInput, policy);
                incrementMetric("dangerous_patterns_blocked");
                // For strict security, reject completely
                if (policy == SanitizationPolicy.STRICT) {
                    return "";
                }
            }
            
            // Step 2: Apply appropriate sanitization policy
            String sanitized = switch (policy) {
                case STRICT -> applyStrictSanitization(input);
                case BASIC -> applyBasicSanitization(input);
                case RICH -> applyRichSanitization(input);
            };
            
            // Step 3: Final validation and cleanup
            sanitized = finalCleanup(sanitized);
            
            // Step 4: Security audit logging
            if (!originalInput.equals(sanitized)) {
                logSanitizationEvent(originalInput, sanitized, policy);
                incrementMetric("content_sanitized");
            }
            
            return sanitized;
            
        } catch (Exception e) {
            logger.error("Error during sanitization with policy {}: {}", policy, e.getMessage(), e);
            incrementMetric("sanitization_errors");
            // Fail secure - return empty string on error
            return "";
        }
    }
    
    /**
     * Check for dangerous patterns that indicate XSS attempts
     */
    private boolean containsDangerousPatterns(String input) {
        if (input == null) return false;
        return DANGEROUS_PATTERNS.matcher(input).find();
    }
    
    /**
     * Apply STRICT sanitization policy
     */
    private String applyStrictSanitization(String input) {
        // Strip all HTML tags first
        String sanitized = strictPolicy.sanitize(input);
        
        // Additional validation for alphanumeric + basic punctuation only
        if (!ALPHANUMERIC_WITH_PUNCTUATION.matcher(sanitized).matches()) {
            // Remove any characters that don't match the pattern
            sanitized = sanitized.replaceAll("[^a-zA-Z0-9\\s\\-_.,'!?():/]", "");
        }
        
        // Normalize whitespace
        sanitized = sanitized.replaceAll("\\s+", " ").trim();
        
        return sanitized;
    }
    
    /**
     * Apply BASIC sanitization policy
     */
    private String applyBasicSanitization(String input) {
        // Strip all HTML tags but preserve text content
        String sanitized = basicPolicy.sanitize(input);
        
        // Convert newlines to spaces for consistency
        sanitized = sanitized.replaceAll("\\r?\\n", " ");
        
        // Normalize whitespace
        sanitized = sanitized.replaceAll("\\s+", " ").trim();
        
        return sanitized;
    }
    
    /**
     * Apply RICH sanitization policy (future use)
     */
    private String applyRichSanitization(String input) {
        // Allow safe HTML tags with strict attribute filtering
        String sanitized = richPolicy.sanitize(input);
        
        // Additional cleanup
        sanitized = sanitized.trim();
        
        return sanitized;
    }
    
    /**
     * Final cleanup and validation
     */
    private String finalCleanup(String input) {
        if (input == null) return "";
        
        // Remove any null bytes
        input = input.replace("\0", "");
        
        // Remove any remaining control characters except tab, newline, carriage return
        input = input.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]", "");
        
        return input.trim();
    }
    
    /**
     * Create STRICT sanitization policy
     */
    private PolicyFactory createStrictPolicy() {
        return new HtmlPolicyBuilder()
            .toFactory(); // No HTML tags allowed at all
    }
    
    /**
     * Create BASIC sanitization policy
     */
    private PolicyFactory createBasicPolicy() {
        return new HtmlPolicyBuilder()
            .toFactory(); // No HTML tags allowed, just strips them
    }
    
    /**
     * Create RICH sanitization policy (for future use)
     */
    private PolicyFactory createRichPolicy() {
        return new HtmlPolicyBuilder()
            .allowElements("b", "i", "u", "br", "p", "strong", "em")
            .allowAttributes("class")
                .matching(Pattern.compile("^(text-\\w+|font-\\w+|bg-\\w+)$"))
                .onElements("p", "span")
            .toFactory();
    }
    
    /**
     * Log security events for audit
     */
    private void logSecurityEvent(String eventType, String content, SanitizationPolicy policy) {
        logger.warn("SECURITY EVENT: {} detected with policy {}. Content preview: {}", 
                   eventType, policy, truncateForLogging(content));
    }
    
    /**
     * Log sanitization events for audit
     */
    private void logSanitizationEvent(String original, String sanitized, SanitizationPolicy policy) {
        if (logger.isDebugEnabled()) {
            logger.debug("Content sanitized with policy {}. Original: '{}' -> Sanitized: '{}'", 
                        policy, truncateForLogging(original), truncateForLogging(sanitized));
        }
    }
    
    /**
     * Truncate content for safe logging
     */
    private String truncateForLogging(String content) {
        if (content == null) return "null";
        if (content.length() <= 100) return content;
        return content.substring(0, 100) + "...";
    }
    
    /**
     * Increment security metrics
     */
    private void incrementMetric(String metricName) {
        sanitizationMetrics.computeIfAbsent(metricName, k -> new AtomicLong(0))
                          .incrementAndGet();
    }
    
    /**
     * Get sanitization metrics for monitoring
     */
    public ConcurrentHashMap<String, Long> getSanitizationMetrics() {
        ConcurrentHashMap<String, Long> metrics = new ConcurrentHashMap<>();
        sanitizationMetrics.forEach((key, value) -> metrics.put(key, value.get()));
        return metrics;
    }
    
    /**
     * Clear metrics (for testing or periodic reset)
     */
    public void clearMetrics() {
        sanitizationMetrics.clear();
    }
    
    /**
     * Validate URL for safety (used for imageUrl, thumbnailUrl)
     */
    public boolean isValidUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return true; // Empty URLs are valid
        }
        
        // Check for dangerous protocols
        String lowercaseUrl = url.toLowerCase();
        if (lowercaseUrl.startsWith("javascript:") || 
            lowercaseUrl.startsWith("data:") || 
            lowercaseUrl.startsWith("vbscript:") ||
            lowercaseUrl.startsWith("file:")) {
            return false;
        }
        
        // Must start with http:// or https://
        return lowercaseUrl.startsWith("http://") || lowercaseUrl.startsWith("https://");
    }
    
    /**
     * Sanitize URL for safety
     */
    public String sanitizeUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return url;
        }
        
        if (!isValidUrl(url)) {
            logSecurityEvent("INVALID_URL_BLOCKED", url, SanitizationPolicy.STRICT);
            incrementMetric("invalid_urls_blocked");
            return ""; // Return empty string for invalid URLs
        }
        
        return url.trim();
    }
    
    /**
     * Sanitization policies enum
     */
    public enum SanitizationPolicy {
        STRICT,  // For item names, case names - no HTML, alphanumeric + basic punctuation only
        BASIC,   // For descriptions - strip HTML, preserve text
        RICH     // For future rich text - allow safe HTML tags only
    }
} 