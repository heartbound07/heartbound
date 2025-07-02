package com.app.heartbound.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * UserInventoryItem Entity
 * 
 * Represents an item in a user's inventory with quantity support.
 * This entity supports multiple instances of the same item (like cases)
 * while maintaining the existing shop system compatibility.
 */
@Entity
@Table(name = "user_inventory_items",
       indexes = {
           @Index(name = "idx_user_inventory_user_id", columnList = "user_id"),
           @Index(name = "idx_user_inventory_item_id", columnList = "item_id"),
           @Index(name = "idx_user_inventory_user_item", columnList = "user_id, item_id")
       },
       uniqueConstraints = {
           @UniqueConstraint(name = "uk_user_inventory_user_item", columnNames = {"user_id", "item_id"})
       })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(exclude = {"user"})
public class UserInventoryItem {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    /**
     * The user who owns this inventory item
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @NotNull
    private User user;
    
    /**
     * The shop item in the inventory
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    @NotNull
    private Shop item;
    
    /**
     * Quantity of this item owned by the user
     * For cases, this represents how many cases the user has
     * For other items, this is typically 1 (owned) or 0 (not owned)
     */
    @NotNull
    @Min(0)
    @Column(name = "quantity", nullable = false)
    private Integer quantity = 1;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    /**
     * Increases the quantity of this inventory item
     * @param amount Amount to add
     */
    public void addQuantity(int amount) {
        this.quantity = this.quantity + amount;
    }
    
    /**
     * Decreases the quantity of this inventory item
     * @param amount Amount to subtract
     * @return true if the item still has quantity > 0, false if it should be removed
     */
    public boolean removeQuantity(int amount) {
        this.quantity = Math.max(0, this.quantity - amount);
        return this.quantity > 0;
    }
    
    /**
     * Checks if this inventory item has any quantity
     * @return true if quantity > 0
     */
    public boolean hasQuantity() {
        return this.quantity > 0;
    }
} 