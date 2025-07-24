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

import java.util.UUID;

@Service
public class UserInventoryService {

    private final UserInventoryItemRepository userInventoryItemRepository;
    private final UserRepository userRepository;
    private final ShopService shopService;

    public UserInventoryService(UserInventoryItemRepository userInventoryItemRepository, UserRepository userRepository, ShopService shopService) {
        this.userInventoryItemRepository = userInventoryItemRepository;
        this.userRepository = userRepository;
        this.shopService = shopService;
    }

    public int getItemQuantity(String userId, UUID itemId) {
        return userInventoryItemRepository.findByUserIdAndItemId(userId, itemId)
                .map(UserInventoryItem::getQuantity)
                .orElse(0);
    }

    @Transactional
    public void transferItem(String fromUserId, String toUserId, UUID itemId, int quantity) {
        userRepository.findById(fromUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Sender not found"));
        User toUser = userRepository.findById(toUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Receiver not found"));
        Shop item = shopService.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Item not found"));

        UserInventoryItem fromItem = userInventoryItemRepository.findByUserIdAndItemId(fromUserId, itemId)
                .orElseThrow(() -> new IllegalStateException("Sender does not have the item to transfer."));

        if (fromItem.getQuantity() < quantity) {
            throw new IllegalStateException("Sender does not have enough quantity to transfer.");
        }

        fromItem.setQuantity(fromItem.getQuantity() - quantity);
        if (fromItem.getQuantity() == 0) {
            userInventoryItemRepository.delete(fromItem);
        } else {
            userInventoryItemRepository.save(fromItem);
        }

        UserInventoryItem toItem = userInventoryItemRepository.findByUserIdAndItemId(toUserId, itemId)
                .orElseGet(() -> {
                    UserInventoryItem newItem = UserInventoryItem.builder()
                            .user(toUser)
                            .item(item)
                            .quantity(0)
                            .build();
                    return userInventoryItemRepository.save(newItem);
                });

        toItem.setQuantity(toItem.getQuantity() + quantity);
        userInventoryItemRepository.save(toItem);
    }
} 