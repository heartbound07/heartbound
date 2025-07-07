package com.app.heartbound.entities;

import com.app.heartbound.enums.Role;
import com.app.heartbound.enums.ShopCategory;

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
import jakarta.persistence.OneToMany;
import jakarta.persistence.CascadeType;
import jakarta.persistence.EntityListeners;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.EqualsAndHashCode;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.LinkedHashSet;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "users")
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"inventoryItems"})
@EntityListeners(UserEntityListener.class)
public class User {
    
    // Remove the MAX_BADGES constant as it's no longer needed for single badge
    // private static final int MAX_BADGES = 5; // Deprecated - now using single badge
    
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
    
    // Add message count field to track total messages sent by user
    private Long messageCount = 0L;
    
    // Add time-based message count fields
    private Integer messagesToday = 0;
    private Integer messagesThisWeek = 0;
    private Integer messagesThisTwoWeeks = 0;
    
    // Add timestamp fields for tracking when to reset counters
    private LocalDateTime lastDailyReset;
    private LocalDateTime lastWeeklyReset;
    private LocalDateTime lastBiWeeklyReset;
    
    // Add voice activity tracking fields
    private Integer voiceTimeMinutesTotal = 0;
    private Integer voiceTimeMinutesToday = 0;
    private Integer voiceTimeMinutesThisWeek = 0;
    private Integer voiceTimeMinutesThisTwoWeeks = 0;
    private Integer voiceRank;
    
    // Add voice activity timestamp fields for tracking when to reset counters
    private LocalDateTime lastVoiceDailyReset;
    private LocalDateTime lastVoiceWeeklyReset;
    private LocalDateTime lastVoiceBiWeeklyReset;
    
    // Daily claim system fields
    private Integer dailyStreak = 0;
    private LocalDateTime lastDailyClaim;
    
    // User active status field
    private Boolean active = true;
    
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
    
    // Add new inventory relationship with quantity support
    @OneToMany(mappedBy = "user", fetch = jakarta.persistence.FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<UserInventoryItem> inventoryItems = new HashSet<>();
    
    // Add these fields to store equipped item IDs
    @Column(name = "equipped_user_color_id")
    private UUID equippedUserColorId;

    @Column(name = "equipped_listing_id")
    private UUID equippedListingId;

    @Column(name = "equipped_accent_id")
    private UUID equippedAccentId;
    
    // Updated field to store single equipped badge ID instead of multiple
    @Column(name = "equipped_badge_id")
    private UUID equippedBadgeId;
    
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
    
    // Helper methods for new quantity-based inventory management
    public void addItemWithQuantity(Shop item, int quantity) {
        if (this.inventoryItems == null) {
            this.inventoryItems = new HashSet<>();
        }
        
        // Check if the item already exists in the new inventory
        UserInventoryItem existingItem = this.inventoryItems.stream()
            .filter(invItem -> invItem.getItem().getId().equals(item.getId()))
            .findFirst()
            .orElse(null);
        
        if (existingItem != null) {
            // Item exists, add to quantity
            existingItem.addQuantity(quantity);
        } else {
            // Create new inventory item with proper bidirectional relationship
            UserInventoryItem newInventoryItem = new UserInventoryItem();
            newInventoryItem.setUser(this);
            newInventoryItem.setItem(item);
            newInventoryItem.setQuantity(quantity);
            
            // Add to the collection
            this.inventoryItems.add(newInventoryItem);
        }
        
        // Also add to old inventory for backwards compatibility
        this.addItem(item);
    }
    
    public boolean hasItemWithQuantity(UUID itemId) {
        if (this.inventoryItems == null) {
            return false;
        }
        
        return this.inventoryItems.stream()
            .anyMatch(invItem -> 
                invItem.getItem().getId().equals(itemId) && 
                invItem.hasQuantity()
            );
    }
    
    public int getItemQuantity(UUID itemId) {
        if (this.inventoryItems == null) {
            return 0;
        }
        
        return this.inventoryItems.stream()
            .filter(invItem -> invItem.getItem().getId().equals(itemId))
            .mapToInt(UserInventoryItem::getQuantity)
            .findFirst()
            .orElse(0);
    }
    
    public boolean removeItemQuantity(UUID itemId, int quantity) {
        if (this.inventoryItems == null) {
            return false;
        }
        
        UserInventoryItem inventoryItem = this.inventoryItems.stream()
            .filter(invItem -> invItem.getItem().getId().equals(itemId))
            .findFirst()
            .orElse(null);
        
        if (inventoryItem == null) {
            return false;
        }
        
        boolean stillHasQuantity = inventoryItem.removeQuantity(quantity);
        
        if (!stillHasQuantity) {
            // Remove from new inventory if no quantity left
            this.inventoryItems.remove(inventoryItem);
            
            // Remove from old inventory for backwards compatibility
            this.inventory.removeIf(item -> item.getId().equals(itemId));
        }
        
        return true;
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

    // Helper methods for badge management - updated for single badge
    public void setEquippedBadge(UUID badgeId) {
        this.equippedBadgeId = badgeId;
    }

    public void removeEquippedBadge() {
        this.equippedBadgeId = null;
    }

    public UUID getEquippedBadgeId() {
        return equippedBadgeId;
    }

    public boolean isBadgeEquipped(UUID badgeId) {
        return this.equippedBadgeId != null && this.equippedBadgeId.equals(badgeId);
    }

    public boolean hasEquippedBadge() {
        return this.equippedBadgeId != null;
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
                return getEquippedBadgeId();
            case CASE:
                throw new UnsupportedOperationException("CASE category items cannot be equipped. Cases are purchased and stored in inventory.");
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
                setEquippedBadge(itemId);
                break;
            case CASE:
                throw new UnsupportedOperationException("CASE category items cannot be equipped. Cases are purchased and stored in inventory.");
            default:
                break;
        }
    }
}
