package com.app.heartbound.controllers;

import com.app.heartbound.dto.UserProfileDTO;
import com.app.heartbound.dto.UpdateProfileDTO;
import com.app.heartbound.enums.Role;
import com.app.heartbound.entities.User;
import com.app.heartbound.services.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/users")
public class UserController {
    
    private final UserService userService;
    
    @Autowired
    public UserController(UserService userService) {
        this.userService = userService;
    }
    
    /**
     * Retrieves a user profile by user ID.
     * 
     * @param userId the ID of the user to fetch
     * @return the user profile data
     */
    @GetMapping("/{userId}/profile")
    public ResponseEntity<UserProfileDTO> getUserProfile(@PathVariable String userId) {
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
    public ResponseEntity<Map<String, UserProfileDTO>> getUserProfiles(@RequestBody Map<String, List<String>> request) {
        List<String> userIds = request.get("userIds");
        Map<String, UserProfileDTO> profiles = new HashMap<>();
        
        if (userIds != null) {
            for (String userId : userIds) {
                User user = userService.getUserById(userId);
                if (user != null) {
                    profiles.put(userId, userService.mapToProfileDTO(user));
                } else {
                    profiles.put(userId, createDefaultProfile(userId));
                }
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
    @PutMapping("/{userId}/profile")
    public ResponseEntity<UserProfileDTO> updateUserProfile(
            @PathVariable String userId,
            @RequestBody UpdateProfileDTO profileDTO,
            Authentication authentication) {
        
        // Security check - ensure the authenticated user is updating their own profile
        String authenticatedUserId = authentication.getName();
        if (!userId.equals(authenticatedUserId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        // The method now returns UserProfileDTO directly, not User
        UserProfileDTO updatedProfile = userService.updateUserProfile(userId, profileDTO);
        if (updatedProfile == null) {
            return ResponseEntity.notFound().build();
        }
        
        return ResponseEntity.ok(updatedProfile);
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
     */
    @PostMapping("/{userId}/upgrade-to-monarch")
    public ResponseEntity<UserProfileDTO> upgradeToMonarch(
            @PathVariable String userId,
            Authentication authentication) {
        
        // Security check - ensure the authenticated user is upgrading their own account
        // or is an admin
        String authenticatedUserId = authentication.getName();
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        
        if (!userId.equals(authenticatedUserId) && !isAdmin) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
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
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
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
    public ResponseEntity<List<UserProfileDTO>> getLeaderboardUsers(
            @RequestParam(required = false, defaultValue = "credits") String sortBy) {
        List<UserProfileDTO> leaderboardUsers = userService.getLeaderboardUsers(sortBy);
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
}
