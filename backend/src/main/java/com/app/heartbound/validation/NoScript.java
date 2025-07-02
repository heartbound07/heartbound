package com.app.heartbound.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * Custom validation annotation that ensures the field contains no HTML or script content.
 * This is a strict validation that rejects any content containing HTML tags or script-like patterns.
 * 
 * Use this for fields that should never contain any markup or potentially dangerous content.
 * 
 * Usage:
 * @NoScript
 * private String plainTextField;
 */
@Documented
@Constraint(validatedBy = NoScriptValidator.class)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface NoScript {
    
    String message() default "Field must not contain HTML tags or script content";
    
    Class<?>[] groups() default {};
    
    Class<? extends Payload>[] payload() default {};
    
    /**
     * Whether to allow basic punctuation and special characters
     * If false, only alphanumeric characters and spaces are allowed
     */
    boolean allowPunctuation() default true;
} 