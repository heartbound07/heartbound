package com.app.heartbound.validation;

import com.app.heartbound.services.HtmlSanitizationService;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * Custom validation annotation for automatic HTML sanitization.
 * Sanitizes the field value during validation and replaces it with the sanitized version.
 * 
 * Usage:
 * @SanitizedHtml(policy = SanitizationPolicy.STRICT)
 * private String itemName;
 * 
 * @SanitizedHtml(policy = SanitizationPolicy.BASIC)
 * private String description;
 */
@Documented
@Constraint(validatedBy = HtmlSanitizationValidator.class)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface SanitizedHtml {
    
    String message() default "Content contains invalid or potentially dangerous HTML";
    
    Class<?>[] groups() default {};
    
    Class<? extends Payload>[] payload() default {};
    
    /**
     * Sanitization policy to apply
     */
    HtmlSanitizationService.SanitizationPolicy policy() default HtmlSanitizationService.SanitizationPolicy.BASIC;
    
    /**
     * Whether to fail validation if sanitization changes the content
     * If false, content is sanitized and validation passes
     * If true, validation fails if content needed sanitization
     */
    boolean failOnSanitization() default false;
    
    /**
     * Maximum length after sanitization
     */
    int maxLength() default Integer.MAX_VALUE;
    
    /**
     * Minimum length after sanitization
     */
    int minLength() default 0;
} 