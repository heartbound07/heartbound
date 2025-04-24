package com.app.heartbound.services.discord;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.awt.Color;

@Component
public class WelcomeListener extends ListenerAdapter {
    
    private static final Logger logger = LoggerFactory.getLogger(WelcomeListener.class);
    private static final String WELCOME_CHANNEL_ID = "1161715793050996828";
    private static final Color EMBED_COLOR = new Color(88, 101, 242); // Discord Blurple
    
    @Value("${frontend.base.url}")
    private String frontendBaseUrl;
    
    @Override
    public void onGuildMemberJoin(GuildMemberJoinEvent event) {
        Guild guild = event.getGuild();
        String memberId = event.getUser().getId();
        String memberName = event.getUser().getName();
        
        logger.info("New member joined: {} ({})", memberName, memberId);
        
        TextChannel welcomeChannel = guild.getTextChannelById(WELCOME_CHANNEL_ID);
        if (welcomeChannel == null) {
            logger.error("Welcome channel with ID {} not found", WELCOME_CHANNEL_ID);
            return;
        }
        
        // Create embed and button for verification
        sendVerificationMessage(welcomeChannel, memberId);
    }
    
    public void sendVerificationMessage(TextChannel channel, String mentionUserId) {
        try {
            // Create the embedded message
            EmbedBuilder embedBuilder = new EmbedBuilder()
                .setTitle("Welcome to Heartbound!")
                .setDescription("In order to verify, you must sign up through the heartbound website")
                .setColor(EMBED_COLOR)
                .setFooter("Heartbound Verification System", null);
            
            // Create the website button
            Button websiteButton = Button.link(frontendBaseUrl, "Website");
            
            // Send the message with the button
            channel.sendMessageEmbeds(embedBuilder.build())
                .setComponents(ActionRow.of(websiteButton))
                .queue(
                    success -> logger.info("Sent verification message to channel {}", channel.getId()),
                    error -> logger.error("Failed to send verification message: {}", error.getMessage())
                );
        } catch (Exception e) {
            logger.error("Error sending verification message", e);
        }
    }
} 