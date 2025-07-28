package com.app.heartbound.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Constraint(validatedBy = FishingLimitValidator.class)
@Target({ ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidFishingLimit {
    String message() default "Minimum catches cannot be greater than maximum catches.";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
} 