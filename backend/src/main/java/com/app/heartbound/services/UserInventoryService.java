package com.app.heartbound.services;

import com.app.heartbound.dto.shop.UserInventoryItemDTO;
import com.app.heartbound.entities.ItemInstance;
import com.app.heartbound.entities.Shop;
import com.app.heartbound.entities.User;
import com.app.heartbound.enums.ShopCategory;
import com.app.heartbound.enums.FishingRodPart;
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
import com.app.heartbound.dto.shop.ShopDTO;
import com.app.heartbound.dto.shop.UserInventoryDTO;
import com.app.heartbound.exceptions.shop.ItemNotEquippableException;
import com.app.heartbound.exceptions.shop.ItemNotOwnedException;
import com.app.heartbound.mappers.ShopMapper;
import com.app.heartbound.repositories.shop.ShopRepository;
import com.app.heartbound.services.discord.DiscordService;
import org.springframework.context.annotation.Lazy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Optional;

import com.app.heartbound.config.CacheConfig;
import com.app.heartbound.dto.CreateAuditDTO;
import com.app.heartbound.enums.AuditSeverity;
import com.app.heartbound.enums.AuditCategory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.time.Instant;

@Service
public class UserInventoryService {

    private static final Logger logger = LoggerFactory.getLogger(UserInventoryService.class);
    private final ItemInstanceRepository itemInstanceRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final ShopRepository shopRepository;
    private final DiscordService discordService;
    private final ShopMapper shopMapper;
    private final AuditService auditService;
    private final CacheConfig cacheConfig;
    private final ObjectMapper objectMapper;

