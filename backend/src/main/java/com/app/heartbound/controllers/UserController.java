package com.app.heartbound.controllers;

import com.app.heartbound.config.security.RateLimited;
import com.app.heartbound.dto.UserProfileDTO;
import com.app.heartbound.dto.LeaderboardEntryDTO;
import com.app.heartbound.dto.UpdateProfileDTO;
import com.app.heartbound.dto.DailyActivityDataDTO;
import com.app.heartbound.dto.shop.UserInventoryItemDTO;
import com.app.heartbound.enums.RateLimitKeyType;
import com.app.heartbound.enums.Role;
import com.app.heartbound.entities.User;
import com.app.heartbound.services.UserService;
import com.app.heartbound.services.UserSecurityService;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.app.heartbound.exceptions.ResourceNotFoundException;

@RestController
@RequestMapping("/users")
public class UserController {
    
    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    private final UserService userService;
    private final UserSecurityService userSecurityService;
    
    public UserController(UserService userService, UserSecurityService userSecurityService) {
        this.userService = userService;
        this.userSecurityService = userSecurityService;
    }
    
    /**
     * Retrieves a user profile by user ID.
     * 
     * @param userId the ID of the user to fetch
     * @return the user profile data
     */
    @GetMapping("/{userId}/profile")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<UserProfileDTO> getUserProfile(@PathVariable String userId, Authentication authentication) {
        // Security validation using centralized service
        if (!userSecurityService.canAccessUserData(authentication, userId)) {
            logger.warn("Unauthorized profile access attempt by user {} for user {}", 
                authentication.getName(), userId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        User user = userService.getUserById(userId);
        if (user == null) {
            return ResponseEntity.ok(createDefaultProfile(userId));
        }
        
        return ResponseEntity.ok(userService.mapToProfileDTO(user));
    }
    
    /**
     * Retrieves multiple user profiles in a single request.
     * 
     * @param request map containing the list of user IDs to fetch
     * @return map of user IDs to their profile data
     */
    @PostMapping("/profiles")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Map<String, UserProfileDTO>> getUserProfiles(@RequestBody Map<String, List<String>> request, Authentication authentication) {
        List<String> userIds = request.get("userIds");
        
        // Security validation: Check input parameters
        if (userIds == null || userIds.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        
        // Security validation: Limit batch size using centralized service
        if (!userSecurityService.validateBatchSize(userIds.size(), 50, "batch_profile_request", authentication.getName())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        
        String authenticatedUserId = authentication.getName();
        boolean isAdmin = userSecurityService.hasAdminRole(authentication);
        
        // Security Check: Validate each user ID individually using centralized security service
        if (!isAdmin) {
            for (String userId : userIds) {
                if (!userSecurityService.canAccessUserData(authentication, userId)) {
                    logger.warn("Unauthorized batch profile access attempt by user {} for user {} in batch {}", 
                        authenticatedUserId, userId, userIds);
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
                }
            }
        }
        
        Map<String, UserProfileDTO> profiles = new HashMap<>();
        
        for (String userId : userIds) {
            User user = userService.getUserById(userId);
            if (user != null) {
                profiles.put(userId, userService.mapToProfileDTO(user));
            } else {
                profiles.put(userId, createDefaultProfile(userId));
            }
        }
        
        return ResponseEntity.ok(profiles);
    }
    
    /**
     * Updates a user profile.
     * 
     * @param userId the ID of the user to update
     * @param profileDTO the profile data to update
     * @return the updated user profile data
     */
    @RateLimited(
        requestsPerMinute = 10,
        requestsPerHour = 50,
        keyType = RateLimitKeyType.USER,
        keyPrefix = "profile_update"
    )
    @PutMapping("/{userId}/profile")
    public ResponseEntity<UserProfileDTO> updateUserProfile(
            @PathVariable String userId,
            @RequestBody @Valid UpdateProfileDTO profileDTO,
            Authentication authentication) {
        
        // Security check - ensure the authenticated user is updating their own profile
        String authenticatedUserId = authentication.getName();
        if (!userId.equals(authenticatedUserId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        try {
            // The method now returns UserProfileDTO directly, not User
            UserProfileDTO updatedProfile = userService.updateUserProfile(userId, profileDTO);
            if (updatedProfile == null) {
                return ResponseEntity.notFound().build();
            }
            
            return ResponseEntity.ok(updatedProfile);
        } catch (Exception e) {
            // Secure error handling - do not expose internal details
            if (e.getMessage() != null && 
                (e.getMessage().contains("sanitization") || 
                 e.getMessage().contains("security") || 
                 e.getMessage().contains("dangerous"))) {
                logger.warn("Profile update validation failed for user {}: {}", userId, e.getMessage());
                return ResponseEntity.badRequest().build();
            }
            
            // Log the actual error for debugging but return generic error to user
            logger.error("Profile update failed for user {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Creates a default profile for users that are not found.
     * 
     * @param userId the ID of the user
     * @return a default UserProfileDTO
     */
    private UserProfileDTO createDefaultProfile(String userId) {
        return UserProfileDTO.builder()
                .id(userId)
                .username("Unknown User")
                .avatar("/default-avatar.png")
                .credits(0)
                .build();
    }
    
    /**
     * Admin endpoint to assign a role to a user.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{userId}/roles")
    public ResponseEntity<UserProfileDTO> assignRole(
            @PathVariable String userId,
            @RequestParam Role role,
            Authentication authentication) {
        
        String adminId = authentication.getName();
        User updatedUser = userService.assignRole(userId, role, adminId);
        return ResponseEntity.ok(userService.mapToProfileDTO(updatedUser));
    }
    
    /**
     * Admin endpoint to remove a role from a user.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{userId}/roles/{role}")
    public ResponseEntity<UserProfileDTO> removeRole(
            @PathVariable String userId,
            @PathVariable Role role,
            Authentication authentication) {
        
        String adminId = authentication.getName();
        User updatedUser = userService.removeRole(userId, role, adminId);
        return ResponseEntity.ok(userService.mapToProfileDTO(updatedUser));
    }
    
    /**
     * Endpoint to upgrade a user to MONARCH (premium) status.
     * This would typically be triggered after payment processing.
     * 
     * ADMIN-ONLY ENDPOINT
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{userId}/upgrade-to-monarch")
    public ResponseEntity<UserProfileDTO> upgradeToMonarch(
            @PathVariable String userId,
            Authentication authentication) {
        
        User upgradedUser = userService.upgradeToMonarch(userId);
        return ResponseEntity.ok(userService.mapToProfileDTO(upgradedUser));
    }
    
    /**
     * Admin/Moderator endpoint to get all users with a specific role.
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")
    @GetMapping("/by-role/{role}")
    public ResponseEntity<List<UserProfileDTO>> getUsersByRole(@PathVariable Role role) {
        List<UserProfileDTO> users = userService.getUsersByRole(role);
        return ResponseEntity.ok(users);
    }
    
    /**
     * Admin endpoint to get all users with pagination.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ResponseEntity<Page<UserProfileDTO>> getAllUsers(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) int size,
            @RequestParam(required = false) String search) {
        
        Page<UserProfileDTO> userProfiles = userService.getAllUsers(page, size, search);
        return ResponseEntity.ok(userProfiles);
    }
    
    /**
     * Update a user's credits.
     * Only accessible to ADMIN users.
     *
     * @param userId the ID of the user to update
     * @param request the request containing the new credits value
     * @return the updated user profile
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{userId}/credits")
    public ResponseEntity<UserProfileDTO> updateUserCredits(
            @PathVariable String userId,
            @RequestBody Map<String, Integer> request) {
        
        Integer credits = request.get("credits");
        if (credits == null) {
            return ResponseEntity.badRequest().build();
        }
        
        User updatedUser = userService.updateUserCredits(userId, credits);
        return ResponseEntity.ok(userService.mapToProfileDTO(updatedUser));
    }
    
    /**
     * Get users for the leaderboard (sorted by credits or level)
     */
    @GetMapping("/leaderboard")
    @RateLimited(requestsPerMinute = 20, keyType = RateLimitKeyType.IP)
    public ResponseEntity<List<LeaderboardEntryDTO>> getLeaderboardUsers(
            @RequestParam(required = false, defaultValue = "credits") String sortBy) {
        List<LeaderboardEntryDTO> leaderboardUsers = userService.getLeaderboardUsers(sortBy);
        return ResponseEntity.ok(leaderboardUsers);
    }
    
    /**
     * Endpoint to get the authenticated user's own profile
     */
    @GetMapping("/me")
    public ResponseEntity<UserProfileDTO> getCurrentUserProfile(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        String userId = authentication.getName();
        User user = userService.getUserById(userId);
        
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        
        return ResponseEntity.ok(userService.mapToProfileDTO(user));
    }
    
    /**
     * Endpoint to get the authenticated user's daily message activity
     */
    @GetMapping("/me/activity/daily-messages")
    public ResponseEntity<List<DailyActivityDataDTO>> getCurrentUserDailyActivity(
            @RequestParam(defaultValue = "30") @Min(1) int days,
            Authentication authentication) {
        
        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        String userId = authentication.getName();
        List<DailyActivityDataDTO> activityData = userService.getUserDailyActivity(userId, days);
        
        return ResponseEntity.ok(activityData);
    }

    /**
     * Endpoint to get the authenticated user's daily voice activity
     */
    @GetMapping("/me/activity/daily-voice")
    public ResponseEntity<List<DailyActivityDataDTO>> getCurrentUserDailyVoiceActivity(
            @RequestParam(defaultValue = "30") @Min(1) int days,
            Authentication authentication) {
        
        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        String userId = authentication.getName();
        List<DailyActivityDataDTO> voiceActivityData = userService.getUserDailyVoiceActivity(userId, days);
        
        return ResponseEntity.ok(voiceActivityData);
    }

    /**
     * Admin endpoint to get a user's inventory items.
     * Only accessible to ADMIN users.
     *
     * @param userId the ID of the user whose inventory to fetch
     * @return list of user's inventory items
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{userId}/inventory")
    public ResponseEntity<List<UserInventoryItemDTO>> getUserInventory(@PathVariable String userId) {
        List<UserInventoryItemDTO> inventoryItems = userService.getUserInventoryItems(userId);
        return ResponseEntity.ok(inventoryItems);
    }

    /**
     * Admin endpoint to remove an item from a user's inventory.
     * Automatically refunds credits if the item was purchased (price > 0).
     * Unequips the item if it's currently equipped.
     * Only accessible to ADMIN users.
     *
     * @param userId the ID of the user whose inventory to modify
     * @param itemId the ID of the item to remove
     * @param authentication the authentication context containing admin ID
     * @return the updated user profile
     */
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{userId}/inventory/{itemId}")
    public ResponseEntity<UserProfileDTO> removeInventoryItem(
            @PathVariable String userId,
            @PathVariable UUID itemId,
            Authentication authentication) {
        
        String adminId = authentication.getName();
        
        try {
            UserProfileDTO updatedProfile = userService.removeInventoryItem(userId, itemId, adminId);
            return ResponseEntity.ok(updatedProfile);
        } catch (ResourceNotFoundException e) {
            logger.warn("Admin {} attempted to remove non-existent item {} from user {}", adminId, itemId, userId);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            // Log detailed error for admin debugging but return generic error
            logger.error("Admin {} failed to remove inventory item {} from user {}: {}", 
                adminId, itemId, userId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
