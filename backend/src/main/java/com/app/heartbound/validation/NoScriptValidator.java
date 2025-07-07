package com.app.heartbound.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

/**
 * Validator for {@link NoScript} annotation.
 * 
 * This validator strictly checks that content contains no HTML tags or script-like patterns.
 * It's designed for fields that should never contain any markup or potentially dangerous content.
 */
public class NoScriptValidator implements ConstraintValidator<NoScript, String> {
    
    // Pattern to detect HTML tags
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]*>");
    
    // Pattern to detect dangerous script-like content
    private static final Pattern SCRIPT_PATTERN = Pattern.compile(
        "(?i)(javascript:|data:|vbscript:|on\\w+\\s*=|<\\s*script|<\\s*iframe|<\\s*object|<\\s*embed|<\\s*link|<\\s*meta|<\\s*style)"
    );
    
    // Pattern for alphanumeric + spaces only
    private static final Pattern ALPHANUMERIC_ONLY = Pattern.compile("^[a-zA-Z0-9\\s]*$");
    
    // Pattern for alphanumeric + basic punctuation
    private static final Pattern ALPHANUMERIC_WITH_PUNCTUATION = Pattern.compile("^[a-zA-Z0-9\\s\\-_.,'!?():/]*$");
    
    private boolean allowPunctuation;
    
    @Override
    public void initialize(NoScript constraintAnnotation) {
        this.allowPunctuation = constraintAnnotation.allowPunctuation();
    }
    
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // Null or empty values are considered valid
        if (!StringUtils.hasText(value)) {
            return true;
        }
        
        // Check for HTML tags
        if (HTML_TAG_PATTERN.matcher(value).find()) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                "Content must not contain HTML tags")
                .addConstraintViolation();
            return false;
        }
        
        // Check for script-like patterns
        if (SCRIPT_PATTERN.matcher(value).find()) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                "Content must not contain script or dangerous patterns")
                .addConstraintViolation();
            return false;
        }
        
        // Check character restrictions
        Pattern allowedPattern = allowPunctuation ? ALPHANUMERIC_WITH_PUNCTUATION : ALPHANUMERIC_ONLY;
        if (!allowedPattern.matcher(value).matches()) {
            String allowedChars = allowPunctuation ? 
                "letters, numbers, spaces, and basic punctuation (- _ . , ' ! ? ( ) : /)" :
                "letters, numbers, and spaces";
            
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                "Content must contain only " + allowedChars)
                .addConstraintViolation();
            return false;
        }
        
        return true;
    }
} 