    public UserInventoryService(ItemInstanceRepository itemInstanceRepository, UserRepository userRepository, UserService userService, ShopRepository shopRepository, @Lazy DiscordService discordService, ShopMapper shopMapper, AuditService auditService, CacheConfig cacheConfig, ObjectMapper objectMapper) {
        this.itemInstanceRepository = itemInstanceRepository;
        this.userRepository = userRepository;
        this.userService = userService;
        this.shopRepository = shopRepository;
        this.discordService = discordService;
        this.shopMapper = shopMapper;
        this.auditService = auditService;
        this.cacheConfig = cacheConfig;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public String giveItemToUser(String userId, UUID itemId) {
        User user = userRepository.findByIdWithLock(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        Shop item = shopRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Shop item not found with id: " + itemId));

        ItemInstance newInstance = new ItemInstance();
        newInstance.setOwner(user);
        newInstance.setBaseItem(item);
        newInstance.setCreatedAt(Instant.now());
        
        // ROD_SHAFT parts have infinite durability, so don't set durability for them
        if (item.getCategory() == ShopCategory.FISHING_ROD_PART && 
            item.getFishingRodPartType() == FishingRodPart.ROD_SHAFT) {
            // ROD_SHAFT parts don't need durability initialization (infinite durability)
        } else {
            newInstance.setDurability(item.getMaxDurability());
            newInstance.setMaxDurability(item.getMaxDurability());
        }
        
        newInstance.setLevel(1);
        newInstance.setExperience(0L);
        newInstance.setRepairCount(0);
        
        itemInstanceRepository.save(newInstance);

        user.getItemInstances().add(newInstance);
        userRepository.save(user);

        logger.info("Gave item '{}' (instance {}) to user {}", item.getName(), newInstance.getId(), userId);
        return item.getName();
    }

    private int getBaseRepairCost(ItemRarity rarity) {
        switch (rarity) {
            case COMMON: return 25;
            case UNCOMMON: return 75;
            case RARE: return 200;
            case EPIC: return 600;
            case LEGENDARY: return 1750;
            default: return 0;
        }
    }

    private int getPartSurcharge(ItemRarity rarity) {
        switch (rarity) {
            case COMMON: return 5;
            case UNCOMMON: return 15;
            case RARE: return 50;
            case EPIC: return 200;
            case LEGENDARY: return 500;
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

        // Check if this is a ROD_SHAFT part - they have infinite durability and cannot be repaired
        if (partBaseItem.getFishingRodPartType() == FishingRodPart.ROD_SHAFT) {
            throw new InvalidOperationException("Rod shafts have infinite durability and cannot be repaired.");
        }

        // Use proper repair cost calculation instead of upgrade cost
        return getBaseRepairCost(partBaseItem.getRarity());
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

        // Check if this is a ROD_SHAFT part - they have infinite durability and cannot be repaired
        if (partInstance.getBaseItem().getFishingRodPartType() == FishingRodPart.ROD_SHAFT) {
            throw new InvalidOperationException("Rod shafts have infinite durability and cannot be repaired.");
        }

        if (partInstance.getDurability() == null || partInstance.getDurability() > 0) {
            throw new InvalidOperationException("This part does not need repairs.");
        }

        // Check if the part has reached its maximum repair limit - handle null repairCount safely
        Integer currentRepairCount = partInstance.getRepairCount() != null ? partInstance.getRepairCount() : 0;
        if (partInstance.getBaseItem().getMaxRepairs() != null &&
            currentRepairCount >= partInstance.getBaseItem().getMaxRepairs()) {
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
        // Safely increment repairCount, handling null values
        partInstance.setRepairCount(currentRepairCount + 1);
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

        // Group instances by base item and count them (excluding FISHING_ROD_PART for Discord display)
        Map<Shop, Long> itemCounts = user.getItemInstances().stream()
            .filter(instance -> instance.getBaseItem().getCategory() != ShopCategory.FISHING_ROD_PART)
            .collect(Collectors.groupingBy(ItemInstance::getBaseItem, Collectors.counting()));

        // Convert to DTOs
        List<UserInventoryItemDTO> inventoryDTOs = itemCounts.entrySet().stream()
            .flatMap(entry -> {
                Shop item = entry.getKey();
                List<ItemInstance> instances = user.getItemInstances().stream()
                    .filter(i -> i.getBaseItem().getId().equals(item.getId()) && i.getBaseItem().getCategory() != ShopCategory.FISHING_ROD_PART)
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
                        .equipped(item.getCategory().isEquippable() && item.getId().equals(user.getEquippedItemIdByCategory(item.getCategory())))
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

        // Check if the part needs repair and validate repair limits before equipping
        Integer partMaxDurability = partInstance.getMaxDurability() != null ? partInstance.getMaxDurability() : partBaseItem.getMaxDurability();
        if (partMaxDurability == null) {
            throw new InvalidOperationException("Part does not have maximum durability set.");
        }
        
        Integer currentDurability = partInstance.getDurability() != null ? partInstance.getDurability() : partMaxDurability;
        boolean partNeedsRepair = currentDurability < partMaxDurability;
        
        // Only apply repair logic for non-ROD_SHAFT parts (ROD_SHAFT has infinite durability)
        if (partNeedsRepair && partBaseItem.getFishingRodPartType() != FishingRodPart.ROD_SHAFT) {
            // Check if the part has reached its maximum repair limit - handle null repairCount safely
            Integer currentRepairCount = partInstance.getRepairCount() != null ? partInstance.getRepairCount() : 0;
            if (partBaseItem.getMaxRepairs() != null &&
                currentRepairCount >= partBaseItem.getMaxRepairs()) {
                throw new InvalidOperationException("This part is broken beyond repair and cannot be equipped.");
            }
            
            // Repair the part to full durability as part of the equipping process
            partInstance.setDurability(partMaxDurability);
            // Safely increment repairCount, handling null values
            partInstance.setRepairCount(currentRepairCount + 1);
        }
        
        itemInstanceRepository.save(partInstance);

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

        // Check if this is a ROD_SHAFT part - they cannot be broken or removed this way
        if (partInstance.getBaseItem().getFishingRodPartType() == FishingRodPart.ROD_SHAFT) {
            throw new InvalidOperationException("Rod shafts cannot be broken or removed this way.");
        }

        // Check if part is broken (durability = 0)
        if (partInstance.getDurability() == null || partInstance.getDurability() > 0) {
            throw new InvalidOperationException("This part is not broken and cannot be removed via this method.");
        }
        
        // Check if part has reached its maximum repair limit (cannot be repaired anymore)
        boolean hasMaxRepairs = partInstance.getBaseItem().getMaxRepairs() != null;
        boolean hasReachedMaxRepairs = hasMaxRepairs && 
                partInstance.getRepairCount() != null && 
                partInstance.getRepairCount() >= partInstance.getBaseItem().getMaxRepairs();
        
        if (hasMaxRepairs && !hasReachedMaxRepairs) {
            throw new InvalidOperationException("This part is broken but can still be repaired. Use the repair function instead of removing it.");
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

        // Handle durability decrease if part provides durability increase
        Shop partBaseItem = partInstance.getBaseItem();
        Integer currentMaxDurability = rodInstance.getMaxDurability() != null ? rodInstance.getMaxDurability() : rodInstance.getBaseItem().getMaxDurability();
        if (currentMaxDurability == null) {
            throw new InvalidOperationException("Rod does not have maximum durability set.");
        }

        boolean isDurabilityIncreasePart = partBaseItem.getDurabilityIncrease() != null && partBaseItem.getDurabilityIncrease() > 0;
        
        if (isDurabilityIncreasePart) {
            // Decrease max durability by removing the part's contribution
            int newMaxDurability = currentMaxDurability - partBaseItem.getDurabilityIncrease();
            rodInstance.setMaxDurability(newMaxDurability);
            
            // Cap current durability if it exceeds new max durability
            if (rodInstance.getDurability() != null && rodInstance.getDurability() > newMaxDurability) {
                rodInstance.setDurability(newMaxDurability);
            }
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

    @Transactional
    public UserProfileDTO unequipFishingRodPart(String userId, UUID rodInstanceId, UUID partInstanceId) {
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

        Shop rodBaseItem = rodInstance.getBaseItem();
        if (rodBaseItem.getCategory() != ShopCategory.FISHING_ROD) {
            throw new InvalidOperationException("Item is not a fishing rod.");
        }

        Shop partBaseItem = partInstance.getBaseItem();
        if (partBaseItem.getCategory() != ShopCategory.FISHING_ROD_PART) {
            throw new InvalidOperationException("Item is not a fishing rod part.");
        }

        // Check if the part is actually equipped on this rod
        boolean isPartEquippedOnThisRod = false;
        switch (partBaseItem.getFishingRodPartType()) {
            case ROD_SHAFT:
                isPartEquippedOnThisRod = rodInstance.getEquippedRodShaft() != null && rodInstance.getEquippedRodShaft().getId().equals(partInstanceId);
                break;
            case REEL:
                isPartEquippedOnThisRod = rodInstance.getEquippedReel() != null && rodInstance.getEquippedReel().getId().equals(partInstanceId);
                break;
            case FISHING_LINE:
                isPartEquippedOnThisRod = rodInstance.getEquippedFishingLine() != null && rodInstance.getEquippedFishingLine().getId().equals(partInstanceId);
                break;
            case HOOK:
                isPartEquippedOnThisRod = rodInstance.getEquippedHook() != null && rodInstance.getEquippedHook().getId().equals(partInstanceId);
                break;
            case GRIP:
                isPartEquippedOnThisRod = rodInstance.getEquippedGrip() != null && rodInstance.getEquippedGrip().getId().equals(partInstanceId);
                break;
        }

        if (!isPartEquippedOnThisRod) {
            throw new InvalidOperationException("This part is not equipped on the specified fishing rod.");
        }

        // Handle durability decrease if part provides durability increase
        Integer currentMaxDurability = rodInstance.getMaxDurability() != null ? rodInstance.getMaxDurability() : rodBaseItem.getMaxDurability();
        if (currentMaxDurability == null) {
            throw new InvalidOperationException("Rod does not have maximum durability set.");
        }

        boolean isDurabilityIncreasePart = partBaseItem.getDurabilityIncrease() != null && partBaseItem.getDurabilityIncrease() > 0;
        
        if (isDurabilityIncreasePart) {
            // Decrease max durability by removing the part's contribution
            int newMaxDurability = currentMaxDurability - partBaseItem.getDurabilityIncrease();
            rodInstance.setMaxDurability(newMaxDurability);
            
            // Cap current durability if it exceeds new max durability
            if (rodInstance.getDurability() > newMaxDurability) {
                rodInstance.setDurability(newMaxDurability);
            }
        }

        // Unequip the part from the rod
        switch (partBaseItem.getFishingRodPartType()) {
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

        itemInstanceRepository.save(rodInstance);
        
        logger.info("User {} successfully unequipped part {} from rod {}.", userId, partInstanceId, rodInstanceId);

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

    /**
     * Equips an item for a user
     * @param userId User ID
     * @param itemId Item ID
     * @return Updated UserProfileDTO
     */
    @Transactional
    public UserProfileDTO equipItem(String userId, UUID itemId) {
        logger.debug("Equipping item {} for user {}", itemId, userId);
        
        // Get user and item
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));
        
        Shop item = shopRepository.findById(itemId)
            .orElseThrow(() -> new ResourceNotFoundException("Shop item not found with ID: " + itemId));

        // For fishing rods, this endpoint is deprecated. Use equipItemInstance instead.
        if (item.getCategory() == ShopCategory.FISHING_ROD) {
            throw new UnsupportedOperationException("Fishing rods must be equipped by their instance ID. Please use the new equip endpoint.");
        }
        
        // Perform equip logic
        performEquipItem(user, item, null);
        
        // Save user changes
        user = userRepository.save(user);
        
        // Return updated profile
        return userService.mapToProfileDTO(user);
    }
    
    /**
     * Equips an item for a user by its instance ID.
     * This is the new standard for equippable items with unique properties.
     * @param userId User ID
     * @param instanceId Item Instance ID
     * @return Updated UserProfileDTO
     */
    @Transactional
    public UserProfileDTO equipItemInstance(String userId, UUID instanceId) {
        logger.debug("Equipping item instance {} for user {}", instanceId, userId);

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));

        ItemInstance instance = itemInstanceRepository.findById(instanceId)
            .orElseThrow(() -> new ResourceNotFoundException("Item instance not found with ID: " + instanceId));

        if (!instance.getOwner().getId().equals(userId)) {
            throw new ItemNotOwnedException("You do not own this item instance.");
        }

        Shop item = instance.getBaseItem();

        performEquipItem(user, item, instanceId);

        user = userRepository.save(user);

        return userService.mapToProfileDTO(user);
    }
    
    /**
     * Equips multiple items for a user in a single atomic transaction
     * @param userId User ID
     * @param itemIds List of item IDs to equip
     * @return Updated UserProfileDTO
     */
    @Transactional
    public UserProfileDTO equipBatch(String userId, List<UUID> itemIds) {
        logger.debug("Batch equipping {} items for user {}", itemIds.size(), userId);
        
        // Validate input
        if (itemIds == null || itemIds.isEmpty()) {
            throw new IllegalArgumentException("Item IDs list cannot be empty");
        }
        
        // Remove duplicates while preserving order
        List<UUID> uniqueItemIds = itemIds.stream()
            .distinct()
            .collect(Collectors.toList());
            
        if (uniqueItemIds.size() != itemIds.size()) {
            logger.debug("Removed {} duplicate item IDs from batch equip request", 
                        itemIds.size() - uniqueItemIds.size());
        }
        
        // Get user
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));
        
        // Fetch all items and validate them first
        List<Shop> itemsToEquip = new ArrayList<>();
        int badgeCount = 0;
        for (UUID itemId : uniqueItemIds) {
            Shop item = shopRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Shop item not found with ID: " + itemId));
            itemsToEquip.add(item);
            
            // Count badges to ensure only one badge is included in batch request
            if (item.getCategory() == ShopCategory.BADGE) {
                badgeCount++;
            }
        }
        
        // Validate that only one badge is included in the batch request
        if (badgeCount > 1) {
            throw new IllegalArgumentException("Only one badge can be equipped at a time. Please select only one badge for batch equipping.");
        }
        
        // Validate all items before equipping any
        for (Shop item : itemsToEquip) {
            validateItemForEquipping(user, item);
        }
        
        // Perform equip operations for all items
        for (Shop item : itemsToEquip) {
            try {
                performEquipItem(user, item, null);
                logger.debug("Successfully equipped item {} for user {}", item.getId(), userId);
            } catch (Exception e) {
                logger.error("Failed to equip item {} for user {}: {}", item.getId(), userId, e.getMessage(), e);
                throw new RuntimeException("Failed to equip item: " + item.getName(), e);
            }
        }
        
        // Save user changes once after all equips
        user = userRepository.save(user);
        
        logger.info("Successfully batch equipped {} items for user {}", itemsToEquip.size(), userId);
        
        // Return updated profile
        return userService.mapToProfileDTO(user);
    }
    
    /**
     * Validates that an item can be equipped by a user
     * @param user User attempting to equip
     * @param item Item to validate
     * @throws ItemNotOwnedException if user doesn't own the item
     * @throws ItemNotEquippableException if item cannot be equipped
     */
    private void validateItemForEquipping(User user, Shop item) {
        // Check if user owns the item
        if (!user.hasItem(item.getId())) {
            throw new ItemNotOwnedException("You don't own this item: " + item.getName());
        }
        
        // Check if item can be equipped
        ShopCategory category = item.getCategory();
        if (category == null) {
            throw new ItemNotEquippableException("This item cannot be equipped: " + item.getName());
        }
        
        // Cases cannot be equipped
        if (category == ShopCategory.CASE) {
            throw new ItemNotEquippableException("Cases cannot be equipped: " + item.getName());
        }
    }
    
    /**
     * Performs the actual equip operation for a single item
     * This method contains the core equip logic shared between single and batch operations
     * @param user User to equip item for
     * @param item Item to equip
     * @param instanceId The specific instance ID to equip (optional, for items like fishing rods)
     */
    private void performEquipItem(User user, Shop item, UUID instanceId) {
        // Validate the item can be equipped
        validateItemForEquipping(user, item);
        
        // Set the item as equipped based on its category
        ShopCategory category = item.getCategory();
        
        // Special handling for BADGE category - single badge only
        if (category == ShopCategory.BADGE) {
            // Check if badge is already equipped
            if (user.isBadgeEquipped(item.getId())) {
                logger.debug("Badge {} is already equipped for user {}", item.getId(), user.getId());
                return; // Already equipped, nothing to do
            }
            
            // Handle Discord role management for previously equipped badge
            UUID previousBadgeId = user.getEquippedBadgeId();
            if (previousBadgeId != null && !previousBadgeId.equals(item.getId())) {
                // Find the previous badge to get its Discord role ID and handle removal
                shopRepository.findById(previousBadgeId).ifPresent(previousBadge -> {
                    String previousRoleId = previousBadge.getDiscordRoleId();
                    if (previousRoleId != null && !previousRoleId.isEmpty()) {
                        logger.debug("Removing previous Discord role {} from user {} before equipping new badge", 
                                previousRoleId, user.getId());
                        
                        boolean removalSuccess = discordService.removeRole(user.getId(), previousRoleId);
                        if (!removalSuccess) {
                            logger.warn("Failed to remove previous Discord role {} from user {}. " +
                                    "Continuing with equipping new badge.", previousRoleId, user.getId());
                        } else {
                            logger.debug("Successfully removed previous Discord role {} from user {}", 
                                    previousRoleId, user.getId());
                        }
                    }
                });
            }
            
            // Set the new badge as equipped (replaces any existing badge)
            user.setEquippedBadge(item.getId());
            logger.debug("Set badge {} as equipped for user {}", item.getId(), user.getId());
            
            // Apply Discord role if applicable
            if (item.getDiscordRoleId() != null && !item.getDiscordRoleId().isEmpty()) {
                logger.debug("Adding Discord role {} for user {} for badge", 
                           item.getDiscordRoleId(), user.getId());
                boolean grantSuccess = discordService.grantRole(user.getId(), item.getDiscordRoleId());
                if (!grantSuccess) {
                    logger.warn("Failed to grant Discord role {} to user {} for badge", 
                              item.getDiscordRoleId(), user.getId());
                }
            }
        } else if (category == ShopCategory.USER_COLOR) {
            // Handle Discord role management for USER_COLOR items
            // Check if there was a previously equipped item of the same category
            UUID previousItemId = user.getEquippedItemIdByCategory(category);
            if (previousItemId != null && !previousItemId.equals(item.getId())) {
                // Find the previous item to get its Discord role ID and handle removal synchronously
                shopRepository.findById(previousItemId).ifPresent(previousItem -> {
                    String previousRoleId = previousItem.getDiscordRoleId();
                    if (previousRoleId != null && !previousRoleId.isEmpty()) {
                        logger.debug("Removing previous Discord role {} from user {} before equipping new item", 
                                previousRoleId, user.getId());
                        
                        // Ensure role removal occurs and log any failures
                        boolean removalSuccess = discordService.removeRole(user.getId(), previousRoleId);
                        if (!removalSuccess) {
                            // Log the issue but continue with equipping the new item
                            logger.warn("Failed to remove previous Discord role {} from user {}. " +
                                    "Continuing with equipping new item.", previousRoleId, user.getId());
                        } else {
                            logger.debug("Successfully removed previous Discord role {} from user {}", 
                                    previousRoleId, user.getId());
                        }
                    }
                });
            }
            
            // Now unequip the previous item and set the new one
            user.setEquippedItemIdByCategory(category, item.getId());
            
            // Grant the new role if it has a discordRoleId
            String newRoleId = item.getDiscordRoleId();
            if (newRoleId != null && !newRoleId.isEmpty()) {
                logger.debug("Granting Discord role {} to user {} for equipped item", newRoleId, user.getId());
                boolean grantSuccess = discordService.grantRole(user.getId(), newRoleId);
                if (!grantSuccess) {
                    logger.warn("Failed to grant Discord role {} to user {}", newRoleId, user.getId());
                }
            }
        } else if (category == ShopCategory.FISHING_ROD) {
            if (instanceId == null) {
                throw new IllegalArgumentException("Fishing rod must be equipped by its instance ID.");
            }
            // Handle Discord role management for FISHING_ROD items
            // Check if there was a previously equipped item of the same category
            UUID previousInstanceId = user.getEquippedFishingRodInstanceId();
            if (previousInstanceId != null && !previousInstanceId.equals(instanceId)) {
                itemInstanceRepository.findById(previousInstanceId).ifPresent(previousInstance -> {
                    Shop previousItem = previousInstance.getBaseItem();
                    String previousRoleId = previousItem.getDiscordRoleId();
                    if (previousRoleId != null && !previousRoleId.isEmpty()) {
                        logger.debug("Removing previous Discord role {} from user {} before equipping new item", 
                                previousRoleId, user.getId());
                        
                        // Ensure role removal occurs and log any failures
                        boolean removalSuccess = discordService.removeRole(user.getId(), previousRoleId);
                        if (!removalSuccess) {
                            // Log the issue but continue with equipping the new item
                            logger.warn("Failed to remove previous Discord role {} from user {}. " +
                                    "Continuing with equipping new item.", previousRoleId, user.getId());
                        } else {
                            logger.debug("Successfully removed previous Discord role {} from user {}", 
                                    previousRoleId, user.getId());
                        }
                    }
                });
            }
            
            // Now unequip the previous item and set the new one
            user.setEquippedFishingRodInstanceId(instanceId);
            
            // Grant the new role if it has a discordRoleId
            String newRoleId = item.getDiscordRoleId();
            if (newRoleId != null && !newRoleId.isEmpty()) {
                logger.debug("Granting Discord role {} to user {} for equipped item", newRoleId, user.getId());
                boolean grantSuccess = discordService.grantRole(user.getId(), newRoleId);
                if (!grantSuccess) {
                    logger.warn("Failed to grant Discord role {} to user {}", newRoleId, user.getId());
                }
            }
        } else {
            // For other categories, simply update the equipped item
            user.setEquippedItemIdByCategory(category, item.getId());
        }
    }
    
    /**
     * Unequips an item for a user by category
     * @param userId User ID
     * @param category Shop category to unequip
     * @return Updated UserProfileDTO
     */
    @Transactional
    public UserProfileDTO unequipItem(String userId, ShopCategory category) {
        logger.debug("Unequipping item of category {} for user {}", category, userId);
        
        // Get user
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));
        
        // Badge category requires specific badge ID to unequip
        if (category == ShopCategory.BADGE) {
            throw new UnsupportedOperationException("BADGE category requires a specific badge ID to unequip. Use unequipBadge(userId, badgeId) instead.");
        }
        
        // Get the currently equipped item ID BEFORE unequipping
        UUID currentlyEquippedItemId = user.getEquippedItemIdByCategory(category);
        
        // Unequip the item in the specified category
        user.setEquippedItemIdByCategory(category, null);
        
        // If this is a USER_COLOR category, remove associated Discord role
        if (category == ShopCategory.USER_COLOR && currentlyEquippedItemId != null) {
            shopRepository.findById(currentlyEquippedItemId).ifPresent(equippedItem -> {
                if (equippedItem.getDiscordRoleId() != null && !equippedItem.getDiscordRoleId().isEmpty()) {
                    logger.debug("Removing Discord role {} from user {} for unequipped item", 
                                equippedItem.getDiscordRoleId(), userId);
                    discordService.removeRole(userId, equippedItem.getDiscordRoleId());
                }
            });
        }
        
        // Save user changes
        user = userRepository.save(user);
        
        // Return updated profile
        return userService.mapToProfileDTO(user);
    }
    
    /**
     * Unequips a specific badge for a user
     * @param userId User ID
     * @param badgeId Badge ID to unequip
     * @return Updated UserProfileDTO
     */
    @Transactional
    public UserProfileDTO unequipBadge(String userId, UUID badgeId) {
        logger.debug("Unequipping badge {} for user {}", badgeId, userId);
        
        // Get user
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));
        
        // Check if user has the badge equipped
        if (!user.isBadgeEquipped(badgeId)) {
            logger.debug("Badge {} is not equipped for user {}", badgeId, userId);
            return userService.mapToProfileDTO(user);
        }
        
        // Get badge item for possible Discord role handling
        Shop badge = shopRepository.findById(badgeId)
            .orElseThrow(() -> new ResourceNotFoundException("Badge not found with ID: " + badgeId));
        
        // Check that it's actually a badge
        if (badge.getCategory() != ShopCategory.BADGE) {
            throw new ItemNotEquippableException("Item is not a badge");
        }
        
        // Remove the equipped badge
        user.removeEquippedBadge();
        
        // Handle Discord role if applicable
        if (badge.getDiscordRoleId() != null && !badge.getDiscordRoleId().isEmpty()) {
            logger.debug("Removing Discord role {} from user {} for unequipped badge", 
                        badge.getDiscordRoleId(), userId);
            boolean removalSuccess = discordService.removeRole(userId, badge.getDiscordRoleId());
            if (!removalSuccess) {
                logger.warn("Failed to remove Discord role {} from user {} for badge", 
                          badge.getDiscordRoleId(), userId);
            }
        }
        
        // Save user changes
        user = userRepository.save(user);
        
        // Return updated profile
        return userService.mapToProfileDTO(user);
    }

    /**
     * Gets a user's inventory with equipped status
     * @param userId User ID
     * @return User's inventory with equipped status
     */
    @Transactional(readOnly = true)
    public UserInventoryDTO getFullUserInventory(String userId) {
        logger.debug("Getting inventory for user {}", userId);

        User user = userRepository.findByIdWithInventory(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));

        // This method needs significant changes to support instance-specific data.
        // We'll create a new list of DTOs.
        List<ShopDTO> itemDTOs = new ArrayList<>();

        // Group all instances by their base item to get counts for stackable items.
        Map<Shop, List<ItemInstance>> groupedInstances = user.getItemInstances().stream()
                .collect(Collectors.groupingBy(ItemInstance::getBaseItem));

        for (Map.Entry<Shop, List<ItemInstance>> entry : groupedInstances.entrySet()) {
            Shop item = entry.getKey();
            List<ItemInstance> instances = entry.getValue();

            if (item.getCategory() == ShopCategory.FISHING_ROD || item.getCategory() == ShopCategory.FISHING_ROD_PART) {
                // For fishing rods and parts, create a DTO for each unique instance.
                for (ItemInstance instance : instances) {
                    ShopDTO dto = shopMapper.mapToShopDTO(item, user, instance);
                    dto.setQuantity(1); // Each instance is unique

                    // Set equipped status based on the instance ID for rods
                    if (item.getCategory() == ShopCategory.FISHING_ROD) {
                        UUID equippedInstanceId = user.getEquippedFishingRodInstanceId();
                        dto.setEquipped(instance.getId().equals(equippedInstanceId));
                    } else { // This implies FISHING_ROD_PART
                        // Check if the part is equipped on ANY rod.
                        dto.setEquipped(itemInstanceRepository.isPartAlreadyEquipped(instance.getId()));
                    }
                    itemDTOs.add(dto);
                }
            } else {
                // For all other items, use the existing stacking logic.
                long quantity = instances.size();
                ShopDTO dto = shopMapper.mapToShopDTO(item, user);
                dto.setQuantity((int) quantity);

                // Add equipped status, skipping for non-equippable categories like CASE
                if (item.getCategory() != null && item.getCategory() != ShopCategory.CASE && item.getCategory() != ShopCategory.FISHING_ROD_PART) {
                    if (item.getCategory() == ShopCategory.BADGE) {
                        dto.setEquipped(user.isBadgeEquipped(item.getId()));
                    } else {
                        UUID equippedItemId = user.getEquippedItemIdByCategory(item.getCategory());
                        dto.setEquipped(equippedItemId != null && equippedItemId.equals(item.getId()));
                    }
                } else {
                    dto.setEquipped(false);
                }
                itemDTOs.add(dto);
            }
        }
        
        return UserInventoryDTO.builder()
            .items(new HashSet<>(itemDTOs))
            .build();
    }
    
