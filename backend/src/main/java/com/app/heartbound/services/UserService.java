package com.app.heartbound.services;

import com.app.heartbound.dto.UserDTO;
import com.app.heartbound.dto.UpdateProfileDTO;
import com.app.heartbound.dto.UserProfileDTO;
import com.app.heartbound.dto.DailyActivityDataDTO;
import com.app.heartbound.dto.shop.UserInventoryItemDTO;
import com.app.heartbound.dto.CreateAuditDTO;
import com.app.heartbound.enums.Role;
import com.app.heartbound.enums.ShopCategory;
import com.app.heartbound.enums.AuditSeverity;
import com.app.heartbound.enums.AuditCategory;
import com.app.heartbound.entities.User;
import com.app.heartbound.entities.Shop;
import com.app.heartbound.entities.UserInventoryItem;
import com.app.heartbound.entities.DailyMessageStat;
import com.app.heartbound.entities.DailyVoiceActivityStat;
import com.app.heartbound.repositories.UserRepository;
import com.app.heartbound.repositories.shop.ShopRepository;
import com.app.heartbound.repositories.UserInventoryItemRepository;
import com.app.heartbound.repositories.DailyMessageStatRepository;
import com.app.heartbound.repositories.DailyVoiceActivityStatRepository;
import com.app.heartbound.repositories.PendingRoleSelectionRepository;
import com.app.heartbound.exceptions.ResourceNotFoundException;
import com.app.heartbound.exceptions.UnauthorizedOperationException;
import com.app.heartbound.config.CacheConfig;
import com.app.heartbound.dto.LeaderboardEntryDTO;
import com.app.heartbound.entities.PendingRoleSelection;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.domain.Sort.Order;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

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
import java.util.Optional;
import java.util.stream.IntStream;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.cache.annotation.Cacheable;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final ShopRepository shopRepository;
    private final UserInventoryItemRepository userInventoryItemRepository;
    private final DailyMessageStatRepository dailyMessageStatRepository;
    private final DailyVoiceActivityStatRepository dailyVoiceActivityStatRepository;
    private final PendingRoleSelectionRepository pendingRoleSelectionRepository;
    private final CacheConfig cacheConfig;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    // Read admin Discord ID from environment variables
    @Value("${admin.discord.id}")
    private String adminDiscordId;

    // Leveling system configuration properties (copied from ChatActivityListener)
    @Value("${discord.leveling.base-xp:100}")
    private int baseXp;
    
    @Value("${discord.leveling.level-multiplier:50}")
    private int levelMultiplier;
    
    @Value("${discord.leveling.level-exponent:2}")
    private int levelExponent;
    
    @Value("${discord.leveling.level-factor:5}")
    private int levelFactor;

    // Constructor-based dependency injection
    public UserService(UserRepository userRepository, ShopRepository shopRepository, UserInventoryItemRepository userInventoryItemRepository, DailyMessageStatRepository dailyMessageStatRepository, DailyVoiceActivityStatRepository dailyVoiceActivityStatRepository, PendingRoleSelectionRepository pendingRoleSelectionRepository, CacheConfig cacheConfig, AuditService auditService, ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.shopRepository = shopRepository;
        this.userInventoryItemRepository = userInventoryItemRepository;
        this.dailyMessageStatRepository = dailyMessageStatRepository;
        this.dailyVoiceActivityStatRepository = dailyVoiceActivityStatRepository;
        this.pendingRoleSelectionRepository = pendingRoleSelectionRepository;
        this.cacheConfig = cacheConfig;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    /**
     * Calculate the required XP for a given level using the same formula as ChatActivityListener.
     * This ensures consistency across the application.
     *
     * @param level the level to calculate required XP for
     * @return the required XP for the given level
     */
    private int calculateRequiredXp(int level) {
        return baseXp + (levelFactor * (int)Math.pow(level, levelExponent)) + (levelMultiplier * level);
    }

    /**
     * Syncs pending role selections from PendingRoleSelection to User entity.
     * This method is called during user creation to apply any roles they selected before registration.
     * 
     * @param user the user entity to sync role selections to
     */
    private void syncPendingRoleSelections(User user) {
        try {
            Optional<PendingRoleSelection> pendingSelection = pendingRoleSelectionRepository.findByDiscordUserId(user.getId());
            
            if (pendingSelection.isPresent()) {
                PendingRoleSelection pending = pendingSelection.get();
                boolean hasChanges = false;
                
                // Sync age role selection
                if (pending.getSelectedAgeRoleId() != null && !pending.getSelectedAgeRoleId().isBlank() &&
                    (user.getSelectedAgeRoleId() == null || user.getSelectedAgeRoleId().isBlank())) {
                    user.setSelectedAgeRoleId(pending.getSelectedAgeRoleId());
                    hasChanges = true;
                    logger.debug("Synced age role selection for user {}: {}", user.getId(), pending.getSelectedAgeRoleId());
                }
                
                // Sync gender role selection
                if (pending.getSelectedGenderRoleId() != null && !pending.getSelectedGenderRoleId().isBlank() &&
                    (user.getSelectedGenderRoleId() == null || user.getSelectedGenderRoleId().isBlank())) {
                    user.setSelectedGenderRoleId(pending.getSelectedGenderRoleId());
                    hasChanges = true;
                    logger.debug("Synced gender role selection for user {}: {}", user.getId(), pending.getSelectedGenderRoleId());
                }
                
                // Sync rank role selection
                if (pending.getSelectedRankRoleId() != null && !pending.getSelectedRankRoleId().isBlank() &&
                    (user.getSelectedRankRoleId() == null || user.getSelectedRankRoleId().isBlank())) {
                    user.setSelectedRankRoleId(pending.getSelectedRankRoleId());
                    hasChanges = true;
                    logger.debug("Synced rank role selection for user {}: {}", user.getId(), pending.getSelectedRankRoleId());
                }
                
                // Sync region role selection
                if (pending.getSelectedRegionRoleId() != null && !pending.getSelectedRegionRoleId().isBlank() &&
                    (user.getSelectedRegionRoleId() == null || user.getSelectedRegionRoleId().isBlank())) {
                    user.setSelectedRegionRoleId(pending.getSelectedRegionRoleId());
                    hasChanges = true;
                    logger.debug("Synced region role selection for user {}: {}", user.getId(), pending.getSelectedRegionRoleId());
                }
                
                if (hasChanges) {
                    // Delete the pending role selection after successful sync
                    pendingRoleSelectionRepository.deleteById(user.getId());
                    
                    // Invalidate pending role selection cache
                    cacheConfig.invalidatePendingRoleSelectionCache(user.getId());
                    
                    logger.info("Successfully synced pending role selections for user {} and deleted pending record", user.getId());
                } else {
                    // No changes needed, but still delete the pending record to clean up
                    pendingRoleSelectionRepository.deleteById(user.getId());
                    cacheConfig.invalidatePendingRoleSelectionCache(user.getId());
                    logger.debug("No pending role selections to sync for user {}, deleted pending record", user.getId());
                }
            }
        } catch (Exception e) {
            logger.error("Error syncing pending role selections for user {}: {}", user.getId(), e.getMessage(), e);
            // Don't throw the exception - role sync failure shouldn't prevent user creation
        }
    }

    /**
     * Creates a new user or updates an existing one based on the provided DTO.
     */
    public User createOrUpdateUser(UserDTO userDTO) {
        String id = userDTO.getId();
        String username = userDTO.getUsername();
        String discriminator = userDTO.getDiscriminator();
        // This is the avatar URL fetched fresh from Discord via the DTO
        String discordAvatarFetched = userDTO.getAvatar(); 

        logger.debug("Attempting to create or update user with ID: {}. DTO Avatar: {}", id, discordAvatarFetched);

        Optional<User> userOpt = userRepository.findById(id);
        User user;

        if (userOpt.isEmpty()) {
            // New User Logic
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

            // Add ADMIN role if this is the admin user - with additional security validation
            if (id.equals(adminDiscordId) && adminDiscordId != null && !adminDiscordId.trim().isEmpty()) {
                // Additional validation: Ensure admin Discord ID is configured properly
                if (adminDiscordId.length() >= 17 && adminDiscordId.length() <= 20 && adminDiscordId.matches("\\d+")) {
                    initialRoles.add(Role.ADMIN); 
                    logger.info("Admin role automatically assigned to new user ID: {}", id);
                } else {
                    logger.error("Invalid admin Discord ID configuration detected: {}", adminDiscordId);
                    // Do not assign admin role if configuration is suspicious
                }
            }
            user.setRoles(initialRoles);
            
            user.setCredits(userDTO.getCredits() != null ? userDTO.getCredits() : 0); // Default credits
            user.setLevel(1); // Default level
            user.setExperience(0); // Default experience
            // Set other new user defaults as needed (e.g., displayName, pronouns, etc.)
            user.setDisplayName(username); // Default display name to username for new users

        } else {
            // Existing User Logic
            user = userOpt.get(); // User cannot be null here
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
                // The primary avatar should be updated if:
                // a) It's blank, null, or the special "USE_DISCORD_AVATAR" marker.
                // b) It's an existing Discord CDN URL (which may be stale).
                // This preserves custom non-Discord avatars while keeping Discord ones synced.
                boolean shouldUpdatePrimaryAvatar = currentPrimaryAvatar == null || currentPrimaryAvatar.isEmpty()
                        || "USE_DISCORD_AVATAR".equals(currentPrimaryAvatar)
                        || currentPrimaryAvatar.contains("cdn.discordapp.com");

                if (shouldUpdatePrimaryAvatar) {
                    if (!discordAvatarFetched.equals(currentPrimaryAvatar)) {
                        user.setAvatar(discordAvatarFetched);
                        logger.debug("Existing user {}: Updated primary avatar to fresh Discord avatar '{}'. Old: '{}'", id, discordAvatarFetched, currentPrimaryAvatar);
                    } else {
                        logger.debug("Existing user {}: Primary avatar ('{}') already matches fresh Discord avatar. No change.", id, currentPrimaryAvatar);
                    }
                } else {
                    // User has a custom primary avatar (e.g., Cloudinary URL). Preserve it.
                    logger.debug("Existing user {}: Preserved custom primary avatar '{}'. (New Discord avatar from DTO was '{}')", id, currentPrimaryAvatar, discordAvatarFetched);
                }
            } else { // discordAvatarFetched is null
                // If Discord provides no avatar, and the user was previously using a Discord avatar for their primary,
                // we should reset it to sync with their new (lack of) Discord avatar.
                boolean wasUsingDiscordAvatar = currentPrimaryAvatar != null
                    && (currentPrimaryAvatar.contains("cdn.discordapp.com") || "USE_DISCORD_AVATAR".equals(currentPrimaryAvatar));

                if (wasUsingDiscordAvatar) {
                    // Setting to USE_DISCORD_AVATAR is safer. mapToProfileDTO will handle the fallback.
                    user.setAvatar("USE_DISCORD_AVATAR");
                    logger.debug("Existing user {}: Reset primary avatar to USE_DISCORD_AVATAR as DTO avatar was null. Old: '{}'", id, currentPrimaryAvatar);
                } else {
                    logger.debug("Existing user {}: DTO avatar is null. Preserving custom primary avatar '{}'.", id, currentPrimaryAvatar);
                }
            }
        }

        // Update common fields for both new and existing users from DTO
        // (Username, discriminator might change on Discord)
        user.setUsername(username);
        user.setDiscriminator(discriminator);
        // Note: Email is no longer requested from Discord OAuth, so it remains null
        
        // Ensure admin role for the configured admin ID (applies to existing users too if role was removed)
        if (id.equals(adminDiscordId) && adminDiscordId != null && !adminDiscordId.trim().isEmpty()) {
            // Additional validation: Ensure admin Discord ID is configured properly
            if (adminDiscordId.length() >= 17 && adminDiscordId.length() <= 20 && adminDiscordId.matches("\\d+")) {
                if (user.getRoles() == null) user.setRoles(new HashSet<>()); // Ensure roles set is initialized
                if (!user.getRoles().contains(Role.ADMIN)) {
                    user.addRole(Role.ADMIN);
                    logger.info("Admin role ensured for user ID: {}", id);
                }
            } else {
                logger.error("Invalid admin Discord ID configuration detected during role check: {}", adminDiscordId);
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
        
        // Sync pending role selections before saving the user
        syncPendingRoleSelections(user);
                
        User savedUser = userRepository.save(user);

        // Invalidate user profile cache to ensure data consistency after login sync
        cacheConfig.invalidateUserProfileCache(id);
        logger.debug("Invalidated user profile cache for user {} after create/update.", id);

        return savedUser;
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
        if (updateProfileDTO.getDisplayName() != null) {
            user.setDisplayName(updateProfileDTO.getDisplayName());
        }
        
        if (updateProfileDTO.getPronouns() != null) {
            user.setPronouns(updateProfileDTO.getPronouns());
        }
        
        if (updateProfileDTO.getAbout() != null) {
            user.setAbout(updateProfileDTO.getAbout());
        }
        
        if (updateProfileDTO.getBannerColor() != null) {
            user.setBannerColor(updateProfileDTO.getBannerColor());
        }
        
        // Security check: Only MONARCH role users can set banner URLs
        if (updateProfileDTO.getBannerUrl() != null) {
            String previousBannerUrl = user.getBannerUrl();
            
            if (user.hasRole(Role.MONARCH) || user.hasRole(Role.ADMIN) || user.hasRole(Role.MODERATOR)) {
                // User has appropriate role, allow banner URL update
                user.setBannerUrl(updateProfileDTO.getBannerUrl());
                logger.debug("Banner URL update allowed for user {} with appropriate role", userId);
                
                // Create audit entry for successful security-sensitive operation
                createProfileUpdateAuditEntry(userId, "bannerUrl", previousBannerUrl, updateProfileDTO.getBannerUrl(), true, true);
            } else {
                // User lacks MONARCH role, log security attempt and ignore banner URL
                logger.warn("Security: User {} without MONARCH role attempted to set banner URL. Request ignored.", userId);
                
                // Create audit entry for unauthorized security-sensitive operation
                createProfileUpdateAuditEntry(userId, "bannerUrl", previousBannerUrl, updateProfileDTO.getBannerUrl(), true, false);
                
                // Do not update banner URL - keep existing value
            }
        } else {
            // Banner URL is null in the request, update normally
            String previousBannerUrl = user.getBannerUrl();
            user.setBannerUrl(updateProfileDTO.getBannerUrl());
            createProfileUpdateAuditEntry(userId, "bannerUrl", previousBannerUrl, null, false, true);
        }
        
        // Special handling for avatar
        if (updateProfileDTO.getAvatar() != null) {
            if (updateProfileDTO.getAvatar().isEmpty()) {
                // Empty avatar string means use Discord avatar
                user.setAvatar("USE_DISCORD_AVATAR");
                
                // Make sure we have a Discord avatar URL to fall back to
                if (user.getDiscordAvatarUrl() == null || user.getDiscordAvatarUrl().isEmpty()) {
                    logger.warn("No cached Discord avatar URL available for user: {}. Attempting to construct default Discord avatar.", userId);
                    
                    // AVATAR FALLBACK FIX: Generate default Discord avatar URL
                    // This handles users who don't have discordAvatarUrl cached
                    try {
                        String defaultDiscordAvatar = null;
                        
                        // Try to construct Discord avatar URL based on user's discriminator
                        if (user.getDiscriminator() != null && !user.getDiscriminator().isEmpty()) {
                            try {
                                int defaultAvatar = Integer.parseInt(user.getDiscriminator()) % 5;
                                defaultDiscordAvatar = "https://cdn.discordapp.com/embed/avatars/" + defaultAvatar + ".png";
                                logger.info("Generated default Discord avatar URL for user {}: {}", userId, defaultDiscordAvatar);
                            } catch (NumberFormatException e) {
                                logger.warn("Invalid discriminator for user {}: {}", userId, user.getDiscriminator());
                            }
                        }
                        
                        // If we couldn't generate a Discord avatar, we'll let it fall back to /default-avatar.png
                        if (defaultDiscordAvatar != null) {
                            user.setDiscordAvatarUrl(defaultDiscordAvatar);
                            logger.info("Cached generated Discord avatar URL for user {}: {}", userId, defaultDiscordAvatar);
                        } else {
                            logger.warn("Could not generate Discord avatar URL for user {}. Profile will use /default-avatar.png", userId);
                        }
                        
                    } catch (Exception e) {
                        logger.error("Error generating Discord avatar for user {}: {}", userId, e.getMessage());
                    }
                }
            } else {
                // Otherwise use the provided avatar
                user.setAvatar(updateProfileDTO.getAvatar());
                
                // If this is a Discord CDN URL, also update the cached URL
                if (updateProfileDTO.getAvatar().contains("cdn.discordapp.com")) {
                    user.setDiscordAvatarUrl(updateProfileDTO.getAvatar());
                    logger.debug("Updated Discord avatar cache from profile update for user: {}", userId);
                }
            }
        }
        
        // Save the updated user
        User updatedUser = userRepository.save(user);
        logger.info("Updated profile for user: {}", updatedUser.getUsername());
        
        // Invalidate user profile cache to ensure fresh data is served
        cacheConfig.invalidateUserProfileCache(userId);
        logger.debug("Invalidated user profile cache for user: {}", userId);
        
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
                avatarUrl = "/images/default-avatar.png";
                logger.warn("No cached Discord avatar URL found for user: {}, using default", user.getId());
            }
        } else if (avatarUrl == null || avatarUrl.isEmpty()) {
            avatarUrl = "/images/default-avatar.png";
            logger.debug("Empty avatar URL for user: {}, using default", user.getId());
        } else {
            logger.debug("Using custom avatar URL for user: {}: {}", user.getId(), avatarUrl);
        }
        
        // Get the user's equipped badge if any
        UUID badgeId = user.getEquippedBadgeId();
        
        // Variables for badge URL and name
        String badgeUrl = null;
        String badgeName = null;
        
        // If user has an equipped badge, fetch its details
        if (badgeId != null) {
            Optional<Shop> badgeOpt = shopRepository.findById(badgeId);
            if (badgeOpt.isPresent()) {
                Shop badge = badgeOpt.get();
                badgeUrl = badge.getThumbnailUrl();
                badgeName = badge.getName();
            }
        }
        
        // Resolve equipped nameplate color
        String nameplateColor = null;
        UUID equippedUserColorId = user.getEquippedUserColorId();
        if (equippedUserColorId != null) {
            try {
                shopRepository.findById(equippedUserColorId).ifPresent(userColorItem -> {
                    // For USER_COLOR items, the imageUrl contains the hex color value
                    if (userColorItem.getImageUrl() != null && !userColorItem.getImageUrl().isEmpty()) {
                        // The imageUrl for USER_COLOR items should be a hex color like "#FF5733"
                        logger.debug("Resolved nameplate color for user {}: {}", user.getId(), userColorItem.getImageUrl());
                    }
                });
                // Use a more direct approach to get the color
                nameplateColor = shopRepository.findById(equippedUserColorId)
                    .map(item -> item.getImageUrl())
                    .orElse(null);
            } catch (Exception e) {
                logger.warn("Failed to resolve nameplate color for user {}: {}", user.getId(), e.getMessage());
            }
        }
        
        // Calculate required XP for next level
        int currentLevel = user.getLevel() != null ? user.getLevel() : 1;
        int requiredXp = calculateRequiredXp(currentLevel);

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
                .level(currentLevel)
                .experience(user.getExperience())
                .xpForNextLevel(requiredXp) // Add the calculated required XP for next level
                .messageCount(user.getMessageCount()) // Add the message count field
                .fishCaughtCount(user.getFishCaughtCount()) // Add the fish caught count
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
                .equippedBadgeId(badgeId)
                .badgeUrl(badgeUrl)
                .badgeName(badgeName) // Add the badge name
                .nameplateColor(nameplateColor) // Add resolved nameplate color
                .dailyStreak(user.getDailyStreak()) // Add daily claim fields
                .lastDailyClaim(user.getLastDailyClaim())
                .selectedAgeRoleId(user.getSelectedAgeRoleId())
                .selectedGenderRoleId(user.getSelectedGenderRoleId())
                .selectedRankRoleId(user.getSelectedRankRoleId())
                .selectedRegionRoleId(user.getSelectedRegionRoleId())
                .build();
    }

    /**
     * Updates an existing user entity.
     *
     * @param user the user entity to update
     * @return the updated User entity
     */
    @Transactional
    public User updateUser(User user) {
        logger.debug("Updating user entity for user ID: {}", user.getId());
        User updatedUser = userRepository.save(user);
        
        // Invalidate user profile cache to ensure data consistency across the app
        cacheConfig.invalidateUserProfileCache(user.getId());
        
        return updatedUser;
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
        
        boolean successful = false;
        String errorMessage = null;
        User user = null;
        
        try {
            // Only allow ADMIN to assign ADMIN or MODERATOR roles
            if ((role == Role.ADMIN || role == Role.MODERATOR) && 
                !userRepository.hasRole(adminId, Role.ADMIN)) {
                errorMessage = "Only ADMIN users can assign ADMIN or MODERATOR roles";
                throw new UnauthorizedOperationException(errorMessage);
            }
            
            user = userRepository.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));
            
            user.addRole(role);
            user = userRepository.save(user);
            successful = true;
            
            logger.info("Successfully assigned role {} to user {} by admin {}", role, userId, adminId);
            return user;
            
        } catch (Exception e) {
            errorMessage = e.getMessage();
            logger.error("Failed to assign role {} to user {} by admin {}: {}", role, userId, adminId, e.getMessage());
            throw e;
        } finally {
            // Create audit trail regardless of success or failure
            createRoleAssignmentAuditEntry(adminId, userId, role, successful, errorMessage);
        }
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
        
        boolean successful = false;
        String errorMessage = null;
        User user = null;
        
        try {
            // Only allow ADMIN to remove ADMIN or MODERATOR roles
            if ((role == Role.ADMIN || role == Role.MODERATOR) && 
                !userRepository.hasRole(adminId, Role.ADMIN)) {
                errorMessage = "Only ADMIN users can remove ADMIN or MODERATOR roles";
                throw new UnauthorizedOperationException(errorMessage);
            }
            
            user = userRepository.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));
            
            user.removeRole(role);
            user = userRepository.save(user);
            successful = true;
            
            logger.info("Successfully removed role {} from user {} by admin {}", role, userId, adminId);
            return user;
            
        } catch (Exception e) {
            errorMessage = e.getMessage();
            logger.error("Failed to remove role {} from user {} by admin {}: {}", role, userId, adminId, e.getMessage());
            throw e;
        } finally {
            // Create audit trail regardless of success or failure
            createRoleRemovalAuditEntry(adminId, userId, role, successful, errorMessage);
        }
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
            // Search by username containing the search term (email no longer available)
            users = userRepository.findByUsernameContaining(search, pageable);
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
     * @param adminId the ID of the admin performing the operation
     * @return the updated user
     * @throws ResourceNotFoundException if the user is not found
     */
    @PreAuthorize("hasRole('ADMIN')")
    public User updateUserCredits(String userId, Integer credits, String adminId) {
        logger.debug("Updating credits for user {} to {} by admin {}", userId, credits, adminId);
        
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));
        
        // Capture previous credits for audit trail
        int previousCredits = user.getCredits() != null ? user.getCredits() : 0;
        
        // Ensure credits are not negative
        if (credits < 0) {
            credits = 0;
        }
        
        user.setCredits(credits);
        User updatedUser = userRepository.save(user);
        
        // Create audit trail for credit update
        createCreditUpdateAuditEntry(adminId, userId, previousCredits, credits);
        
        // Invalidate leaderboard cache since credits affect ranking
        cacheConfig.invalidateLeaderboardCache();
        
        logger.info("Updated credits for user {} from {} to {} by admin {}", userId, previousCredits, credits, adminId);
        
        return updatedUser;
    }

    /**
     * Overloaded method for backward compatibility - extracts admin ID from security context
     */
    @PreAuthorize("hasRole('ADMIN')")
    public User updateUserCredits(String userId, Integer credits) {
        String adminId = getCurrentAdminId();
        return updateUserCredits(userId, credits, adminId);
    }

    /**
     * Utility method to get the current admin's ID from the security context
     */
    private String getCurrentAdminId() {
        try {
            // Try to get the current authentication from Spring Security context
            org.springframework.security.core.Authentication authentication = 
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            
            if (authentication != null && authentication.isAuthenticated()) {
                // The principal name should be the user ID in our system
                return authentication.getName();
            }
        } catch (Exception e) {
            logger.debug("Unable to extract admin ID from security context: {}", e.getMessage());
        }
        
        // Fallback to "SYSTEM" if we can't determine the admin ID
        return "SYSTEM";
    }

    /**
     * Get users for the leaderboard, sorted by a specified criterion.
     * Fetches a paginated and optimized list of the top 100 users from the database,
     * with sorting handled at the database level for efficiency and correctness.
     *
     * @param sortBy Sorting criterion: "credits", "level", "messages", or "voice"
     * @return List of sorted LeaderboardEntryDTOs with calculated ranks
     */
    public List<LeaderboardEntryDTO> getLeaderboardUsers(String sortBy) {
        Sort sort;
        switch (sortBy.toLowerCase()) {
            case "level":
                // For level, nulls should also be last. Combine with secondary sort on experience.
                sort = Sort.by(
                    new Sort.Order(Direction.DESC, "level", Sort.NullHandling.NULLS_LAST),
                    new Sort.Order(Direction.DESC, "experience", Sort.NullHandling.NULLS_LAST)
                );
                break;
            case "messages":
                sort = Sort.by(new Sort.Order(Direction.DESC, "messageCount", Sort.NullHandling.NULLS_LAST));
                break;
            case "voice":
                sort = Sort.by(new Sort.Order(Direction.DESC, "voiceTimeMinutesTotal", Sort.NullHandling.NULLS_LAST));
                break;
            case "credits":
            default:
                sort = Sort.by(new Sort.Order(Direction.DESC, "credits", Sort.NullHandling.NULLS_LAST));
                break;
        }

        // Fetch the top 100 users, sorted by the database.
        Pageable pageable = PageRequest.of(0, 100, sort);
        Page<LeaderboardEntryDTO> userPage = userRepository.findLeaderboardEntries(pageable);
        List<LeaderboardEntryDTO> leaderboardEntries = new ArrayList<>(userPage.getContent());

        // The list is already sorted by the database, so we just need to assign ranks.
        IntStream.range(0, leaderboardEntries.size())
                 .forEach(i -> leaderboardEntries.get(i).setRank(i + 1));
        
        return leaderboardEntries;
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
        dto.setEmail(null); // Email is no longer available from Discord OAuth
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

    /**
     * Get inventory items for a specific user.
     * Only accessible to ADMIN users.
     * Checks both new inventory system (with quantities) and legacy inventory system.
     * 
     * @param userId the ID of the user whose inventory to fetch
     * @return list of user's inventory items with details
     */
    @PreAuthorize("hasRole('ADMIN')")
    public List<UserInventoryItemDTO> getUserInventoryItems(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));
        
        // Get items from new inventory system (with quantities)
        List<UserInventoryItem> newInventoryItems = userInventoryItemRepository.findByUserWithQuantity(user);
        
        // Convert new inventory items to DTOs
        List<UserInventoryItemDTO> dtoList = newInventoryItems.stream()
                .map(this::mapInventoryItemToDTO)
                .collect(Collectors.toList());
        
        // Get item IDs from new inventory to avoid duplicates
        Set<UUID> newInventoryItemIds = newInventoryItems.stream()
                .map(item -> item.getItem().getId())
                .collect(Collectors.toSet());
        
        // Get items from legacy inventory system that aren't already in new system
        Set<Shop> legacyInventoryItems = user.getInventory();
        if (legacyInventoryItems != null) {
            List<UserInventoryItemDTO> legacyDtos = legacyInventoryItems.stream()
                    .filter(item -> !newInventoryItemIds.contains(item.getId())) // Avoid duplicates
                    .map(this::mapLegacyInventoryItemToDTO)
                    .collect(Collectors.toList());
            
            dtoList.addAll(legacyDtos);
        }
        
        return dtoList;
    }

    /**
     * Maps a UserInventoryItem entity to UserInventoryItemDTO.
     * 
     * @param inventoryItem the inventory item entity
     * @return the corresponding DTO
     */
    private UserInventoryItemDTO mapInventoryItemToDTO(UserInventoryItem inventoryItem) {
        Shop item = inventoryItem.getItem();
        
        return UserInventoryItemDTO.builder()
                .itemId(item.getId())
                .name(item.getName())
                .description(item.getDescription())
                .category(item.getCategory())
                .thumbnailUrl(item.getThumbnailUrl())
                .imageUrl(item.getImageUrl())
                .quantity(inventoryItem.getQuantity())
                .price(item.getPrice())
                .build();
    }

    /**
     * Maps a legacy Shop inventory item to UserInventoryItemDTO.
     * Legacy items have no quantity tracking, so quantity defaults to 1.
     * 
     * @param item the shop item from legacy inventory
     * @return the corresponding DTO
     */
    private UserInventoryItemDTO mapLegacyInventoryItemToDTO(Shop item) {
        return UserInventoryItemDTO.builder()
                .itemId(item.getId())
                .name(item.getName())
                .description(item.getDescription())
                .category(item.getCategory())
                .thumbnailUrl(item.getThumbnailUrl())
                .imageUrl(item.getImageUrl())
                .quantity(1) // Legacy items don't have quantity tracking, default to 1
                .price(item.getPrice())
                .build();
    }

    /**
     * Remove an item from a user's inventory by admin.
     * Handles both new inventory system (with quantities) and legacy inventory system.
     * Automatically refunds credits if the item was purchased (price > 0).
     * Unequips the item if it's currently equipped.
     * 
     * @param userId the ID of the user whose inventory to modify
     * @param itemId the ID of the item to remove
     * @param adminId the ID of the admin performing the operation
     * @return the updated user profile
     * @throws ResourceNotFoundException if user or item not found
     */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public UserProfileDTO removeInventoryItem(String userId, UUID itemId, String adminId) {
        logger.debug("Admin {} removing item {} from user {}'s inventory", adminId, itemId, userId);
        
        // Verify user exists
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));
        
        // Get the shop item for refund calculation and validation
        Shop shopItem = shopRepository.findById(itemId)
                .orElse(null); // Item might have been deleted from shop, but still in inventory
        
        boolean itemRemoved = false;
        boolean wasEquipped = false;
        int refundAmount = 0;
        String itemName = "Unknown Item";
        
        // Check if item is equipped and unequip it first (skip for CASE items as they cannot be equipped)
        if (shopItem != null && shopItem.getCategory() != ShopCategory.CASE) {
            UUID equippedItemId = user.getEquippedItemIdByCategory(shopItem.getCategory());
            if (equippedItemId != null && equippedItemId.equals(itemId)) {
                user.setEquippedItemIdByCategory(shopItem.getCategory(), null);
                wasEquipped = true;
                logger.debug("Unequipped item {} from user {} before removal", itemId, userId);
            }
        }
        
        // Try to remove from new inventory system first
        if (shopItem != null) {
            Optional<UserInventoryItem> inventoryItemOpt = userInventoryItemRepository.findByUserAndItem(user, shopItem);
            if (inventoryItemOpt.isPresent()) {
                UserInventoryItem inventoryItem = inventoryItemOpt.get();
                itemName = inventoryItem.getItem().getName();
                
                // Calculate refund if item has a price
                if (inventoryItem.getItem().getPrice() != null && inventoryItem.getItem().getPrice() > 0) {
                    refundAmount = inventoryItem.getItem().getPrice() * inventoryItem.getQuantity();
                }
                
                // Remove from new inventory
                userInventoryItemRepository.delete(inventoryItem);
                itemRemoved = true;
                
                logger.debug("Removed item {} (quantity: {}) from new inventory system for user {}", 
                        itemId, inventoryItem.getQuantity(), userId);
            }
        } else {
            // Handle deleted shop items by finding the inventory item directly by item ID
            List<UserInventoryItem> allUserItems = userInventoryItemRepository.findByUser(user);
            Optional<UserInventoryItem> inventoryItemOpt = allUserItems.stream()
                .filter(item -> item.getItem().getId().equals(itemId))
                .findFirst();
            
            if (inventoryItemOpt.isPresent()) {
                UserInventoryItem inventoryItem = inventoryItemOpt.get();
                itemName = inventoryItem.getItem().getName();
                
                // For deleted shop items, we can't refund as we don't know the original price
                refundAmount = 0;
                
                // Remove from new inventory
                userInventoryItemRepository.delete(inventoryItem);
                itemRemoved = true;
                
                logger.debug("Removed deleted item {} (quantity: {}) from new inventory system for user {} - no refund", 
                        itemId, inventoryItem.getQuantity(), userId);
            }
        }
        
        // Also check and remove from legacy inventory system
        if (user.getInventory() != null && shopItem != null) {
            boolean removedFromLegacy = user.getInventory().removeIf(item -> item.getId().equals(itemId));
            if (removedFromLegacy && !itemRemoved) {
                // Only calculate refund if not already calculated from new system
                if (shopItem.getPrice() != null && shopItem.getPrice() > 0) {
                    refundAmount = shopItem.getPrice(); // Legacy items have quantity 1
                }
                itemName = shopItem.getName();
                itemRemoved = true;
                logger.debug("Removed item {} from legacy inventory system for user {}", itemId, userId);
            }
        }
        
        // Verify that the item was actually removed
        if (!itemRemoved) {
            throw new ResourceNotFoundException("Item not found in user's inventory: " + itemId);
        }
        
        // Process credit refund if applicable
        if (refundAmount > 0) {
            int currentCredits = user.getCredits() != null ? user.getCredits() : 0;
            user.setCredits(currentCredits + refundAmount);
            logger.info("Refunded {} credits to user {} for removed item {}", refundAmount, userId, itemId);
        }
        
        // Save the updated user
        User updatedUser = userRepository.save(user);
        
        // Create audit trail for inventory removal
        createInventoryRemovalAuditEntry(adminId, userId, itemId, itemName, refundAmount, wasEquipped);
        
        // Invalidate relevant caches
        cacheConfig.invalidateUserProfileCache(userId);
        cacheConfig.invalidateLeaderboardCache();
        
        // Audit log
        logger.info("ADMIN INVENTORY REMOVAL - Admin: {}, User: {}, Item: {} ({}), Refund: {} credits, Was Equipped: {}", 
                adminId, userId, itemId, itemName, refundAmount, wasEquipped);
        
        return mapToProfileDTO(updatedUser);
    }

    /**
     * Utility method to get the client IP address from the current HTTP request context
     */
    private String getClientIp() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                
                // Check for X-Forwarded-For header first (for proxied requests)
                String xForwardedFor = request.getHeader("X-Forwarded-For");
                if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
                    // X-Forwarded-For can contain multiple IPs, take the first one
                    return xForwardedFor.split(",")[0].trim();
                }
                
                // Check for X-Real-IP header (alternative proxy header)
                String xRealIp = request.getHeader("X-Real-IP");
                if (xRealIp != null && !xRealIp.isEmpty()) {
                    return xRealIp;
                }
                
                // Fallback to standard remote address
                return request.getRemoteAddr();
            }
        } catch (Exception e) {
            logger.debug("Unable to extract client IP address: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Utility method to get the user agent from the current HTTP request context
     */
    private String getUserAgent() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                return request.getHeader("User-Agent");
            }
        } catch (Exception e) {
            logger.debug("Unable to extract user agent: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Utility method to get the session ID from the current HTTP request context
     */
    private String getSessionId() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                return request.getSession(false) != null ? request.getSession(false).getId() : null;
            }
        } catch (Exception e) {
            logger.debug("Unable to extract session ID: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Creates an audit entry for role assignment operations
     */
    private void createRoleAssignmentAuditEntry(String adminId, String targetUserId, Role role, boolean successful, String errorMessage) {
        try {
            // Determine severity based on role being assigned and operation outcome
            AuditSeverity severity;
            if (!successful) {
                severity = AuditSeverity.WARNING;
            } else if (role == Role.ADMIN || role == Role.MODERATOR) {
                severity = AuditSeverity.HIGH;
            } else {
                severity = AuditSeverity.INFO;
            }

            // Build description
            String description = successful 
                ? String.format("Assigned role %s to user %s", role.name(), targetUserId)
                : String.format("Failed to assign role %s to user %s - %s", role.name(), targetUserId, errorMessage);

            // Build detailed JSON for audit trail
            Map<String, Object> details = new HashMap<>();
            details.put("adminId", adminId);
            details.put("targetUserId", targetUserId);
            details.put("assignedRole", role.name());
            details.put("successful", successful);
            details.put("timestamp", LocalDateTime.now().toString());
            
            if (errorMessage != null) {
                details.put("errorMessage", errorMessage);
            }

            String detailsJson;
            try {
                detailsJson = objectMapper.writeValueAsString(details);
            } catch (JsonProcessingException e) {
                logger.error("Failed to serialize role assignment details to JSON: {}", e.getMessage());
                detailsJson = "{\"error\": \"Failed to serialize role assignment details\"}";
            }

            // Create audit entry
            CreateAuditDTO auditDTO = CreateAuditDTO.builder()
                .userId(adminId)
                .action("ASSIGN_ROLE")
                .entityType("User")
                .entityId(targetUserId)
                .description(description)
                .ipAddress(getClientIp())
                .userAgent(getUserAgent())
                .sessionId(getSessionId())
                .severity(severity)
                .category(AuditCategory.USER_MANAGEMENT)
                .details(detailsJson)
                .source("UserService")
                .build();

            // Use createSystemAuditEntry for internal operations
            auditService.createSystemAuditEntry(auditDTO);

        } catch (Exception e) {
            // Log the error but don't let audit failures break the role assignment flow
            logger.error("Failed to create audit entry for role assignment - adminId: {}, targetUserId: {}, role: {}, error: {}", 
                adminId, targetUserId, role, e.getMessage(), e);
        }
    }

    /**
     * Creates an audit entry for role removal operations
     */
    private void createRoleRemovalAuditEntry(String adminId, String targetUserId, Role role, boolean successful, String errorMessage) {
        try {
            // Determine severity based on role being removed and operation outcome
            AuditSeverity severity;
            if (!successful) {
                severity = AuditSeverity.WARNING;
            } else if (role == Role.ADMIN || role == Role.MODERATOR) {
                severity = AuditSeverity.HIGH;
            } else {
                severity = AuditSeverity.WARNING;
            }

            // Build description
            String description = successful 
                ? String.format("Removed role %s from user %s", role.name(), targetUserId)
                : String.format("Failed to remove role %s from user %s - %s", role.name(), targetUserId, errorMessage);

            // Build detailed JSON for audit trail
            Map<String, Object> details = new HashMap<>();
            details.put("adminId", adminId);
            details.put("targetUserId", targetUserId);
            details.put("removedRole", role.name());
            details.put("successful", successful);
            details.put("timestamp", LocalDateTime.now().toString());
            
            if (errorMessage != null) {
                details.put("errorMessage", errorMessage);
            }

            String detailsJson;
            try {
                detailsJson = objectMapper.writeValueAsString(details);
            } catch (JsonProcessingException e) {
                logger.error("Failed to serialize role removal details to JSON: {}", e.getMessage());
                detailsJson = "{\"error\": \"Failed to serialize role removal details\"}";
            }

            // Create audit entry
            CreateAuditDTO auditDTO = CreateAuditDTO.builder()
                .userId(adminId)
                .action("REMOVE_ROLE")
                .entityType("User")
                .entityId(targetUserId)
                .description(description)
                .ipAddress(getClientIp())
                .userAgent(getUserAgent())
                .sessionId(getSessionId())
                .severity(severity)
                .category(AuditCategory.USER_MANAGEMENT)
                .details(detailsJson)
                .source("UserService")
                .build();

            // Use createSystemAuditEntry for internal operations
            auditService.createSystemAuditEntry(auditDTO);

        } catch (Exception e) {
            // Log the error but don't let audit failures break the role removal flow
            logger.error("Failed to create audit entry for role removal - adminId: {}, targetUserId: {}, role: {}, error: {}", 
                adminId, targetUserId, role, e.getMessage(), e);
        }
    }

    /**
     * Creates an audit entry for credit modification operations
     */
    private void createCreditUpdateAuditEntry(String adminId, String targetUserId, int previousCredits, int newCredits) {
        try {
            int difference = newCredits - previousCredits;
            String operation = difference > 0 ? "increased" : difference < 0 ? "decreased" : "unchanged";
            
            // Build description
            String description = String.format("Credits %s for user %s from %d to %d (difference: %+d)", 
                operation, targetUserId, previousCredits, newCredits, difference);

            // Build detailed JSON for audit trail
            Map<String, Object> details = new HashMap<>();
            details.put("adminId", adminId);
            details.put("targetUserId", targetUserId);
            details.put("previousCredits", previousCredits);
            details.put("newCredits", newCredits);
            details.put("difference", difference);
            details.put("operation", operation);
            details.put("timestamp", LocalDateTime.now().toString());

            String detailsJson;
            try {
                detailsJson = objectMapper.writeValueAsString(details);
            } catch (JsonProcessingException e) {
                logger.error("Failed to serialize credit update details to JSON: {}", e.getMessage());
                detailsJson = "{\"error\": \"Failed to serialize credit update details\"}";
            }

            // Create audit entry
            CreateAuditDTO auditDTO = CreateAuditDTO.builder()
                .userId(adminId)
                .action("UPDATE_CREDITS")
                .entityType("User")
                .entityId(targetUserId)
                .description(description)
                .ipAddress(getClientIp())
                .userAgent(getUserAgent())
                .sessionId(getSessionId())
                .severity(AuditSeverity.INFO)
                .category(AuditCategory.FINANCIAL)
                .details(detailsJson)
                .source("UserService")
                .build();

            // Use createSystemAuditEntry for internal operations
            auditService.createSystemAuditEntry(auditDTO);

        } catch (Exception e) {
            // Log the error but don't let audit failures break the credit update flow
            logger.error("Failed to create audit entry for credit update - adminId: {}, targetUserId: {}, previousCredits: {}, newCredits: {}, error: {}", 
                adminId, targetUserId, previousCredits, newCredits, e.getMessage(), e);
        }
    }

    /**
     * Creates an audit entry for profile update operations, particularly for security-sensitive changes
     */
    private void createProfileUpdateAuditEntry(String userId, String fieldName, String previousValue, String newValue, boolean isSecuritySensitive, boolean wasAuthorized) {
        try {
            // Determine severity and category based on security sensitivity and authorization
            AuditSeverity severity;
            AuditCategory category;
            
            if (isSecuritySensitive) {
                category = AuditCategory.SECURITY;
                severity = wasAuthorized ? AuditSeverity.INFO : AuditSeverity.WARNING;
            } else {
                category = AuditCategory.USER_MANAGEMENT;
                severity = AuditSeverity.INFO;
            }

            // Build description
            String description;
            if (isSecuritySensitive && !wasAuthorized) {
                description = String.format("Unauthorized attempt to update %s for user %s", fieldName, userId);
            } else {
                description = String.format("Updated %s for user %s", fieldName, userId);
            }

            // Build detailed JSON for audit trail (be careful with sensitive data)
            Map<String, Object> details = new HashMap<>();
            details.put("userId", userId);
            details.put("fieldName", fieldName);
            details.put("isSecuritySensitive", isSecuritySensitive);
            details.put("wasAuthorized", wasAuthorized);
            details.put("timestamp", LocalDateTime.now().toString());
            
            // Only include actual values for non-sensitive fields or authorized operations
            if (!isSecuritySensitive || wasAuthorized) {
                details.put("previousValue", previousValue != null ? previousValue : "null");
                details.put("newValue", newValue != null ? newValue : "null");
            } else {
                details.put("note", "Values not logged due to unauthorized access attempt");
            }

            String detailsJson;
            try {
                detailsJson = objectMapper.writeValueAsString(details);
            } catch (JsonProcessingException e) {
                logger.error("Failed to serialize profile update details to JSON: {}", e.getMessage());
                detailsJson = "{\"error\": \"Failed to serialize profile update details\"}";
            }

            // Create audit entry
            CreateAuditDTO auditDTO = CreateAuditDTO.builder()
                .userId(userId)
                .action("UPDATE_PROFILE")
                .entityType("User")
                .entityId(userId)
                .description(description)
                .ipAddress(getClientIp())
                .userAgent(getUserAgent())
                .sessionId(getSessionId())
                .severity(severity)
                .category(category)
                .details(detailsJson)
                .source("UserService")
                .build();

            // Use createSystemAuditEntry for internal operations
            auditService.createSystemAuditEntry(auditDTO);

        } catch (Exception e) {
            // Log the error but don't let audit failures break the profile update flow
            logger.error("Failed to create audit entry for profile update - userId: {}, fieldName: {}, error: {}", 
                userId, fieldName, e.getMessage(), e);
        }
    }

    /**
     * Creates an audit entry for inventory item removal operations
     */
    private void createInventoryRemovalAuditEntry(String adminId, String targetUserId, UUID itemId, String itemName, int refundAmount, boolean wasEquipped) {
        try {
            // Build description
            String description = String.format("Removed item '%s' from user %s's inventory%s%s", 
                itemName, 
                targetUserId,
                refundAmount > 0 ? String.format(" (refunded %d credits)", refundAmount) : "",
                wasEquipped ? " (item was equipped)" : "");

            // Build detailed JSON for audit trail
            Map<String, Object> details = new HashMap<>();
            details.put("adminId", adminId);
            details.put("targetUserId", targetUserId);
            details.put("itemId", itemId.toString());
            details.put("itemName", itemName);
            details.put("refundAmount", refundAmount);
            details.put("wasEquipped", wasEquipped);
            details.put("timestamp", LocalDateTime.now().toString());

            String detailsJson;
            try {
                detailsJson = objectMapper.writeValueAsString(details);
            } catch (JsonProcessingException e) {
                logger.error("Failed to serialize inventory removal details to JSON: {}", e.getMessage());
                detailsJson = "{\"error\": \"Failed to serialize inventory removal details\"}";
            }

            // Create audit entry
            CreateAuditDTO auditDTO = CreateAuditDTO.builder()
                .userId(adminId)
                .action("REMOVE_INVENTORY_ITEM")
                .entityType("User")
                .entityId(targetUserId)
                .description(description)
                .ipAddress(getClientIp())
                .userAgent(getUserAgent())
                .sessionId(getSessionId())
                .severity(AuditSeverity.INFO)
                .category(AuditCategory.DATA_ACCESS)
                .details(detailsJson)
                .source("UserService")
                .build();

            // Use createSystemAuditEntry for internal operations
            auditService.createSystemAuditEntry(auditDTO);

        } catch (Exception e) {
            // Log the error but don't let audit failures break the inventory removal flow
            logger.error("Failed to create audit entry for inventory removal - adminId: {}, targetUserId: {}, itemId: {}, error: {}", 
                adminId, targetUserId, itemId, e.getMessage(), e);
        }
    }
}
