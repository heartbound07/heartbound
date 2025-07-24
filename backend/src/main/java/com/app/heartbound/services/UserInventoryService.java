package com.app.heartbound.services;

import com.app.heartbound.entities.Shop;
import com.app.heartbound.entities.User;
import com.app.heartbound.entities.UserInventoryItem;
import com.app.heartbound.exceptions.ResourceNotFoundException;
import com.app.heartbound.repositories.UserInventoryItemRepository;
import com.app.heartbound.repositories.UserRepository;
import com.app.heartbound.services.shop.ShopService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Optional;

@Service
public class UserInventoryService {

    private static final Logger logger = LoggerFactory.getLogger(UserInventoryService.class);
    private final UserInventoryItemRepository userInventoryItemRepository;
    private final UserRepository userRepository;
    private final ShopService shopService;

    public UserInventoryService(UserInventoryItemRepository userInventoryItemRepository, UserRepository userRepository, ShopService shopService) {
        this.userInventoryItemRepository = userInventoryItemRepository;
        this.userRepository = userRepository;
        this.shopService = shopService;
    }

    public int getItemQuantity(String userId, UUID itemId) {
        logger.debug("Checking quantity for userId: {} and itemId: {}", userId, itemId);

        // Check the new inventory system first. This handles items that can have a quantity > 1.
        Optional<UserInventoryItem> itemInNewSystem = userInventoryItemRepository.findByUserIdAndItemId(userId, itemId);
        if (itemInNewSystem.isPresent()) {
            int quantity = itemInNewSystem.get().getQuantity();
            logger.debug("Item {} found in new inventory system for user {} with quantity: {}", itemId, userId, quantity);
            return quantity;
        }

        // If not found, check the legacy inventory system. These items always have a quantity of 1.
        logger.debug("Item {} not in new inventory. Checking legacy system for user {}.", itemId, userId);
        User user = userRepository.findByIdWithInventories(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        boolean inLegacyInventory = user.getInventory().stream()
                .anyMatch(shopItem -> shopItem.getId().equals(itemId));

        if (inLegacyInventory) {
            logger.debug("Item {} found in legacy inventory system for user {}. Quantity is 1.", itemId, userId);
            return 1;
        }

        logger.debug("Item {} not found in any inventory system for user {}.", itemId, userId);
        return 0;
    }

    public List<UserInventoryItem> getUserInventory(String userId) {
        logger.debug("Fetching inventory for user ID: {}", userId);
        User user = userRepository.findByIdWithInventories(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
        logger.debug("User found: {}. Eagerly fetched legacy inventory size: {}, new inventory size: {}", user.getUsername(), user.getInventory().size(), user.getInventoryItems().size());


        // Eagerly fetch items from both inventory systems to prevent LazyInitializationException
        List<UserInventoryItem> inventoryItems = userInventoryItemRepository.findByUserWithItems(user);
        logger.debug("Fetched {} items from the new inventory system (UserInventoryItem).", inventoryItems.size());
        
        // Use a map to handle potential duplicates and combine inventories
        Map<UUID, UserInventoryItem> combinedInventory = new HashMap<>();
        for (UserInventoryItem item : inventoryItems) {
            combinedInventory.put(item.getItem().getId(), item);
        }

        // Add items from the legacy inventory system if they are not already present
        logger.debug("Processing {} items from the legacy inventory system (User.inventory).", user.getInventory().size());
        for (Shop shopItem : user.getInventory()) {
            if (!combinedInventory.containsKey(shopItem.getId())) {
                logger.debug("Adding legacy item '{}' ({}) to combined inventory.", shopItem.getName(), shopItem.getId());
                UserInventoryItem legacyItem = UserInventoryItem.builder()
                        .user(user)
                        .item(shopItem)
                        .quantity(1) // Legacy items have a quantity of 1
                        .build();
                combinedInventory.put(shopItem.getId(), legacyItem);
            }
        }

        logger.debug("Combined inventory size for user {}: {}", userId, combinedInventory.size());
        return new ArrayList<>(combinedInventory.values());
    }

    @Transactional
    public void transferItem(String fromUserId, String toUserId, UUID itemId, int quantity) {
        logger.debug("Attempting to transfer {}x item {} from user {} to user {}", quantity, itemId, fromUserId, toUserId);

        User fromUser = userRepository.findByIdWithInventories(fromUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Sender not found with id: " + fromUserId));
        User toUser = userRepository.findById(toUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Receiver not found with id: " + toUserId));
        Shop item = shopService.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Item not found with id: " + itemId));

        // --- Handle Sender's Inventory ---
        Optional<UserInventoryItem> fromItemOptional = userInventoryItemRepository.findByUserIdAndItemId(fromUserId, itemId);

        if (fromItemOptional.isPresent()) {
            // Item is in the new inventory system
            UserInventoryItem fromItem = fromItemOptional.get();
            logger.debug("Found item '{}' ({}) in new inventory for sender {}. Current quantity: {}, Required: {}", item.getName(), itemId, fromUserId, fromItem.getQuantity(), quantity);

            if (fromItem.getQuantity() < quantity) {
                throw new IllegalStateException("Sender does not have enough quantity of '" + item.getName() + "' to transfer.");
            }

            fromItem.removeQuantity(quantity);
            if (fromItem.hasQuantity()) {
                userInventoryItemRepository.save(fromItem);
            } else {
                userInventoryItemRepository.delete(fromItem);
            }
        } else {
            // Item not in new system, check legacy inventory
            logger.debug("Item '{}' ({}) not found in new inventory for sender {}. Checking legacy system.", item.getName(), itemId, fromUserId);
            if (fromUser.getInventory().stream().anyMatch(legacyItem -> legacyItem.getId().equals(itemId))) {
                if (quantity > 1) {
                    throw new IllegalStateException("Attempted to trade more than 1 of legacy item '" + item.getName() + "'.");
                }
                fromUser.getInventory().removeIf(legacyItem -> legacyItem.getId().equals(itemId));
                userRepository.save(fromUser);
                logger.debug("Removed legacy item '{}' ({}) from sender's inventory.", item.getName(), itemId);
            } else {
                throw new IllegalStateException("Sender does not have the item to transfer.");
            }
        }

        // --- Handle Receiver's Inventory (always use new system) ---
        UserInventoryItem toItem = userInventoryItemRepository.findByUserIdAndItemId(toUserId, itemId)
                .orElseGet(() -> UserInventoryItem.builder()
                        .user(toUser)
                        .item(item)
                        .quantity(0)
                        .build());

        toItem.addQuantity(quantity);
        userInventoryItemRepository.save(toItem);

        logger.info("Successfully transferred {}x '{}' from {} to {}", quantity, item.getName(), fromUserId, toUserId);
    }
} 