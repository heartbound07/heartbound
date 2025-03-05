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
            
            // Cache the Discord avatar URL
            if (userDTO.getAvatar() != null && userDTO.getAvatar().contains("cdn.discordapp.com")) {
                newUser.setDiscordAvatarUrl(userDTO.getAvatar());
            }
            
            logger.info("Created new user: {}", newUser.getUsername());
            return userRepository.save(newUser);
        } else {
            // Update existing user information
            existingUser.setUsername(userDTO.getUsername());
            existingUser.setEmail(userDTO.getEmail());
            
            // ALWAYS cache the Discord avatar URL if we receive one from Discord
            // This ensures we always have the latest Discord avatar to fall back to
            if (userDTO.getAvatar() != null && userDTO.getAvatar().contains("cdn.discordapp.com")) {
                existingUser.setDiscordAvatarUrl(userDTO.getAvatar());
                logger.debug("Updated Discord avatar cache for user: {}", userId);
            }
            
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
        
        // Special handling for avatar
        if (updateProfileDTO.getAvatar() != null && updateProfileDTO.getAvatar().isEmpty()) {
            // Empty avatar string means use Discord avatar
            user.setAvatar("USE_DISCORD_AVATAR");
            
            // Make sure we have a Discord avatar URL to fall back to
            if (user.getDiscordAvatarUrl() == null || user.getDiscordAvatarUrl().isEmpty()) {
                // If no cached Discord avatar, attempt to fetch it
                try {
                    // Here we would ideally fetch it from Discord API
                    // For now, we'll add a log to identify this issue
                    logger.warn("No cached Discord avatar URL available for user: {}", userId);
                } catch (Exception e) {
                    logger.error("Error fetching Discord avatar: {}", e.getMessage());
                }
            }
        } else if (updateProfileDTO.getAvatar() != null) {
            // Otherwise use the provided avatar
            user.setAvatar(updateProfileDTO.getAvatar());
            
            // If this is a Discord CDN URL, also update the cached URL
            if (updateProfileDTO.getAvatar().contains("cdn.discordapp.com")) {
                user.setDiscordAvatarUrl(updateProfileDTO.getAvatar());
                logger.debug("Updated Discord avatar cache from profile update for user: {}", userId);
            }
        }
        
        // Save the updated user
        User updatedUser = userRepository.save(user);
        logger.info("Updated profile for user: {}", updatedUser.getUsername());
        
        // Convert to DTO and return
        return mapToProfileDTO(updatedUser);
    }

    /**
     * Enhanced mapToProfileDTO method that handles the special marker for Discord avatars.
     */
    public UserProfileDTO mapToProfileDTO(User user) {
        String avatarUrl = user.getAvatar();
        
        // If the special marker is found, use the cached Discord avatar
        if ("USE_DISCORD_AVATAR".equals(avatarUrl)) {
            logger.debug("Special avatar marker found for user: {}", user.getId());
            
            if (user.getDiscordAvatarUrl() != null && !user.getDiscordAvatarUrl().isEmpty()) {
                avatarUrl = user.getDiscordAvatarUrl();
                logger.debug("Using cached Discord avatar URL: {}", avatarUrl);
            } else {
                // Fallback if no cached avatar is found
                avatarUrl = "/default-avatar.png";
                logger.warn("No cached Discord avatar URL found for user: {}, using default", user.getId());
            }
        } else if (avatarUrl == null || avatarUrl.isEmpty()) {
            avatarUrl = "/default-avatar.png";
            logger.debug("Empty avatar URL for user: {}, using default", user.getId());
        } else {
            logger.debug("Using custom avatar URL for user: {}: {}", user.getId(), avatarUrl);
        }
        
        return UserProfileDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .avatar(avatarUrl)
                .displayName(user.getDisplayName())
                .pronouns(user.getPronouns())
                .about(user.getAbout())
                .bannerColor(user.getBannerColor())
                .bannerUrl(user.getBannerUrl())
                .build();
    }

    /**
     * Updates an existing user entity.
     *
     * @param user the user entity to update
     * @return the updated User entity
     */
    public User updateUser(User user) {
        logger.debug("Updating user entity for user ID: {}", user.getId());
        return userRepository.save(user);
    }
}
