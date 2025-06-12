package com.app.heartbound.controllers;

import com.app.heartbound.config.security.JWTCacheConfig;
import com.app.heartbound.config.security.JWTTokenProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Admin controller for monitoring and managing JWT cache performance.
 * Provides endpoints for cache statistics, maintenance, and performance analysis.
 */
@RestController
@RequestMapping("/admin/jwt-cache")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "JWT Cache Admin", description = "Admin operations for JWT cache performance monitoring")
@PreAuthorize("hasRole('ADMIN')")
public class JWTCacheController {

    private final JWTTokenProvider jwtTokenProvider;
    private final JWTCacheConfig jwtCacheConfig;

    @Operation(summary = "Get JWT cache statistics", 
               description = "Returns comprehensive statistics about JWT cache performance including hit rates and sizes")
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getCacheStatistics() {
        try {
            log.info("Admin requesting JWT cache statistics");

            JWTCacheConfig.CacheStats stats = jwtTokenProvider.getCacheStatistics();
            Map<String, Object> response = new HashMap<>();

            if (stats != null) {
                response.put("status", "success");
                response.put("cacheEnabled", true);
                response.put("tokenValidation", Map.of(
                    "hitRate", stats.getTokenValidationHitRate(),
                    "currentSize", stats.getTokenValidationSize(),
                    "hitRatePercentage", String.format("%.2f%%", stats.getTokenValidationHitRate() * 100)
                ));
                response.put("claims", Map.of(
                    "hitRate", stats.getClaimsHitRate(),
                    "currentSize", stats.getClaimsSize(),
                    "hitRatePercentage", String.format("%.2f%%", stats.getClaimsHitRate() * 100)
                ));
                response.put("userDetails", Map.of(
                    "hitRate", stats.getUserDetailsHitRate(),
                    "currentSize", stats.getUserDetailsSize(),
                    "hitRatePercentage", String.format("%.2f%%", stats.getUserDetailsHitRate() * 100)
                ));

                // Calculate overall cache effectiveness
                double overallHitRate = (stats.getTokenValidationHitRate() + 
                                       stats.getClaimsHitRate() + 
                                       stats.getUserDetailsHitRate()) / 3.0;
                response.put("overallPerformance", Map.of(
                    "averageHitRate", overallHitRate,
                    "averageHitRatePercentage", String.format("%.2f%%", overallHitRate * 100),
                    "performanceStatus", getPerformanceStatus(overallHitRate)
                ));

                long totalCacheSize = stats.getTokenValidationSize() + 
                                    stats.getClaimsSize() + 
                                    stats.getUserDetailsSize();
                response.put("totalCacheSize", totalCacheSize);
                
            } else {
                response.put("status", "disabled");
                response.put("cacheEnabled", false);
                response.put("message", "JWT caching is disabled");
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error retrieving JWT cache statistics: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "error", "message", "Failed to retrieve cache statistics: " + e.getMessage()));
        }
    }

    @Operation(summary = "Perform cache maintenance", 
               description = "Manually triggers cache cleanup to remove expired entries")
    @PostMapping("/maintenance")
    public ResponseEntity<Map<String, String>> performCacheMaintenance() {
        try {
            log.info("Admin requesting JWT cache maintenance");
            
            jwtCacheConfig.performMaintenance();
            
            Map<String, String> response = Map.of(
                "status", "success",
                "message", "Cache maintenance completed successfully"
            );
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error performing cache maintenance: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "error", "message", "Cache maintenance failed: " + e.getMessage()));
        }
    }

    @Operation(summary = "Invalidate all JWT caches", 
               description = "WARNING: Clears all JWT caches. This will cause temporary performance degradation.")
    @PostMapping("/invalidate-all")
    public ResponseEntity<Map<String, String>> invalidateAllCaches() {
        try {
            log.warn("Admin requesting complete JWT cache invalidation");
            
            jwtCacheConfig.invalidateAllCaches();
            
            Map<String, String> response = Map.of(
                "status", "success",
                "message", "All JWT caches invalidated successfully",
                "warning", "Temporary performance degradation expected until caches rebuild"
            );
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error invalidating all caches: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "error", "message", "Cache invalidation failed: " + e.getMessage()));
        }
    }

    @Operation(summary = "Get cache configuration", 
               description = "Returns current cache configuration and limits")
    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getCacheConfiguration() {
        try {
            Map<String, Object> config = Map.of(
                "tokenValidation", Map.of(
                    "maxSize", jwtCacheConfig.getTokenValidationCacheMaxSize(),
                    "expireAfterWriteMinutes", jwtCacheConfig.getTokenValidationCacheExpireMinutes()
                ),
                "claims", Map.of(
                    "maxSize", jwtCacheConfig.getClaimsCacheMaxSize(),
                    "expireAfterWriteMinutes", jwtCacheConfig.getClaimsCacheExpireMinutes()
                ),
                "userDetails", Map.of(
                    "maxSize", jwtCacheConfig.getUserDetailsCacheMaxSize(),
                    "expireAfterWriteMinutes", jwtCacheConfig.getUserDetailsCacheExpireMinutes()
                )
            );
            
            return ResponseEntity.ok(Map.of("status", "success", "configuration", config));
            
        } catch (Exception e) {
            log.error("Error retrieving cache configuration: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "error", "message", "Failed to retrieve cache configuration: " + e.getMessage()));
        }
    }

    @Operation(summary = "Health check for JWT caching system", 
               description = "Performs a health check on the JWT caching system")
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        try {
            Map<String, Object> health = new HashMap<>();
            
            // Check if caches are accessible
            boolean cacheHealthy = true;
            String healthMessage = "All caches are healthy";
            
            try {
                JWTCacheConfig.CacheStats stats = jwtTokenProvider.getCacheStatistics();
                if (stats == null) {
                    cacheHealthy = false;
                    healthMessage = "Cache statistics not available - caching may be disabled";
                }
            } catch (Exception e) {
                cacheHealthy = false;
                healthMessage = "Cache access error: " + e.getMessage();
            }
            
            health.put("status", cacheHealthy ? "healthy" : "unhealthy");
            health.put("cacheSystemHealthy", cacheHealthy);
            health.put("message", healthMessage);
            health.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(health);
            
        } catch (Exception e) {
            log.error("Error performing cache health check: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "error", "message", "Health check failed: " + e.getMessage()));
        }
    }

    /**
     * Determines the performance status based on hit rate.
     */
    private String getPerformanceStatus(double hitRate) {
        if (hitRate >= 0.85) {
            return "Excellent";
        } else if (hitRate >= 0.70) {
            return "Good";
        } else if (hitRate >= 0.50) {
            return "Fair";
        } else {
            return "Poor - Consider cache tuning";
        }
    }
} 