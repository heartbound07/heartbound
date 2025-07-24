package com.app.heartbound.migrations;

import com.app.heartbound.entities.ItemInstance;
import com.app.heartbound.entities.Shop;
import com.app.heartbound.entities.User;
import com.app.heartbound.entities.UserInventoryItem;
import com.app.heartbound.repositories.ItemInstanceRepository;
import com.app.heartbound.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Component
@Slf4j
@RequiredArgsConstructor
public class DataMigrationRunner implements ApplicationRunner {

    private final ItemInstanceRepository itemInstanceRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        log.info("Checking if inventory migration to ItemInstance is needed...");

        // Guard clause: Only run if the new item_instances table is empty.
        if (itemInstanceRepository.count() > 0) {
            log.info("Migration to ItemInstance already completed. Skipping.");
            return;
        }

        log.info("Starting data migration from legacy inventory systems to ItemInstance...");
        List<User> users = userRepository.findAll();
        int totalUsers = users.size();
        int migratedInstances = 0;
        int processedUsers = 0;

        for (User user : users) {
            Set<UUID> migratedItemIds = new HashSet<>();
            List<ItemInstance> newInstances = new ArrayList<>();

            // 1. Migrate from the 'inventoryItems' (quantity-based) system
            for (UserInventoryItem inventoryItem : user.getInventoryItems()) {
                for (int i = 0; i < inventoryItem.getQuantity(); i++) {
                    ItemInstance instance = ItemInstance.builder()
                            .owner(user)
                            .baseItem(inventoryItem.getItem())
                            .serialNumber(null) // Legacy items have no serial number
                            .build();
                    newInstances.add(instance);
                }
                migratedItemIds.add(inventoryItem.getItem().getId());
            }

            // 2. Migrate from the oldest 'inventory' system
            for (Shop legacyItem : user.getInventory()) {
                // Only migrate if we haven't already handled this item from the newer inventory system
                if (!migratedItemIds.contains(legacyItem.getId())) {
                    ItemInstance instance = ItemInstance.builder()
                            .owner(user)
                            .baseItem(legacyItem)
                            .serialNumber(null)
                            .build();
                    newInstances.add(instance);
                }
            }
            
            if (!newInstances.isEmpty()) {
                itemInstanceRepository.saveAll(newInstances);
                migratedInstances += newInstances.size();
            }
            processedUsers++;
            log.debug("Migrated inventory for user {}/{}: {}", processedUsers, totalUsers, user.getId());
        }

        log.info("Successfully completed inventory migration. Migrated {} item instances for {} users.", migratedInstances, totalUsers);
    }
} 