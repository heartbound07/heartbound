package com.app.heartbound.controllers;

import com.app.heartbound.dto.UserProfileDTO;
import com.app.heartbound.enums.Role;
import com.app.heartbound.services.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/admin/roles")
@PreAuthorize("hasRole('ADMIN')")
public class RoleController {
    
    private final UserService userService;
    private static final Logger logger = LoggerFactory.getLogger(RoleController.class);
    
    @Autowired
    public RoleController(UserService userService) {
        this.userService = userService;
    }
    
    /**
     * Get all available roles in the system.
     */
    @GetMapping
    public ResponseEntity<Role[]> getAllRoles() {
        return ResponseEntity.ok(Role.values());
    }
    
    /**
     * Get all users with a specific role.
     */
    @GetMapping("/users/{role}")
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
    @PostMapping("/batch-assign")
    public ResponseEntity<?> batchAssignRoles(
            @RequestBody Map<String, Object> request,
            Authentication authentication) {
        
        String adminId = authentication.getName();
        List<String> userIds = (List<String>) request.get("userIds");
        
        // Check if we're receiving a single role string or multiple roles
        Object roleObj = request.get("role");
        Set<Role> roles = new HashSet<>();
        
        if (roleObj instanceof String) {
            // Single role case
            Role role = Role.valueOf((String) roleObj);
            roles.add(role);
            logger.debug("Batch assigning single role {} to {} users", role, userIds.size());
            
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
            
            logger.debug("Setting {} roles for {} users", roles.size(), userIds.size());
            
            // For each user, replace their roles with the new set
            for (String userId : userIds) {
                userService.setUserRoles(userId, roles, adminId);
            }
        }
        
        return ResponseEntity.ok(Map.of(
            "message", "Roles updated successfully", 
            "userIds", userIds,
            "roles", roles
        ));
    }
}
