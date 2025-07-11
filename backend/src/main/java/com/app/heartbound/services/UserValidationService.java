package com.app.heartbound.services;

import com.app.heartbound.entities.User;
import com.app.heartbound.entities.DiscordBotSettings;
import com.app.heartbound.enums.Gender;
import com.app.heartbound.enums.Rank;
import com.app.heartbound.enums.Region;
import com.app.heartbound.services.discord.DiscordBotSettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserValidationService {

    private final DiscordBotSettingsService discordBotSettingsService;

    public void validateUserForPairing(User user) {
        validateUserRoleSelections(user);
        validateLevelRequirement(user);
    }

    public void validateUserRoleSelections(User user) {
        List<String> missingRoles = new ArrayList<>();
        if (user.getSelectedAgeRoleId() == null || user.getSelectedAgeRoleId().isBlank()) {
            missingRoles.add("Age");
        }
        if (user.getSelectedGenderRoleId() == null || user.getSelectedGenderRoleId().isBlank()) {
            missingRoles.add("Gender");
        }
        if (user.getSelectedRankRoleId() == null || user.getSelectedRankRoleId().isBlank()) {
            missingRoles.add("Rank");
        }
        if (user.getSelectedRegionRoleId() == null || user.getSelectedRegionRoleId().isBlank()) {
            missingRoles.add("Region");
        }
        if (!missingRoles.isEmpty()) {
            throw new IllegalStateException("Please select all required roles in the roles channel first. Missing: " + String.join(", ", missingRoles));
        }
    }

    public void validateLevelRequirement(User user) {
        DiscordBotSettings settings = discordBotSettingsService.getDiscordBotSettings();
        String heHimRoleId = settings.getGenderHeHimRoleId();
        if (heHimRoleId != null && heHimRoleId.equals(user.getSelectedGenderRoleId())) {
            int userLevel = user.getLevel() != null ? user.getLevel() : 1;
            if (userLevel < 5) {
                log.warn("User {} (level {}) failed level requirement for pairing.", user.getId(), userLevel);
                throw new IllegalStateException("Male users must be level 5 or higher to pair. Your current level is " + userLevel + ".");
            }
        }
    }

    public Integer convertAgeRoleToAge(String roleId) {
        DiscordBotSettings settings = discordBotSettingsService.getDiscordBotSettings();
        if (roleId.equals(settings.getAge15RoleId())) return 15;
        if (roleId.equals(settings.getAge16To17RoleId())) return 16;
        if (roleId.equals(settings.getAge18PlusRoleId())) return 18;
        throw new IllegalStateException("Invalid Age role selected. Please re-select your age role in Discord.");
    }

    public Gender convertGenderRoleToEnum(String roleId) {
        DiscordBotSettings settings = discordBotSettingsService.getDiscordBotSettings();
        if (roleId.equals(settings.getGenderSheHerRoleId())) return Gender.FEMALE;
        if (roleId.equals(settings.getGenderHeHimRoleId())) return Gender.MALE;
        if (roleId.equals(settings.getGenderAskRoleId())) return Gender.PREFER_NOT_TO_SAY;
        throw new IllegalStateException("Invalid Gender role selected. Please re-select your gender role in Discord.");
    }

    public Rank convertRankRoleToEnum(String roleId) {
        DiscordBotSettings settings = discordBotSettingsService.getDiscordBotSettings();
        if (roleId.equals(settings.getRankIronRoleId())) return Rank.IRON;
        if (roleId.equals(settings.getRankBronzeRoleId())) return Rank.BRONZE;
        if (roleId.equals(settings.getRankSilverRoleId())) return Rank.SILVER;
        if (roleId.equals(settings.getRankGoldRoleId())) return Rank.GOLD;
        if (roleId.equals(settings.getRankPlatinumRoleId())) return Rank.PLATINUM;
        if (roleId.equals(settings.getRankDiamondRoleId())) return Rank.DIAMOND;
        if (roleId.equals(settings.getRankAscendantRoleId())) return Rank.ASCENDANT;
        if (roleId.equals(settings.getRankImmortalRoleId())) return Rank.IMMORTAL;
        if (roleId.equals(settings.getRankRadiantRoleId())) return Rank.RADIANT;
        throw new IllegalStateException("Invalid Rank role selected. Please re-select your rank role in Discord.");
    }

    public Region convertRegionRoleToEnum(String roleId) {
        DiscordBotSettings settings = discordBotSettingsService.getDiscordBotSettings();
        if (roleId.equals(settings.getRegionNaRoleId())) return Region.NA_CENTRAL;
        if (roleId.equals(settings.getRegionEuRoleId())) return Region.EU;
        if (roleId.equals(settings.getRegionSaRoleId())) return Region.LATAM;
        if (roleId.equals(settings.getRegionApRoleId())) return Region.AP;
        if (roleId.equals(settings.getRegionOceRoleId())) return Region.AP;
        throw new IllegalStateException("Invalid Region role selected. Please re-select your region role in Discord.");
    }
} 