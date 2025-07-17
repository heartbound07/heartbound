package com.app.heartbound.services;

import com.app.heartbound.dto.UserProfileDTO;
import com.app.heartbound.entities.User;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class UserProfileService {
    
    private final UserService userService;
    
    public UserProfileService(UserService userService) {
        this.userService = userService;
    }
    
    /**
     * Retrieves a user profile by user ID.
     * 
     * @param userId the ID of the user to fetch
     * @return the UserProfileDTO containing complete profile information
     */
    public UserProfileDTO getUserProfile(String userId) {
        User user = userService.getUserById(userId);
        if (user == null) {
            return createDefaultProfile(userId);
        }
        
        // Use UserService's complete mapToProfileDTO method which includes all fields
        return userService.mapToProfileDTO(user);
    }
    
    /**
     * Retrieves multiple user profiles by their IDs.
     * 
     * @param userIds list of user IDs to fetch
     * @return map of user IDs to their profile data
     */
    public Map<String, UserProfileDTO> getUserProfiles(List<String> userIds) {
        Map<String, UserProfileDTO> profiles = new HashMap<>();
        
        if (userIds != null) {
            for (String userId : userIds) {
                User user = userService.getUserById(userId);
                if (user != null) {
                    // Use UserService's complete mapToProfileDTO method
                    profiles.put(userId, userService.mapToProfileDTO(user));
                } else {
                    profiles.put(userId, createDefaultProfile(userId));
                }
            }
        }
        
        return profiles;
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
                .avatar("/images/default-avatar.png")
                .credits(0) // Default credits for unknown users
                .level(1) // Default level
                .experience(0) // Default experience
                .xpForNextLevel(100) // Default XP required for next level
                .messageCount(0L) // Default message count
                .voiceTimeMinutesTotal(0) // Default voice time
                .build();
    }
}
