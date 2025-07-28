package com.app.heartbound.services;

import com.app.heartbound.dto.EconomyStatsDTO;
import com.app.heartbound.enums.ItemRarity;
import com.app.heartbound.repositories.ItemInstanceRepository;
import com.app.heartbound.repositories.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.Map;

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

        Map<ItemRarity, String> recommendedPriceRanges = new EnumMap<>(ItemRarity.class);
        recommendedPriceRanges.put(ItemRarity.COMMON, "1 - 10 credits");
        recommendedPriceRanges.put(ItemRarity.UNCOMMON, "10 - 50 credits");
        recommendedPriceRanges.put(ItemRarity.RARE, "50 - 250 credits");
        recommendedPriceRanges.put(ItemRarity.EPIC, "250 - 1000 credits");
        recommendedPriceRanges.put(ItemRarity.LEGENDARY, "1000+ credits");


        return EconomyStatsDTO.builder()
                .totalCredits(totalCredits)
                .totalUsers(totalUsers)
                .averageCreditsPerUser(averageCreditsPerUser)
                .itemsInCirculation(itemsInCirculation)
                .recommendedPriceRanges(recommendedPriceRanges)
                .build();
    }
} 