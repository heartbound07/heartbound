package com.app.heartbound.services.discord;

import com.app.heartbound.entities.DiscordBotSettings;
import com.app.heartbound.entities.User;
import com.app.heartbound.services.UserService;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Nonnull;
import java.awt.Color;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class RolesCommandListener extends ListenerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(RolesCommandListener.class);
    private final UserService userService;
    private final DiscordBotSettingsService discordBotSettingsService;
    private final TermsOfServiceService termsOfServiceService;

    public RolesCommandListener(UserService userService, DiscordBotSettingsService discordBotSettingsService, TermsOfServiceService termsOfServiceService) {
        this.userService = userService;
        this.discordBotSettingsService = discordBotSettingsService;
        this.termsOfServiceService = termsOfServiceService;
    }

    @Override
    public void onSlashCommandInteraction(@Nonnull SlashCommandInteractionEvent event) {
        if (!event.getName().equals("roles")) {
            return;
        }

        // This command is admin-only, but we double-check permissions
        Member member = event.getMember();
        if (member == null || !member.hasPermission(Permission.ADMINISTRATOR)) {
            event.reply("You do not have permission to use this command.").setEphemeral(true).queue();
            return;
        }
        
        event.deferReply(true).queue();

        DiscordBotSettings settings = discordBotSettingsService.getDiscordBotSettings();

        // Embed 1: Age Roles
        EmbedBuilder ageEmbed = new EmbedBuilder()
            .setTitle("✨ 𝘼 𝙂 𝙀 ✨")
            .setColor(Color.decode("#FFB6C1"));
        if (settings.getAgeRolesThumbnailUrl() != null && !settings.getAgeRolesThumbnailUrl().isBlank()) {
            ageEmbed.setThumbnail(settings.getAgeRolesThumbnailUrl());
        }
        List<Button> ageButtons = List.of(
            Button.secondary("roles:age:" + settings.getAge15RoleId(), "15"),
            Button.secondary("roles:age:" + settings.getAge16To17RoleId(), "16-17"),
            Button.secondary("roles:age:" + settings.getAge18PlusRoleId(), "18+")
        );
        event.getChannel().sendMessageEmbeds(ageEmbed.build()).addActionRow(ageButtons).queue();

        // Embed 2: Gender Roles
        EmbedBuilder genderEmbed = new EmbedBuilder()
            .setTitle("✨ 𝙂 𝙀 𝙉 𝘿 𝙀 𝙍 ✨")
            .setColor(Color.decode("#ADD8E6"));
        if (settings.getGenderRolesThumbnailUrl() != null && !settings.getGenderRolesThumbnailUrl().isBlank()) {
            genderEmbed.setThumbnail(settings.getGenderRolesThumbnailUrl());
        }
        List<Button> genderButtons = List.of(
            Button.secondary("roles:gender:" + settings.getGenderSheHerRoleId(), "she/her"),
            Button.secondary("roles:gender:" + settings.getGenderHeHimRoleId(), "he/him"),
            Button.secondary("roles:gender:" + settings.getGenderAskRoleId(), "ask")
        );
        event.getChannel().sendMessageEmbeds(genderEmbed.build()).addActionRow(genderButtons).queue();

        // Embed 3: Rank Roles
        EmbedBuilder rankEmbed = new EmbedBuilder()
            .setTitle("✨ 𝙍 𝘼 𝙉 𝑲 ✨")
            .setColor(Color.decode("#D4AF37"))
            .setDescription("**REQUIRES VERIFICATION**\n\n**ASCENDANT**\n**IMMORTAL**\n**RADIANT**");
        if (settings.getRankRolesThumbnailUrl() != null && !settings.getRankRolesThumbnailUrl().isBlank()) {
            rankEmbed.setThumbnail(settings.getRankRolesThumbnailUrl());
        }
        List<Button> rankButtons = List.of(
            Button.secondary("roles:rank:" + settings.getRankIronRoleId(), "Iron"),
            Button.secondary("roles:rank:" + settings.getRankBronzeRoleId(), "Bronze"),
            Button.secondary("roles:rank:" + settings.getRankSilverRoleId(), "Silver"),
            Button.secondary("roles:rank:" + settings.getRankGoldRoleId(), "Gold"),
            Button.secondary("roles:rank:" + settings.getRankPlatinumRoleId(), "Platinum")
        );
         List<Button> rankButtons2 = List.of(
            Button.secondary("roles:rank:" + settings.getRankDiamondRoleId(), "Diamond")
        );

        event.getChannel().sendMessageEmbeds(rankEmbed.build()).addActionRow(rankButtons).addActionRow(rankButtons2).queue();

        // Embed 4: Region Roles
        EmbedBuilder regionEmbed = new EmbedBuilder()
            .setTitle("✨ 𝑹 𝑬 𝑮 𝑰 𝑶 𝑵 ✨")
            .setColor(Color.decode("#90EE90"));
        if (settings.getRegionRolesThumbnailUrl() != null && !settings.getRegionRolesThumbnailUrl().isBlank()) {
            regionEmbed.setThumbnail(settings.getRegionRolesThumbnailUrl());
        }
        List<Button> regionButtons = List.of(
            Button.secondary("roles:region:" + settings.getRegionNaRoleId(), "NA"),
            Button.secondary("roles:region:" + settings.getRegionEuRoleId(), "EU"),
            Button.secondary("roles:region:" + settings.getRegionSaRoleId(), "SA"),
            Button.secondary("roles:region:" + settings.getRegionApRoleId(), "AP"),
            Button.secondary("roles:region:" + settings.getRegionOceRoleId(), "OCE")
        );
        event.getChannel().sendMessageEmbeds(regionEmbed.build()).addActionRow(regionButtons).queue();

        event.getHook().editOriginal("Role selection messages have been sent to this channel.").queue();
    }

    @Override
    @Transactional
    public void onButtonInteraction(@Nonnull ButtonInteractionEvent event) {
        String componentId = event.getComponentId();
        
        // Handle ToS-related button interactions for roles
        if (componentId.startsWith("tos-role-agree:") || componentId.startsWith("tos-role-disagree:")) {
            handleToSButtonInteraction(event);
            return;
        }
        
        if (!componentId.startsWith("roles:")) {
            return;
        }
        
        event.deferReply(true).queue();

        String[] parts = componentId.split(":");
        if (parts.length != 3) {
            logger.warn("Invalid role button component ID format: {}", componentId);
            event.getHook().editOriginal("An error occurred: Invalid button ID.").queue();
            return;
        }

        String category = parts[1];
        String roleIdToAssign = parts[2];
        
        Member member = event.getMember();
        Guild guild = event.getGuild();
        if (member == null || guild == null) {
            event.getHook().editOriginal("This action can only be performed in a server.").queue();
            return;
        }

        DiscordBotSettings settings = discordBotSettingsService.getDiscordBotSettings();

        Map<String, String> categoryRoles = getCategoryRoles(settings, category);

        // Check if the role ID from the button interaction is in the pre-approved list.
        if (!categoryRoles.containsKey(roleIdToAssign)) {
            // If the roleId is not in the approved list, this is a security event.
            logger.warn("SECURITY ALERT: User attempted to assign an unauthorized role." +
                            " UserId: {}, GuildId: {}, Category: {}, Attempted RoleId: {}",
                    member.getId(), guild.getId(), category, roleIdToAssign);
            
            // Inform the user and abort the operation.
            event.getHook().editOriginal("Invalid role selection. This attempt has been logged.").queue();
            return;
        }

        // Security Check: Prevent users with a verified rank from getting a self-assigned one.
        if (category.equals("rank")) {
            List<String> verifiedRankRoleIds = Stream.of(
                settings.getRankAscendantRoleId(),
                settings.getRankImmortalRoleId(),
                settings.getRankRadiantRoleId()
            ).filter(id -> id != null && !id.isBlank()).collect(Collectors.toList());

            boolean hasVerifiedRank = member.getRoles().stream()
                .anyMatch(role -> verifiedRankRoleIds.contains(role.getId()));

            if (hasVerifiedRank) {
                event.getHook().editOriginal("You cannot select a self-assignable rank because you already have a moderator-verified rank.").queue();
                return;
            }
        }

        User user = userService.getUserById(member.getId());

        // Check if user is registered on the website - if not, show ToS
        if (user == null) {
            // Use ToS service instead of direct error message
            try {
                // Cancel the deferred reply since ToS service will handle the response
                event.getHook().editOriginal("Please wait...").queue();
                
                // Show ToS agreement with original component ID encoded
                termsOfServiceService.requireAgreement(event, componentId, existingUser -> {
                    // This callback will be called after ToS agreement, proceed with role assignment
                    processRoleAssignment(event, existingUser, category, roleIdToAssign, settings);
                });
            } catch (Exception e) {
                logger.error("Error initiating ToS flow for user {}: {}", member.getId(), e.getMessage(), e);
                event.getHook().editOriginal("❌ An error occurred while processing your request. Please try again later.").queue();
            }
            return;
        }

        // User exists, proceed with role assignment
        processRoleAssignment(event, user, category, roleIdToAssign, settings);
    }

    private boolean hasRoleFromCategory(User user, String category, Member member) {
        
        // First, check the database record if user exists
        if (user != null) {
            String selectedRoleId = switch (category) {
                default -> null;
            };

            if (selectedRoleId != null && !selectedRoleId.isBlank()) {
                // To be certain, verify they still have the role on Discord
                return member.getRoles().stream().anyMatch(r -> r.getId().equals(selectedRoleId));
            }
        }
        
        // If no database record, check their Discord roles against all roles in the category
        DiscordBotSettings settings = discordBotSettingsService.getDiscordBotSettings();
        Map<String, String> categoryRoles = getCategoryRoles(settings, category);
        return member.getRoles().stream().anyMatch(r -> categoryRoles.containsKey(r.getId()));
    }

    private Map<String, String> getCategoryRoles(DiscordBotSettings settings, String category) {
        Stream<String> roleIdsStream;
        switch (category) {
            case "age":
                roleIdsStream = Stream.of(settings.getAge15RoleId(), settings.getAge16To17RoleId(), settings.getAge18PlusRoleId());
                break;
            case "gender":
                roleIdsStream = Stream.of(settings.getGenderSheHerRoleId(), settings.getGenderHeHimRoleId(), settings.getGenderAskRoleId());
                break;
            case "rank":
                roleIdsStream = Stream.of(settings.getRankIronRoleId(), settings.getRankBronzeRoleId(), settings.getRankSilverRoleId(), settings.getRankGoldRoleId(), settings.getRankPlatinumRoleId(), settings.getRankDiamondRoleId());
                break;
            case "region":
                roleIdsStream = Stream.of(settings.getRegionNaRoleId(), settings.getRegionEuRoleId(), settings.getRegionSaRoleId(), settings.getRegionApRoleId(), settings.getRegionOceRoleId());
                break;
            default:
                return Map.of();
        }
        return roleIdsStream
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toMap(id -> id, id -> ""));
    }

    private void updateUserRoleSelection(User user, String discordUserId, String category, String roleId) {
        // Update the User entity (user is guaranteed to be non-null due to earlier check)
        switch (category) {
            default:
                // No database tracking for deprecated categories
                break;
        }
        userService.updateUser(user);
        logger.debug("Updated role selection for registered user {} in category '{}': {}", discordUserId, category, roleId);
    }
    
    /**
     * Handles Terms of Service button interactions for role assignments.
     */
    private void handleToSButtonInteraction(ButtonInteractionEvent event) {
        String componentId = event.getComponentId();
        String clickingUserId = event.getUser().getId();

        logger.debug("Processing ToS role button interaction: {} by user: {}", componentId, clickingUserId);

        try {
            // Extract the target user ID and original component ID from the component ID
            String targetUserId;
            String originalComponentId = null;
            boolean isAgree;
            
            if (componentId.startsWith("tos-role-agree:")) {
                String remainder = componentId.substring("tos-role-agree:".length());
                String[] parts = remainder.split(":", 2);
                if (parts.length != 2) {
                    logger.warn("Invalid ToS role agree button format: {}", componentId);
                    event.reply("❌ Invalid button format.").setEphemeral(true).queue();
                    return;
                }
                targetUserId = parts[0];
                originalComponentId = parts[1];
                isAgree = true;
            } else if (componentId.startsWith("tos-role-disagree:")) {
                targetUserId = componentId.substring("tos-role-disagree:".length());
                isAgree = false;
            } else {
                logger.warn("Unexpected ToS role button component ID: {}", componentId);
                event.reply("❌ Invalid button.").setEphemeral(true).queue();
                return;
            }

            // Critical security check: Verify the clicking user matches the target user
            if (!clickingUserId.equals(targetUserId)) {
                logger.warn("Security violation: User {} attempted to interact with ToS role button for user {}", 
                    clickingUserId, targetUserId);
                
                event.reply("❌ You can only interact with your own Terms of Service agreement.")
                        .setEphemeral(true)
                        .queue();
                return;
            }

            if (isAgree) {
                handleToSRoleAgreeButton(event, targetUserId, originalComponentId);
            } else {
                handleToSRoleDisagreeButton(event, targetUserId);
            }

        } catch (Exception e) {
            logger.error("Error processing ToS role button interaction for component ID {}: {}", componentId, e.getMessage(), e);
            
            // Send error response
            try {
                event.reply("❌ An error occurred while processing your response. Please try again later.")
                        .setEphemeral(true)
                        .queue();
            } catch (Exception fallbackError) {
                logger.error("Failed to send error response for ToS role button interaction: {}", fallbackError.getMessage());
            }
        }
    }
    
    /**
     * Handles the "Agree" button for role ToS interactions.
     * Creates user and continues with role assignment.
     */
    private void handleToSRoleAgreeButton(ButtonInteractionEvent event, String userId, String originalComponentId) {
        logger.debug("User {} agreed to Terms of Service for role assignment", userId);

        try {
            // Check if user already exists (race condition protection)
            User existingUser = userService.getUserById(userId);
            if (existingUser != null) {
                logger.debug("User {} already exists during ToS role agreement, proceeding with role assignment", userId);
                updateToSEmbedForAgreement(event, true);
                // Continue with role assignment
                continueRoleAssignmentAfterToS(event, existingUser, originalComponentId);
                return;
            }

            // Create new user from Discord data
            User newUser = userService.createUserFromDiscord(event.getUser());
            logger.info("Successfully created new user {} from ToS role agreement", userId);

            // Update the embed to show agreement confirmation
            updateToSEmbedForAgreement(event, true);
            
            // Continue with role assignment
            continueRoleAssignmentAfterToS(event, newUser, originalComponentId);

        } catch (Exception e) {
            logger.error("Error creating user {} from ToS role agreement: {}", userId, e.getMessage(), e);
            
            // Send error response
            event.reply("❌ An error occurred while creating your account. Please try again later.")
                    .setEphemeral(true)
                    .queue();
        }
    }
    
    /**
     * Handles the "Disagree" button for role ToS interactions.
     */
    private void handleToSRoleDisagreeButton(ButtonInteractionEvent event, String userId) {
        logger.debug("User {} disagreed to Terms of Service for role assignment", userId);

        try {
            // Send ephemeral disagreement message and update embed
            event.reply("You have disagreed to the Terms of Service.")
                    .setEphemeral(true)
                    .queue(
                            success -> {
                                logger.debug("Sent role ToS disagreement message to user {}", userId);
                                // Update the original embed to show disagreement
                                updateToSEmbedForDisagreement(event);
                            },
                            error -> logger.error("Failed to send role ToS disagreement message to user {}: {}", userId, error.getMessage())
                    );

        } catch (Exception e) {
            logger.error("Error handling role ToS disagreement for user {}: {}", userId, e.getMessage(), e);
        }
    }
    
    /**
     * Updates the original ToS embed to show agreement confirmation and removes buttons.
     */
    private void updateToSEmbedForAgreement(ButtonInteractionEvent event, boolean success) {
        try {
            EmbedBuilder embedBuilder = new EmbedBuilder()
                    .setTitle("Terms of Service")
                    .setColor(success ? Color.GREEN : Color.RED);

            if (success) {
                embedBuilder.setDescription("✅ You have agreed to the Terms of Service. Processing your role selection...");
            } else {
                embedBuilder.setDescription("❌ An error occurred while processing your agreement.");
            }

            // Update the message, removing the buttons
            event.editMessageEmbeds(embedBuilder.build())
                    .setComponents() // Remove all components (buttons)
                    .queue(
                            updateSuccess -> logger.debug("Updated ToS role embed for agreement confirmation"),
                            updateError -> logger.error("Failed to update ToS role embed: {}", updateError.getMessage())
                    );

        } catch (Exception e) {
            logger.error("Error updating ToS role embed for agreement: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Updates the original ToS embed to show disagreement and removes buttons.
     */
    private void updateToSEmbedForDisagreement(ButtonInteractionEvent event) {
        try {
            EmbedBuilder embedBuilder = new EmbedBuilder()
                    .setTitle("Terms of Service")
                    .setDescription("❌ You have disagreed to the Terms of Service. Role selection cancelled.")
                    .setColor(Color.RED);

            // Update the message, removing the buttons
            event.getMessage().editMessageEmbeds(embedBuilder.build())
                    .setComponents() // Remove all components (buttons)
                    .queue(
                            updateSuccess -> logger.debug("Updated ToS role embed for disagreement"),
                            updateError -> logger.error("Failed to update ToS role embed after disagreement: {}", updateError.getMessage())
                    );

        } catch (Exception e) {
            logger.error("Error updating ToS role embed for disagreement: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Continues with role assignment after ToS agreement.
     */
    private void continueRoleAssignmentAfterToS(ButtonInteractionEvent event, User user, String originalComponentId) {
        logger.debug("Continuing role assignment after ToS agreement for user: {}, original component: {}", user.getId(), originalComponentId);
        
        // Parse the original component ID to extract category and role ID
        String[] parts = originalComponentId.split(":");
        if (parts.length != 3 || !parts[0].equals("roles")) {
            logger.error("Invalid original component ID format after ToS: {}", originalComponentId);
            return;
        }
        
        String category = parts[1];
        String roleIdToAssign = parts[2];
        
        DiscordBotSettings settings = discordBotSettingsService.getDiscordBotSettings();
        
        // Create a mock deferred interaction for processRoleAssignment
        // Since we can't defer the original button interaction again, we'll send a follow-up
        processRoleAssignmentDirectly(event, user, category, roleIdToAssign, settings);
    }
    
    /**
     * Processes role assignment with all necessary checks and validations.
     */
    private void processRoleAssignment(ButtonInteractionEvent event, User user, String category, String roleIdToAssign, DiscordBotSettings settings) {
        Member member = event.getMember();
        Guild guild = event.getGuild();
        
        if (member == null || guild == null) {
            event.getHook().editOriginal("This action can only be performed in a server.").queue();
            return;
        }
        
        Map<String, String> categoryRoles = getCategoryRoles(settings, category);

        // Check if user already has a role from this category.
        // The 'gender' category is immutable; once selected, it cannot be changed.
        // Other categories (age, rank, region) are mutable.
        if ("gender".equals(category) && hasRoleFromCategory(user, category, member)) {
            event.getHook().editOriginal("You have already selected a role from this category. Your choice cannot be changed.").queue();
            return;
        }
        
        Role newRole = guild.getRoleById(roleIdToAssign);
        if (newRole == null) {
            logger.error("Role with ID {} not found in guild {}", roleIdToAssign, guild.getId());
            event.getHook().editOriginal("Error: The role to be assigned no longer exists.").queue();
            return;
        }

        // Security checks for bot permissions and hierarchy
        if (!guild.getSelfMember().hasPermission(Permission.MANAGE_ROLES)) {
            logger.error("Bot lacks MANAGE_ROLES permission in guild {}", guild.getId());
            event.getHook().editOriginal("I don't have permission to manage roles. Please contact an administrator.").queue();
            return;
        }

        if (!guild.getSelfMember().canInteract(newRole)) {
            logger.error("Bot cannot interact with role {} (ID: {}) due to hierarchy.", newRole.getName(), newRole.getId());
            event.getHook().editOriginal("I cannot assign this role due to hierarchy. Please contact an administrator.").queue();
            return;
        }

        List<Role> rolesToRemove = member.getRoles().stream()
                .filter(role -> categoryRoles.containsKey(role.getId()))
                .collect(Collectors.toList());

        guild.modifyMemberRoles(member, List.of(newRole), rolesToRemove).queue(
            success -> {
                updateUserRoleSelection(user, member.getId(), category, roleIdToAssign);
                event.getHook().editOriginal("You have been successfully assigned the '" + newRole.getName() + "' role!").queue();
                logger.info("Assigned role '{}' to user {} in category '{}'", newRole.getName(), member.getId(), category);
            },
            error -> {
                logger.error("Failed to modify roles for user {}: {}", member.getId(), error.getMessage());
                event.getHook().editOriginal("An error occurred while assigning the role. Please try again.").queue();
            }
        );
    }
    
    /**
     * Processes role assignment directly for post-ToS scenarios.
     */
    private void processRoleAssignmentDirectly(ButtonInteractionEvent event, User user, String category, String roleIdToAssign, DiscordBotSettings settings) {
        Member member = event.getMember();
        Guild guild = event.getGuild();
        
        if (member == null || guild == null) {
            logger.error("Member or guild is null during post-ToS role assignment for user: {}", user.getId());
            return;
        }
        
        Map<String, String> categoryRoles = getCategoryRoles(settings, category);

        // Check if user already has a role from this category.
        if ("gender".equals(category) && hasRoleFromCategory(user, category, member)) {
            event.getHook().sendMessage("You have already selected a role from this category. Your choice cannot be changed.")
                    .setEphemeral(true)
                    .queue();
            return;
        }
        
        Role newRole = guild.getRoleById(roleIdToAssign);
        if (newRole == null) {
            logger.error("Role with ID {} not found in guild {} during post-ToS assignment", roleIdToAssign, guild.getId());
            event.getHook().sendMessage("Error: The role to be assigned no longer exists.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        // Security checks for bot permissions and hierarchy
        if (!guild.getSelfMember().hasPermission(Permission.MANAGE_ROLES)) {
            logger.error("Bot lacks MANAGE_ROLES permission in guild {} during post-ToS assignment", guild.getId());
            event.getHook().sendMessage("I don't have permission to manage roles. Please contact an administrator.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        if (!guild.getSelfMember().canInteract(newRole)) {
            logger.error("Bot cannot interact with role {} (ID: {}) due to hierarchy during post-ToS assignment.", newRole.getName(), newRole.getId());
            event.getHook().sendMessage("I cannot assign this role due to hierarchy. Please contact an administrator.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        List<Role> rolesToRemove = member.getRoles().stream()
                .filter(role -> categoryRoles.containsKey(role.getId()))
                .collect(Collectors.toList());

        guild.modifyMemberRoles(member, List.of(newRole), rolesToRemove).queue(
            success -> {
                updateUserRoleSelection(user, member.getId(), category, roleIdToAssign);
                event.getHook().sendMessage("You have been successfully assigned the '" + newRole.getName() + "' role!")
                        .setEphemeral(true)
                        .queue();
                logger.info("Assigned role '{}' to user {} in category '{}' after ToS agreement", newRole.getName(), member.getId(), category);
            },
            error -> {
                logger.error("Failed to modify roles for user {} during post-ToS assignment: {}", member.getId(), error.getMessage());
                event.getHook().sendMessage("An error occurred while assigning the role. Please try again.")
                        .setEphemeral(true)
                        .queue();
            }
        );
    }
} 