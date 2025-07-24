package com.app.heartbound.services;

import com.app.heartbound.entities.Shop;
import com.app.heartbound.entities.User;
import com.app.heartbound.entities.UserInventoryItem;
import com.app.heartbound.entities.ItemInstance;
import com.app.heartbound.exceptions.ResourceNotFoundException;
import com.app.heartbound.repositories.UserInventoryItemRepository;
import com.app.heartbound.repositories.UserRepository;
import com.app.heartbound.repositories.ItemInstanceRepository;
import com.app.heartbound.services.shop.ShopService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class UserInventoryService {

    private static final Logger logger = LoggerFactory.getLogger(UserInventoryService.class);
    private final ItemInstanceRepository itemInstanceRepository;
    private final UserRepository userRepository;

    public UserInventoryService(UserInventoryItemRepository userInventoryItemRepository, ItemInstanceRepository itemInstanceRepository, UserRepository userRepository, ShopService shopService) {
        this.itemInstanceRepository = itemInstanceRepository;
        this.userRepository = userRepository;
    }

    public int getItemQuantity(String userId, UUID itemId) {
        logger.debug("Checking quantity for userId: {} and itemId: {}", userId, itemId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        return (int) user.getItemInstances().stream()
                .filter(instance -> instance.getBaseItem().getId().equals(itemId))
                .count();
    }

    public List<UserInventoryItem> getUserInventory(String userId) {
        logger.debug("Fetching inventory for user ID: {}", userId);
        User user = userRepository.findByIdWithInventories(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
    
        // Group instances by base item and count them
        Map<Shop, Long> itemCounts = user.getItemInstances().stream()
            .collect(Collectors.groupingBy(ItemInstance::getBaseItem, Collectors.counting()));
    
        // Convert to the old UserInventoryItem structure for compatibility
        List<UserInventoryItem> inventoryItems = itemCounts.entrySet().stream()
            .map(entry -> UserInventoryItem.builder()
                .user(user)
                .item(entry.getKey())
                .quantity(entry.getValue().intValue())
                .build())
            .collect(Collectors.toList());
    
        logger.debug("Combined inventory size for user {}: {}", userId, inventoryItems.size());
        return inventoryItems;
    }


    @Transactional
    public void transferItem(String fromUserId, String toUserId, UUID itemInstanceId) {
        logger.debug("Attempting to transfer item instance {} from user {} to user {}", itemInstanceId, fromUserId, toUserId);

        ItemInstance itemInstance = itemInstanceRepository.findById(itemInstanceId)
                .orElseThrow(() -> new ResourceNotFoundException("Item instance not found with id: " + itemInstanceId));

        User toUser = userRepository.findById(toUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Receiver not found with id: " + toUserId));

        if (!itemInstance.getOwner().getId().equals(fromUserId)) {
            throw new IllegalStateException("Sender does not own this item instance.");
        }

        itemInstance.setOwner(toUser);
        itemInstanceRepository.save(itemInstance);

        logger.info("Successfully transferred item instance '{}' from {} to {}", itemInstance.getBaseItem().getName(), fromUserId, toUserId);
    }
} 