package com.app.heartbound.services.discord;

import com.app.heartbound.config.CacheConfig;
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
 * - If the user is not in prison: caches their roles, removes all roles, adds prison role
 * - If the user is in prison: restores cached roles, removes prison role
 * - Uses Caffeine cache to store original role data for restoration
 */
@Service
public class PrisonCommandListener extends ListenerAdapter {
    
    private static final Logger logger = LoggerFactory.getLogger(PrisonCommandListener.class);
    private static final String PRISON_ROLE_ID = "1387934212216328202";
    private static final String HEAD_MOD_ROLE_ID = "1161777177109483581";
    private static final String MOD_ROLE_ID = "1161797355096518759";
    private static final String JR_MOD_ROLE_ID = "1167669829117935666";
    
    private final CacheConfig cacheConfig;
    
    @Autowired
    public PrisonCommandListener(CacheConfig cacheConfig) {
        this.cacheConfig = cacheConfig;
        logger.info("PrisonCommandListener initialized");
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
            event.reply("This command can only be used in a server.").queue();
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
            event.reply("You do not have permission to use this command. You need Administrator, Moderate Members permission, or a moderator role.").queue();
            return;
        }
        
        // Get the target user
        OptionMapping userOption = event.getOption("user");
        if (userOption == null) {
            event.reply("You must specify a user to prison or release.").queue();
            return;
        }
        
        Member targetMember = userOption.getAsMember();
        if (targetMember == null) {
            event.reply("The specified user was not found in this server.").queue();
            return;
        }
        
        // Prevent self-targeting
        if (targetMember.getId().equals(event.getUser().getId())) {
            event.reply("You cannot use the prison command on yourself.").queue();
            return;
        }
        
        // Prevent targeting other moderators/administrators
        if (targetMember.hasPermission(Permission.ADMINISTRATOR) || 
            targetMember.hasPermission(Permission.MODERATE_MEMBERS)) {
            event.reply("You cannot use the prison command on other moderators or administrators.").queue();
            return;
        }
        
        // Get the prison role
        Role prisonRole = guild.getRoleById(PRISON_ROLE_ID);
        if (prisonRole == null) {
            logger.error("Prison role with ID {} not found", PRISON_ROLE_ID);
            event.reply("Error: Prison role not found. Please contact an administrator.").queue();
            return;
        }
        
        // Check if the bot has permission to manage roles
        Member botMember = guild.getSelfMember();
        if (!botMember.hasPermission(Permission.MANAGE_ROLES)) {
            event.reply("I don't have permission to manage roles. Please grant me the 'Manage Roles' permission.").queue();
            return;
        }
        
        // Check if the bot can interact with the prison role
        if (!botMember.canInteract(prisonRole)) {
            event.reply("I cannot assign the prison role. Please ensure my role is higher than the prison role in the hierarchy.").queue();
            return;
        }
        
        // Acknowledge the command immediately to prevent timeout
        event.deferReply().queue();
        
        try {
            // Check if user is already in prison
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
     * Sends a user to prison by caching their roles and replacing them with the prison role
     */
    private void sendToPrison(SlashCommandInteractionEvent event, Member targetMember, Role prisonRole, Guild guild) {
        String userId = targetMember.getId();
        logger.info("Sending user {} to prison", userId);
        
        try {
            // Get current roles (excluding @everyone and bot roles)
            List<Role> currentRoles = targetMember.getRoles().stream()
                    .filter(role -> !role.isPublicRole() && !role.isManaged())
                    .collect(Collectors.toList());
            
            // Cache the original roles
            List<String> roleIds = currentRoles.stream()
                    .map(Role::getId)
                    .collect(Collectors.toList());
            
                         cacheConfig.getPrisonCache().put(userId, roleIds);
            logger.debug("Cached {} roles for user {}", roleIds.size(), userId);
            
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
                    logger.error("Failed to modify roles for user {}", userId, error);
                    // Remove from cache if role modification failed
                    cacheConfig.getPrisonCache().invalidate(userId);
                    event.getHook().editOriginal("Failed to modify user roles: " + error.getMessage()).queue();
                }
            );
            
        } catch (Exception e) {
            logger.error("Error sending user {} to prison", userId, e);
            event.getHook().editOriginal("An error occurred while sending the user to prison.").queue();
        }
    }
    
    /**
     * Releases a user from prison by restoring their cached roles
     */
    private void releaseFromPrison(SlashCommandInteractionEvent event, Member targetMember, Role prisonRole, Guild guild) {
        String userId = targetMember.getId();
        logger.info("Releasing user {} from prison", userId);
        
        try {
            // Get cached roles
            @SuppressWarnings("unchecked")
            List<String> cachedRoleIds = (List<String>) cacheConfig.getPrisonCache().getIfPresent(userId);
            
            if (cachedRoleIds == null || cachedRoleIds.isEmpty()) {
                logger.warn("No cached roles found for user {} during release", userId);
                event.getHook().editOriginal("⚠️ Cannot release " + targetMember.getAsMention() + 
                        " - original roles not found in cache. Please restore their roles manually.").queue();
                return;
            }
            
            // Convert role IDs to Role objects
            List<Role> rolesToRestore = cachedRoleIds.stream()
                    .map(guild::getRoleById)
                    .filter(role -> role != null) // Filter out deleted roles
                    .collect(Collectors.toList());
            
            if (rolesToRestore.size() != cachedRoleIds.size()) {
                int deletedRoles = cachedRoleIds.size() - rolesToRestore.size();
                logger.warn("{} cached roles no longer exist for user {}", deletedRoles, userId);
            }
            
            // Check if bot can interact with the roles to restore
            Member botMember = guild.getSelfMember();
            List<Role> unmanageableRoles = rolesToRestore.stream()
                    .filter(role -> !botMember.canInteract(role))
                    .collect(Collectors.toList());
            
            if (!unmanageableRoles.isEmpty()) {
                String roleNames = unmanageableRoles.stream()
                        .map(Role::getName)
                        .collect(Collectors.joining(", "));
                logger.warn("Cannot interact with some roles for user {}: {}", userId, roleNames);
                
                // Only restore the roles we can manage
                rolesToRestore = rolesToRestore.stream()
                        .filter(botMember::canInteract)
                        .collect(Collectors.toList());
                
                event.getHook().editOriginal("⚠️ " + targetMember.getAsMention() + 
                        " has been released from prison, but I cannot restore some roles due to role hierarchy: " + roleNames).queue();
            }
            
            // Restore roles and remove prison role
            guild.modifyMemberRoles(targetMember, rolesToRestore, List.of(prisonRole)).queue(
                success -> {
                    logger.info("Successfully released user {} from prison", userId);
                    // Remove from cache
                    cacheConfig.getPrisonCache().invalidate(userId);
                    
                    if (unmanageableRoles.isEmpty()) {
                        event.getHook().editOriginal("🔓 " + targetMember.getAsMention() + " has been released from prison.").queue();
                    }
                    // If there were unmanageable roles, the message was already sent above
                },
                error -> {
                    logger.error("Failed to restore roles for user {}", userId, error);
                    event.getHook().editOriginal("Failed to restore user roles: " + error.getMessage()).queue();
                }
            );
            
        } catch (Exception e) {
            logger.error("Error releasing user {} from prison", userId, e);
            event.getHook().editOriginal("An error occurred while releasing the user from prison.").queue();
        }
    }
} 