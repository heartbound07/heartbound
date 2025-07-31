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
import com.app.heartbound.dto.UserProfileDTO;
import com.app.heartbound.exceptions.InvalidOperationException;
import com.app.heartbound.exceptions.shop.InsufficientCreditsException;

import java.util.ArrayList;

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

    private int getBaseRepairCost(ItemRarity rarity) {
        switch (rarity) {
            case COMMON: return 100;
            case UNCOMMON: return 300;
            case RARE: return 800;
            case EPIC: return 2500;
            case LEGENDARY: return 7000;
            default: return 0;
        }
    }

    private int getPartSurcharge(ItemRarity rarity) {
        switch (rarity) {
            case COMMON: return 20;
            case UNCOMMON: return 60;
            case RARE: return 200;
            case EPIC: return 800;
            case LEGENDARY: return 3000;
            default: return 0;
        }
    }

    private List<ItemInstance> getEquippedParts(ItemInstance rodInstance) {
        List<ItemInstance> parts = new ArrayList<>();
        if (rodInstance.getEquippedRodShaft() != null) parts.add(rodInstance.getEquippedRodShaft());
        if (rodInstance.getEquippedReel() != null) parts.add(rodInstance.getEquippedReel());
        if (rodInstance.getEquippedFishingLine() != null) parts.add(rodInstance.getEquippedFishingLine());
        if (rodInstance.getEquippedHook() != null) parts.add(rodInstance.getEquippedHook());
        if (rodInstance.getEquippedGrip() != null) parts.add(rodInstance.getEquippedGrip());
        return parts;
    }

    @Transactional(readOnly = true)
    public int getRepairCost(String userId, UUID rodInstanceId) {
        ItemInstance rodInstance = itemInstanceRepository.findById(rodInstanceId)
                .orElseThrow(() -> new ResourceNotFoundException("Fishing rod instance not found with id: " + rodInstanceId));

        if (!rodInstance.getOwner().getId().equals(userId)) {
            throw new ResourceNotFoundException("User does not own the fishing rod.");
        }

        Shop rodBaseItem = rodInstance.getBaseItem();
        int baseCost = getBaseRepairCost(rodBaseItem.getRarity());

        int partsSurcharge = 0;
        for (ItemInstance partInstance : getEquippedParts(rodInstance)) {
            partsSurcharge += getPartSurcharge(partInstance.getBaseItem().getRarity());
        }

        int rodLevel = rodInstance.getLevel() != null ? rodInstance.getLevel() : 1;
        double levelMultiplier = 1 + (rodLevel * 0.10);

        double finalCost = (baseCost + partsSurcharge) * levelMultiplier;

        return (int) Math.round(finalCost);
    }

    @Transactional
    public UserProfileDTO repairFishingRod(String userId, UUID rodInstanceId) {
        User user = userRepository.findByIdWithLock(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        ItemInstance rodInstance = itemInstanceRepository.findByIdWithLock(rodInstanceId)
                .orElseThrow(() -> new ResourceNotFoundException("Fishing rod instance not found with id: " + rodInstanceId));

        if (!rodInstance.getOwner().getId().equals(userId)) {
            throw new ResourceNotFoundException("User does not own the fishing rod.");
        }

        if (rodInstance.getDurability() == null || rodInstance.getDurability() > 0) {
            throw new InvalidOperationException("This fishing rod does not need repairs.");
        }

        int cost = getRepairCost(userId, rodInstanceId);

        boolean success = userService.deductCreditsIfSufficient(user, cost);
        if (!success) {
            throw new InsufficientCreditsException("You do not have enough credits to repair this rod. Required: " + cost + " credits.");
        }

        Integer maxDurability = rodInstance.getMaxDurability() != null ? rodInstance.getMaxDurability() : rodInstance.getBaseItem().getMaxDurability();
        if (maxDurability == null) {
            throw new InvalidOperationException("Rod does not have maximum durability set.");
        }

        rodInstance.setDurability(maxDurability);
        itemInstanceRepository.save(rodInstance);

        logger.info("User {} successfully repaired rod {} for {} credits.", userId, rodInstanceId, cost);

        return userService.mapToProfileDTO(user);
    }

    @Transactional(readOnly = true)
    public int getPartRepairCost(UUID partInstanceId) {
        ItemInstance partInstance = itemInstanceRepository.findById(partInstanceId)
                .orElseThrow(() -> new ResourceNotFoundException("Fishing rod part instance not found with id: " + partInstanceId));
        
        Shop partBaseItem = partInstance.getBaseItem();
        if (partBaseItem.getCategory() != ShopCategory.FISHING_ROD_PART) {
            throw new InvalidOperationException("Item is not a fishing rod part.");
        }

        // Using getPartUpgradeCost as a reference for cost structure
        return getPartUpgradeCost(partBaseItem.getRarity());
    }

    @Transactional
    public UserProfileDTO repairFishingRodPart(String userId, UUID partInstanceId) {
        User user = userRepository.findByIdWithLock(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        ItemInstance partInstance = itemInstanceRepository.findByIdWithLock(partInstanceId)
                .orElseThrow(() -> new ResourceNotFoundException("Fishing rod part instance not found with id: " + partInstanceId));

        if (!partInstance.getOwner().getId().equals(userId)) {
            throw new ResourceNotFoundException("User does not own this fishing rod part.");
        }

        if (partInstance.getDurability() != null && partInstance.getDurability() > 0) {
            throw new InvalidOperationException("This part does not need repairs.");
        }

        // Check if the part has reached its maximum repair limit
        if (partInstance.getRepairCount() != null &&
            partInstance.getBaseItem().getMaxRepairs() != null &&
            partInstance.getRepairCount() >= partInstance.getBaseItem().getMaxRepairs()) {
            throw new InvalidOperationException("This part has reached its maximum repair limit.");
        }

        int cost = getPartRepairCost(partInstanceId);

        boolean success = userService.deductCreditsIfSufficient(user, cost);
        if (!success) {
            throw new InsufficientCreditsException("You do not have enough credits to repair this part. Required: " + cost + " credits.");
        }

        Integer maxDurability = partInstance.getMaxDurability() != null ? partInstance.getMaxDurability() : partInstance.getBaseItem().getMaxDurability();
        if (maxDurability == null) {
            throw new InvalidOperationException("Part does not have maximum durability set.");
        }

        partInstance.setDurability(maxDurability);
        partInstance.setRepairCount(partInstance.getRepairCount() + 1);
        itemInstanceRepository.save(partInstance);

        logger.info("User {} successfully repaired part {} for {} credits. Repair count: {}", userId, partInstanceId, cost, partInstance.getRepairCount());

        return userService.mapToProfileDTO(user);
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
            throw new ResourceNotFoundException("Sender does not own this item instance.");
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
            throw new ResourceNotFoundException("User does not own the fishing rod.");
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

        // Prevent equipping a part to a slot that is already occupied
        switch (partBaseItem.getFishingRodPartType()) {
            case ROD_SHAFT:
                if (rodInstance.getEquippedRodShaft() != null) throw new InvalidOperationException("A part is already equipped in this slot.");
                break;
            case REEL:
                if (rodInstance.getEquippedReel() != null) throw new InvalidOperationException("A part is already equipped in this slot.");
                break;
            case FISHING_LINE:
                if (rodInstance.getEquippedFishingLine() != null) throw new InvalidOperationException("A part is already equipped in this slot.");
                break;
            case HOOK:
                if (rodInstance.getEquippedHook() != null) throw new InvalidOperationException("A part is already equipped in this slot.");
                break;
            case GRIP:
                if (rodInstance.getEquippedGrip() != null) throw new InvalidOperationException("A part is already equipped in this slot.");
                break;
        }

        int cost = getPartUpgradeCost(partBaseItem.getRarity());
        if (cost > 0) {
            boolean success = userService.deductCreditsIfSufficient(user, cost);
            if (!success) {
                throw new InsufficientCreditsException("You do not have enough credits to apply this part. Required: " + cost + " credits.");
            }
        }

        Integer currentMaxDurability = rodInstance.getMaxDurability() != null ? rodInstance.getMaxDurability() : rodBaseItem.getMaxDurability();
        if (currentMaxDurability == null) {
            throw new InvalidOperationException("Rod does not have maximum durability set.");
        }

        boolean isMaxDurabilityIncreasePart =
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

    @Transactional
    public UserProfileDTO unequipAndRemoveBrokenPart(String userId, UUID rodInstanceId, UUID partInstanceId) {
        User user = userRepository.findByIdWithLock(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        ItemInstance rodInstance = itemInstanceRepository.findByIdWithLock(rodInstanceId)
                .orElseThrow(() -> new ResourceNotFoundException("Fishing rod instance not found with id: " + rodInstanceId));

        if (!rodInstance.getOwner().getId().equals(userId)) {
            throw new ResourceNotFoundException("User does not own the fishing rod.");
        }

        ItemInstance partInstance = user.getItemInstances().stream()
                .filter(instance -> instance.getId().equals(partInstanceId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Fishing rod part not found in user inventory with id: " + partInstanceId));

        if (partInstance.getBaseItem().getMaxRepairs() == null || partInstance.getRepairCount() == null || partInstance.getRepairCount() < partInstance.getBaseItem().getMaxRepairs()) {
            throw new InvalidOperationException("This part has not reached its maximum repair limit and cannot be unequipped.");
        }

        // Check if the part is actually equipped on this rod
        boolean isPartEquippedOnThisRod = (rodInstance.getEquippedRodShaft() != null && rodInstance.getEquippedRodShaft().getId().equals(partInstanceId)) ||
                                        (rodInstance.getEquippedReel() != null && rodInstance.getEquippedReel().getId().equals(partInstanceId)) ||
                                        (rodInstance.getEquippedFishingLine() != null && rodInstance.getEquippedFishingLine().getId().equals(partInstanceId)) ||
                                        (rodInstance.getEquippedHook() != null && rodInstance.getEquippedHook().getId().equals(partInstanceId)) ||
                                        (rodInstance.getEquippedGrip() != null && rodInstance.getEquippedGrip().getId().equals(partInstanceId));

        if (!isPartEquippedOnThisRod) {
            throw new InvalidOperationException("This part is not equipped on the specified fishing rod.");
        }


        // Unequip the part from the rod
        switch (partInstance.getBaseItem().getFishingRodPartType()) {
            case ROD_SHAFT:
                rodInstance.setEquippedRodShaft(null);
                break;
            case REEL:
                rodInstance.setEquippedReel(null);
                break;
            case FISHING_LINE:
                rodInstance.setEquippedFishingLine(null);
                break;
            case HOOK:
                rodInstance.setEquippedHook(null);
                break;
            case GRIP:
                rodInstance.setEquippedGrip(null);
                break;
        }

        // Remove the part from the user's inventory, which will delete it due to orphanRemoval=true
        user.getItemInstances().remove(partInstance);
        
        // Explicitly delete part instance for clarity and immediate removal
        itemInstanceRepository.delete(partInstance);

        itemInstanceRepository.save(rodInstance);
        userRepository.save(user);
        
        logger.info("User {} successfully unequipped and removed broken part {} from rod {}.", userId, partInstanceId, rodInstanceId);

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