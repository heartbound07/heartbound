package com.app.heartbound.services;

import com.app.heartbound.dto.EconomyStatsDTO;
import com.app.heartbound.enums.ItemRarity;
import com.app.heartbound.repositories.ItemInstanceRepository;
import com.app.heartbound.repositories.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class EconomyService {

    private final UserRepository userRepository;
    private final ItemInstanceRepository itemInstanceRepository;

    public EconomyService(UserRepository userRepository, ItemInstanceRepository itemInstanceRepository) {
        this.userRepository = userRepository;
        this.itemInstanceRepository = itemInstanceRepository;
    }

    @Transactional(readOnly = true)
    public EconomyStatsDTO getEconomyStats() {
        long totalCredits = userRepository.getTotalCredits();
        long totalUsers = userRepository.count();
        long itemsInCirculation = itemInstanceRepository.countTotalInstances();
        double averageCreditsPerUser = (totalUsers > 0) ? (double) totalCredits / totalUsers : 0;

        Map<ItemRarity, Long> itemsByRarity = itemInstanceRepository.countByRarity().stream()
                .collect(Collectors.toMap(
                        row -> (ItemRarity) row[0],
                        row -> (Long) row[1]
                ));

        Map<ItemRarity, String> recommendedPriceRanges = calculateRecommendedPrices(averageCreditsPerUser, itemsInCirculation, itemsByRarity);


        return EconomyStatsDTO.builder()
                .totalCredits(totalCredits)
                .totalUsers(totalUsers)
                .averageCreditsPerUser(averageCreditsPerUser)
                .itemsInCirculation(itemsInCirculation)
                .recommendedPriceRanges(recommendedPriceRanges)
                .build();
    }

    private Map<ItemRarity, String> calculateRecommendedPrices(double averageCreditsPerUser, long totalItemsInCirculation, Map<ItemRarity, Long> itemsByRarity) {
        Map<ItemRarity, String> priceRanges = new EnumMap<>(ItemRarity.class);
        Map<ItemRarity, Double> basePrices = getBasePrices();
        final double economyFactor = Math.max(1.0, averageCreditsPerUser / 500.0); // Baseline average is 500

        for (ItemRarity rarity : ItemRarity.values()) {
            long countForRarity = itemsByRarity.getOrDefault(rarity, 0L);
            double basePrice = basePrices.get(rarity);
            double scarcityMultiplier;

            if (totalItemsInCirculation == 0) {
                scarcityMultiplier = 2.5; // Default multiplier when no items exist
            } else if (countForRarity == 0) {
                scarcityMultiplier = 5.0; // High multiplier for uncirculated rarities
            } else {
                double marketShare = (double) countForRarity / totalItemsInCirculation;
                scarcityMultiplier = 1.0 + (1.0 - marketShare) * 2.0; // Scale from 1.0 up to 3.0
            }

            double calculatedPrice = basePrice * economyFactor * scarcityMultiplier;

            long minPrice = Math.round(calculatedPrice * 0.85);
            long maxPrice = Math.round(calculatedPrice * 1.15);

            // Ensure min is at least 1
            minPrice = Math.max(1, minPrice);
            maxPrice = Math.max(minPrice + 1, maxPrice);

            priceRanges.put(rarity, String.format("%d - %d credits", minPrice, maxPrice));
        }

        return priceRanges;
    }

    private Map<ItemRarity, Double> getBasePrices() {
        Map<ItemRarity, Double> basePrices = new EnumMap<>(ItemRarity.class);
        basePrices.put(ItemRarity.COMMON, 5.0);
        basePrices.put(ItemRarity.UNCOMMON, 20.0);
        basePrices.put(ItemRarity.RARE, 100.0);
        basePrices.put(ItemRarity.EPIC, 450.0);
        basePrices.put(ItemRarity.LEGENDARY, 2000.0);
        return basePrices;
    }
} 