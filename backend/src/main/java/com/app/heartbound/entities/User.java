package com.app.heartbound.entities;

import com.app.heartbound.enums.Role;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Enumerated;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.JoinColumn;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashSet;
import java.util.Set;

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
    
    // Add credits field with default value
    private Integer credits = 0;
    
    // Riot account fields
    @Column(unique = true, nullable = true)
    private String riotPuuid;
    
    @Column(nullable = true)
    private String riotGameName;
    
    @Column(nullable = true)
    private String riotTagLine;
    
    // Role-based security addition
    @ElementCollection(fetch = jakarta.persistence.FetchType.EAGER)
    @Enumerated(jakarta.persistence.EnumType.STRING)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role")
    private Set<Role> roles = new HashSet<>();
    
    // Helper methods for role management
    public void addRole(Role role) {
        if (this.roles == null) {
            this.roles = new HashSet<>();
        }
        this.roles.add(role);
    }
    
    public boolean hasRole(Role role) {
        return this.roles != null && this.roles.contains(role);
    }
    
    public void removeRole(Role role) {
        if (this.roles != null) {
            this.roles.remove(role);
        }
    }
}
