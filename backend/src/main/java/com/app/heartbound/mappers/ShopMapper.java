package com.app.heartbound.mappers;

import com.app.heartbound.dto.shop.ShopDTO;
import com.app.heartbound.entities.ItemInstance;
import com.app.heartbound.entities.Shop;
import com.app.heartbound.entities.User;
import com.app.heartbound.enums.FishingRodPart;
import com.app.heartbound.enums.ItemRarity;
import com.app.heartbound.enums.ShopCategory;
import com.app.heartbound.repositories.shop.CaseItemRepository;
import com.app.heartbound.utils.LevelingUtil;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class ShopMapper {

    private final CaseItemRepository caseItemRepository;

    public ShopMapper(CaseItemRepository caseItemRepository) {
        this.caseItemRepository = caseItemRepository;
    }

    public ShopDTO mapToShopDTO(Shop shop, User user) {
        boolean owned = false;

        // For cases, never show as owned since they can be purchased multiple times
        if (shop.getCategory() != ShopCategory.CASE && user != null) {
            owned = user.getItemInstances().stream()
                .anyMatch(instance -> instance.getBaseItem().getId().equals(shop.getId()));
        }

        // Check if this is a case and get contents count
        boolean isCase = shop.getCategory() == ShopCategory.CASE;
        Integer caseContentsCount = 0;
        if (isCase) {
            caseContentsCount = Math.toIntExact(caseItemRepository.countByCaseShopItem(shop));
        }

        return ShopDTO.builder()
            .id(shop.getId())
            .name(shop.getName())
            .description(shop.getDescription())
            .price(shop.getPrice())
            .category(shop.getCategory())
            .imageUrl(shop.getImageUrl())
            .thumbnailUrl(shop.getThumbnailUrl())
            .requiredRole(shop.getRequiredRole())
            .owned(owned)
            .expiresAt(shop.getExpiresAt())
            .discordRoleId(shop.getDiscordRoleId())
            .rarity(shop.getRarity() != null ? shop.getRarity() : ItemRarity.COMMON)
            .isCase(isCase)
            .caseContentsCount(caseContentsCount)
            .isFeatured(shop.getIsFeatured())
            .isDaily(shop.getIsDaily())
            .fishingRodMultiplier(shop.getFishingRodMultiplier())
            .gradientEndColor(shop.getGradientEndColor())
            .maxCopies(shop.getMaxCopies())
            .copiesSold(shop.getCopiesSold())
            .maxDurability(shop.getMaxDurability())
            .fishingRodPartType(shop.getFishingRodPartType())
            .durabilityIncrease(shop.getDurabilityIncrease())
            .bonusLootChance(shop.getBonusLootChance())
            .rarityChanceIncrease(shop.getRarityChanceIncrease())
            .multiplierIncrease(shop.getMultiplierIncrease())
            .negationChance(shop.getNegationChance())
            .maxRepairs(shop.getMaxRepairs())
            .build();
    }

    public ShopDTO mapToShopDTO(Shop shop, User user, ItemInstance instance) {
        ShopDTO dto = this.mapToShopDTO(shop, user);
        if (instance != null) {
            dto.setInstanceId(instance.getId());
            dto.setDurability(instance.getDurability());
            dto.setMaxDurability(instance.getMaxDurability());
            dto.setRepairCount(instance.getRepairCount());

            if (shop.getCategory() == ShopCategory.FISHING_ROD) {
                int level = instance.getLevel() != null ? instance.getLevel() : 1;
                dto.setExperience(instance.getExperience());
                dto.setLevel(level);
                dto.setXpForNextLevel(LevelingUtil.calculateXpForRodLevel(level));

                Map<FishingRodPart, ShopDTO> equippedParts = new HashMap<>();
                if (instance.getEquippedRodShaft() != null) {
                    equippedParts.put(FishingRodPart.ROD_SHAFT, this.mapToShopDTO(instance.getEquippedRodShaft().getBaseItem(), user, instance.getEquippedRodShaft()));
                }
                if (instance.getEquippedReel() != null) {
                    equippedParts.put(FishingRodPart.REEL, this.mapToShopDTO(instance.getEquippedReel().getBaseItem(), user, instance.getEquippedReel()));
                }
                if (instance.getEquippedFishingLine() != null) {
                    equippedParts.put(FishingRodPart.FISHING_LINE, this.mapToShopDTO(instance.getEquippedFishingLine().getBaseItem(), user, instance.getEquippedFishingLine()));
                }
                if (instance.getEquippedHook() != null) {
                    equippedParts.put(FishingRodPart.HOOK, this.mapToShopDTO(instance.getEquippedHook().getBaseItem(), user, instance.getEquippedHook()));
                }
                if (instance.getEquippedGrip() != null) {
                    equippedParts.put(FishingRodPart.GRIP, this.mapToShopDTO(instance.getEquippedGrip().getBaseItem(), user, instance.getEquippedGrip()));
                }
                dto.setEquippedParts(equippedParts);
            }
        }
        return dto;
    }
} 