package com.app.heartbound.controllers;

import com.app.heartbound.dto.pairing.*;
import com.app.heartbound.dto.pairing.UpdatePairLevelDTO;
import com.app.heartbound.dto.pairing.ManageAchievementDTO;
import com.app.heartbound.dto.pairing.UpdateVoiceStreakDTO;
import com.app.heartbound.dto.pairing.CreateVoiceStreakDTO;
import com.app.heartbound.entities.PairLevel;
import com.app.heartbound.entities.PairAchievement;
import com.app.heartbound.entities.Achievement;
import com.app.heartbound.entities.VoiceStreak;
import com.app.heartbound.services.pairing.PairLevelService;
import com.app.heartbound.services.pairing.AchievementService;
import com.app.heartbound.services.pairing.VoiceStreakService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * PairLevelController
 * 
 * REST controller for managing XP, levels, achievements, and voice streaks for pairings.
 */
@RestController
@RequestMapping("/pairings")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Pair Levels & Achievements", description = "Endpoints for managing XP, levels, achievements, and voice streaks")
public class PairLevelController {

    private final PairLevelService pairLevelService;
    private final AchievementService achievementService;
    private final VoiceStreakService voiceStreakService;

    @Operation(summary = "Get pair level and XP information", description = "Get current level and XP data for a pairing")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Pair level retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Pairing not found"),
            @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @GetMapping("/{pairingId}/level")
    @PreAuthorize("@pairingSecurityService.isUserInPairing(authentication, #pairingId)")
    public ResponseEntity<PairLevelDTO> getPairLevel(@PathVariable Long pairingId) {
        log.info("Getting pair level for pairing {}", pairingId);
        
        try {
            Optional<PairLevel> pairLevel = pairLevelService.getPairLevel(pairingId);
            
            if (pairLevel.isEmpty()) {
                // Create new pair level if it doesn't exist
                PairLevel newLevel = pairLevelService.getOrCreatePairLevel(pairingId);
                return ResponseEntity.ok(mapToPairLevelDTO(newLevel));
            }
            
            return ResponseEntity.ok(mapToPairLevelDTO(pairLevel.get()));
            
        } catch (IllegalArgumentException e) {
            log.warn("Invalid request for pair level: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (Exception e) {
            log.error("Error retrieving pair level for pairing {}", pairingId, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to retrieve pair level");
        }
    }

    @Operation(summary = "Get achievements for a pairing", description = "Get all completed achievements for a pairing")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Achievements retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Pairing not found"),
            @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @GetMapping("/{pairingId}/achievements")
    @PreAuthorize("@pairingSecurityService.isUserInPairing(authentication, #pairingId)")
    public ResponseEntity<List<PairAchievementDTO>> getPairingAchievements(@PathVariable Long pairingId) {
        log.info("Getting achievements for pairing {}", pairingId);
        
        try {
            List<PairAchievement> achievements = achievementService.getPairingAchievements(pairingId);
            List<PairAchievementDTO> achievementDTOs = achievements.stream()
                    .map(this::mapToPairAchievementDTO)
                    .toList();
            
            return ResponseEntity.ok(achievementDTOs);
            
        } catch (Exception e) {
            log.error("Error retrieving achievements for pairing {}", pairingId, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to retrieve achievements");
        }
    }

    @Operation(summary = "Get available achievements", description = "Get achievements that can still be unlocked for a pairing")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Available achievements retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Pairing not found"),
            @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @GetMapping("/{pairingId}/achievements/available")
    @PreAuthorize("@pairingSecurityService.isUserInPairing(authentication, #pairingId)")
    public ResponseEntity<List<AchievementDTO>> getAvailableAchievements(@PathVariable Long pairingId) {
        log.info("Getting available achievements for pairing {}", pairingId);
        
        try {
            List<Achievement> achievements = achievementService.getAvailableAchievements(pairingId);
            List<AchievementDTO> achievementDTOs = achievements.stream()
                    .map(this::mapToAchievementDTO)
                    .toList();
            
            return ResponseEntity.ok(achievementDTOs);
            
        } catch (Exception e) {
            log.error("Error retrieving available achievements for pairing {}", pairingId, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to retrieve available achievements");
        }
    }

    @Operation(summary = "Get voice streaks for a pairing", description = "Get voice activity streaks for a pairing")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Voice streaks retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Pairing not found"),
            @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @GetMapping("/{pairingId}/streaks")
    @PreAuthorize("@pairingSecurityService.isUserInPairing(authentication, #pairingId)")
    public ResponseEntity<Map<String, Object>> getVoiceStreaks(@PathVariable Long pairingId) {
        log.info("Getting voice streaks for pairing {}", pairingId);
        
        try {
            Map<String, Object> streakStats = voiceStreakService.getStreakStatistics(pairingId);
            List<VoiceStreak> recentStreaks = voiceStreakService.getVoiceStreaks(pairingId);
            
            // Limit to last 30 days for performance
            List<VoiceStreakDTO> streakDTOs = recentStreaks.stream()
                    .limit(30)
                    .map(this::mapToVoiceStreakDTO)
                    .toList();
            
            Map<String, Object> response = Map.of(
                "statistics", streakStats,
                "recentStreaks", streakDTOs
            );
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error retrieving voice streaks for pairing {}", pairingId, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to retrieve voice streaks");
        }
    }

    @Operation(summary = "Check achievements manually", description = "Manually trigger achievement checking for a pairing (admin only)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Achievements checked successfully"),
            @ApiResponse(responseCode = "404", description = "Pairing not found"),
            @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @PostMapping("/achievements/check/{pairingId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<PairAchievementDTO>> checkAchievements(@PathVariable Long pairingId) {
        log.info("Manually checking achievements for pairing {}", pairingId);
        
        try {
            List<PairAchievement> newAchievements = achievementService.checkAndUnlockAchievements(pairingId);
            List<PairAchievementDTO> achievementDTOs = newAchievements.stream()
                    .map(this::mapToPairAchievementDTO)
                    .toList();
            
            return ResponseEntity.ok(achievementDTOs);
            
        } catch (IllegalArgumentException e) {
            log.warn("Invalid request for achievement check: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (Exception e) {
            log.error("Error checking achievements for pairing {}", pairingId, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to check achievements");
        }
    }

    // ===== ADMIN ENDPOINTS =====

    @Operation(summary = "Admin: Update pair level and XP", description = "Admin-only endpoint to directly modify pair level and XP")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Pair level updated successfully"),
            @ApiResponse(responseCode = "404", description = "Pairing not found"),
            @ApiResponse(responseCode = "403", description = "Admin access required")
    })
    @PatchMapping("/{pairingId}/level/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PairLevelDTO> updatePairLevelAdmin(
            @PathVariable Long pairingId,
            @RequestBody UpdatePairLevelDTO updateRequest) {
        log.info("Admin updating pair level for pairing {}: {}", pairingId, updateRequest);
        
        try {
            PairLevel updatedLevel = pairLevelService.updatePairLevelAdmin(pairingId, updateRequest);
            return ResponseEntity.ok(mapToPairLevelDTO(updatedLevel));
            
        } catch (IllegalArgumentException e) {
            log.warn("Invalid request for pair level update: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (Exception e) {
            log.error("Error updating pair level for pairing {}", pairingId, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to update pair level");
        }
    }

    @Operation(summary = "Admin: Manage achievement", description = "Admin-only endpoint to manually unlock or lock achievements")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Achievement managed successfully"),
            @ApiResponse(responseCode = "404", description = "Pairing or achievement not found"),
            @ApiResponse(responseCode = "403", description = "Admin access required")
    })
    @PostMapping("/{pairingId}/achievements/admin/manage")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> manageAchievement(
            @PathVariable Long pairingId,
            @RequestBody ManageAchievementDTO manageRequest) {
        log.info("Admin managing achievement for pairing {}: {}", pairingId, manageRequest);
        
        try {
            Map<String, Object> result = achievementService.manageAchievementAdmin(pairingId, manageRequest);
            return ResponseEntity.ok(result);
            
        } catch (IllegalArgumentException e) {
            log.warn("Invalid request for achievement management: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (Exception e) {
            log.error("Error managing achievement for pairing {}", pairingId, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to manage achievement");
        }
    }

    @Operation(summary = "Admin: Update voice streak", description = "Admin-only endpoint to update an existing voice streak")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Voice streak updated successfully"),
            @ApiResponse(responseCode = "404", description = "Voice streak not found"),
            @ApiResponse(responseCode = "403", description = "Admin access required")
    })
    @PatchMapping("/voice-streaks/{streakId}/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<VoiceStreakDTO> updateVoiceStreakAdmin(
            @PathVariable Long streakId,
            @RequestBody UpdateVoiceStreakDTO updateRequest) {
        log.info("Admin updating voice streak {}: {}", streakId, updateRequest);
        
        try {
            VoiceStreak updatedStreak = voiceStreakService.updateVoiceStreakAdmin(streakId, updateRequest);
            return ResponseEntity.ok(mapToVoiceStreakDTO(updatedStreak));
            
        } catch (IllegalArgumentException e) {
            log.warn("Invalid request for voice streak update: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (Exception e) {
            log.error("Error updating voice streak {}", streakId, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to update voice streak");
        }
    }

    @Operation(summary = "Admin: Create voice streak", description = "Admin-only endpoint to create a new voice streak")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Voice streak created successfully"),
            @ApiResponse(responseCode = "404", description = "Pairing not found"),
            @ApiResponse(responseCode = "403", description = "Admin access required")
    })
    @PostMapping("/{pairingId}/streaks/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<VoiceStreakDTO> createVoiceStreakAdmin(
            @PathVariable Long pairingId,
            @RequestBody CreateVoiceStreakDTO createRequest) {
        log.info("Admin creating voice streak for pairing {}: {}", pairingId, createRequest);
        
        try {
            VoiceStreak newStreak = voiceStreakService.createVoiceStreakAdmin(pairingId, createRequest);
            return ResponseEntity.ok(mapToVoiceStreakDTO(newStreak));
            
        } catch (IllegalArgumentException e) {
            log.warn("Invalid request for voice streak creation: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (Exception e) {
            log.error("Error creating voice streak for pairing {}", pairingId, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to create voice streak");
        }
    }

    @Operation(summary = "Admin: Delete voice streak", description = "Admin-only endpoint to delete a voice streak")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Voice streak deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Voice streak not found"),
            @ApiResponse(responseCode = "403", description = "Admin access required")
    })
    @DeleteMapping("/voice-streaks/{streakId}/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> deleteVoiceStreakAdmin(@PathVariable Long streakId) {
        log.info("Admin deleting voice streak {}", streakId);
        
        try {
            voiceStreakService.deleteVoiceStreakAdmin(streakId);
            return ResponseEntity.ok(Map.of("message", "Voice streak deleted successfully"));
            
        } catch (IllegalArgumentException e) {
            log.warn("Invalid request for voice streak deletion: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (Exception e) {
            log.error("Error deleting voice streak {}", streakId, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to delete voice streak");
        }
    }

    @Operation(summary = "Get level leaderboard", description = "Get top pairs by level")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Leaderboard retrieved successfully")
    })
    @GetMapping("/leaderboard/levels")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<PairLevelDTO>> getLevelLeaderboard(
            @Parameter(description = "Number of top pairs to return") 
            @RequestParam(defaultValue = "10") int limit) {
        log.info("Getting level leaderboard with limit {}", limit);
        
        try {
            List<PairLevel> topPairs = pairLevelService.getTopLevelPairs(Math.min(limit, 50)); // Cap at 50
            List<PairLevelDTO> leaderboard = topPairs.stream()
                    .map(this::mapToPairLevelDTO)
                    .toList();
            
            return ResponseEntity.ok(leaderboard);
            
        } catch (Exception e) {
            log.error("Error retrieving level leaderboard", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to retrieve leaderboard");
        }
    }

    @Operation(summary = "Get XP leaderboard", description = "Get top pairs by total XP")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Leaderboard retrieved successfully")
    })
    @GetMapping("/leaderboard/xp")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<PairLevelDTO>> getXPLeaderboard(
            @Parameter(description = "Number of top pairs to return") 
            @RequestParam(defaultValue = "10") int limit) {
        log.info("Getting XP leaderboard with limit {}", limit);
        
        try {
            List<PairLevel> topPairs = pairLevelService.getTopXPPairs(Math.min(limit, 50)); // Cap at 50
            List<PairLevelDTO> leaderboard = topPairs.stream()
                    .map(this::mapToPairLevelDTO)
                    .toList();
            
            return ResponseEntity.ok(leaderboard);
            
        } catch (Exception e) {
            log.error("Error retrieving XP leaderboard", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to retrieve leaderboard");
        }
    }

    // Mapping methods
    private PairLevelDTO mapToPairLevelDTO(PairLevel pairLevel) {
        return PairLevelDTO.builder()
                .id(pairLevel.getId())
                .pairingId(pairLevel.getPairing().getId())
                .currentLevel(pairLevel.getCurrentLevel())
                .totalXP(pairLevel.getTotalXP())
                .currentLevelXP(pairLevel.getCurrentLevelXP())
                .nextLevelXP(pairLevel.getNextLevelXP())
                .xpNeededForNextLevel(pairLevel.getXPNeededForNextLevel())
                .levelProgressPercentage(pairLevel.getLevelProgressPercentage())
                .readyToLevelUp(pairLevel.isReadyToLevelUp())
                .createdAt(pairLevel.getCreatedAt())
                .updatedAt(pairLevel.getUpdatedAt())
                .build();
    }

    private AchievementDTO mapToAchievementDTO(Achievement achievement) {
        return AchievementDTO.builder()
                .id(achievement.getId())
                .achievementKey(achievement.getAchievementKey())
                .name(achievement.getName())
                .description(achievement.getDescription())
                .achievementType(achievement.getAchievementType())
                .xpReward(achievement.getXpReward())
                .requirementValue(achievement.getRequirementValue())
                .requirementDescription(achievement.getRequirementDescription())
                .iconUrl(achievement.getIconUrl())
                .badgeColor(achievement.getBadgeColor())
                .rarity(achievement.getRarity())
                .tier(achievement.getTier())
                .active(achievement.isActive())
                .hidden(achievement.isHidden())
                .createdAt(achievement.getCreatedAt())
                .updatedAt(achievement.getUpdatedAt())
                .build();
    }

    private PairAchievementDTO mapToPairAchievementDTO(PairAchievement pairAchievement) {
        return PairAchievementDTO.builder()
                .id(pairAchievement.getId())
                .pairingId(pairAchievement.getPairing().getId())
                .achievement(mapToAchievementDTO(pairAchievement.getAchievement()))
                .unlockedAt(pairAchievement.getUnlockedAt())
                .progressValue(pairAchievement.getProgressValue())
                .xpAwarded(pairAchievement.getXpAwarded())
                .notified(pairAchievement.isNotified())
                .recentlyUnlocked(pairAchievement.isRecentlyUnlocked())
                .unlockTimeDisplay(pairAchievement.getUnlockTimeDisplay())
                .createdAt(pairAchievement.getCreatedAt())
                .build();
    }

    private VoiceStreakDTO mapToVoiceStreakDTO(VoiceStreak voiceStreak) {
        return VoiceStreakDTO.builder()
                .id(voiceStreak.getId())
                .pairingId(voiceStreak.getPairing().getId())
                .streakDate(voiceStreak.getStreakDate())
                .voiceMinutes(voiceStreak.getVoiceMinutes())
                .streakCount(voiceStreak.getStreakCount())
                .active(voiceStreak.isActive())
                .isToday(voiceStreak.isToday())
                .isYesterday(voiceStreak.isYesterday())
                .meetsMinimumActivity(voiceStreak.meetsMinimumActivity())
                .streakXPReward(voiceStreak.getStreakXPReward())
                .streakTier(voiceStreak.getStreakTier())
                .createdAt(voiceStreak.getCreatedAt())
                .updatedAt(voiceStreak.getUpdatedAt())
                .build();
    }
} 