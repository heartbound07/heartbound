package com.app.heartbound.validation;

import com.app.heartbound.dto.discord.DiscordBotSettingsDTO;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class FishingLimitValidator implements ConstraintValidator<ValidFishingLimit, DiscordBotSettingsDTO> {

    @Override
    public void initialize(ValidFishingLimit constraintAnnotation) {
    }

    @Override
    public boolean isValid(DiscordBotSettingsDTO dto, ConstraintValidatorContext context) {
        if (dto.getFishingMinCatches() == null || dto.getFishingMaxCatches() == null) {
            return true; // Let @NotNull handle this
        }
        return dto.getFishingMinCatches() <= dto.getFishingMaxCatches();
    }
} 