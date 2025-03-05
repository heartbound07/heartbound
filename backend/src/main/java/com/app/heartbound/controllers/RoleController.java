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

@RestController
@RequestMapping("/admin/roles")
@PreAuthorize("hasRole('ADMIN')")
public class RoleController {
    
    private final UserService userService;
    
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
     */
    @PostMapping("/batch-assign")
    public ResponseEntity<?> batchAssignRoles(
            @RequestBody Map<String, Object> request,
            Authentication authentication) {
        
        String adminId = authentication.getName();
        List<String> userIds = (List<String>) request.get("userIds");
        Role role = Role.valueOf((String) request.get("role"));
        
        for (String userId : userIds) {
            userService.assignRole(userId, role, adminId);
        }
        
        return ResponseEntity.ok(Map.of("message", "Roles assigned successfully"));
    }
}
