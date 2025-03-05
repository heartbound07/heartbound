package com.app.heartbound.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data transfer object for user profile information.
 * Contains minimal user data needed for UI display.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserProfileDTO {
    private String id;
    private String username;
    private String avatar;
    private String displayName;
    private String pronouns;
    private String about;
    private String bannerColor;
}
