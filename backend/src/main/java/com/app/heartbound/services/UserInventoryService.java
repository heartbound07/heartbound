package com.app.heartbound.services;

import com.app.heartbound.dto.shop.UserInventoryItemDTO;
import com.app.heartbound.entities.ItemInstance;
import com.app.heartbound.entities.Shop;
import com.app.heartbound.entities.User;
import com.app.heartbound.enums.ShopCategory;
import com.app.heartbound.exceptions.ResourceNotFoundException;
import com.app.heartbound.repositories.ItemInstanceRepository;
import com.app.heartbound.repositories.UserRepository;
import com.app.heartbound.utils.LevelingUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import com.app.heartbound.enums.ItemRarity;
import com.app.heartbound.enums.FishingRodPart;
import com.app.heartbound.dto.UserProfileDTO;
import com.app.heartbound.exceptions.InvalidOperationException;
import com.app.heartbound.exceptions.shop.InsufficientCreditsException;

@Service
public class UserInventoryService {

    private static final Logger logger = LoggerFactory.getLogger(UserInventoryService.class);
    private final ItemInstanceRepository itemInstanceRepository;
    private final UserRepository userRepository;
    private final UserService userService;

    public UserInventoryService(ItemInstanceRepository itemInstanceRepository, UserRepository userRepository, UserService userService) {
        this.itemInstanceRepository = itemInstanceRepository;
        this.userRepository = userRepository;
        this.userService = userService;
    }

