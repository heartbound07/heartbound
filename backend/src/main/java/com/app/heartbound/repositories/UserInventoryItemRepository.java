package com.app.heartbound.repositories;

import com.app.heartbound.entities.UserInventoryItem;
import com.app.heartbound.entities.User;
import com.app.heartbound.entities.Shop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserInventoryItemRepository extends JpaRepository<UserInventoryItem, UUID> {
    
    /**
     * Find all inventory items for a specific user
     */
    List<UserInventoryItem> findByUser(User user);
    
    /**
     * Find all inventory items for a specific user with quantity > 0
     */
    @Query("SELECT ui FROM UserInventoryItem ui WHERE ui.user = :user AND ui.quantity > 0")
    List<UserInventoryItem> findByUserWithQuantity(@Param("user") User user);
    
    /**
     * Find a specific inventory item by user and shop item
     */
    Optional<UserInventoryItem> findByUserAndItem(User user, Shop item);
    
    /**
     * Find inventory items by user ID
     */
    @Query("SELECT ui FROM UserInventoryItem ui WHERE ui.user.id = :userId AND ui.quantity > 0")
    List<UserInventoryItem> findByUserIdWithQuantity(@Param("userId") String userId);
    
    /**
     * Check if a user owns a specific item (quantity > 0)
     */
    @Query("SELECT COUNT(ui) > 0 FROM UserInventoryItem ui WHERE ui.user.id = :userId AND ui.item.id = :itemId AND ui.quantity > 0")
    boolean existsByUserIdAndItemIdWithQuantity(@Param("userId") String userId, @Param("itemId") UUID itemId);
    
    /**
     * Get the quantity of a specific item for a user
     */
    @Query("SELECT COALESCE(ui.quantity, 0) FROM UserInventoryItem ui WHERE ui.user.id = :userId AND ui.item.id = :itemId")
    Integer getQuantityByUserIdAndItemId(@Param("userId") String userId, @Param("itemId") UUID itemId);
    
    /**
     * Remove inventory items with zero quantity
     */
    @Modifying
    @Query("DELETE FROM UserInventoryItem ui WHERE ui.quantity <= 0")
    void removeZeroQuantityItems();
    
    /**
     * Remove all inventory items for a specific shop item (when item is deleted)
     */
    @Modifying
    @Query("DELETE FROM UserInventoryItem ui WHERE ui.item = :item")
    void deleteByItem(@Param("item") Shop item);
    
    /**
     * Atomically decrements the quantity of a specific item for a user if enough quantity exists.
     * This prevents race conditions in concurrent case opening by ensuring the check and decrement 
     * operation is performed atomically at the database level.
     * 
     * @param userId the ID of the user whose inventory to modify
     * @param itemId the ID of the item to decrement
     * @param quantity the quantity to decrement (must be > 0)
     * @return the number of rows updated (1 if successful, 0 if insufficient quantity or item not found)
     */
    @Modifying
    @Query("UPDATE UserInventoryItem ui " +
           "SET ui.quantity = ui.quantity - :quantity " +
           "WHERE ui.user.id = :userId AND ui.item.id = :itemId AND ui.quantity >= :quantity")
    int decrementQuantityIfEnough(@Param("userId") String userId, @Param("itemId") UUID itemId, @Param("quantity") int quantity);
    
    /**
     * Atomically remove UserInventoryItem entries that have zero or negative quantity.
     * Should be called after decrementQuantityIfEnough to clean up consumed items.
     * 
     * @param userId the ID of the user whose inventory to clean
     * @param itemId the ID of the item to clean if quantity is zero
     * @return the number of rows deleted
     */
    @Modifying
    @Query("DELETE FROM UserInventoryItem ui WHERE ui.user.id = :userId AND ui.item.id = :itemId AND ui.quantity <= 0")
    int removeZeroQuantityItem(@Param("userId") String userId, @Param("itemId") UUID itemId);
    
    /**
     * Deletes all inventory items for a given user.
     * This is crucial for ensuring data integrity when a user is deleted.
     *
     * @param user The user whose inventory items are to be deleted.
     */
    void deleteByUser(User user);
} 