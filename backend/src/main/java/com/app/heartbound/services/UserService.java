package com.app.heartbound.services;

import com.app.heartbound.dto.UserDTO;
import com.app.heartbound.dto.UpdateProfileDTO;
import com.app.heartbound.dto.UserProfileDTO;
import com.app.heartbound.enums.Role;
import com.app.heartbound.entities.User;
import com.app.heartbound.entities.Shop;
import com.app.heartbound.repositories.UserRepository;
import com.app.heartbound.repositories.shop.ShopRepository;
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
import java.util.Map;
import java.util.UUID;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.HashSet;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final ShopRepository shopRepository;
    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    // Read admin Discord ID from environment variables
    @Value("${admin.discord.id}")
    private String adminDiscordId;

    // Constructor-based dependency injection for UserRepository and ShopRepository
    @Autowired
    public UserService(UserRepository userRepository, ShopRepository shopRepository) {
        this.userRepository = userRepository;
        this.shopRepository = shopRepository;
    }

    /**
     * Creates a new user or updates an existing one based on the provided DTO.
     */
    public User createOrUpdateUser(UserDTO userDTO) {
        String id = userDTO.getId();
        String username = userDTO.getUsername();
        String discriminator = userDTO.getDiscriminator();
        String email = userDTO.getEmail();
        // This is the avatar URL fetched fresh from Discord via the DTO
        String discordAvatarFetched = userDTO.getAvatar(); 

        logger.debug("Attempting to create or update user with ID: {}. DTO Avatar: {}", id, discordAvatarFetched);

        User user = userRepository.findById(id).orElse(null);
        boolean isNewUser = (user == null);

        if (isNewUser) {
            logger.debug("User with ID: {} not found. Creating new user.", id);
            user = new User();
            user.setId(id);
            
            // New user: set primary and Discord-specific avatar to the one from Discord
            user.setAvatar(discordAvatarFetched);
            user.setDiscordAvatarUrl(discordAvatarFetched);
            logger.debug("New user {}: Set avatar and discordAvatarUrl to '{}'", id, discordAvatarFetched);

            // Initialize default roles, credits, level, experience for new user
            // Ensure roles are initialized, defaulting to USER if not provided
            Set<Role> initialRoles = userDTO.getRoles();
            if (initialRoles == null || initialRoles.isEmpty()) {
                initialRoles = Set.of(Role.USER);
            }
            // Add ADMIN role if this is the admin user
            if (id.equals(adminDiscordId)) {
                initialRoles.add(Role.ADMIN); // Make sure this is a mutable set if adding
                logger.info("Admin role automatically assigned to new user ID: {}", id);
            }
            user.setRoles(initialRoles);
            
            user.setCredits(userDTO.getCredits() != null ? userDTO.getCredits() : 0); // Default credits
            user.setLevel(1); // Default level
            user.setExperience(0); // Default experience
            // Set other new user defaults as needed (e.g., displayName, pronouns, etc.)
            user.setDisplayName(username); // Default display name to username for new users

        } else {
            // Existing User Logic
            logger.debug("Found existing user with ID: {}. Current primary avatar: '{}', current Discord cache: '{}'",
                    id, user.getAvatar(), user.getDiscordAvatarUrl());

            // 1. Always update the dedicated Discord avatar URL cache
            if (discordAvatarFetched != null) {
                if (!discordAvatarFetched.equals(user.getDiscordAvatarUrl())) {
                    user.setDiscordAvatarUrl(discordAvatarFetched);
                    logger.debug("Existing user {}: Updated discordAvatarUrl from '{}' to '{}'", id, user.getDiscordAvatarUrl(), discordAvatarFetched);
                }
            } else if (user.getDiscordAvatarUrl() != null) { 
                // If Discord API returns no avatar (null), clear our cache of it.
                user.setDiscordAvatarUrl(null);
                logger.debug("Existing user {}: Cleared discordAvatarUrl as DTO avatar was null.", id);
            }

            // 2. Conditionally update the primary avatar
            String currentPrimaryAvatar = user.getAvatar();
            if (discordAvatarFetched != null) { 
                // If current primary avatar is null, empty, or explicitly set to "USE_DISCORD_AVATAR",
                // then update it with the new one from Discord.
                if (currentPrimaryAvatar == null || currentPrimaryAvatar.isEmpty() || "USE_DISCORD_AVATAR".equals(currentPrimaryAvatar)) {
                    if (!discordAvatarFetched.equals(currentPrimaryAvatar)) {
                        user.setAvatar(discordAvatarFetched);
                        logger.debug("Existing user {}: Updated primary avatar to Discord avatar '{}' (was blank, null, or USE_DISCORD_AVATAR). Old: '{}'", id, discordAvatarFetched, currentPrimaryAvatar);
                    } else {
                        logger.debug("Existing user {}: Primary avatar ('{}') already matches Discord avatar and was eligible for update. No change.", id, currentPrimaryAvatar);
                    }
                } else {
                    // User has a custom primary avatar (e.g., Cloudinary URL). Preserve it.
                    logger.debug("Existing user {}: Preserved custom primary avatar '{}'. (New Discord avatar from DTO was '{}')", currentPrimaryAvatar, id, discordAvatarFetched);
                }
            } else { // discordAvatarFetched is null
                // If Discord provides no avatar, and user was using "USE_DISCORD_AVATAR",
                // their primary avatar will effectively become null/default after this.
                // The mapToProfileDTO logic might then apply a default.
                // No direct change to user.setAvatar() here if discordAvatarFetched is null,
                // unless currentPrimaryAvatar was "USE_DISCORD_AVATAR" and you want to explicitly clear it.
                // For now, if discordAvatarFetched is null, we don't change a custom avatar.
                // If it was "USE_DISCORD_AVATAR", it will reflect the (now null) discordAvatarUrl implicitly.
                logger.debug("Existing user {}: DTO avatar is null. Primary avatar is '{}'. No change to primary avatar from this path.", id, currentPrimaryAvatar);
            }
        }

        // Update common fields for both new and existing users from DTO
        // (Username, discriminator, email might change on Discord)
        user.setUsername(username);
        user.setDiscriminator(discriminator);
        if (email != null) { // Only update email if provided by Discord
            user.setEmail(email);
        }
        
        // Ensure admin role for the configured admin ID (applies to existing users too if role was removed)
        if (id.equals(adminDiscordId)) {
            if (user.getRoles() == null) user.setRoles(new HashSet<>()); // Ensure roles set is initialized
            if (!user.getRoles().contains(Role.ADMIN)) {
                user.addRole(Role.ADMIN);
                logger.info("Admin role ensured for user ID: {}", id);
            }
        }
        // Ensure every user has at least the USER role
        if (user.getRoles() == null || user.getRoles().isEmpty()) {
            if (user.getRoles() == null) user.setRoles(new HashSet<>());
            user.addRole(Role.USER);
            logger.debug("Default USER role ensured for user ID: {}", id);
        }

        // Credits logic:
        // For new users, credits are initialized when the user object is created.
        // For existing users, generally, OAuth sync should not overwrite credits unless explicitly intended.
        // The original logic for `isFromOAuth` was complex. A common pattern:
        // if userDTO.getCredits() is null (typical for basic Discord profile fetch), preserve existing credits.
        if (!isNewUser) { // For existing users
            if (userDTO.getCredits() == null) {
                // DTO doesn't provide credits (likely OAuth profile sync), so preserve existing.
                if (user.getCredits() == null) { // If existing credits are somehow null, default to 0.
                    user.setCredits(0);
                    logger.debug("Existing user {}: Initialized null credits to 0.", id);
                }
                // Otherwise, user.getCredits() remains unchanged.
                logger.debug("Existing user {}: DTO credits null, preserving existing credits: {}", id, user.getCredits());
            } else {
                // DTO provides credits. This implies an intentional update or a flow where credits are included.
                if (!userDTO.getCredits().equals(user.getCredits())) {
                    user.setCredits(userDTO.getCredits());
                    logger.debug("Existing user {}: Updated credits from DTO to {}", id, userDTO.getCredits());
                }
            }
        }
        // For new users, credits are set during initialization.

        logger.debug("Saving user {} with final state - Avatar: '{}', DiscordAvatarUrl: '{}', Roles: {}, Credits: {}",
                id, user.getAvatar(), user.getDiscordAvatarUrl(), user.getRoles(), user.getCredits());
                
        return userRepository.save(user);
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
        
        // If the primary avatar is the special marker, use the cached Discord URL
        if ("USE_DISCORD_AVATAR".equals(avatarUrl)) {
            logger.debug("Primary avatar is USE_DISCORD_AVATAR, attempting to use cached Discord avatar.");
            // Use cached Discord avatar if available
            if (user.getDiscordAvatarUrl() != null && !user.getDiscordAvatarUrl().isEmpty()) {
                avatarUrl = user.getDiscordAvatarUrl();
                logger.debug("Using cached Discord avatar URL: {}", avatarUrl);
            } else {
                // Fallback if no cached avatar is found
                avatarUrl = "/default-avatar.png"; // Consider a consistent default placeholder
                logger.warn("No cached Discord avatar URL found for user: {}, using default", user.getId());
            }
        } else if (avatarUrl == null || avatarUrl.isEmpty()) {
            avatarUrl = "/default-avatar.png"; // Consider a consistent default placeholder
            logger.debug("Empty avatar URL for user: {}, using default", user.getId());
        } else {
            logger.debug("Using custom avatar URL for user: {}: {}", user.getId(), avatarUrl);
        }
        
        // Get the user's equipped badges if any
        Set<UUID> badgeIds = user.getEquippedBadgeIds();
        
        // Create maps for badge URLs and names
        Map<String, String> badgeUrls = new HashMap<>();
        Map<String, String> badgeNames = new HashMap<>();
        
        // If user has equipped badges, fetch their details
        if (badgeIds != null && !badgeIds.isEmpty()) {
            List<Shop> badges = shopRepository.findAllByIdIn(badgeIds);
            
            // Populate both maps (URLs and names)
            for (Shop badge : badges) {
                String idStr = badge.getId().toString();
                badgeUrls.put(idStr, badge.getThumbnailUrl());
                badgeNames.put(idStr, badge.getName()); // Add the actual badge name
            }
        }
        
        // Build and return the DTO with all user profile data
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
                .level(user.getLevel())
                .experience(user.getExperience())
                .equippedUserColorId(user.getEquippedUserColorId())
                .equippedListingId(user.getEquippedListingId())
                .equippedAccentId(user.getEquippedAccentId())
                .equippedBadgeIds(badgeIds)
                .badgeUrls(badgeUrls)
                .badgeNames(badgeNames) // Add the badge names map
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
     * Get users for the leaderboard, sorted by credits or level/experience
     * 
     * @param sortBy Sorting criterion: "credits" or "level"
     * @return List of sorted user profiles
     */
    public List<UserProfileDTO> getLeaderboardUsers(String sortBy) {
        // Fetch users
        List<User> users = userRepository.findAll();
        
        // Sort based on the provided criterion
        if ("level".equalsIgnoreCase(sortBy)) {
            // Sort by level (desc), then by experience (desc) with null-safe comparison
            return users.stream()
                .sorted((a, b) -> {
                    Integer levelA = a.getLevel() != null ? a.getLevel() : 1;
                    Integer levelB = b.getLevel() != null ? b.getLevel() : 1;
                    
                    int levelCompare = levelB.compareTo(levelA); // Descending order
                    if (levelCompare != 0) {
                        return levelCompare;
                    }
                    
                    Integer xpA = a.getExperience() != null ? a.getExperience() : 0;
                    Integer xpB = b.getExperience() != null ? b.getExperience() : 0;
                    return xpB.compareTo(xpA); // Descending order
                })
                .map(this::mapToProfileDTO)
                .collect(Collectors.toList());
        } else {
            // Default: sort by credits descending
            return users.stream()
                .sorted(Comparator.comparing(user -> user.getCredits() != null ? user.getCredits() : 0, 
                        Comparator.reverseOrder()))
                .map(this::mapToProfileDTO)
                .collect(Collectors.toList());
        }
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
