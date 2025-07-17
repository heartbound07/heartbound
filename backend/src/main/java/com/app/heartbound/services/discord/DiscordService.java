package com.app.heartbound.services.discord;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.exceptions.HierarchyException;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class DiscordService {

    private static final Logger logger = LoggerFactory.getLogger(DiscordService.class);

    private final JDA jda;
    private final String guildId;

    public DiscordService(JDA jda, @Value("${discord.server.id}") String guildId) {
        this.jda = jda;
        this.guildId = guildId;
    }

    /**
     * Grant a Discord role to a user
     * @param userId Discord user ID
     * @param roleId Discord role ID
     * @return true if successful, false otherwise
     */
    public boolean grantRole(String userId, String roleId) {
        if (userId == null || roleId == null || userId.isEmpty() || roleId.isEmpty()) {
            logger.warn("Cannot grant role: userId or roleId is null/empty");
            return false;
        }

        try {
            Guild guild = jda.getGuildById(guildId);
            if (guild == null) {
                logger.error("Cannot grant role: Guild not found with ID {}", guildId);
                return false;
            }

            Role role = guild.getRoleById(roleId);
            if (role == null) {
                logger.error("Cannot grant role: Role not found with ID {}", roleId);
                return false;
            }

            // Retrieve member and add role asynchronously
            guild.retrieveMemberById(userId).queue(
                member -> {
                    guild.addRoleToMember(member, role).queue(
                        success -> logger.info("Successfully granted role {} to user {}", roleId, userId),
                        error -> handleRoleError("grant", error, userId, roleId)
                    );
                },
                error -> {
                    if (error instanceof ErrorResponseException) {
                        ErrorResponseException ere = (ErrorResponseException) error;
                        if (ere.getErrorResponse() == ErrorResponse.UNKNOWN_MEMBER) {
                            logger.warn("User {} is not a member of guild {}", userId, guildId);
                        } else {
                            logger.error("Error retrieving member {}: {}", userId, ere.getMessage());
                        }
                    } else {
                        logger.error("Error retrieving member {}: {}", userId, error.getMessage());
                    }
                }
            );
            
            return true;
        } catch (Exception e) {
            logger.error("Unexpected error granting role {} to user {}: {}", roleId, userId, e.getMessage());
            return false;
        }
    }

    /**
     * Remove a Discord role from a user
     * @param userId Discord user ID
     * @param roleId Discord role ID
     * @return true if the process was initiated successfully, false otherwise
     */
    public boolean removeRole(String userId, String roleId) {
        if (userId == null || roleId == null || userId.isEmpty() || roleId.isEmpty()) {
            logger.warn("Cannot remove role: userId or roleId is null/empty");
            return false;
        }

        try {
            Guild guild = jda.getGuildById(guildId);
            if (guild == null) {
                logger.error("Cannot remove role: Guild not found with ID {}", guildId);
                return false;
            }

            Role role = guild.getRoleById(roleId);
            if (role == null) {
                logger.error("Cannot remove role: Role not found with ID {}", roleId);
                return false;
            }

            // Add more detailed logging
            logger.debug("Attempting to remove role {} from user {}", roleId, userId);
            
            // Retrieve member and remove role asynchronously
            guild.retrieveMemberById(userId).queue(
                member -> {
                    // Check if member has the role before removing
                    if (member.getRoles().contains(role)) {
                        guild.removeRoleFromMember(member, role).queue(
                            success -> logger.info("Successfully removed role {} from user {}", roleId, userId),
                            error -> handleRoleError("remove", error, userId, roleId)
                        );
                    } else {
                        logger.debug("User {} doesn't have role {}, no need to remove", userId, roleId);
                    }
                },
                error -> {
                    if (error instanceof ErrorResponseException) {
                        ErrorResponseException ere = (ErrorResponseException) error;
                        if (ere.getErrorResponse() == ErrorResponse.UNKNOWN_MEMBER) {
                            logger.warn("User {} is not a member of guild {}", userId, guildId);
                        } else {
                            logger.error("Error retrieving member {}: {}", userId, ere.getMessage());
                        }
                    } else {
                        logger.error("Error retrieving member {}: {}", userId, error.getMessage());
                    }
                }
            );
            
            return true;
        } catch (Exception e) {
            logger.error("Unexpected error removing role {} from user {}: {}", roleId, userId, e.getMessage());
            return false;
        }
    }

    /**
     * Handle role-related errors with appropriate logging
     */
    private void handleRoleError(String operation, Throwable error, String userId, String roleId) {
        if (error instanceof InsufficientPermissionException) {
            InsufficientPermissionException ipe = (InsufficientPermissionException) error;
            logger.error("Bot lacks permission to {} role {}: {}", 
                operation, roleId, ipe.getPermission().getName());
        } else if (error instanceof HierarchyException) {
            logger.error("Bot cannot {} role {} due to role hierarchy restrictions", operation, roleId);
        } else {
            logger.error("Error during {} role {} for user {}: {}", 
                operation, roleId, userId, error.getMessage());
        }
    }

    /**
     * Get the JDA instance for other services to use
     * @return JDA instance
     */
    public JDA getJDA() {
        return jda;
    }
} 