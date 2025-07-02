package com.app.heartbound.controllers.monitoring;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.app.heartbound.services.RateLimitingService;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Controller for monitoring rate limiting metrics and health.
 * Only accessible by admin users for security monitoring.
 */
@RestController
@RequestMapping("/monitoring/rate-limits")
public class RateLimitMonitoringController {
    
    private final RateLimitingService rateLimitingService;
    
    @Autowired
    public RateLimitMonitoringController(RateLimitingService rateLimitingService) {
        this.rateLimitingService = rateLimitingService;
    }
    
    /**
     * Get current rate limiting metrics
     * Shows which endpoints are being hit by rate limits
     */
    @GetMapping("/metrics")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getRateLimitMetrics() {
        ConcurrentHashMap<String, Long> rateLimitHits = rateLimitingService.getRateLimitHits();
        
        Map<String, Object> response = new HashMap<>();
        response.put("total_rate_limit_hits", rateLimitHits.values().stream().mapToLong(Long::longValue).sum());
        response.put("hits_by_endpoint", rateLimitHits);
        response.put("endpoints_monitored", rateLimitHits.size());
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get rate limiting health information
     */
    @GetMapping("/health")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getRateLimitHealth() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("enhanced_rate_limiting", "ENABLED");
        health.put("monitoring", "ACTIVE");
        
        ConcurrentHashMap<String, Long> rateLimitHits = rateLimitingService.getRateLimitHits();
        long totalHits = rateLimitHits.values().stream().mapToLong(Long::longValue).sum();
        
        // Simple health indicators
        health.put("total_rate_limit_violations", totalHits);
        health.put("high_activity_warning", totalHits > 1000 ? "YES" : "NO");
        
        return ResponseEntity.ok(health);
    }
    
    /**
     * Clear rate limiting metrics (for testing or periodic cleanup)
     */
    @PostMapping("/metrics/clear")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> clearMetrics() {
        rateLimitingService.clearMetrics();
        
        Map<String, String> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Rate limiting metrics cleared");
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get rate limiting configuration information
     */
    @GetMapping("/config")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getRateLimitConfig() {
        Map<String, Object> config = new HashMap<>();
        
        // Financial endpoints configuration
        Map<String, Object> endpoints = new HashMap<>();
        
        Map<String, Object> purchase = new HashMap<>();
        purchase.put("requests_per_minute", 5);
        purchase.put("requests_per_hour", 20);
        purchase.put("burst_capacity", 6);
        purchase.put("key_type", "USER");
        endpoints.put("purchase", purchase);
        
        Map<String, Object> caseOpen = new HashMap<>();
        caseOpen.put("requests_per_minute", 10);
        caseOpen.put("requests_per_hour", 50);
        caseOpen.put("burst_capacity", 12);
        caseOpen.put("key_type", "USER");
        endpoints.put("case_open", caseOpen);
        
        Map<String, Object> equip = new HashMap<>();
        equip.put("requests_per_minute", 30);
        equip.put("requests_per_hour", 200);
        equip.put("burst_capacity", 35);
        equip.put("key_type", "USER");
        endpoints.put("equip_unequip", equip);
        
        config.put("financial_endpoints", endpoints);
        config.put("enhanced_rate_limiting_enabled", true);
        config.put("monitoring_enabled", true);
        
        return ResponseEntity.ok(config);
    }
} 