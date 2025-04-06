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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.Comparator;

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
     * Creates a new user or updates an existing one based on the provided DTO.
     */
    public User createOrUpdateUser(UserDTO userDTO) {
        String id = userDTO.getId();
        String username = userDTO.getUsername();
        String discriminator = userDTO.getDiscriminator();
        String email = userDTO.getEmail();
        String avatar = userDTO.getAvatar();
        
        logger.debug("Creating or updating user with ID: {}", id);
        
        // Check if user exists
        User user = userRepository.findById(id).orElse(new User());
        
        // Log the current credits for debugging
        Integer currentCredits = user.getCredits();
        logger.debug("Current credits for user {}: {}", id, currentCredits);
        
        // Basic user info
        user.setId(id);
        user.setUsername(username);
        user.setDiscriminator(discriminator);
        user.setEmail(email);
        
        // Determine if this is from OAuth (usually lacks credits information)
        boolean isFromOAuth = userDTO.getCredits() == null || 
                              (userDTO.getCredits() == 0 && currentCredits != null && currentCredits > 0);
        
        // Explicitly handle credits preservation for OAuth login
        if (isFromOAuth) {
            logger.debug("OAuth login detected - preserving existing credits: {}", currentCredits);
            // Do nothing - keep existing credits
        } else if (userDTO.getCredits() != null) {
            // Normal update with provided credits
            logger.debug("Updating credits from {} to {}", currentCredits, userDTO.getCredits());
            user.setCredits(userDTO.getCredits());
        } else if (user.getCredits() == null) {
            // Initialize credits for brand new users
            logger.debug("Initializing credits to 0 for new user");
            user.setCredits(0);
        }
        
        // Check if user is admin based on the admin Discord ID from environment
        if (id.equals(adminDiscordId)) {
            logger.info("Admin user detected with ID: {}. Adding ADMIN role.", id);
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
        logger.info("Updated existing user: {}. Credits: {}", username, user.getCredits());
        
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
                .credits(user.getCredits())
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

    /**
     * Retrieves all users with pagination and optional search filtering.
     * Only accessible to ADMIN users.
     * 
     * @param page the page number (0-based)
     * @param size the page size
     * @param search optional search term for username/email
     * @return paginated list of user profiles
     */
    @PreAuthorize("hasRole('ADMIN')")
    public Page<UserProfileDTO> getAllUsers(int page, int size, String search) {
        logger.debug("Fetching users page {} with size {}", page, size);
        
        Pageable pageable = PageRequest.of(page, size);
        Page<User> users;
        
        if (search != null && !search.trim().isEmpty()) {
            // Search by username or email containing the search term
            users = userRepository.findByUsernameContainingOrEmailContaining(search, search, pageable);
        } else {
            // Get all users without filtering
            users = userRepository.findAll(pageable);
        }
        
        return users.map(this::mapToProfileDTO);
    }

    /**
     * Sets the complete set of roles for a user (replacing existing roles).
     * Only accessible to ADMIN users.
     * 
     * @param userId the ID of the user to update
     * @param roles the complete set of roles to assign
     * @param adminId the ID of the admin performing the operation
     * @return the updated user
     */
    @PreAuthorize("hasRole('ADMIN')")
    public User setUserRoles(String userId, Set<Role> roles, String adminId) {
        logger.debug("Admin {} setting roles for user {}: {}", adminId, userId, roles);
        
        // Verify the admin has permission to manage these roles
        boolean containsAdminOrModerator = roles.contains(Role.ADMIN) || roles.contains(Role.MODERATOR);
        
        if (containsAdminOrModerator && !userRepository.hasRole(adminId, Role.ADMIN)) {
            throw new UnauthorizedOperationException("Only ADMIN users can manage ADMIN or MODERATOR roles");
        }
        
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));
        
        // Always ensure users keep the USER role
        roles.add(Role.USER);
        
        // Replace all roles with the new set
        user.setRoles(roles);
        return userRepository.save(user);
    }

    /**
     * Updates a user's credit balance.
     * Only accessible to ADMIN users.
     * 
     * @param userId the ID of the user to update
     * @param credits the new credit balance
     * @return the updated user
     * @throws ResourceNotFoundException if the user is not found
     */
    @PreAuthorize("hasRole('ADMIN')")
    public User updateUserCredits(String userId, Integer credits) {
        logger.debug("Updating credits for user {} to {}", userId, credits);
        
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));
        
        // Ensure credits are not negative
        if (credits < 0) {
            credits = 0;
        }
        
        user.setCredits(credits);
        return userRepository.save(user);
    }

    /**
     * Get users for the leaderboard, sorted by credits in descending order
     */
    public List<UserProfileDTO> getLeaderboardUsers() {
        // Fetch users, sort by credits descending, and map to DTOs
        List<User> users = userRepository.findAll();
        
        return users.stream()
            .sorted(Comparator.comparing(User::getCredits).reversed())
            .map(this::mapToProfileDTO)
            .collect(Collectors.toList());
    }

    /**
     * Maps a User entity to a UserDTO.
     *
     * @param user The User entity.
     * @return The corresponding UserDTO.
     */
    public UserDTO mapUserToDTO(User user) {
        if (user == null) {
            return null;
        }
        UserDTO dto = new UserDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setDiscriminator(user.getDiscriminator()); // Include discriminator if needed in DTO
        dto.setEmail(user.getEmail());
        dto.setAvatar(user.getAvatar()); // Use the avatar from the User entity
        dto.setRoles(user.getRoles());
        dto.setCredits(user.getCredits());
        // Add any other fields from User that are present in UserDTO
        return dto;
    }

    /**
     * Maps a User entity to a UserProfileDTO.
     * Used for public profile views.
     */
}
