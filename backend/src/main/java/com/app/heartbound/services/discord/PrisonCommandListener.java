package com.app.heartbound.services.discord;

import com.app.heartbound.config.CacheConfig;
import com.app.heartbound.entities.User;
import com.app.heartbound.services.PrisonService;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * PrisonCommandListener
 * 
 * Discord slash command listener for the prison moderation system.
 * Allows administrators and moderators to temporarily remove a user's roles
 * and assign them a "Prison" role, or release them by restoring their original roles.
 * 
 * Command: /prison <user>
 * - Only users with ADMINISTRATOR or MODERATE_MEMBERS permissions can execute this command
 * - If the user is not in prison: stores their roles in the database, removes all roles, adds prison role
 * - If the user is in prison: restores roles from the database, removes prison role
 * - Uses the database for persistent storage of original role data.
 */
@Service
public class PrisonCommandListener extends ListenerAdapter {
    
    private static final Logger logger = LoggerFactory.getLogger(PrisonCommandListener.class);
    private static final String PRISON_ROLE_ID = "1387934212216328202";
    private static final String HEAD_MOD_ROLE_ID = "1161777177109483581";
    private static final String MOD_ROLE_ID = "1161797355096518759";
    private static final String JR_MOD_ROLE_ID = "1167669829117935666";
    
    private final PrisonService prisonService;
    private final UserService userService; // Keep for getUserById
    
