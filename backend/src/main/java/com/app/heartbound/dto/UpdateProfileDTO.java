package com.app.heartbound.dto;

import com.app.heartbound.validation.ValidAvatarUrl;
import com.app.heartbound.validation.ValidBannerUrl;
import com.app.heartbound.validation.SanitizedHtml;
import com.app.heartbound.validation.NoScript;
import com.app.heartbound.services.HtmlSanitizationService;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Data Transfer Object for profile updates")
public class UpdateProfileDTO {

    @Schema(description = "The display name for the user", example = "John Doe")
    @Size(max = 50, message = "Display name cannot exceed 50 characters")
    @SanitizedHtml(
        policy = HtmlSanitizationService.SanitizationPolicy.STRICT,
        maxLength = 50,
        failOnSanitization = false
    )
    @NoScript(allowPunctuation = true)
    private String displayName;
    
    @Schema(description = "The user's pronouns", example = "they/them")
    @Size(max = 20, message = "Pronouns cannot exceed 20 characters")
    @SanitizedHtml(
        policy = HtmlSanitizationService.SanitizationPolicy.STRICT,
        maxLength = 20,
        failOnSanitization = false
    )
    @NoScript(allowPunctuation = true)
    private String pronouns;
    
    @Schema(description = "About me section text", example = "I love gaming and coding!")
    @Size(max = 200, message = "About section cannot exceed 200 characters")
    @SanitizedHtml(
        policy = HtmlSanitizationService.SanitizationPolicy.BASIC,
        maxLength = 200,
        failOnSanitization = false
    )
    private String about;
    
    @Schema(description = "The banner color selection", example = "bg-blue-600")
    @Pattern(regexp = "^$|^(#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{8})|bg-[a-z]+-[0-9]+(/[0-9]+)?|bg-white/[0-9]+)$", 
             message = "Banner color must be empty, a valid hex color (e.g., #RRGGBB, #RRGGBBAA) or a valid Tailwind CSS class (e.g., bg-blue-600, bg-white/10)")
    private String bannerColor;
    
    @Schema(description = "The banner image URL", example = "https://cloudinary.com/banner.jpg")
    @ValidBannerUrl
    private String bannerUrl;
}
