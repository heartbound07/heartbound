package com.app.heartbound.services;

import com.app.heartbound.dto.UserDTO;
import com.app.heartbound.dto.UpdateProfileDTO;
import com.app.heartbound.dto.UserProfileDTO;
import com.app.heartbound.dto.DailyActivityDataDTO;
import com.app.heartbound.enums.Role;
import com.app.heartbound.entities.User;
import com.app.heartbound.entities.Shop;
import com.app.heartbound.entities.DailyMessageStat;
import com.app.heartbound.entities.DailyVoiceActivityStat;
import com.app.heartbound.repositories.UserRepository;
import com.app.heartbound.repositories.shop.ShopRepository;
import com.app.heartbound.repositories.DailyMessageStatRepository;
import com.app.heartbound.repositories.DailyVoiceActivityStatRepository;
import com.app.heartbound.exceptions.ResourceNotFoundException;
import com.app.heartbound.exceptions.UnauthorizedOperationException;
import com.app.heartbound.config.CacheConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
    private final DailyMessageStatRepository dailyMessageStatRepository;
    private final DailyVoiceActivityStatRepository dailyVoiceActivityStatRepository;
    private final CacheConfig cacheConfig;
    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    // Read admin Discord ID from environment variables
    @Value("${admin.discord.id}")
    private String adminDiscordId;

    // Constructor-based dependency injection
    @Autowired
    public UserService(UserRepository userRepository, ShopRepository shopRepository, DailyMessageStatRepository dailyMessageStatRepository, DailyVoiceActivityStatRepository dailyVoiceActivityStatRepository, CacheConfig cacheConfig) {
        this.userRepository = userRepository;
        this.shopRepository = shopRepository;
        this.dailyMessageStatRepository = dailyMessageStatRepository;
        this.dailyVoiceActivityStatRepository = dailyVoiceActivityStatRepository;
        this.cacheConfig = cacheConfig;
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
                // Ensure initialRoles is a mutable set
                initialRoles = new HashSet<>();
                initialRoles.add(Role.USER);
            } else {
                // If roles are provided by DTO, ensure it's a mutable copy
                initialRoles = new HashSet<>(initialRoles);
            }

            // Add ADMIN role if this is the admin user
            if (id.equals(adminDiscordId)) {
                initialRoles.add(Role.ADMIN); 
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

        // For existing users, credits, level, and experience are managed by other
        // parts of the application (e.g., admin panel, game mechanics).
        // The OAuth sync (createOrUpdateUser) should primarily sync Discord profile data
        // (username, avatar, email, roles) and should not overwrite these
        // application-specific values from a DTO that defaults them.

        // New users have their credits, level, and experience initialized earlier.
        // For existing users, their current credits, level, and experience (loaded from DB)
        // will be preserved as we are not setting them from the userDTO here.

        logger.debug("Saving user {} with final state - Avatar: '{}', DiscordAvatarUrl: '{}', Roles: {}, Credits: {}, Level: {}, Experience: {}",
                id, user.getAvatar(), user.getDiscordAvatarUrl(), user.getRoles(), user.getCredits(), user.getLevel(), user.getExperience());
                
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
                .messageCount(user.getMessageCount()) // Add the message count field
                .messagesToday(user.getMessagesToday()) // Add time-based message counts
                .messagesThisWeek(user.getMessagesThisWeek())
                .messagesThisTwoWeeks(user.getMessagesThisTwoWeeks())
                .voiceRank(user.getVoiceRank()) // Add voice rank
                .voiceTimeMinutesToday(user.getVoiceTimeMinutesToday()) // Add voice time fields
                .voiceTimeMinutesThisWeek(user.getVoiceTimeMinutesThisWeek())
                .voiceTimeMinutesThisTwoWeeks(user.getVoiceTimeMinutesThisTwoWeeks())
                .voiceTimeMinutesTotal(user.getVoiceTimeMinutesTotal()) // Add total voice time
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
     * Get users for the leaderboard, sorted by credits, level, messages, or voice
     * 
     * @param sortBy Sorting criterion: "credits", "level", "messages", or "voice"
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
        } else if ("messages".equalsIgnoreCase(sortBy)) {
            // Sort by message count descending
            return users.stream()
                .sorted(Comparator.comparing(user -> user.getMessageCount() != null ? user.getMessageCount() : 0, 
                        Comparator.reverseOrder()))
                .map(this::mapToProfileDTO)
                .collect(Collectors.toList());
        } else if ("voice".equalsIgnoreCase(sortBy)) {
            // Sort by total voice time descending with null-safe comparison
            return users.stream()
                .sorted(Comparator.comparing(user -> user.getVoiceTimeMinutesTotal() != null ? user.getVoiceTimeMinutesTotal() : 0, 
                        Comparator.reverseOrder()))
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
     * Get daily message activity for a user over the specified number of days
     * Uses cache-aside strategy for improved performance
     *
     * @param userId the ID of the user
     * @param days the number of days to fetch (starting from today going back)
     * @return list of daily activity data
     */
    @SuppressWarnings("unchecked")
    public List<DailyActivityDataDTO> getUserDailyActivity(String userId, int days) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return new ArrayList<>();
        }

        // Cache-aside strategy: Generate cache key
        String cacheKey = "activity_stats_" + userId;
        
        // Try to get data from cache first
        List<Object> cachedData = cacheConfig.getDailyMessageActivityCache().getIfPresent(cacheKey);
        if (cachedData != null) {
            logger.debug("Cache hit for daily activity data: userId={}", userId);
            // Cast cached objects back to DTOs
            return cachedData.stream()
                    .map(obj -> (DailyActivityDataDTO) obj)
                    .collect(Collectors.toList());
        }

        logger.debug("Cache miss for daily activity data: userId={}, fetching from database", userId);
        
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days - 1);

        // Get existing daily message stats from database
        List<DailyMessageStat> existingStats = dailyMessageStatRepository.findByUserAndDateBetweenOrderByDateAsc(
            user, startDate, endDate);

        // Create a map for quick lookup
        Map<LocalDate, Long> statsMap = existingStats.stream()
            .collect(Collectors.toMap(
                DailyMessageStat::getDate,
                DailyMessageStat::getMessageCount
            ));

        // Generate complete date range with zeros for missing dates
        List<DailyActivityDataDTO> result = new ArrayList<>();
        LocalDate currentDate = startDate;
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        while (!currentDate.isAfter(endDate)) {
            Long count = statsMap.getOrDefault(currentDate, 0L);
            result.add(new DailyActivityDataDTO(currentDate.format(formatter), count));
            currentDate = currentDate.plusDays(1);
        }

        // Store result in cache for future requests
        List<Object> cacheableData = new ArrayList<>(result);
        cacheConfig.getDailyMessageActivityCache().put(cacheKey, cacheableData);
        logger.debug("Cached daily activity data for userId={}", userId);

        return result;
    }

    /**
     * Increments the time-based voice counters for a user.
     * Handles resetting counters if the time periods have elapsed.
     *
     * @param user the user to update
     * @param sessionMinutes the minutes to add to the voice time
     */
    public void incrementVoiceTimeCounters(User user, int sessionMinutes) {
        LocalDateTime now = LocalDateTime.now();
        
        // Update total voice time
        int currentTotal = user.getVoiceTimeMinutesTotal() != null ? user.getVoiceTimeMinutesTotal() : 0;
        user.setVoiceTimeMinutesTotal(currentTotal + sessionMinutes);
        
        // Handle daily voice counter
        if (shouldResetVoiceDailyCounter(user, now)) {
            user.setVoiceTimeMinutesToday(0);
            user.setLastVoiceDailyReset(now);
        }
        int todayMinutes = user.getVoiceTimeMinutesToday() != null ? user.getVoiceTimeMinutesToday() : 0;
        user.setVoiceTimeMinutesToday(todayMinutes + sessionMinutes);
        
        // Handle weekly voice counter
        if (shouldResetVoiceWeeklyCounter(user, now)) {
            user.setVoiceTimeMinutesThisWeek(0);
            user.setLastVoiceWeeklyReset(now);
        }
        int weekMinutes = user.getVoiceTimeMinutesThisWeek() != null ? user.getVoiceTimeMinutesThisWeek() : 0;
        user.setVoiceTimeMinutesThisWeek(weekMinutes + sessionMinutes);
        
        // Handle bi-weekly voice counter
        if (shouldResetVoiceBiWeeklyCounter(user, now)) {
            user.setVoiceTimeMinutesThisTwoWeeks(0);
            user.setLastVoiceBiWeeklyReset(now);
        }
        int biWeekMinutes = user.getVoiceTimeMinutesThisTwoWeeks() != null ? user.getVoiceTimeMinutesThisTwoWeeks() : 0;
        user.setVoiceTimeMinutesThisTwoWeeks(biWeekMinutes + sessionMinutes);
        
        // Save the updated user
        try {
            userRepository.save(user);
            
            // Invalidate user profile cache to ensure fresh data
            cacheConfig.invalidateUserProfileCache(user.getId());
            
            logger.debug("Updated voice time for user {} - added {} minutes (total: {})", 
                user.getId(), sessionMinutes, user.getVoiceTimeMinutesTotal());
        } catch (Exception e) {
            logger.error("Failed to save voice time update for user {}: {}", user.getId(), e.getMessage(), e);
        }
    }

    /**
     * Check if voice daily counter should be reset (new day)
     */
    private boolean shouldResetVoiceDailyCounter(User user, LocalDateTime now) {
        if (user.getLastVoiceDailyReset() == null) {
            return true; // First time, needs initialization
        }
        return !user.getLastVoiceDailyReset().toLocalDate().equals(now.toLocalDate());
    }
    
    /**
     * Check if voice weekly counter should be reset (new week - Monday)
     */
    private boolean shouldResetVoiceWeeklyCounter(User user, LocalDateTime now) {
        if (user.getLastVoiceWeeklyReset() == null) {
            return true; // First time, needs initialization
        }
        
        // Get the start of this week (Monday)
        LocalDateTime startOfThisWeek = now.with(java.time.DayOfWeek.MONDAY).withHour(0).withMinute(0).withSecond(0).withNano(0);
        return user.getLastVoiceWeeklyReset().isBefore(startOfThisWeek);
    }
    
    /**
     * Check if voice bi-weekly counter should be reset (every 2 weeks from a fixed start date)
     */
    private boolean shouldResetVoiceBiWeeklyCounter(User user, LocalDateTime now) {
        if (user.getLastVoiceBiWeeklyReset() == null) {
            return true; // First time, needs initialization
        }
        
        // Reset every 14 days from the last reset
        return user.getLastVoiceBiWeeklyReset().plusDays(14).isBefore(now) || 
               user.getLastVoiceBiWeeklyReset().plusDays(14).toLocalDate().equals(now.toLocalDate());
    }

    /**
     * Updates voice ranks for all users based on total voice time
     */
    @Transactional
    public void updateVoiceRanks() {
        try {
            logger.debug("Starting voice rank update for all users");
            
            List<User> users = userRepository.findAll();
            
            // Sort users by total voice time in descending order
            users.sort((a, b) -> {
                Integer voiceTimeA = a.getVoiceTimeMinutesTotal() != null ? a.getVoiceTimeMinutesTotal() : 0;
                Integer voiceTimeB = b.getVoiceTimeMinutesTotal() != null ? b.getVoiceTimeMinutesTotal() : 0;
                return voiceTimeB.compareTo(voiceTimeA); // Descending order
            });
            
            // Assign ranks
            for (int i = 0; i < users.size(); i++) {
                User user = users.get(i);
                Integer newRank = i + 1; // Rank starts from 1
                
                // Only update if rank has changed to avoid unnecessary database writes
                if (!newRank.equals(user.getVoiceRank())) {
                    user.setVoiceRank(newRank);
                    userRepository.save(user);
                    
                    // Invalidate user profile cache
                    cacheConfig.invalidateUserProfileCache(user.getId());
                }
            }
            
            logger.debug("Completed voice rank update for {} users", users.size());
        } catch (Exception e) {
            logger.error("Failed to update voice ranks: {}", e.getMessage(), e);
        }
    }

    /**
     * Track daily message statistics for dashboard charts
     * This method is transactional and handles the database upsert operation
     * Also invalidates the user's daily activity cache to ensure fresh data
     * 
     * @param userId the Discord user ID
     */
    @Transactional
    public void trackDailyMessageStat(String userId) {
        try {
            LocalDate today = LocalDate.now();
            
            // Use the repository's optimized upsert query to increment daily count
            dailyMessageStatRepository.incrementMessageCount(userId, today);
            
            // Invalidate cache to ensure next request gets fresh data
            cacheConfig.invalidateDailyMessageActivityCache(userId);
            
            logger.debug("[DAILY STATS] Incremented daily message count for user {} on {} and invalidated cache", userId, today);
        } catch (Exception e) {
            logger.error("Failed to track daily message stat for user {}: {}", userId, e.getMessage(), e);
        }
    }

    /**
     * Track daily voice activity statistics for dashboard charts
     * This method is transactional and handles the database upsert operation
     * Also invalidates the user's daily activity cache to ensure fresh data
     * 
     * @param userId the Discord user ID
     * @param voiceMinutes the minutes to add to today's voice activity
     */
    @Transactional
    public void trackDailyVoiceActivityStat(String userId, int voiceMinutes) {
        try {
            LocalDate today = LocalDate.now();
            
            // Use the repository's optimized upsert query to increment daily voice minutes
            dailyVoiceActivityStatRepository.incrementVoiceMinutes(userId, today, voiceMinutes);
            
            // Invalidate voice activity cache to ensure next request gets fresh data
            cacheConfig.invalidateDailyVoiceActivityCache(userId);
            
            logger.debug("[DAILY VOICE STATS] Incremented daily voice minutes for user {} by {} minutes on {} and invalidated cache", userId, voiceMinutes, today);
        } catch (Exception e) {
            logger.error("Failed to track daily voice activity stat for user {}: {}", userId, e.getMessage(), e);
        }
    }

    /**
     * Get daily voice activity for a user over the specified number of days
     * Uses cache-aside strategy for improved performance
     *
     * @param userId the ID of the user
     * @param days the number of days to fetch (starting from today going back)
     * @return list of daily activity data
     */
    @SuppressWarnings("unchecked")
    public List<DailyActivityDataDTO> getUserDailyVoiceActivity(String userId, int days) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return new ArrayList<>();
        }

        // Cache-aside strategy: Generate cache key
        String cacheKey = "voice_activity_stats_" + userId;
        
        // Try to get data from cache first
        List<Object> cachedData = cacheConfig.getDailyMessageActivityCache().getIfPresent(cacheKey);
        if (cachedData != null) {
            logger.debug("Cache hit for daily voice activity data: userId={}", userId);
            // Cast cached objects back to DTOs
            return cachedData.stream()
                    .map(obj -> (DailyActivityDataDTO) obj)
                    .collect(Collectors.toList());
        }

        logger.debug("Cache miss for daily voice activity data: userId={}, fetching from database", userId);
        
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days - 1);

        // Get existing daily voice activity stats from database
        List<DailyVoiceActivityStat> existingStats = dailyVoiceActivityStatRepository.findByUserAndDateBetweenOrderByDateAsc(
            user, startDate, endDate);

        // Create a map for quick lookup
        Map<LocalDate, Long> statsMap = existingStats.stream()
            .collect(Collectors.toMap(
                DailyVoiceActivityStat::getDate,
                DailyVoiceActivityStat::getVoiceMinutes
            ));

        // Generate complete date range with zeros for missing dates
        List<DailyActivityDataDTO> result = new ArrayList<>();
        LocalDate currentDate = startDate;
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        while (!currentDate.isAfter(endDate)) {
            Long count = statsMap.getOrDefault(currentDate, 0L);
            result.add(new DailyActivityDataDTO(currentDate.format(formatter), count));
            currentDate = currentDate.plusDays(1);
        }

        // Store result in cache for future requests
        List<Object> cacheableData = new ArrayList<>(result);
        cacheConfig.getDailyMessageActivityCache().put(cacheKey, cacheableData);
        logger.debug("Cached daily voice activity data for userId={}", userId);

        return result;
    }
}
