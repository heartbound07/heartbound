package com.app.heartbound.repositories.pairing;

import com.app.heartbound.entities.Achievement;
import com.app.heartbound.enums.AchievementType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AchievementRepository extends JpaRepository<Achievement, Long> {

    // Find achievement by unique key
    Optional<Achievement> findByAchievementKey(String achievementKey);

    // Find all active achievements
    List<Achievement> findByActiveTrue();

    // Find achievements by type
    List<Achievement> findByAchievementTypeAndActiveTrue(AchievementType achievementType);

    // Find achievements by type and requirement value
    @Query("SELECT a FROM Achievement a WHERE a.achievementType = :type AND a.requirementValue = :value AND a.active = true")
    Optional<Achievement> findByTypeAndRequirementValue(@Param("type") AchievementType type, @Param("value") int value);

    // Find achievements by rarity
    List<Achievement> findByRarityAndActiveTrue(String rarity);

    // Find achievements with requirement value less than or equal to given value
    @Query("SELECT a FROM Achievement a WHERE a.achievementType = :type AND a.requirementValue <= :value AND a.active = true ORDER BY a.requirementValue DESC")
    List<Achievement> findEligibleAchievements(@Param("type") AchievementType type, @Param("value") int value);

    // Find all achievement types
    @Query("SELECT DISTINCT a.achievementType FROM Achievement a WHERE a.active = true")
    List<AchievementType> findDistinctAchievementTypes();

    // Count achievements by type
    @Query("SELECT a.achievementType, COUNT(a) FROM Achievement a WHERE a.active = true GROUP BY a.achievementType")
    List<Object[]> countAchievementsByType();

    // Find achievements by XP reward range
    @Query("SELECT a FROM Achievement a WHERE a.xpReward BETWEEN :minXP AND :maxXP AND a.active = true ORDER BY a.xpReward DESC")
    List<Achievement> findByXPRewardRange(@Param("minXP") int minXP, @Param("maxXP") int maxXP);

    // Check if achievement key exists
    boolean existsByAchievementKey(String achievementKey);
} 