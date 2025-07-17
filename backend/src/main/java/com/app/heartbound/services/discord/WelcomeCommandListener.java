package com.app.heartbound.services.discord;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import javax.annotation.Nonnull;

@Component
public class WelcomeCommandListener extends ListenerAdapter {
    
    private static final Logger logger = LoggerFactory.getLogger(WelcomeCommandListener.class);
    private static final String WELCOME_CHANNEL_ID = "1161715793050996828";
    private static final String ADMIN_ROLE_ID = "1173102438694264883";
    
    private final WelcomeListener welcomeListener;
    
    public WelcomeCommandListener(WelcomeListener welcomeListener) {
        this.welcomeListener = welcomeListener;
        logger.info("WelcomeCommandListener initialized");
    }
    
    @Override
    public void onSlashCommandInteraction(@Nonnull SlashCommandInteractionEvent event) {
        if (!event.getName().equals("welcome")) {
            return; // Not our command
        }
        
        logger.info("Welcome command received from user: {}", event.getUser().getId());
        
        // Verify permissions
        Member member = event.getMember();
        Guild guild = event.getGuild();
        
        if (guild == null || member == null) {
            event.reply("This command can only be used in a server.").setEphemeral(true).queue();
            return;
        }
        
        Role requiredRole = guild.getRoleById(ADMIN_ROLE_ID);
        if (requiredRole == null) {
            logger.error("Admin role with ID {} not found", ADMIN_ROLE_ID);
            event.reply("Error: Admin role not found.").setEphemeral(true).queue();
            return;
        }
        
        if (!member.getRoles().contains(requiredRole)) {
            logger.warn("User {} attempted to use /welcome without required role", event.getUser().getId());
            event.reply("You do not have permission to use this command.").setEphemeral(true).queue();
            return;
        }
        
        // Get the welcome channel
        TextChannel welcomeChannel = guild.getTextChannelById(WELCOME_CHANNEL_ID);
        if (welcomeChannel == null) {
            logger.error("Welcome channel with ID {} not found", WELCOME_CHANNEL_ID);
            event.reply("Error: Welcome channel not found.").setEphemeral(true).queue();
            return;
        }
        
        // Acknowledge the command immediately to prevent timeout
        event.deferReply(true).queue();
        
        logger.info("Admin {} triggered welcome message in channel {}", event.getUser().getId(), WELCOME_CHANNEL_ID);
        
        // Send verification message to welcome channel
        welcomeListener.sendVerificationMessage(welcomeChannel, null);
        
        // Provide more detailed feedback to the admin
        event.getHook().editOriginal("✅ Verification welcome message sent to the welcome channel.").queue();
    }
} 