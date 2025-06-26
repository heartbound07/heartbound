package com.app.heartbound.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

/**
 * Custom validation annotation for avatar URLs.
 * Validates that the string is either:
 * - A valid URL
 * - An empty string
 * - The exact string "USE_DISCORD_AVATAR"
 */
@Documented
@Constraint(validatedBy = ValidAvatarUrlValidator.class)
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.ANNOTATION_TYPE, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidAvatarUrl {
    String message() default "Avatar must be a valid URL, empty string, or 'USE_DISCORD_AVATAR'";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
} 