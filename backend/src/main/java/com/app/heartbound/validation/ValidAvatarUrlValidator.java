package com.app.heartbound.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.util.StringUtils;

import java.net.MalformedURLException;
import java.net.URI;
import java.util.regex.Pattern;

/**
 * Validator for {@link ValidAvatarUrl} annotation.
 * Validates that avatar URLs are either:
 * - A valid URL (HTTP/HTTPS)
 * - An empty string or null
 * - The exact string "USE_DISCORD_AVATAR"
 */
public class ValidAvatarUrlValidator implements ConstraintValidator<ValidAvatarUrl, String> {

    // Pattern to match valid URL schemes (http, https)
    private static final Pattern URL_SCHEME_PATTERN = Pattern.compile("^https?://.*", Pattern.CASE_INSENSITIVE);
    
    // Special value that indicates using Discord avatar
    private static final String USE_DISCORD_AVATAR = "USE_DISCORD_AVATAR";

    @Override
    public void initialize(ValidAvatarUrl constraintAnnotation) {
        // No initialization needed
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // Null or empty strings are valid (user wants to use Discord avatar)
        if (!StringUtils.hasText(value)) {
            return true;
        }

        // Special case: "USE_DISCORD_AVATAR" is valid
        if (USE_DISCORD_AVATAR.equals(value)) {
            return true;
        }

        // Check if it's a valid URL with http/https scheme
        if (!URL_SCHEME_PATTERN.matcher(value).matches()) {
            return false;
        }

        // Additional URL validation using modern URI.create().toURL() approach
        // This replaces the deprecated URL(String) constructor
        try {
            URI.create(value).toURL();
            return true;
        } catch (MalformedURLException | IllegalArgumentException e) {
            return false;
        }
    }
} 