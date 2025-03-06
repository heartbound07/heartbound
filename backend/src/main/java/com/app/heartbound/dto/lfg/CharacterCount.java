package com.app.heartbound.dto.lfg;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = CharacterCountValidator.class)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface CharacterCount {
    String message() default "Character count must be between {min} and {max}";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
    
    int min() default 0;
    int max() default Integer.MAX_VALUE;
}
