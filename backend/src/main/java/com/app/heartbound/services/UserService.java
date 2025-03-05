package com.app.heartbound.services;

import com.app.heartbound.dto.UserDTO;
import com.app.heartbound.dto.UpdateProfileDTO;
import com.app.heartbound.dto.UserProfileDTO;
import com.app.heartbound.enums.Role;
import com.app.heartbound.entities.User;
import com.app.heartbound.repositories.UserRepository;
import com.app.heartbound.exceptions.ResourceNotFoundException;
import com.app.heartbound.exceptions.UnauthorizedOperationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserService {

    private final UserRepository userRepository;
    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    // Read admin Discord ID from environment variables
    @Value("${admin.discord.id}")
    private String adminDiscordId;

    // Constructor-based dependency injection for UserRepository
    @Autowired
    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Creates a new user or updates an existing user using the provided UserDTO data.
     * New users are assigned the USER role by default.
     *
     * @param userDTO the data transfer object containing user details
     * @return the saved User entity
     */
    public User createOrUpdateUser(UserDTO userDTO) {
        String id = userDTO.getId();
        String username = userDTO.getUsername();
        String discriminator = userDTO.getDiscriminator();
        String email = userDTO.getEmail();
        String avatar = userDTO.getAvatar();
        
        logger.debug("Creating or updating user with ID: {}", id);
        
        User user = userRepository.findById(id).orElse(new User());
        
        // Set user properties
        user.setId(id);
        user.setUsername(username);
        user.setDiscriminator(discriminator);
        user.setEmail(email);
        
        // Auto-assign ADMIN role to your Discord account
        if (adminDiscordId.equals(id)) {
            logger.info("Assigning ADMIN role to user: {} ({})", username, id);
            user.addRole(Role.ADMIN);
        }
        
        // Ensure user has at least USER role
        if (user.getRoles() == null || user.getRoles().isEmpty()) {
            user.addRole(Role.USER);
        }
        
        // Update avatar if provided
        if (avatar != null && !avatar.isEmpty()) {
            logger.debug("Updating avatar for user: {}", id);
            user.setAvatar(avatar);
        }
        
        // Store the Discord avatar URL for caching
        if (avatar != null && avatar.contains("cdn.discordapp.com")) {
            logger.debug("Updated Discord avatar cache for user: {}", id);
            user.setDiscordAvatarUrl(avatar);
        }
        
        user = userRepository.save(user);
        logger.info("Updated existing user: {}", username);
        
        return user;
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
     * @return the updated UserProfileDTO
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
                .roles(user.getRoles())
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
    
    /**
     * Assigns a role to a user. Only ADMIN users can assign ADMIN or MODERATOR roles.
     * 
     * @param userId the ID of the user to update
     * @param role the role to assign
     * @param adminId the ID of the admin performing the operation
     * @return the updated user
     */
    @PreAuthorize("hasRole('ADMIN')")
    public User assignRole(String userId, Role role, String adminId) {
        logger.debug("Admin {} assigning role {} to user {}", adminId, role, userId);
        
        // Only allow ADMIN to assign ADMIN or MODERATOR roles
        if ((role == Role.ADMIN || role == Role.MODERATOR) && 
            !userRepository.hasRole(adminId, Role.ADMIN)) {
            throw new UnauthorizedOperationException("Only ADMIN users can assign ADMIN or MODERATOR roles");
        }
        
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));
        
        user.addRole(role);
        return userRepository.save(user);
    }
    
    /**
     * Removes a role from a user. Only ADMIN users can remove ADMIN or MODERATOR roles.
     * 
     * @param userId the ID of the user to update
     * @param role the role to remove
     * @param adminId the ID of the admin performing the operation
     * @return the updated user
     */
    @PreAuthorize("hasRole('ADMIN')")
    public User removeRole(String userId, Role role, String adminId) {
        logger.debug("Admin {} removing role {} from user {}", adminId, role, userId);
        
        // Only allow ADMIN to remove ADMIN or MODERATOR roles
        if ((role == Role.ADMIN || role == Role.MODERATOR) && 
            !userRepository.hasRole(adminId, Role.ADMIN)) {
            throw new UnauthorizedOperationException("Only ADMIN users can remove ADMIN or MODERATOR roles");
        }
        
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));
        
        user.removeRole(role);
        return userRepository.save(user);
    }
    
    /**
     * Upgrades a user to MONARCH (premium) status.
     * This could be triggered after payment confirmation.
     * 
     * @param userId the ID of the user to upgrade
     * @return the updated user
     */
    public User upgradeToMonarch(String userId) {
        logger.debug("Upgrading user {} to MONARCH status", userId);
        
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));
        
        user.addRole(Role.MONARCH);
        return userRepository.save(user);
    }
    
    /**
     * Retrieves all users with a specific role.
     * Only accessible to ADMIN and MODERATOR users.
     * 
     * @param role the role to filter by
     * @return list of users with the specified role
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")
    public List<UserProfileDTO> getUsersByRole(Role role) {
        logger.debug("Fetching users with role: {}", role);
        
        List<User> users = userRepository.findByRole(role);
        return users.stream()
                .map(this::mapToProfileDTO)
                .collect(Collectors.toList());
    }
}
