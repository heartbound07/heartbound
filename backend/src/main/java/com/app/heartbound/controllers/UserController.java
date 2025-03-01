package com.app.heartbound.controllers;

import com.app.heartbound.dto.UserProfileDTO;
import com.app.heartbound.entities.User;
import com.app.heartbound.services.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
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
        
        return ResponseEntity.ok(mapToProfileDTO(user));
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
                    profiles.put(userId, mapToProfileDTO(user));
                } else {
                    profiles.put(userId, createDefaultProfile(userId));
                }
            }
        }
        
        return ResponseEntity.ok(profiles);
    }
    
    /**
     * Maps a User entity to a UserProfileDTO.
     * 
     * @param user the User entity
     * @return the UserProfileDTO
     */
    private UserProfileDTO mapToProfileDTO(User user) {
        return UserProfileDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .avatar(user.getAvatar() != null ? user.getAvatar() : "/default-avatar.png")
                .build();
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
                .build();
    }
}
