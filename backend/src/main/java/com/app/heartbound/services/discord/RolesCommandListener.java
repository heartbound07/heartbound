package com.app.heartbound.services.discord;

import com.app.heartbound.entities.DiscordBotSettings;
import com.app.heartbound.entities.User;
import com.app.heartbound.services.UserService;
import com.app.heartbound.services.PendingRoleSelectionService;
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
    private final PendingRoleSelectionService pendingRoleSelectionService;

    public RolesCommandListener(UserService userService, DiscordBotSettingsService discordBotSettingsService, PendingRoleSelectionService pendingRoleSelectionService) {
        this.userService = userService;
        this.discordBotSettingsService = discordBotSettingsService;
        this.pendingRoleSelectionService = pendingRoleSelectionService;
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

        // Check if user already has a role from this category (either in User entity or PendingRoleSelection)
        if (hasRoleFromCategory(user, category, member)) {
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
                updateUserOrPendingWithNewRole(user, member.getId(), category, roleIdToAssign);
                event.getHook().editOriginal("You have been successfully assigned the '" + newRole.getName() + "' role!").queue();
                logger.info("Assigned role '{}' to user {} in category '{}'", newRole.getName(), member.getId(), category);
            },
            error -> {
                logger.error("Failed to modify roles for user {}: {}", member.getId(), error.getMessage());
                event.getHook().editOriginal("An error occurred while assigning the role. Please try again.").queue();
            }
        );
    }

    private boolean hasRoleFromCategory(User user, String category, Member member) {
        String discordUserId = member.getId();
        
        // First, check the database record if user exists
        if (user != null) {
            String selectedRoleId = switch (category) {
                case "age" -> user.getSelectedAgeRoleId();
                case "gender" -> user.getSelectedGenderRoleId();
                case "rank" -> user.getSelectedRankRoleId();
                case "region" -> user.getSelectedRegionRoleId();
                default -> null;
            };

            if (selectedRoleId != null && !selectedRoleId.isBlank()) {
                // To be certain, verify they still have the role on Discord
                return member.getRoles().stream().anyMatch(r -> r.getId().equals(selectedRoleId));
            }
        }
        
        // Check pending role selections for unregistered users
        if (pendingRoleSelectionService.hasRoleInCategory(discordUserId, category)) {
            String pendingRoleId = pendingRoleSelectionService.getRoleIdForCategory(discordUserId, category);
            if (pendingRoleId != null && !pendingRoleId.isBlank()) {
                // Verify they still have the role on Discord
                return member.getRoles().stream().anyMatch(r -> r.getId().equals(pendingRoleId));
            }
        }
        
        // If no database or pending record, check their Discord roles against all roles in the category
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

    private void updateUserOrPendingWithNewRole(User user, String discordUserId, String category, String roleId) {
        if (user != null) {
            // User exists in database, update the User entity
            switch (category) {
                case "age":
                    user.setSelectedAgeRoleId(roleId);
                    break;
                case "gender":
                    user.setSelectedGenderRoleId(roleId);
                    break;
                case "rank":
                    user.setSelectedRankRoleId(roleId);
                    break;
                case "region":
                    user.setSelectedRegionRoleId(roleId);
                    break;
            }
            userService.updateUser(user);
            logger.debug("Updated role selection for registered user {} in category '{}': {}", discordUserId, category, roleId);
        } else {
            // User doesn't exist in database, update pending role selection
            pendingRoleSelectionService.updateRoleSelection(discordUserId, category, roleId);
            logger.debug("Updated pending role selection for unregistered user {} in category '{}': {}", discordUserId, category, roleId);
        }
    }
} 