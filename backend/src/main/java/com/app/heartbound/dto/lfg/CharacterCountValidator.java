package com.app.heartbound.dto.lfg;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class CharacterCountValidator implements ConstraintValidator<CharacterCount, String> {
    
    private int min;
    private int max;
    
    @Override
    public void initialize(CharacterCount constraintAnnotation) {
        this.min = constraintAnnotation.min();
        this.max = constraintAnnotation.max();
    }
    
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // Allow null values (this should be combined with @NotNull if nulls are not allowed)
        if (value == null) {
            return true;
        }
        
        // Count characters in the string
        int charCount = value.length();
        
        return charCount >= min && charCount <= max;
    }
}
