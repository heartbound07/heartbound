package com.app.heartbound.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.util.StringUtils;

import java.net.MalformedURLException;
import java.net.URI;
import java.util.regex.Pattern;

/**
 * Validator for {@link ValidBannerUrl} annotation.
 * Validates that banner URLs are either:
 * - A valid URL (HTTP/HTTPS)
 * - An empty string or null
 */
public class ValidBannerUrlValidator implements ConstraintValidator<ValidBannerUrl, String> {

    // Pattern to match valid URL schemes (http, https)
    private static final Pattern URL_SCHEME_PATTERN = Pattern.compile("^https?://.*", Pattern.CASE_INSENSITIVE);

    @Override
    public void initialize(ValidBannerUrl constraintAnnotation) {
        // No initialization needed
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // Null or empty strings are valid (no banner)
        if (!StringUtils.hasText(value)) {
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