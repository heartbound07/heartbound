package com.app.heartbound.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

/**
 * Custom validation annotation for banner URLs.
 * Validates that the string is either:
 * - A valid URL
 * - An empty string or null
 */
@Documented
@Constraint(validatedBy = ValidBannerUrlValidator.class)
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.ANNOTATION_TYPE, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidBannerUrl {
    String message() default "Banner URL must be a valid URL or empty string";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
} 