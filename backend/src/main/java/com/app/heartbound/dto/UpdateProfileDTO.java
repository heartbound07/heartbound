package com.app.heartbound.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
    private String displayName;
    
    @Schema(description = "The user's pronouns", example = "they/them")
    private String pronouns;
    
    @Schema(description = "About me section text", example = "I love gaming and coding!")
    private String about;
    
    @Schema(description = "The banner color selection", example = "bg-blue-600")
    private String bannerColor;
    
    @Schema(description = "The avatar image URL", example = "https://cloudinary.com/image.jpg")
    private String avatar;
}
