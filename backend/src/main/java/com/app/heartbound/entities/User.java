package com.app.heartbound.entities;

import com.app.heartbound.enums.Role;
import com.app.heartbound.enums.ShopCategory;
import com.app.heartbound.exceptions.shop.BadgeLimitException;
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
import java.util.LinkedHashSet;

@Data
@Entity
@Table(name = "users")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {
    
    private static final int MAX_BADGES = 5; // You can change this number as needed
    
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
    
    // Add these fields to store equipped item IDs
    @Column(name = "equipped_user_color_id")
    private UUID equippedUserColorId;

    @Column(name = "equipped_listing_id")
    private UUID equippedListingId;

    @Column(name = "equipped_accent_id")
    private UUID equippedAccentId;
    
    // Add these fields to store multiple equipped badge IDs
    @ElementCollection(fetch = jakarta.persistence.FetchType.EAGER)
    @CollectionTable(
        name = "user_equipped_badges", 
        joinColumns = @JoinColumn(name = "user_id")
    )
    @Column(name = "badge_id")
    private Set<UUID> equippedBadgeIds = new LinkedHashSet<>();
    
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

    // Add getter/setter methods for the new fields
    public UUID getEquippedUserColorId() {
        return equippedUserColorId;
    }

    public void setEquippedUserColorId(UUID equippedUserColorId) {
        this.equippedUserColorId = equippedUserColorId;
    }

    public UUID getEquippedListingId() {
        return equippedListingId;
    }

    public void setEquippedListingId(UUID equippedListingId) {
        this.equippedListingId = equippedListingId;
    }

    public UUID getEquippedAccentId() {
        return equippedAccentId;
    }

    public void setEquippedAccentId(UUID equippedAccentId) {
        this.equippedAccentId = equippedAccentId;
    }

    // Helper methods for badge management
    public void addEquippedBadge(UUID badgeId) {
        if (this.equippedBadgeIds == null) {
            this.equippedBadgeIds = new HashSet<>();
        }
        // Add configurable badge limit (e.g., from application properties)
        if (this.equippedBadgeIds.size() >= MAX_BADGES) {
            throw new BadgeLimitException("Maximum number of badges (" + MAX_BADGES + ") already equipped");
        }
        this.equippedBadgeIds.add(badgeId);
    }

    public void removeEquippedBadge(UUID badgeId) {
        if (this.equippedBadgeIds != null) {
            this.equippedBadgeIds.remove(badgeId);
        }
    }

    public Set<UUID> getEquippedBadgeIds() {
        return equippedBadgeIds != null ? equippedBadgeIds : new HashSet<>();
    }

    public boolean isBadgeEquipped(UUID badgeId) {
        return this.equippedBadgeIds != null && this.equippedBadgeIds.contains(badgeId);
    }

    // Helper method to get equipped item ID by category
    public UUID getEquippedItemIdByCategory(ShopCategory category) {
        switch (category) {
            case USER_COLOR:
                return getEquippedUserColorId();
            case LISTING:
                return getEquippedListingId();
            case ACCENT:
                return getEquippedAccentId();
            case BADGE:
                throw new UnsupportedOperationException("BADGE category supports multiple equipped items. Use getEquippedBadgeIds() instead.");
            default:
                return null;
        }
    }

    // Helper method to set equipped item ID by category
    public void setEquippedItemIdByCategory(ShopCategory category, UUID itemId) {
        switch (category) {
            case USER_COLOR:
                setEquippedUserColorId(itemId);
                break;
            case LISTING:
                setEquippedListingId(itemId);
                break;
            case ACCENT:
                setEquippedAccentId(itemId);
                break;
            case BADGE:
                throw new UnsupportedOperationException("BADGE category supports multiple equipped items. Use addEquippedBadge() or removeEquippedBadge() instead.");
            default:
                break;
        }
    }
}