    /**
     * Gets a user's inventory specifically for Discord commands
     * Uses eager fetching to avoid LazyInitializationException
     * @param userId User ID
     * @return User's inventory with equipped status
     */
    public UserInventoryDTO getUserInventoryForDiscord(String userId) {
        logger.debug("Getting inventory for Discord command for user {}", userId);
        
        User user = userRepository.findByIdWithInventory(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));
        
        Map<Shop, Long> itemCounts = user.getItemInstances().stream()
            .collect(Collectors.groupingBy(ItemInstance::getBaseItem, Collectors.counting()));
            
        java.util.Set<ShopDTO> itemDTOs = itemCounts.entrySet().stream().map(entry -> {
            Shop item = entry.getKey();
            long quantity = entry.getValue();
            
            ShopDTO dto = shopMapper.mapToShopDTO(item, user);
            dto.setQuantity((int) quantity);
            
            // Add equipped status, skipping for non-equippable categories like CASE
            if (item.getCategory() != null && item.getCategory() != ShopCategory.CASE && item.getCategory() != ShopCategory.FISHING_ROD_PART) {
                if (item.getCategory() == ShopCategory.BADGE) {
                    dto.setEquipped(user.isBadgeEquipped(item.getId()));
                } else if (item.getCategory() == ShopCategory.FISHING_ROD) {
                    // This is tricky because we don't have instance info here.
                    // The main inventory endpoint is better. For Discord, we can just show if ANY rod is equipped.
                    dto.setEquipped(user.getEquippedFishingRodInstanceId() != null);
                } else {
                    UUID equippedItemId = user.getEquippedItemIdByCategory(item.getCategory());
                    dto.setEquipped(equippedItemId != null && equippedItemId.equals(item.getId()));
                }
            } else {
                // For CASE or null category, it's not equipped
                dto.setEquipped(false);
            }
            
            return dto;
        }).collect(Collectors.toSet());
        
