package com.app.heartbound.dto;

import com.app.heartbound.enums.Role;
import lombok.Builder;
import lombok.Data;

import java.util.Set;

@Data
@Builder
public class PublicUserProfileDTO {
    private String id;
    private String username;
    private String avatar;
    private String displayName;
    private String pronouns;
    private String about;
    private String bannerColor;
    private String bannerUrl;
    private Set<Role> roles;
    private String badgeUrl;
    private String badgeName;
    private String nameplateColor;
} 