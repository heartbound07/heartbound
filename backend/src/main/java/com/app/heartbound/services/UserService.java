package com.app.heartbound.services;

import com.app.heartbound.dto.UserDTO;
import com.app.heartbound.dto.UpdateProfileDTO;
import com.app.heartbound.dto.UserProfileDTO;
import com.app.heartbound.entities.User;
import com.app.heartbound.repositories.UserRepository;
import com.app.heartbound.exceptions.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    // Constructor-based dependency injection for UserRepository
    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Creates a new user or updates an existing user using the provided UserDTO data.
     *
     * @param userDTO the data transfer object containing user details
     * @return the saved User entity
     */
    public User createOrUpdateUser(UserDTO userDTO) {
        String userId = userDTO.getId();
        logger.debug("Creating or updating user with ID: {}", userId);
        
        // Check if user exists in database
        User existingUser = userRepository.findById(userId).orElse(null);
        
        if (existingUser == null) {
            // Create new user
            User newUser = new User();
            newUser.setId(userId);
            newUser.setUsername(userDTO.getUsername());
            newUser.setEmail(userDTO.getEmail());
            newUser.setAvatar(userDTO.getAvatar());
            newUser.setDiscriminator(userDTO.getDiscriminator());
            
            logger.info("Created new user: {}", newUser.getUsername());
            return userRepository.save(newUser);
        } else {
            // Update existing user information
            existingUser.setUsername(userDTO.getUsername());
            existingUser.setEmail(userDTO.getEmail());
            
            // Special avatar handling logic
            if (shouldUpdateAvatar(existingUser, userDTO.getAvatar())) {
                logger.debug("Updating avatar for user: {}", userId);
                existingUser.setAvatar(userDTO.getAvatar());
            } else {
                logger.debug("Preserving custom avatar for user: {}", userId);
            }
            
            existingUser.setDiscriminator(userDTO.getDiscriminator());
            
            logger.info("Updated existing user: {}", existingUser.getUsername());
            return userRepository.save(existingUser);
        }
    }

    /**
     * Determines if user's avatar should be updated.
     * - Always update if user doesn't have an avatar yet
     * - Always update if existing avatar is a Discord CDN URL
     * - Do not update if user has a custom avatar (Cloudinary URL)
     * - SPECIAL CASE: Update if new avatar is "USE_DISCORD_AVATAR" special marker
     */
    private boolean shouldUpdateAvatar(User user, String newAvatarUrl) {
        // Special case: If new avatar URL is our special "use Discord avatar" marker
        if ("USE_DISCORD_AVATAR".equals(newAvatarUrl)) {
            return true;
        }
        
        // No existing avatar - always update
        if (user.getAvatar() == null || user.getAvatar().isEmpty()) {
            return true;
        }
        
        // If current avatar is from Discord CDN, update it
        if (user.getAvatar().contains("cdn.discordapp.com")) {
            return true;
        }
        
        // Otherwise, it's a custom avatar (Cloudinary) - don't update
        return false;
    }

    /**
     * Retrieves a user by their ID.
     *
     * @param id the user identifier
     * @return the User entity if found, otherwise null
     */
    public User getUserById(String id) {
        return userRepository.findById(id).orElse(null);
    }

    /**
     * Updates profile information for a user.
     *
     * @param userId the ID of the user to update
     * @param updateProfileDTO the profile data to update
     * @return the updated User entity or null if user not found
     */
    public UserProfileDTO updateUserProfile(String userId, UpdateProfileDTO updateProfileDTO) {
        logger.debug("Updating profile for user ID: {}", userId);
        
        // Get the user entity
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));
        
        // Update user profile fields
        user.setDisplayName(updateProfileDTO.getDisplayName());
        user.setPronouns(updateProfileDTO.getPronouns());
        user.setAbout(updateProfileDTO.getAbout());
        user.setBannerColor(updateProfileDTO.getBannerColor());
        user.setBannerUrl(updateProfileDTO.getBannerUrl());
        
        // Special handling for avatar - if avatar is empty string and useDiscordAvatar is true
        // Set it to our special marker
        if (updateProfileDTO.getAvatar() != null && updateProfileDTO.getAvatar().isEmpty()) {
            user.setAvatar("USE_DISCORD_AVATAR");
        } else if (updateProfileDTO.getAvatar() != null) {
            // Otherwise use the provided avatar
            user.setAvatar(updateProfileDTO.getAvatar());
        }
        
        // Save the updated user
        User updatedUser = userRepository.save(user);
        logger.info("Updated profile for user: {}", updatedUser.getUsername());
        
        // Convert to DTO and return
        return mapToProfileDTO(updatedUser);
    }

    /**
     * Enhanced mapToProfileDTO method that includes all profile fields.
     */
    public UserProfileDTO mapToProfileDTO(User user) {
        return UserProfileDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .avatar(user.getAvatar() != null ? user.getAvatar() : "/default-avatar.png")
                .displayName(user.getDisplayName())
                .pronouns(user.getPronouns())
                .about(user.getAbout())
                .bannerColor(user.getBannerColor())
                .bannerUrl(user.getBannerUrl())
                .build();
    }
}
