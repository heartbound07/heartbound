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
import jakarta.persistence.ManyToMany;
import jakarta.persistence.JoinTable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

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
    
    // Add level and experience fields with default values
    private Integer level = 1;
    private Integer experience = 0;
    
    // Role-based security addition
    @ElementCollection(fetch = jakarta.persistence.FetchType.EAGER)
    @Enumerated(jakarta.persistence.EnumType.STRING)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role")
    private Set<Role> roles = new HashSet<>();
    
    // Add inventory relationship
    @ManyToMany(fetch = jakarta.persistence.FetchType.LAZY)
    @JoinTable(
        name = "user_inventory",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "item_id")
    )
    private Set<Shop> inventory = new HashSet<>();
    
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
    
    // Helper methods for inventory management
    public void addItem(Shop item) {
        if (this.inventory == null) {
            this.inventory = new HashSet<>();
        }
        this.inventory.add(item);
    }
    
    public boolean hasItem(UUID itemId) {
        return this.inventory != null && 
               this.inventory.stream()
                   .anyMatch(item -> item.getId().equals(itemId));
    }
}