    @Autowired
    public PrisonCommandListener(PrisonService prisonService, UserService userService) {
        this.prisonService = prisonService;
        this.userService = userService;
        logger.info("PrisonCommandListener initialized with persistent storage via PrisonService");
    }
    
    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("prison")) {
            return; // Not our command
        }
        
        logger.info("Prison command received from user: {}", event.getUser().getId());
        
        // Verify permissions
        Member commandMember = event.getMember();
        Guild guild = event.getGuild();
        
        if (guild == null || commandMember == null) {
            event.reply("This command can only be used in a server.").setEphemeral(true).queue();
            return;
        }
        
        // Check if the user has ADMINISTRATOR, MODERATE_MEMBERS permissions, or moderator roles
        boolean hasPermission = commandMember.hasPermission(Permission.ADMINISTRATOR) || 
                               commandMember.hasPermission(Permission.MODERATE_MEMBERS) ||
                               commandMember.getRoles().stream().anyMatch(role -> 
                                   role.getId().equals(HEAD_MOD_ROLE_ID) || 
                                   role.getId().equals(MOD_ROLE_ID) || 
                                   role.getId().equals(JR_MOD_ROLE_ID));
        
        if (!hasPermission) {
            logger.warn("User {} attempted to use /prison without required permissions", event.getUser().getId());
            event.reply("You do not have permission to use this command.").setEphemeral(true).queue();
            return;
        }
        
        // Get the target user
        OptionMapping userOption = event.getOption("user");
        if (userOption == null) {
            event.reply("You must specify a user to prison or release.").setEphemeral(true).queue();
            return;
        }
        
        Member targetMember = userOption.getAsMember();
        if (targetMember == null) {
            event.reply("The specified user was not found in this server.").setEphemeral(true).queue();
            return;
        }
        
        // Prevent self-targeting
        if (targetMember.getId().equals(event.getUser().getId())) {
            event.reply("You cannot use the prison command on yourself.").setEphemeral(true).queue();
            return;
        }
        
        // Prevent targeting other moderators/administrators
        if (targetMember.hasPermission(Permission.ADMINISTRATOR) || 
            targetMember.hasPermission(Permission.MODERATE_MEMBERS)) {
            event.reply("You cannot use the prison command on other moderators or administrators.").setEphemeral(true).queue();
            return;
        }
        
        // Get the prison role
        Role prisonRole = guild.getRoleById(PRISON_ROLE_ID);
        if (prisonRole == null) {
            logger.error("Prison role with ID {} not found", PRISON_ROLE_ID);
            event.reply("Error: Prison role not found. Please contact an administrator.").setEphemeral(true).queue();
            return;
        }
        
        // Check if the bot has permission to manage roles
        Member botMember = guild.getSelfMember();
        if (!botMember.hasPermission(Permission.MANAGE_ROLES)) {
            event.reply("I don't have permission to manage roles. Please grant me the 'Manage Roles' permission.").setEphemeral(true).queue();
            return;
        }
        
        // Check if the bot can interact with the prison role
        if (!botMember.canInteract(prisonRole)) {
            event.reply("I cannot assign the prison role. Please ensure my role is higher than the prison role in the hierarchy.").setEphemeral(true).queue();
            return;
        }
        
        // Acknowledge the command immediately to prevent timeout
        event.deferReply().queue();
        
        try {
            // Check if user is already in prison based on their Discord roles
            boolean isInPrison = targetMember.getRoles().contains(prisonRole);
            
            if (isInPrison) {
                // Release user from prison
                releaseFromPrison(event, targetMember, prisonRole, guild);
            } else {
                // Send user to prison
                sendToPrison(event, targetMember, prisonRole, guild);
            }
        } catch (Exception e) {
            logger.error("Error processing prison command for user {}", targetMember.getId(), e);
            event.getHook().editOriginal("An error occurred while processing the command. Please try again.").queue();
        }
    }
    
    /**
     * Sends a user to prison by storing their roles in the database and replacing them with the prison role
     */
    private void sendToPrison(SlashCommandInteractionEvent event, Member targetMember, Role prisonRole, Guild guild) {
        String userId = targetMember.getId();
        logger.info("Sending user {} to prison", userId);
        
        try {
            // Ensure the user exists in our database before proceeding
            User user = userService.getUserById(userId);
            if (user == null) {
                logger.warn("Attempted to prison user {} not found in the database.", userId);
                event.getHook().editOriginal("Cannot prison user: This user has not logged into the web application yet.").queue();
                return;
            }

            // Get current roles (excluding @everyone and bot-managed roles)
            List<Role> currentRoles = targetMember.getRoles().stream()
                    .filter(role -> !role.isPublicRole() && !role.isManaged())
                    .collect(Collectors.toList());
            
            List<String> roleIds = currentRoles.stream()
                    .map(Role::getId)
                    .collect(Collectors.toList());
            
            // Persist roles to the database before making Discord API calls
            prisonService.prisonUser(userId, roleIds);
            logger.debug("Persisted {} roles for user {}", roleIds.size(), userId);
            
            // Check if bot can interact with all user's roles
            Member botMember = guild.getSelfMember();
            List<Role> unmanageableRoles = currentRoles.stream()
                    .filter(role -> !botMember.canInteract(role))
                    .collect(Collectors.toList());
            
            if (!unmanageableRoles.isEmpty()) {
                String roleNames = unmanageableRoles.stream()
                        .map(Role::getName)
                        .collect(Collectors.joining(", "));
                logger.warn("Cannot interact with roles for user {}: {}", userId, roleNames);
                event.getHook().editOriginal("Warning: I cannot manage some of the user's roles due to role hierarchy. " +
                        "The following roles will remain: " + roleNames).queue();
                
                // Remove only the roles we can manage
                currentRoles = currentRoles.stream()
                        .filter(botMember::canInteract)
                        .collect(Collectors.toList());
            }
            
            // Remove current roles and add prison role
            guild.modifyMemberRoles(targetMember, List.of(prisonRole), currentRoles).queue(
                success -> {
                    logger.info("Successfully imprisoned user {}", userId);
                    event.getHook().editOriginal("🏛️ " + targetMember.getAsMention() + " has been imprisoned.").queue();
                },
                error -> {
                    logger.error("Failed to modify roles for user {}. Reverting database change.", userId, error);
                    // If Discord API fails, roll back the database change
                    prisonService.releaseUser(userId);
                    event.getHook().editOriginal("Failed to modify user roles: " + error.getMessage()).queue();
                }
            );
            
        } catch (Exception e) {
            logger.error("Error sending user {} to prison", userId, e);
            event.getHook().editOriginal("An error occurred while sending the user to prison.").queue();
        }
    }
    
    /**
     * Releases a user from prison by restoring their roles from the database
     */
    private void releaseFromPrison(SlashCommandInteractionEvent event, Member targetMember, Role prisonRole, Guild guild) {
        String userId = targetMember.getId();
        logger.info("Releasing user {} from prison", userId);
        
        try {
            // Get user from database to retrieve stored roles
            User user = userService.getUserById(userId);
            if (user == null || user.getOriginalRoleIds() == null || user.getOriginalRoleIds().isEmpty()) {
                logger.warn("No stored roles found in database for user {} during release", userId);
                event.getHook().editOriginal("⚠️ Cannot release " + targetMember.getAsMention() + 
                        " - original roles not found in the database. Please restore their roles manually.").queue();
                return;
            }

            List<String> storedRoleIds = user.getOriginalRoleIds();
            
            // Convert role IDs to Role objects
            List<Role> rolesToRestore = storedRoleIds.stream()
                    .map(guild::getRoleById)
                    .filter(role -> role != null) // Filter out any roles that may have been deleted from the server
                    .collect(Collectors.toList());
            
            if (rolesToRestore.size() != storedRoleIds.size()) {
                int deletedRoles = storedRoleIds.size() - rolesToRestore.size();
                logger.warn("{} stored roles no longer exist for user {}", deletedRoles, userId);
            }
            
            // Check if bot can interact with the roles to restore
            Member botMember = guild.getSelfMember();
            List<Role> unmanageableRoles = rolesToRestore.stream()
                    .filter(role -> !botMember.canInteract(role))
                    .collect(Collectors.toList());
            
            StringBuilder finalMessage = new StringBuilder();
            if (!unmanageableRoles.isEmpty()) {
                String roleNames = unmanageableRoles.stream()
                        .map(Role::getName)
                        .collect(Collectors.joining(", "));
                logger.warn("Cannot interact with some roles for user {}: {}", userId, roleNames);
                
                // Only restore the roles we can manage
                rolesToRestore = rolesToRestore.stream()
                        .filter(botMember::canInteract)
                        .collect(Collectors.toList());
                
                finalMessage.append("⚠️ ").append(targetMember.getAsMention())
                            .append(" has been released from prison, but I cannot restore some roles due to hierarchy: ")
                            .append(roleNames);
            }
            
            // Restore roles and remove prison role
            guild.modifyMemberRoles(targetMember, rolesToRestore, List.of(prisonRole)).queue(
                success -> {
                    logger.info("Successfully released user {} from prison on Discord", userId);
                    // Clear the stored roles from the database
                    prisonService.releaseUser(userId);
                    
                    if (finalMessage.length() > 0) {
                        event.getHook().editOriginal(finalMessage.toString()).queue();
                    } else {
                        event.getHook().editOriginal("🔓 " + targetMember.getAsMention() + " has been released from prison.").queue();
                    }
                },
                error -> {
                    logger.error("Failed to restore roles for user {}. Database state was not changed.", userId, error);
                    event.getHook().editOriginal("Failed to restore user roles: " + error.getMessage()).queue();
                }
            );
            
        } catch (Exception e) {
            logger.error("Error releasing user {} from prison", userId, e);
            event.getHook().editOriginal("An error occurred while releasing the user from prison.").queue();
        }
    }
} 