    public int getItemQuantity(String userId, UUID itemId) {
        logger.debug("Checking quantity for userId: {} and itemId: {}", userId, itemId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        return (int) user.getItemInstances().stream()
                .filter(instance -> instance.getBaseItem().getId().equals(itemId))
                .count();
    }

    public List<UserInventoryItemDTO> getUserInventory(String userId) {
        logger.debug("Fetching inventory for user ID: {}", userId);
        User user = userRepository.findByIdWithInventory(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        // Group instances by base item and count them
        Map<Shop, Long> itemCounts = user.getItemInstances().stream()
            .collect(Collectors.groupingBy(ItemInstance::getBaseItem, Collectors.counting()));

        // Convert to DTOs
        List<UserInventoryItemDTO> inventoryDTOs = itemCounts.entrySet().stream()
            .flatMap(entry -> {
                Shop item = entry.getKey();
                List<ItemInstance> instances = user.getItemInstances().stream()
                    .filter(i -> i.getBaseItem().getId().equals(item.getId()))
                    .collect(Collectors.toList());

                if (item.getCategory() == ShopCategory.FISHING_ROD) {
                    // Create a DTO for each unique instance of a fishing rod
                    return instances.stream().map(instance -> {
                        UUID equippedInstanceId = user.getEquippedFishingRodInstanceId();
                        int level = instance.getLevel() != null ? instance.getLevel() : 1;
                        return UserInventoryItemDTO.builder()
                            .itemId(item.getId())
                            .instanceId(instance.getId())
                            .name(item.getName())
                            .description(item.getDescription())
                            .category(item.getCategory())
                            .thumbnailUrl(item.getThumbnailUrl())
                            .imageUrl(item.getImageUrl())
                            .price(item.getPrice())
                            .quantity(1)
                            .rarity(item.getRarity())
                            .discordRoleId(item.getDiscordRoleId())
                            .durability(instance.getDurability())
                            .maxDurability(instance.getMaxDurability() != null ? instance.getMaxDurability() : item.getMaxDurability())
                            .experience(instance.getExperience())
                            .level(level)
                            .xpForNextLevel(LevelingUtil.calculateXpForRodLevel(level))
                            .equipped(instance.getId().equals(equippedInstanceId))
                            .build();
                    });
                } else {
                    // For other items, create a single stacked DTO
                    return List.of(UserInventoryItemDTO.builder()
                        .itemId(item.getId())
                        .name(item.getName())
                        .description(item.getDescription())
                        .category(item.getCategory())
                        .thumbnailUrl(item.getThumbnailUrl())
                        .imageUrl(item.getImageUrl())
                        .price(item.getPrice())
                        .quantity(entry.getValue().intValue())
                        .rarity(item.getRarity())
                        .discordRoleId(item.getDiscordRoleId())
                        .equipped(item.getId().equals(user.getEquippedItemIdByCategory(item.getCategory())))
                        .build()).stream();
                }
            })
            .collect(Collectors.toList());

        logger.debug("Combined inventory DTO size for user {}: {}", userId, inventoryDTOs.size());
        return inventoryDTOs;
    }


    @Transactional
    public void transferItem(String fromUserId, String toUserId, UUID itemInstanceId) {
        logger.debug("Attempting to transfer item instance {} from user {} to user {}", itemInstanceId, fromUserId, toUserId);

        ItemInstance itemInstance = itemInstanceRepository.findByIdWithLock(itemInstanceId)
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

    @Transactional
    public UserProfileDTO equipAndRepairFishingRodPart(String userId, UUID rodInstanceId, UUID partInstanceId) {
        User user = userRepository.findByIdWithLock(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        ItemInstance rodInstance = itemInstanceRepository.findByIdWithLock(rodInstanceId)
                .orElseThrow(() -> new ResourceNotFoundException("Fishing rod instance not found with id: " + rodInstanceId));

        if (!rodInstance.getOwner().getId().equals(userId)) {
            throw new IllegalStateException("User does not own the fishing rod.");
        }
        
        ItemInstance partInstance = user.getItemInstances().stream()
                .filter(instance -> instance.getId().equals(partInstanceId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Fishing rod part not found in user inventory with id: " + partInstanceId));

        if (itemInstanceRepository.isPartAlreadyEquipped(partInstanceId)) {
            throw new InvalidOperationException("This part is already equipped on another fishing rod.");
        }

        Shop rodBaseItem = rodInstance.getBaseItem();
        if (rodBaseItem.getCategory() != ShopCategory.FISHING_ROD) {
            throw new InvalidOperationException("Item is not a fishing rod.");
        }

        Shop partBaseItem = partInstance.getBaseItem();
        if (partBaseItem.getCategory() != ShopCategory.FISHING_ROD_PART) {
            throw new InvalidOperationException("Item is not a fishing rod part.");
        }

        int cost = getPartUpgradeCost(partBaseItem.getRarity());
        if (cost > 0) {
            boolean success = userService.deductCreditsIfSufficient(userId, cost);
            if (!success) {
                throw new InsufficientCreditsException("You do not have enough credits to apply this part. Required: " + cost + " credits.");
            }
        }

        Integer currentMaxDurability = rodInstance.getMaxDurability() != null ? rodInstance.getMaxDurability() : rodBaseItem.getMaxDurability();
        if (currentMaxDurability == null) {
            throw new InvalidOperationException("Rod does not have maximum durability set.");
        }

        boolean isMaxDurabilityIncreasePart =
            (partBaseItem.getRarity() == ItemRarity.EPIC || partBaseItem.getRarity() == ItemRarity.LEGENDARY) &&
            partBaseItem.getDurabilityIncrease() != null &&
            partBaseItem.getDurabilityIncrease() > 0;

        if (isMaxDurabilityIncreasePart) {
            currentMaxDurability += partBaseItem.getDurabilityIncrease();
            rodInstance.setMaxDurability(currentMaxDurability);
        }

        if (rodInstance.getDurability() < currentMaxDurability) {
            double rarityPercentage = getRarityPercentage(partBaseItem.getRarity());
            int durabilityToRestore = (int) Math.round(currentMaxDurability * rarityPercentage);

            int newDurability = Math.min(rodInstance.getDurability() + durabilityToRestore, currentMaxDurability);
            rodInstance.setDurability(newDurability);
        }

        switch (partBaseItem.getFishingRodPartType()) {
            case ROD_SHAFT:
                rodInstance.setEquippedRodShaft(partInstance);
                break;
            case REEL:
                rodInstance.setEquippedReel(partInstance);
                break;
            case FISHING_LINE:
                rodInstance.setEquippedFishingLine(partInstance);
                break;
            case HOOK:
                rodInstance.setEquippedHook(partInstance);
                break;
            case GRIP:
                rodInstance.setEquippedGrip(partInstance);
                break;
        }

        itemInstanceRepository.save(rodInstance);

        return userService.mapToProfileDTO(user);
    }

    private double getRarityPercentage(ItemRarity rarity) {
        switch (rarity) {
            case COMMON:
                return 0.05;
            case UNCOMMON:
                return 0.10;
            case RARE:
                return 0.25;
            case EPIC:
                return 0.50;
            case LEGENDARY:
                return 1.0;
            default:
                return 0.0;
        }
    }

    private int getPartUpgradeCost(ItemRarity rarity) {
        switch (rarity) {
            case COMMON:
                return 60;
            case UNCOMMON:
                return 280;
            case RARE:
                return 1450;
            case EPIC:
                return 6200;
            case LEGENDARY:
                return 30000;
            default:
                return 0;
        }
    }

} 