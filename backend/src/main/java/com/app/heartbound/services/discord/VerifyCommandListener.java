package com.app.heartbound.services.discord;

import com.app.heartbound.entities.DiscordBotSettings;
import com.app.heartbound.entities.User;
import com.app.heartbound.services.UserService;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import javax.annotation.Nonnull;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class VerifyCommandListener extends ListenerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(VerifyCommandListener.class);
    // Role IDs for permission checking, mirroring PrisonCommandListener
    private static final String HEAD_MOD_ROLE_ID = "1161777177109483581";
    private static final String MOD_ROLE_ID = "1161797355096518759";
    private static final String JR_MOD_ROLE_ID = "1167669829117935666";

    private final UserService userService;
    private final DiscordBotSettingsService discordBotSettingsService;

    public VerifyCommandListener(UserService userService, DiscordBotSettingsService discordBotSettingsService) {
        this.userService = userService;
        this.discordBotSettingsService = discordBotSettingsService;
    }

    @Override
    @Transactional
    public void onSlashCommandInteraction(@Nonnull SlashCommandInteractionEvent event) {
        if (!event.getName().equals("verify")) {
            return;
        }

        event.deferReply(true).queue();

        Member commandMember = event.getMember();
        Guild guild = event.getGuild();

        if (guild == null || commandMember == null) {
            event.getHook().editOriginal("This command can only be used in a server.").queue();
            return;
        }

        // Permission check: Must have MANAGE_ROLES or specific moderator roles
        boolean hasPermission = commandMember.hasPermission(Permission.MANAGE_ROLES) ||
                                commandMember.getRoles().stream().anyMatch(role ->
                                    role.getId().equals(HEAD_MOD_ROLE_ID) ||
                                    role.getId().equals(MOD_ROLE_ID) ||
                                    role.getId().equals(JR_MOD_ROLE_ID));

        if (!hasPermission) {
            logger.warn("User {} attempted to use /verify without required permissions.", commandMember.getId());
            event.getHook().editOriginal("You do not have permission to use this command.").queue();
            return;
        }

        OptionMapping userOption = event.getOption("user");
        OptionMapping rankOption = event.getOption("rank");

        if (userOption == null || rankOption == null) {
            event.getHook().editOriginal("Both `user` and `rank` options are required.").queue();
            return;
        }

        Member targetMember = userOption.getAsMember();
        String rank = rankOption.getAsString().toLowerCase();

        if (targetMember == null) {
            event.getHook().editOriginal("The specified user could not be found in this server.").queue();
            return;
        }

        User user = userService.getUserById(targetMember.getId());
        if (user == null) {
            event.getHook().editOriginal("Could not find the target user's profile in the database. They may need to log into the web app first.").queue();
            return;
        }

        DiscordBotSettings settings = discordBotSettingsService.getDiscordBotSettings();
        String roleIdToAssign = switch (rank) {
            case "ascendant" -> settings.getRankAscendantRoleId();
            case "immortal" -> settings.getRankImmortalRoleId();
            case "radiant" -> settings.getRankRadiantRoleId();
            default -> null;
        };

        if (roleIdToAssign == null || roleIdToAssign.isBlank()) {
            logger.warn("Attempted to assign rank '{}' but its Role ID is not configured.", rank);
            event.getHook().editOriginal("Error: The Role ID for the '" + rank + "' rank has not been configured by an administrator.").queue();
            return;
        }

        Role newRole = guild.getRoleById(roleIdToAssign);
        if (newRole == null) {
            logger.error("Verified rank role with ID {} not found in guild {}", roleIdToAssign, guild.getId());
            event.getHook().editOriginal("Error: The role to be assigned ('" + rank + "') no longer exists on the server.").queue();
            return;
        }

        // Security checks for bot permissions and hierarchy
        if (!guild.getSelfMember().hasPermission(Permission.MANAGE_ROLES)) {
            logger.error("Bot lacks MANAGE_ROLES permission in guild {}", guild.getId());
            event.getHook().editOriginal("I don't have permission to manage roles. Please contact an administrator.").queue();
            return;
        }
        if (!guild.getSelfMember().canInteract(newRole)) {
            logger.error("Bot cannot interact with verified rank role {} (ID: {}) due to hierarchy.", newRole.getName(), newRole.getId());
            event.getHook().editOriginal("I cannot assign this role because it is higher than my own in the role hierarchy.").queue();
            return;
        }

        // Get all configured rank roles to check for and remove existing ones
        Map<String, String> allRankRoles = getAllRankRoles(settings);
        List<Role> rolesToRemove = targetMember.getRoles().stream()
                .filter(role -> allRankRoles.containsKey(role.getId()))
                .collect(Collectors.toList());

        guild.modifyMemberRoles(targetMember, List.of(newRole), rolesToRemove).queue(
            success -> {
                event.getHook().editOriginal("Successfully assigned the '" + newRole.getName() + "' role to " + targetMember.getAsMention() + ".").queue();
                logger.info("Moderator {} assigned verified rank role '{}' ({}) to user {}", commandMember.getId(), newRole.getName(), newRole.getId(), targetMember.getId());
                // TODO: Add audit log and verification log channel message
            },
            error -> {
                logger.error("Failed to modify roles for user {} during verification: {}", targetMember.getId(), error.getMessage());
                event.getHook().editOriginal("An error occurred while assigning the role. Please check my permissions and the role hierarchy.").queue();
            }
        );
    }

    private Map<String, String> getAllRankRoles(DiscordBotSettings settings) {
        return Stream.of(
                    settings.getRankIronRoleId(),
                    settings.getRankBronzeRoleId(),
                    settings.getRankSilverRoleId(),
                    settings.getRankGoldRoleId(),
                    settings.getRankPlatinumRoleId(),
                    settings.getRankDiamondRoleId(),
                    settings.getRankAscendantRoleId(),
                    settings.getRankImmortalRoleId(),
                    settings.getRankRadiantRoleId()
                )
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toMap(id -> id, id -> ""));
    }
} 