        return UserInventoryDTO.builder()
            .items(itemDTOs)
            .build();
    }
    
    /**
     * Unequips multiple items for a user in a single atomic transaction.
     * @param userId User ID
     * @param itemIds List of item IDs to unequip
     * @return Updated UserProfileDTO
     */
    @Transactional
    public UserProfileDTO unequipBatch(String userId, List<UUID> itemIds) {
        logger.debug("Batch unequipping {} items for user {}", itemIds.size(), userId);

        if (itemIds == null || itemIds.isEmpty()) {
            throw new IllegalArgumentException("Item IDs list cannot be empty");
        }

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));

        List<Shop> itemsToUnequip = shopRepository.findAllById(itemIds);

        if (itemsToUnequip.size() != itemIds.size()) {
            logger.warn("Some items for batch unequip were not found. Requested: {}, Found: {}", itemIds.size(), itemsToUnequip.size());
        }

        for (Shop item : itemsToUnequip) {
            ShopCategory category = item.getCategory();
            if (category == null) continue;

            boolean wasEquipped = false;
            if (category == ShopCategory.BADGE) {
                if (user.isBadgeEquipped(item.getId())) {
                    user.removeEquippedBadge();
                    wasEquipped = true;
                }
            } else if (category == ShopCategory.FISHING_ROD) {
                // Unequipping a fishing rod means unequipping the instance.
                // The client should send the instance ID, but here we can only unequip whatever is equipped.
                // This batch unequip is by item ID, not instance ID, so we just clear the equipped slot.
                UUID equippedInstanceId = user.getEquippedFishingRodInstanceId();
                if (equippedInstanceId != null) {
                    // Find which item this instance belongs to
                    Optional<ItemInstance> instanceOpt = itemInstanceRepository.findById(equippedInstanceId);
                    if (instanceOpt.isPresent() && instanceOpt.get().getBaseItem().getId().equals(item.getId())) {
                        user.setEquippedFishingRodInstanceId(null);
                        wasEquipped = true;
                    }
                }
            } else {
                UUID equippedId = user.getEquippedItemIdByCategory(category);
                if (equippedId != null && equippedId.equals(item.getId())) {
                    user.setEquippedItemIdByCategory(category, null);
                    wasEquipped = true;
                }
            }

            if (wasEquipped && item.getDiscordRoleId() != null && !item.getDiscordRoleId().isEmpty()) {
                logger.debug("Removing Discord role {} from user {} for unequipped item {}", 
                            item.getDiscordRoleId(), userId, item.getId());
                discordService.removeRole(userId, item.getDiscordRoleId());
            }
        }

        userRepository.save(user);

        logger.info("Successfully batch unequipped {} items for user {}", itemsToUnequip.size(), userId);

        return userService.mapToProfileDTO(user);
    }

    /**
     * Handles the level-up logic for a fishing rod instance.
     * This method checks if the rod has enough experience to level up and does so,
     * handling multiple level-ups in a single call if necessary.
     *
     * @param rodInstance The ItemInstance of the fishing rod to process.
     */
    public void handleRodLevelUp(ItemInstance rodInstance) {
        if (rodInstance == null || rodInstance.getBaseItem() == null || rodInstance.getBaseItem().getCategory() != ShopCategory.FISHING_ROD) {
            return;
        }

        Integer currentLevel = rodInstance.getLevel();
        if (currentLevel == null) {
            currentLevel = 1; // Default to level 1 if null
        }

        if (currentLevel >= LevelingUtil.MAX_ROD_LEVEL) {
            return; // Already at max level
        }

        Long currentXp = rodInstance.getExperience();
        if (currentXp == null) {
            currentXp = 0L;
        }

        long xpForNextLevel = LevelingUtil.calculateXpForRodLevel(currentLevel);

        // Loop to handle multiple level-ups at once
        while (currentXp >= xpForNextLevel && currentLevel < LevelingUtil.MAX_ROD_LEVEL) {
            currentLevel++;
            currentXp -= xpForNextLevel;

            logger.info("Fishing rod instance {} leveled up to {} for user {}. Remaining XP: {}",
                    rodInstance.getId(), currentLevel, rodInstance.getOwner().getId(), currentXp);

            // Calculate XP for the *new* next level
            xpForNextLevel = LevelingUtil.calculateXpForRodLevel(currentLevel);
        }

        rodInstance.setLevel(currentLevel);
        rodInstance.setExperience(currentXp);
        // The calling method is responsible for saving the updated instance
    }

    /**
     * Admin method to get user's inventory items (moved from UserService)
     */
    @PreAuthorize("hasRole('ADMIN')")
    public List<UserInventoryItemDTO> getUserInventoryItems(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));

        // Group item instances by their base item and count them to get quantities.
        Map<Shop, Long> itemCounts = user.getItemInstances().stream()
            .collect(Collectors.groupingBy(
                ItemInstance::getBaseItem,
                Collectors.counting()
            ));

        // Map the grouped items and their counts to DTOs.
        return itemCounts.entrySet().stream()
            .map(entry -> {
                Shop item = entry.getKey();
                int quantity = entry.getValue().intValue();
                return UserInventoryItemDTO.builder()
                        .itemId(item.getId())
                        .name(item.getName())
                        .description(item.getDescription())
                        .category(item.getCategory())
                        .thumbnailUrl(item.getThumbnailUrl())
                        .imageUrl(item.getImageUrl())
                        .quantity(quantity)
                        .price(item.getPrice())
                        .build();
            })
            .collect(Collectors.toList());
    }

    /**
     * Remove an item from a user's inventory by admin (moved from UserService).
     * Handles both new inventory system (with quantities) and legacy inventory system.
     * Automatically refunds credits if the item was purchased (price > 0).
     * Unequips the item if it's currently equipped.
     * 
     * @param userId the ID of the user whose inventory to modify
     * @param itemId the ID of the item to remove
     * @param adminId the ID of the admin performing the operation
     * @return the updated user profile
     * @throws ResourceNotFoundException if user or item not found
     */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public UserProfileDTO removeInventoryItem(String userId, UUID itemId, String adminId) {
        logger.debug("Admin {} removing item {} from user {}'s inventory", adminId, itemId, userId);
        
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));
        
        Shop shopItem = shopRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Shop item not found with ID: " + itemId));
        
        boolean itemRemoved = false;
        boolean wasEquipped = false;
        int refundAmount = 0;
        String itemName = shopItem.getName();
        
        if (shopItem.getCategory() != ShopCategory.CASE) {
            UUID equippedItemId = user.getEquippedItemIdByCategory(shopItem.getCategory());
            if (equippedItemId != null && equippedItemId.equals(itemId)) {
                user.setEquippedItemIdByCategory(shopItem.getCategory(), null);
                wasEquipped = true;
                logger.debug("Unequipped item {} from user {} before removal", itemId, userId);
            }
        }
        
        Optional<ItemInstance> instanceToRemove = user.getItemInstances().stream()
            .filter(instance -> instance.getBaseItem().getId().equals(itemId))
            .findFirst();

        if (instanceToRemove.isPresent()) {
            ItemInstance itemInstance = instanceToRemove.get();
            
            if (itemInstance.getBaseItem().getPrice() != null && itemInstance.getBaseItem().getPrice() > 0) {
                refundAmount = itemInstance.getBaseItem().getPrice();
            }
            
            itemInstanceRepository.delete(itemInstance);
            user.getItemInstances().remove(itemInstance);
            itemRemoved = true;
            
            logger.debug("Removed item instance {} for item {} from user {}", itemInstance.getId(), itemId, userId);
        }
        
        if (!itemRemoved) {
            throw new ResourceNotFoundException("Item not found in user's inventory: " + itemId);
        }
        
        // Use atomic credit operation for refund instead of direct assignment
        if (refundAmount > 0) {
            boolean refundSuccess = userService.updateCreditsAtomic(userId, refundAmount);
            if (refundSuccess) {
                logger.info("Refunded {} credits to user {} for removed item {}", refundAmount, userId, itemId);
            } else {
                logger.warn("Failed to refund {} credits to user {} for removed item {}", refundAmount, userId, itemId);
            }
        }
        
        User updatedUser = userRepository.save(user);
        
        createInventoryRemovalAuditEntry(adminId, userId, itemId, itemName, refundAmount, wasEquipped);
        
        cacheConfig.invalidateUserProfileCache(userId);
        cacheConfig.invalidateLeaderboardCache();
        
        logger.info("ADMIN INVENTORY REMOVAL - Admin: {}, User: {}, Item: {} ({}), Refund: {} credits, Was Equipped: {}", 
                adminId, userId, itemId, itemName, refundAmount, wasEquipped);
        
        return userService.mapToProfileDTO(updatedUser);
    }

    /**
     * Utility method to get the client IP address from the current HTTP request context
     */
    private String getClientIp() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                
                // Check for X-Forwarded-For header first (for proxied requests)
                String xForwardedFor = request.getHeader("X-Forwarded-For");
                if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
                    // X-Forwarded-For can contain multiple IPs, take the first one
                    return xForwardedFor.split(",")[0].trim();
                }
                
                // Check for X-Real-IP header (alternative proxy header)
                String xRealIp = request.getHeader("X-Real-IP");
                if (xRealIp != null && !xRealIp.isEmpty()) {
                    return xRealIp;
                }
                
                // Fallback to standard remote address
                return request.getRemoteAddr();
            }
        } catch (Exception e) {
            logger.debug("Unable to extract client IP address: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Utility method to get the user agent from the current HTTP request context
     */
    private String getUserAgent() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                return request.getHeader("User-Agent");
            }
        } catch (Exception e) {
            logger.debug("Unable to extract user agent: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Utility method to get the session ID from the current HTTP request context
     */
    private String getSessionId() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                return request.getSession(false) != null ? request.getSession(false).getId() : null;
            }
        } catch (Exception e) {
            logger.debug("Unable to extract session ID: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Creates an audit entry for inventory item removal operations
     */
    private void createInventoryRemovalAuditEntry(String adminId, String targetUserId, UUID itemId, String itemName, int refundAmount, boolean wasEquipped) {
        try {
            // Build description
            String description = String.format("Removed item '%s' from user %s's inventory%s%s", 
                itemName, 
                targetUserId,
                refundAmount > 0 ? String.format(" (refunded %d credits)", refundAmount) : "",
                wasEquipped ? " (item was equipped)" : "");

            // Build detailed JSON for audit trail
            Map<String, Object> details = new HashMap<>();
            details.put("adminId", adminId);
            details.put("targetUserId", targetUserId);
            details.put("itemId", itemId.toString());
            details.put("itemName", itemName);
            details.put("refundAmount", refundAmount);
            details.put("wasEquipped", wasEquipped);
            details.put("timestamp", LocalDateTime.now().toString());

            String detailsJson;
            try {
                detailsJson = objectMapper.writeValueAsString(details);
            } catch (JsonProcessingException e) {
                logger.error("Failed to serialize inventory removal details to JSON: {}", e.getMessage());
                detailsJson = "{\"error\": \"Failed to serialize inventory removal details\"}";
            }

            // Create audit entry
            CreateAuditDTO auditDTO = CreateAuditDTO.builder()
                .userId(adminId)
                .action("REMOVE_INVENTORY_ITEM")
                .entityType("User")
                .entityId(targetUserId)
                .description(description)
                .ipAddress(getClientIp())
                .userAgent(getUserAgent())
                .sessionId(getSessionId())
                .severity(AuditSeverity.INFO)
                .category(AuditCategory.DATA_ACCESS)
                .details(detailsJson)
                .source("UserInventoryService")
                .build();

            // Use createSystemAuditEntry for internal operations
            auditService.createSystemAuditEntry(auditDTO);

        } catch (Exception e) {
            // Log the error but don't let audit failures break the inventory removal flow
            logger.error("Failed to create audit entry for inventory removal - adminId: {}, targetUserId: {}, itemId: {}, error: {}", 
                adminId, targetUserId, itemId, e.getMessage(), e);
        }
    }
} 