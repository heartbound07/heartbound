package com.app.heartbound.controllers;

import com.app.heartbound.dto.UpdateProfileDTO;
import com.app.heartbound.dto.UserProfileDTO;
import com.app.heartbound.entities.User;
import com.app.heartbound.enums.Role;
import com.app.heartbound.services.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.app.heartbound.exceptions.ResourceNotFoundException;
import com.app.heartbound.exceptions.UnauthorizedOperationException;

@RestController
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public class RoleController {
    
    private final UserService userService;
    private static final Logger logger = LoggerFactory.getLogger(RoleController.class);
    
    public RoleController(UserService userService) {
        this.userService = userService;
    }
    
    /**
     * Get all available roles in the system.
     */
    @GetMapping("/roles")
    public ResponseEntity<Role[]> getAllRoles() {
        return ResponseEntity.ok(Role.values());
    }
    
    /**
     * Get all users with a specific role.
     */
    @GetMapping("/roles/users/{role}")
    public ResponseEntity<List<UserProfileDTO>> getUsersByRole(@PathVariable Role role) {
        List<UserProfileDTO> users = userService.getUsersByRole(role);
        return ResponseEntity.ok(users);
    }
    
    /**
     * Batch assign roles to users.
     * This endpoint can handle:
     * 1. Setting multiple roles for a single user (replaces existing roles)
     * 2. Assigning a single role to multiple users
     */
    @PostMapping("/roles/batch-assign")
    public ResponseEntity<?> batchAssignRoles(
            @RequestBody Map<String, Object> request,
            Authentication authentication) {
        
        String adminId = authentication.getName();
        
        Object userIdsRaw = request.get("userIds");
        if (!(userIdsRaw instanceof List)) {
            logger.warn("Admin {} attempted batch role assignment with missing or invalid userIds field", adminId);
            return ResponseEntity.badRequest().body(Map.of("error", "The 'userIds' field is required and must be a list."));
        }

        List<String> userIds;
        try {
            userIds = ((List<?>) userIdsRaw).stream()
                    .map(String.class::cast)
                    .collect(Collectors.toList());
        } catch (ClassCastException e) {
            logger.warn("Admin {} provided a non-string user ID in batch assignment.", adminId);
            return ResponseEntity.badRequest().body(Map.of("error", "All user IDs in the list must be strings."));
        }
        
        // Security validation: Check input parameters
        if (userIds.isEmpty()) {
            logger.warn("Admin {} attempted batch role assignment with no user IDs", adminId);
            return ResponseEntity.badRequest().body(Map.of("error", "User IDs are required"));
        }
        
        // Security limit: Prevent DoS attacks through large batch operations
        if (userIds.size() > 100) {
            logger.warn("Admin {} attempted batch role assignment exceeding limit: {} users", adminId, userIds.size());
            return ResponseEntity.badRequest().body(Map.of("error", "Batch size cannot exceed 100 users"));
        }
        
        // Check if we're receiving a single role string or multiple roles
        Object roleObj = request.get("role");
        if (roleObj == null) {
            logger.warn("Admin {} attempted batch role assignment with no roles specified", adminId);
            return ResponseEntity.badRequest().body(Map.of("error", "Roles are required"));
        }
        
        Set<Role> roles = new HashSet<>();
        
        try {
            if (roleObj instanceof String) {
                // Single role case
                Role role = Role.valueOf((String) roleObj);
                roles.add(role);
                logger.info("Admin {} batch assigning single role {} to {} users", adminId, role, userIds.size());
                
                // Original behavior - assign single role to multiple users
                for (String userId : userIds) {
                    userService.assignRole(userId, role, adminId);
                }
            } else if (roleObj instanceof List) {
                // Multiple roles case - typically for a single user
                List<?> rolesList = (List<?>) roleObj;
                roles = rolesList.stream()
                        .map(r -> Role.valueOf(r.toString()))
                        .collect(Collectors.toSet());
                
                logger.info("Admin {} setting {} roles for {} users", adminId, roles.size(), userIds.size());
                
                // For each user, replace their roles with the new set
                for (String userId : userIds) {
                    userService.setUserRoles(userId, roles, adminId);
                }
            } else {
                logger.warn("Admin {} provided invalid role format in batch assignment", adminId);
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid role format"));
            }
        } catch (IllegalArgumentException e) {
            logger.warn("Admin {} attempted to assign invalid role: {}", adminId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid role specified"));
        } catch (Exception e) {
            logger.error("Admin {} batch role assignment failed: {}", adminId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Role assignment failed"));
        }
        
        return ResponseEntity.ok(Map.of(
            "message", "Roles updated successfully", 
            "userIds", userIds,
            "roles", roles
        ));
    }

    /**
     * Admin endpoint to update a user's profile.
     *
     * @param userId       the ID of the user to update
     * @param profileDTO   the profile data to update
     * @param authentication the authentication context
     * @return the updated user profile
     */
    @PutMapping("/users/{userId}/profile")
    public ResponseEntity<UserProfileDTO> adminUpdateUserProfile(
            @PathVariable String userId,
            @RequestBody @Valid UpdateProfileDTO profileDTO,
            Authentication authentication) {

        try {
            User updatedUser = userService.adminUpdateUserProfile(userId, profileDTO);
            if (updatedUser == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(userService.mapToProfileDTO(updatedUser));
        } catch (Exception e) {
            logger.error("Admin failed to update profile for user {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Admin endpoint to permanently delete a user.
     *
     * @param userId the ID of the user to delete
     * @param authentication the authentication context
     * @return a confirmation message
     */
    @DeleteMapping("/users/{userId}")
    public ResponseEntity<?> deleteUser(@PathVariable String userId, Authentication authentication) {
        String adminId = authentication.getName();
        try {
            userService.deleteUser(userId, adminId);
            return ResponseEntity.ok(Map.of("message", "User " + userId + " has been permanently deleted."));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (UnauthorizedOperationException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Admin {} failed to delete user {}: {}", adminId, userId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
