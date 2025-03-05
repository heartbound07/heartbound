package com.app.heartbound.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Entity
@Table(name = "users")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {
    
    @Id
    private String id; // External (OAuth) user id

    private String username;
    private String discriminator;
    private String avatar;
    private String email;
    
    // Add this field to cache the Discord avatar URL
    private String discordAvatarUrl;
    
    // Profile Update Fields
    
    private String displayName;
    private String pronouns;
    private String about;
    private String bannerColor;
    private String bannerUrl;
}
