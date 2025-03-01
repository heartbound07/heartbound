package com.app.heartbound.services;

import com.app.heartbound.dto.UserProfileDTO;
import com.app.heartbound.entities.User;
import com.app.heartbound.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class UserProfileService {
    
    private final UserService userService;
    
    @Autowired
    public UserProfileService(UserService userService) {
        this.userService = userService;
    }
    
    /**
     * Retrieves a user profile by user ID.
     * 
     * @param userId the ID of the user to fetch
     * @return the UserProfileDTO containing basic profile information
     */
    public UserProfileDTO getUserProfile(String userId) {
        User user = userService.getUserById(userId);
        if (user == null) {
            return createDefaultProfile(userId);
        }
        
        return mapToProfileDTO(user);
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
                    profiles.put(userId, mapToProfileDTO(user));
                } else {
                    profiles.put(userId, createDefaultProfile(userId));
                }
            }
        }
        
        return profiles;
    }
    
    /**
     * Maps a User entity to a UserProfileDTO.
     * 
     * @param user the User entity
     * @return the UserProfileDTO with basic profile information
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
