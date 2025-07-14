package com.app.heartbound.services.discord;

import com.app.heartbound.services.UserService;
import net.dv8tion.jda.api.events.guild.GuildBanEvent;
import net.dv8tion.jda.api.events.guild.GuildUnbanEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.Nonnull;

@Service
public class GuildEventListener extends ListenerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(GuildEventListener.class);

    private final UserService userService;

    @Autowired
    public GuildEventListener(UserService userService) {
        this.userService = userService;
    }

    @Override
    public void onGuildBan(@Nonnull GuildBanEvent event) {
        String userId = event.getUser().getId();
        logger.info("User {} was banned from the Discord server. Syncing ban status.", userId);
        try {
            if (userService.userExists(userId)) {
                userService.banUser(userId);
            } else {
                logger.info("User {} does not exist in the application database. No action taken.", userId);
            }
        } catch (Exception e) {
            logger.error("Failed to sync ban for user {}", userId, e);
        }
    }

    @Override
    public void onGuildUnban(@Nonnull GuildUnbanEvent event) {
        String userId = event.getUser().getId();
        logger.info("User {} was unbanned from the Discord server. Syncing ban status.", userId);
        try {
            if (userService.userExists(userId)) {
                userService.unbanUser(userId);
            } else {
                logger.info("User {} does not exist in the application database. No action taken.", userId);
            }
        } catch (Exception e) {
            logger.error("Failed to sync unban for user {}", userId, e);
        }
    }
} 