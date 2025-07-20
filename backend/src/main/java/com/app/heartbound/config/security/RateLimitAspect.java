package com.app.heartbound.config.security;

import com.app.heartbound.exceptions.RateLimitExceededException;
import com.app.heartbound.services.RateLimitingService;

import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Aspect to handle rate limiting for methods annotated with @RateLimited
 */
@Aspect
@Component
public class RateLimitAspect {
    
    private static final Logger logger = LoggerFactory.getLogger(RateLimitAspect.class);
    
    private final RateLimitingService rateLimitingService;
    
    public RateLimitAspect(RateLimitingService rateLimitingService) {
        this.rateLimitingService = rateLimitingService;
    }
    
    /**
     * Around advice for methods annotated with @RateLimited
     */
    @Around("@annotation(rateLimited)")
    public Object checkRateLimit(ProceedingJoinPoint joinPoint, RateLimited rateLimited) throws Throwable {
        try {
            // Get the current HTTP request
            HttpServletRequest request = getCurrentHttpRequest();
            if (request == null) {
                logger.warn("No HTTP request found in context for rate limiting");
                return joinPoint.proceed();
            }
            
            // Apply rate limiting
            rateLimitingService.checkRateLimit(rateLimited, request);
            
            logger.debug("Rate limit check passed for method: {}", joinPoint.getSignature().getName());
            
            // Proceed with the original method execution
            return joinPoint.proceed();
            
        } catch (RateLimitExceededException e) {
            logger.warn("Rate limit exceeded for method: {} - {}", 
                       joinPoint.getSignature().getName(), e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("Error during rate limit check for method: {} - {}", 
                        joinPoint.getSignature().getName(), e.getMessage(), e);
            // On error, allow the request to proceed to avoid breaking functionality
            return joinPoint.proceed();
        }
    }
    
    /**
     * Get the current HTTP request from the request context
     */
    private HttpServletRequest getCurrentHttpRequest() {
        try {
            ServletRequestAttributes requestAttributes = 
                (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
            return requestAttributes.getRequest();
        } catch (IllegalStateException e) {
            // No request context available (e.g., async calls, scheduled tasks)
            logger.debug("No request context available for rate limiting");
            return null;
        }
    }
} 