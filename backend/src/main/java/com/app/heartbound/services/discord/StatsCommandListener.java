package com.app.heartbound.services.discord;

import com.app.heartbound.dto.pairing.PairingDTO;
import com.app.heartbound.services.pairing.PairingService;
import com.app.heartbound.services.UserService;
import com.app.heartbound.entities.User;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import net.dv8tion.jda.api.JDA;
import java.awt.Color;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * StatsCommandListener
 * 
 * Discord slash command listener for displaying pairing statistics.
 * Allows users to view their current pairing stats if they are actively matched.
 */
@Component
public class StatsCommandListener extends ListenerAdapter {
    
    private static final Logger logger = LoggerFactory.getLogger(StatsCommandListener.class);
    private static final Color EMBED_COLOR = new Color(88, 101, 242); // Discord Blurple
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MMM dd, yyyy");
    
    private final PairingService pairingService;
    private final UserService userService;
    
    @Value("${frontend.base.url}")
    private String frontendBaseUrl;
    
    // Add field to track registration status
    private boolean isRegistered = false;
    private JDA jdaInstance;

    @Autowired
    public StatsCommandListener(@Lazy PairingService pairingService, UserService userService) {
        this.pairingService = pairingService;
        this.userService = userService;
        logger.info("StatsCommandListener initialized");
    }
    
    /**
     * Register this listener with the JDA instance.
     * This method is called by DiscordConfig after JDA is initialized.
     * 
     * @param jda The JDA instance to register with
     */
    public void registerWithJDA(JDA jda) {
        if (jda != null && !isRegistered) {
            this.jdaInstance = jda;
            jda.addEventListener(this);
            this.isRegistered = true;
            logger.debug("StatsCommandListener registered with JDA");
        }
    }
    
    /**
     * Clean up method called before bean destruction.
     * Ensures this listener is removed from JDA to prevent events during shutdown.
     */
    @PreDestroy
    public void cleanup() {
        logger.debug("StatsCommandListener cleanup started");
        if (isRegistered && jdaInstance != null) {
            try {
                jdaInstance.removeEventListener(this);
                logger.info("StatsCommandListener successfully unregistered from JDA");
            } catch (Exception e) {
                logger.warn("Error while unregistering StatsCommandListener: {}", e.getMessage());
            }
            isRegistered = false;
            jdaInstance = null;
        }
        logger.debug("StatsCommandListener cleanup completed");
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("stats")) {
            return; // Not our command
        }

        logger.debug("Stats command received from user: {}", event.getUser().getId());
        
        // Acknowledge the interaction immediately
        event.deferReply().queue();
        
