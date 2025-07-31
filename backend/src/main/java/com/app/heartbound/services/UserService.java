package com.app.heartbound.services;

import com.app.heartbound.dto.UserDTO;
import com.app.heartbound.dto.UpdateProfileDTO;
import com.app.heartbound.dto.UserProfileDTO;
import com.app.heartbound.dto.PublicUserProfileDTO;
import com.app.heartbound.dto.DailyActivityDataDTO;
import com.app.heartbound.dto.CreateAuditDTO;
import com.app.heartbound.enums.Role;
import com.app.heartbound.enums.AuditSeverity;
import com.app.heartbound.enums.AuditCategory;
import com.app.heartbound.entities.User;
import com.app.heartbound.entities.Shop;
import com.app.heartbound.entities.DailyMessageStat;
import com.app.heartbound.entities.DailyVoiceActivityStat;
import com.app.heartbound.repositories.UserRepository;
import com.app.heartbound.repositories.shop.ShopRepository;
import com.app.heartbound.repositories.DailyMessageStatRepository;
import com.app.heartbound.repositories.DailyVoiceActivityStatRepository;
import com.app.heartbound.repositories.ItemInstanceRepository;
import com.app.heartbound.exceptions.ResourceNotFoundException;
import com.app.heartbound.exceptions.UnauthorizedOperationException;
import com.app.heartbound.config.CacheConfig;
import com.app.heartbound.dto.LeaderboardEntryDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
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
import java.util.Map;
import java.util.UUID;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Optional;
import java.util.stream.IntStream;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import com.app.heartbound.dto.RegisterRequestDTO;
import com.app.heartbound.entities.DiscordBotSettings;
import net.dv8tion.jda.api.entities.Member;
import java.util.Arrays;
import com.app.heartbound.entities.ItemInstance;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final ShopRepository shopRepository;
    private final ItemInstanceRepository itemInstanceRepository;
    private final DailyMessageStatRepository dailyMessageStatRepository;
    private final DailyVoiceActivityStatRepository dailyVoiceActivityStatRepository;
    private final PendingPrisonService pendingPrisonService;
    private final CacheConfig cacheConfig;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final JDA jda;
    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    // Read admin Discord ID from environment variables
    @Value("${admin.discord.id}")
    private String adminDiscordId;

    @Value("${discord.guild.id}")
    private String guildId;

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
    public UserService(UserRepository userRepository, ShopRepository shopRepository, ItemInstanceRepository itemInstanceRepository, DailyMessageStatRepository dailyMessageStatRepository, DailyVoiceActivityStatRepository dailyVoiceActivityStatRepository, PendingPrisonService pendingPrisonService, CacheConfig cacheConfig, AuditService auditService, ObjectMapper objectMapper, @Lazy JDA jda) {
        this.userRepository = userRepository;
        this.shopRepository = shopRepository;
        this.itemInstanceRepository = itemInstanceRepository;
        this.dailyMessageStatRepository = dailyMessageStatRepository;
        this.dailyVoiceActivityStatRepository = dailyVoiceActivityStatRepository;
        this.pendingPrisonService = pendingPrisonService;
        this.cacheConfig = cacheConfig;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.jda = jda;
    }

    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    /**
     * Calculate the required XP for a given level using the same formula as ChatActivityListener.
     * This ensures consistency across the application.
     *
     * @param level the level to calculate required XP for
     * @return the required XP for the given level
     */
    private int calculateRequiredXp(int level) {
        // Base XP + (level * multiplier)^exponent + (level * factor)
        return baseXp + (int) Math.pow(level * levelMultiplier, levelExponent) + (level * levelFactor);
    }



    /**
     * Syncs a pending prison record to a newly created User entity.
     * @param user The user to sync the record to.
     */
    private void syncPendingPrison(User user) {
        try {
            pendingPrisonService.findByDiscordUserId(user.getId()).ifPresent(pending -> {
                user.setOriginalRoleIds(pending.getOriginalRoleIds());
                user.setPrisonedAt(pending.getPrisonedAt());
                user.setPrisonReleaseAt(pending.getPrisonReleaseAt());

                pendingPrisonService.deleteByDiscordUserId(user.getId());
                logger.info("Successfully synced pending prison record for user {} and deleted pending record.", user.getId());
            });
        } catch (Exception e) {
            logger.error("Error syncing pending prison record for user {}: {}", user.getId(), e.getMessage(), e);
        }
    }

    /**
     * Creates a new user or updates an existing one based on the provided DTO.
     */
    @Transactional
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
        
        // Sync ban status with Discord as the source of truth
        try {
            Guild guild = jda.getGuildById(guildId);
            if (guild != null) {
                // This is a blocking call to ensure ban status is known before proceeding
                guild.retrieveBan(net.dv8tion.jda.api.entities.User.fromId(id)).complete();
                // If the above line doesn't throw an exception, the user IS banned on Discord.
                if (user.getBanned() == null || !user.getBanned()) {
                    user.setBanned(true);
                    logger.info("User {} is banned on Discord. Syncing status to application.", id);
                }
            } else {
                logger.warn("Discord Guild with ID {} not found. Cannot sync ban status.", guildId);
            }
        } catch (ErrorResponseException e) {
            if (e.getErrorResponse() == ErrorResponse.UNKNOWN_BAN) {
                // This error means the user is NOT banned.
                if (user.getBanned() != null && user.getBanned()) {
                    user.setBanned(false);
                    logger.info("User {} is not banned on Discord, but was in DB. Syncing status to application.", id);
                }
            } else {
                // A different Discord API error occurred.
                logger.error("Discord API error while checking ban status for user {}: {}", id, e.getMessage());
            }
        } catch (Exception e) {
            // Catch any other exceptions during the process.
            logger.error("An unexpected error occurred while syncing ban status for user {}: {}", id, e.getMessage(), e);
        }
        
        // Sync pending data before saving the user with user-level locking to prevent race conditions
        User savedUser;
        synchronized(user.getId().intern()) {
            syncPendingPrison(user);
                    
            savedUser = userRepository.save(user);
        }

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
     * Retrieves a user by their ID with a pessimistic write lock to prevent race conditions.
     *
     * @param id the user identifier
     * @return the locked User entity if found, otherwise null
     */
    @Transactional
    public User getUserByIdWithLock(String id) {
        return userRepository.findByIdWithLock(id).orElse(null);
    }

    /**
     * Retrieves a user by their ID with their inventory eagerly fetched.
     *
     * @param id the user identifier
     * @return the User entity with itemInstances initialized, or null if not found
     */
    @Transactional(readOnly = true)
    public User getUserByIdWithInventory(String id) {
        return userRepository.findByIdWithInventory(id).orElse(null);
    }

    /**
     * Check if a user exists by their ID.
     * @param userId The ID of the user to check.
     * @return true if the user exists, false otherwise.
     */
    public boolean userExists(String userId) {
        return userRepository.existsById(userId);
    }

    /**
     * Updates profile information for a user.
     *
     * @param userId the ID of the user to update
     * @param updateProfileDTO the profile data to update
     * @return the updated UserProfileDTO
     */
    public User updateUserProfile(String userId, UpdateProfileDTO updateProfileDTO) {
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
        
        // Save the updated user
        User updatedUser = userRepository.save(user);
        logger.info("Updated profile for user: {}", updatedUser.getUsername());
        
        // Invalidate user profile cache to ensure fresh data is served
        cacheConfig.invalidateUserProfileCache(userId);
        logger.debug("Invalidated user profile cache for user: {}", userId);
        
        // Convert to DTO and return
        return updatedUser;
    }

    /**
     * Updates profile information for a user by an admin.
     *
     * @param userId the ID of the user to update
     * @param updateProfileDTO the profile data to update
     * @return the updated User
     */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public User adminUpdateUserProfile(String userId, UpdateProfileDTO updateProfileDTO) {
        logger.debug("Admin is updating profile for user ID: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));

        if (updateProfileDTO.getDisplayName() != null) {
            user.setDisplayName(updateProfileDTO.getDisplayName());
        }
        if (updateProfileDTO.getPronouns() != null) {
            user.setPronouns(updateProfileDTO.getPronouns());
        }
        if (updateProfileDTO.getAbout() != null) {
            user.setAbout(updateProfileDTO.getAbout());
        }
        if (updateProfileDTO.getAvatar() != null) {
            user.setAvatar(updateProfileDTO.getAvatar());
        }

        User updatedUser = userRepository.save(user);
        logger.info("Admin updated profile for user: {}", updatedUser.getUsername());

        // Invalidate user profile cache
        cacheConfig.invalidateUserProfileCache(userId);
        logger.debug("Invalidated user profile cache for user: {}", userId);

        // Create audit entry for this sensitive operation
        createAdminProfileUpdateAuditEntry(getCurrentAdminId(), userId, updateProfileDTO);

        return updatedUser;
    }

    /**
     * Creates an audit entry for admin profile update operations.
     */
    private void createAdminProfileUpdateAuditEntry(String adminId, String targetUserId, UpdateProfileDTO changes) {
        try {
            String description = String.format("Admin %s updated profile for user %s.", adminId, targetUserId);

            Map<String, Object> details = new HashMap<>();
            details.put("adminId", adminId);
            details.put("targetUserId", targetUserId);
            details.put("changes", changes);
            details.put("timestamp", LocalDateTime.now().toString());

            String detailsJson;
            try {
                detailsJson = objectMapper.writeValueAsString(details);
            } catch (JsonProcessingException e) {
                logger.error("Failed to serialize admin profile update details to JSON: {}", e.getMessage());
                detailsJson = "{\"error\": \"Failed to serialize details\"}";
            }

            CreateAuditDTO auditDTO = CreateAuditDTO.builder()
                .userId(adminId)
                .action("ADMIN_UPDATE_PROFILE")
                .entityType("User")
                .entityId(targetUserId)
                .description(description)
                .severity(AuditSeverity.HIGH)
                .category(AuditCategory.USER_MANAGEMENT)
                .details(detailsJson)
                .source("UserService")
                .build();

            auditService.createSystemAuditEntry(auditDTO);
        } catch (Exception e) {
            logger.error("Failed to create audit entry for admin profile update - adminId: {}, targetUserId: {}, error: {}",
                adminId, targetUserId, e.getMessage(), e);
        }
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
        
        // Resolve equipped nameplate color and gradient
        String nameplateColor = null;
        String gradientEndColor = null;
        UUID equippedUserColorId = user.getEquippedUserColorId();
        if (equippedUserColorId != null) {
            try {
                Optional<Shop> userColorItemOpt = shopRepository.findById(equippedUserColorId);
                if (userColorItemOpt.isPresent()) {
                    Shop userColorItem = userColorItemOpt.get();
                    nameplateColor = userColorItem.getImageUrl();
                    gradientEndColor = userColorItem.getGradientEndColor();
                    logger.debug("Resolved nameplate for user {}: color={}, gradientEnd={}", user.getId(), nameplateColor, gradientEndColor);
                }
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
                .banned(user.getBanned() != null && user.getBanned())
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
                .gradientEndColor(gradientEndColor)
                .dailyStreak(user.getDailyStreak()) // Add daily claim fields
                .lastDailyClaim(user.getLastDailyClaim())
                .selectedAgeRoleId(user.getSelectedAgeRoleId())
                .selectedGenderRoleId(user.getSelectedGenderRoleId())
                .selectedRankRoleId(user.getSelectedRankRoleId())
                .selectedRegionRoleId(user.getSelectedRegionRoleId())
                .fishingLimitCooldownUntil(user.getFishingLimitCooldownUntil())
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
    @Transactional
    public User updateUserCredits(String userId, Integer credits, String adminId) {
        logger.debug("Updating credits for user {} to {} by admin {}", userId, credits, adminId);
        
        // Use a pessimistic lock to ensure atomicity for both the update and the audit log
        User user = userRepository.findByIdWithLock(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));
        
        // Capture previous credits for audit trail
        int previousCredits = user.getCredits() != null ? user.getCredits() : 0;
        
        // Ensure credits are not negative
        if (credits < 0) {
            credits = 0;
        }
        
        user.setCredits(credits);
        
        // Create audit trail for credit update
        createCreditUpdateAuditEntry(adminId, userId, previousCredits, credits);
        
        // Invalidate caches since credits affect ranking and user profile
        cacheConfig.invalidateLeaderboardCache();
        cacheConfig.invalidateUserProfileCache(userId);
        
        logger.info("Updated credits for user {} from {} to {} by admin {}", userId, previousCredits, credits, adminId);
        
        // The transaction will handle saving the updated user entity
        return user;
    }

    /**
     * Atomically increment user credits and experience in a single transaction.
     * This method is designed to prevent race conditions.
     *
     * @param userId The ID of the user to update.
     * @param credits The amount of credits to add (can be negative).
     * @param xp The amount of experience (XP) to add.
     */
    @Transactional
    public void incrementCreditsAndXp(String userId, int credits, int xp) {
        if (credits == 0 && xp == 0) {
            return; // No operation needed
        }
        userRepository.incrementCreditsAndXp(userId, credits, xp);
        cacheConfig.invalidateUserProfileCache(userId);
        logger.debug("Atomically updated credits by {} and xp by {} for user {}", credits, xp, userId);
    }

    /**
     * Atomically increment user credits - prevents race conditions
     * @param userId the user ID
     * @param amount the amount to add (can be negative for deduction)
     * @return true if operation succeeded, false if user not found or insufficient credits
     */
    @Transactional
    public boolean updateCreditsAtomic(String userId, int amount) {
        logger.debug("Atomically updating credits for user {} by amount {}", userId, amount);
        
        int rowsAffected;
        if (amount >= 0) {
            rowsAffected = userRepository.incrementCredits(userId, amount);
        } else {
            // For negative amounts, use deduction with floor to prevent negative balances
            rowsAffected = userRepository.deductCreditsWithFloor(userId, Math.abs(amount));
        }
        
        if (rowsAffected > 0) {
            cacheConfig.invalidateUserProfileCache(userId);
            logger.debug("Successfully updated credits for user {} by amount {}", userId, amount);
            return true;
        } else {
            logger.warn("Failed to update credits for user {} by amount {} - user not found or insufficient credits", userId, amount);
            return false;
        }
    }

    /**
     * Atomically deduct credits with validation - ensures sufficient balance
     * @param userId the user ID
     * @param amount the amount to deduct (positive value)
     * @return true if deduction succeeded, false if insufficient credits or user not found
     */
    @Transactional
    public boolean deductCreditsIfSufficient(String userId, int amount) {
        logger.debug("Attempting to deduct {} credits from user {}", amount, userId);
        
        if (amount <= 0) {
            logger.warn("Invalid deduction amount: {} for user {}", amount, userId);
            return false;
        }

        User user = userRepository.findByIdWithLock(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        return deductCreditsIfSufficient(user, amount);
    }

    /**
     * Overloaded method to deduct credits from a pre-fetched/locked User entity.
     * This is the core logic that should be used within larger transactions.
     * @param user the locked User entity
     * @param amount the amount to deduct
     * @return true if deduction succeeded, false otherwise
     */
    @Transactional
    public boolean deductCreditsIfSufficient(User user, int amount) {
        if (user == null) {
            logger.warn("User object is null, cannot deduct credits.");
            return false;
        }
        if (amount <= 0) {
            logger.warn("Invalid deduction amount: {} for user {}", amount, user.getId());
            return false;
        }

        if (user.getCredits() < amount) {
            logger.warn("Failed to deduct {} credits from user {} - insufficient balance.", amount, user.getId());
            return false;
        }

        user.setCredits(user.getCredits() - amount);
        userRepository.save(user);

        cacheConfig.invalidateUserProfileCache(user.getId());
        logger.debug("Successfully deducted {} credits from user {}", amount, user.getId());
        return true;
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
     * @param sortBy Sorting criterion: "credits", "level", "messages", "voice", or "fish"
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
            case "fish":
                sort = Sort.by(new Sort.Order(Direction.DESC, "fishCaughtCount", Sort.NullHandling.NULLS_LAST));
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
     * This method is transactional and uses a pessimistic lock to prevent race conditions.
     *
     * @param userId the ID of the user to update
     * @param sessionMinutes the minutes to add to the voice time
     */
    @Transactional
    public void incrementVoiceTimeCounters(String userId, int sessionMinutes) {
        LocalDateTime now = LocalDateTime.now();

        // Fetch user with a lock to ensure atomic update
        User user = userRepository.findByIdWithLock(userId).orElse(null);

        if (user == null) {
            logger.warn("User {} not found in database, cannot increment voice time", userId);
            return;
        }
        
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
        
        // The transaction will handle saving the updated user entity.
        // We still need to invalidate cache.
        cacheConfig.invalidateUserProfileCache(user.getId());
        
        logger.debug("Updated voice time for user {} - added {} minutes (total: {})", 
            user.getId(), sessionMinutes, user.getVoiceTimeMinutesTotal());
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
     * Bans a user.
     * @param userId the ID of the user to ban
     */
    @Transactional
    public void banUser(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));
        if (user.getBanned() == null || !user.getBanned()) {
            user.setBanned(true);
            userRepository.save(user);
            cacheConfig.invalidateUserProfileCache(userId);
            logger.info("User {} has been banned.", userId);
        } else {
            logger.info("User {} was already banned.", userId);
        }
    }

    /**
     * Unbans a user.
     * @param userId the ID of the user to unban
     */
    @Transactional
    public void unbanUser(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));
        if (user.getBanned() != null && user.getBanned()) {
            user.setBanned(false);
            userRepository.save(user);
            cacheConfig.invalidateUserProfileCache(userId);
            logger.info("User {} has been unbanned.", userId);
        } else {
            logger.info("User {} was not banned.", userId);
        }
    }

    public PublicUserProfileDTO mapToPublicProfileDTO(User user) {
        String avatarUrl = user.getAvatar();

        if ("USE_DISCORD_AVATAR".equals(avatarUrl)) {
            if (user.getDiscordAvatarUrl() != null && !user.getDiscordAvatarUrl().isEmpty()) {
                avatarUrl = user.getDiscordAvatarUrl();
            } else {
                avatarUrl = "/images/default-avatar.png";
            }
        } else if (avatarUrl == null || avatarUrl.isEmpty()) {
            avatarUrl = "/images/default-avatar.png";
        }

        UUID badgeId = user.getEquippedBadgeId();
        String badgeUrl = null;
        String badgeName = null;
        if (badgeId != null) {
            shopRepository.findById(badgeId).ifPresent(badge -> {
                // This is not ideal, need to assign to outer scope variables.
            });
        }
        
        Shop equippedBadge = null;
        if (badgeId != null) {
            equippedBadge = shopRepository.findById(badgeId).orElse(null);
        }
        if (equippedBadge != null) {
            badgeUrl = equippedBadge.getThumbnailUrl();
            badgeName = equippedBadge.getName();
        }

        String nameplateColor = null;
        String gradientEndColor = null;
        UUID equippedUserColorId = user.getEquippedUserColorId();
        if (equippedUserColorId != null) {
            try {
                Optional<Shop> userColorItemOpt = shopRepository.findById(equippedUserColorId);
                if (userColorItemOpt.isPresent()) {
                    Shop userColorItem = userColorItemOpt.get();
                    nameplateColor = userColorItem.getImageUrl();
                    gradientEndColor = userColorItem.getGradientEndColor();
                    logger.debug("Resolved nameplate for user {}: color={}, gradientEnd={}", user.getId(), nameplateColor, gradientEndColor);
                }
            } catch (Exception e) {
                logger.warn("Failed to resolve nameplate color for user {}: {}", user.getId(), e.getMessage());
            }
        }

        return PublicUserProfileDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .avatar(avatarUrl)
                .displayName(user.getDisplayName())
                .pronouns(user.getPronouns())
                .about(user.getAbout())
                .bannerColor(user.getBannerColor())
                .bannerUrl(user.getBannerUrl())
                .roles(user.getRoles())
                .badgeUrl(badgeUrl)
                .badgeName(badgeName)
                .nameplateColor(nameplateColor)
                .gradientEndColor(gradientEndColor)
                .build();
    }

    /**
     * Creates a new user in the database from a registration request.
     *
     * @param registerRequest DTO containing registration details (username, email).
     * @param hashedPassword The securely hashed password for the new user.
     * @return The newly created User entity.
     */
    @Transactional
    public User createUser(RegisterRequestDTO registerRequest, String hashedPassword) {
        logger.debug("Creating a new user with username: {}", registerRequest.getUsername());

        User newUser = new User();
        // Generate a new UUID for the user ID
        newUser.setId(UUID.randomUUID().toString());
        newUser.setUsername(registerRequest.getUsername());
        newUser.setEmail(registerRequest.getEmail());
        newUser.setPassword(hashedPassword);

        // Set default values for new users
        newUser.setRoles(Collections.singleton(Role.USER));
        newUser.setCredits(0);
        newUser.setLevel(1);
        newUser.setExperience(0);
        newUser.setBanned(false);
        newUser.setActive(true);

        // Save the new user to the database
        User savedUser = userRepository.save(newUser);
        logger.info("Successfully created new user with ID: {} and username: {}", savedUser.getId(), savedUser.getUsername());

        return savedUser;
    }

    /**
     * Calculates the highest role-based multiplier for a given Discord member.
     *
     * @param member The JDA Member object representing the user.
     * @param settings The current DiscordBotSettings.
     * @return The highest applicable multiplier, or 1.0 if none applies or if the feature is disabled.
     */
    public double getUserHighestMultiplier(Member member, DiscordBotSettings settings) {
        if (member == null || settings == null || !Boolean.TRUE.equals(settings.getRoleMultipliersEnabled()) || settings.getRoleMultipliers() == null || settings.getRoleMultipliers().isBlank()) {
            return 1.0;
        }

        String roleMultipliersStr = settings.getRoleMultipliers();
        java.util.Map<String, Double> multipliersMap;

        try {
            multipliersMap = Arrays.stream(roleMultipliersStr.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty() && s.contains(":"))
                    .map(entry -> entry.split(":"))
                    .filter(parts -> parts.length == 2)
                    .collect(Collectors.toMap(
                            parts -> parts[0].trim(),
                            parts -> {
                                try {
                                    return Double.parseDouble(parts[1].trim());
                                } catch (NumberFormatException e) {
                                    logger.warn("Invalid multiplier format for role ID {}: {}", parts[0].trim(), parts[1].trim());
                                    return null; // Will be filtered out
                                }
                            }
                    ));
            
            multipliersMap.values().removeIf(java.util.Objects::isNull);

        } catch (Exception e) {
            logger.error("Error parsing role multipliers string: {}", roleMultipliersStr, e);
            return 1.0; // Return default if format is invalid
        }

        if (multipliersMap.isEmpty()) {
            return 1.0;
        }

        return member.getRoles().stream()
                .map(net.dv8tion.jda.api.entities.Role::getId)
                .filter(multipliersMap::containsKey)
                .mapToDouble(multipliersMap::get)
                .max()
                .orElse(1.0);
    }

    /**
     * Deletes a user permanently from the database.
     *
     * @param userId the ID of the user to delete
     * @param adminId the ID of the admin performing the operation
     */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void deleteUser(String userId, String adminId) {
        // Security check: an admin cannot delete their own account
        if (userId.equals(adminId)) {
            throw new UnauthorizedOperationException("Admins cannot delete their own accounts.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));
        
        // --- PRE-DELETION CLEANUP ---
        // 1. Delete associated daily message stats
        dailyMessageStatRepository.deleteByUserId(userId);
        
        // 2. Delete associated daily voice activity stats
        dailyVoiceActivityStatRepository.deleteByUserId(userId);

        // 3. Delete inventory items (handled by cascade on User entity, but explicit is safer)
        // This is no longer needed as orphanRemoval=true on User.itemInstances will handle it.

        // The user exists and related data is cleaned up, proceed with deletion
        userRepository.delete(user);

        // Create a high-severity audit log for this action
        createDeletionAuditEntry(adminId, userId, user.getUsername());

        // Invalidate any caches related to this user
        cacheConfig.invalidateUserProfileCache(userId);
        cacheConfig.invalidateLeaderboardCache();

        logger.warn("ADMIN USER DELETION - Admin: {} permanently deleted user: {} (Username: {})",
                adminId, userId, user.getUsername());
    }

    private void createDeletionAuditEntry(String adminId, String deletedUserId, String deletedUsername) {
        try {
            String description = String.format("Admin %s permanently deleted user %s (username: %s)",
                    adminId, deletedUserId, deletedUsername);

            Map<String, Object> details = new HashMap<>();
            details.put("adminId", adminId);
            details.put("deletedUserId", deletedUserId);
            details.put("deletedUsername", deletedUsername);
            details.put("timestamp", LocalDateTime.now().toString());

            String detailsJson = objectMapper.writeValueAsString(details);

            CreateAuditDTO auditDTO = CreateAuditDTO.builder()
                    .userId(adminId)
                    .action("DELETE_USER")
                    .entityType("User")
                    .entityId(deletedUserId)
                    .description(description)
                    .severity(AuditSeverity.CRITICAL)
                    .category(AuditCategory.USER_MANAGEMENT)
                    .details(detailsJson)
                    .source("UserService")
                    .build();

            auditService.createSystemAuditEntry(auditDTO);
        } catch (Exception e) {
            logger.error("Failed to create audit entry for user deletion - adminId: {}, deletedUserId: {}, error: {}",
                    adminId, deletedUserId, e.getMessage(), e);
        }
    }

    /**
     * Retrieves a list of users who own a specific item.
     * Only accessible to ADMIN users.
     *
     * @param itemId The ID of the shop item.
     * @return A list of public user profiles of the owners.
     */
    @Transactional(readOnly = true)
    public List<PublicUserProfileDTO> getOwnersOfItem(UUID itemId) {
        logger.debug("Fetching owners for item ID: {}", itemId);

        Shop shopItem = shopRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Shop item not found with ID: " + itemId));

        List<ItemInstance> instances = itemInstanceRepository.findByBaseItem(shopItem);

        List<User> owners = instances.stream()
                .map(ItemInstance::getOwner)
                .distinct()
                .collect(Collectors.toList());

        logger.info("Found {} unique owners for item '{}'", owners.size(), shopItem.getName());

        return owners.stream()
                .map(this::mapToPublicProfileDTO)
                .collect(Collectors.toList());
    }
}