        try {
            String discordUserId = event.getUser().getId();
            
            // Validate user exists in the system
            User user = userService.getUserById(discordUserId);
            if (user == null) {
                logger.warn("User {} not found in system for /stats command", discordUserId);
                event.getHook().editOriginal("❌ **User not found in system**\n" +
                    "You need to log in to the web application first before using this command.\n" +
                    "Visit: " + frontendBaseUrl).queue();
                return;
            }
            
            // Get current active pairing
            Optional<PairingDTO> pairingOpt = pairingService.getCurrentPairing(discordUserId);
            if (pairingOpt.isEmpty()) {
                logger.debug("User {} has no active pairing", discordUserId);
                event.getHook().editOriginal("💔 **You are not currently matched with anyone**\n" +
                    "Use the web application to join the matching queue and find your perfect match!\n" +
                    "Visit: " + frontendBaseUrl + "/pairings").queue();
                return;
            }
            
            PairingDTO pairing = pairingOpt.get();
            
            // Validate pairing is active
            if (!pairing.isActive()) {
                logger.warn("User {} has an inactive pairing for /stats command", discordUserId);
                event.getHook().editOriginal("💔 **You are not currently matched with anyone**\n" +
                    "Your previous pairing has ended. Use the web application to find a new match!\n" +
                    "Visit: " + frontendBaseUrl + "/pairings").queue();
                return;
            }
            
            // Determine which user is requesting and get their stats
            boolean isUser1 = pairing.getUser1Id().equals(discordUserId);
            String partnerId = isUser1 ? pairing.getUser2Id() : pairing.getUser1Id();
            int userMessageCount = isUser1 ? pairing.getUser1MessageCount() : pairing.getUser2MessageCount();
            int partnerMessageCount = isUser1 ? pairing.getUser2MessageCount() : pairing.getUser1MessageCount();
            
            // Get partner's username for display
            User partner = userService.getUserById(partnerId);
            String partnerName = partner != null ? 
                (partner.getDisplayName() != null ? partner.getDisplayName() : partner.getUsername()) : 
                "Unknown";
            
            // Build the stats embed
            MessageEmbed embed = buildStatsEmbed(pairing, userMessageCount, partnerMessageCount, partnerName, event.getUser().getName());
            
            // Send the response
            event.getHook().editOriginalEmbeds(embed).queue(
                success -> logger.debug("Stats displayed successfully for user {}", discordUserId),
                error -> logger.error("Failed to send stats for user {}: {}", discordUserId, error.getMessage())
            );
            
        } catch (Exception e) {
            logger.error("Error processing stats command for user {}: {}", event.getUser().getId(), e.getMessage(), e);
            try {
                event.getHook().editOriginal("❌ **Error retrieving pairing data**\n" +
                    "An error occurred while fetching your pairing statistics. Please try again later.").queue();
            } catch (Exception ignored) {
                logger.error("Failed to send error message for stats command", ignored);
            }
        }
    }
    
    /**
     * Builds a Discord embed for displaying pairing statistics.
     *
     * @param pairing The pairing data
     * @param userMessageCount The requesting user's message count
     * @param partnerMessageCount The partner's message count
     * @param partnerName The partner's display name
     * @param userName The requesting user's name
     * @return A MessageEmbed containing the formatted statistics
     */
    private MessageEmbed buildStatsEmbed(PairingDTO pairing, int userMessageCount, int partnerMessageCount, 
                                       String partnerName, String userName) {
        EmbedBuilder embed = new EmbedBuilder();
        
        // Set title with user names and color
        embed.setTitle(userName + " ♡ " + partnerName);
        embed.setColor(EMBED_COLOR);
        
        // Format voice time
        int totalMinutes = pairing.getVoiceTimeMinutes();
        int hours = totalMinutes / 60;
        int minutes = totalMinutes % 60;
        String voiceTimeFormatted = String.format("%d hours %d minutes", hours, minutes);
        
        // Format matched date
        String matchedDate = pairing.getMatchedAt() != null ? 
            pairing.getMatchedAt().format(DATE_FORMATTER) : "Unknown";
        
        // Build description with stats
        StringBuilder description = new StringBuilder();
        description.append("📊 **Total Messages:** ").append(pairing.getMessageCount()).append(" messages\n");
        description.append("💬 **Your Messages:** ").append(userMessageCount).append(" messages\n");
        description.append("👥 **").append(partnerName).append("'s Messages:** ").append(partnerMessageCount).append(" messages\n");
        description.append("🎤 **Voice Time:** ").append(voiceTimeFormatted).append("\n");
        description.append("💕 **Compatibility:** ").append(pairing.getCompatibilityScore()).append("%\n");
        description.append("📅 **Matched Since:** ").append(matchedDate).append("\n");
        
        // Add Discord channel info if available
        if (pairing.getDiscordChannelName() != null && !pairing.getDiscordChannelName().isEmpty()) {
            description.append("🌐 **Discord Channel:** #").append(pairing.getDiscordChannelName()).append("\n");
        }
        
        embed.setDescription(description.toString());
        
        // Set footer as clickable link to pairings page
        embed.setFooter("View more details on the website", frontendBaseUrl + "/pairings");
        
        return embed.build();
    }
} 