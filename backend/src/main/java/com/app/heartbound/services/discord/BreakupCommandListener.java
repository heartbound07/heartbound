package com.app.heartbound.services.discord;

import com.app.heartbound.dto.pairing.BreakupRequestDTO;
import com.app.heartbound.dto.pairing.PairingDTO;
import com.app.heartbound.services.pairing.PairingService;
import com.app.heartbound.services.UserService;
import com.app.heartbound.entities.User;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
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
import java.time.Duration;
import java.util.Optional;

/**
 * BreakupCommandListener
 * 
 * Discord slash command listener for ending pairings via Discord.
 * Replicates the "End This Match" functionality from the web interface.
 * 
 * Command: /breakup [reason]
 * - Only users in active pairings can execute this command
 * - Optional reason parameter for why they want to end the match
 * - Integrates with existing PairingService.breakupPairing() method
 * - Provides rich embed responses with pairing statistics
 */
@Component
public class BreakupCommandListener extends ListenerAdapter {
    
    private static final Logger logger = LoggerFactory.getLogger(BreakupCommandListener.class);
    private static final Color EMBED_COLOR = new Color(220, 53, 69); // Bootstrap danger red
    private static final Color SUCCESS_COLOR = new Color(40, 167, 69); // Bootstrap success green
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MMM dd, yyyy");
    
    private final PairingService pairingService;
    private final UserService userService;
    
    @Value("${frontend.base.url}")
    private String frontendBaseUrl;
    
    // Track registration status
    private boolean isRegistered = false;
    private JDA jdaInstance;

    @Autowired
    public BreakupCommandListener(@Lazy PairingService pairingService, UserService userService) {
        this.pairingService = pairingService;
        this.userService = userService;
        logger.info("BreakupCommandListener initialized");
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
            logger.debug("BreakupCommandListener registered with JDA");
        }
    }
    
    /**
     * Clean up method called before bean destruction.
     * Ensures this listener is removed from JDA to prevent events during shutdown.
     */
    @PreDestroy
    public void cleanup() {
        logger.debug("BreakupCommandListener cleanup started");
        if (isRegistered && jdaInstance != null) {
            try {
                jdaInstance.removeEventListener(this);
                logger.info("BreakupCommandListener successfully unregistered from JDA");
            } catch (Exception e) {
                logger.warn("Error while unregistering BreakupCommandListener: {}", e.getMessage());
            }
            isRegistered = false;
            jdaInstance = null;
        }
        logger.debug("BreakupCommandListener cleanup completed");
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("breakup")) {
            return; // Not our command
        }

        logger.debug("Breakup command received from user: {}", event.getUser().getId());
        
        try {
            String discordUserId = event.getUser().getId();
            
            // Validate user exists in the system
            User user = userService.getUserById(discordUserId);
            if (user == null) {
                logger.warn("User {} not found in system for /breakup command", discordUserId);
                event.reply("❌ **User not found in system**\n" +
                    "You need to log in to the web application first before using this command.\n" +
                    "Visit: " + frontendBaseUrl).setEphemeral(true).queue();
                return;
            }
            
            // Get current active pairing
            Optional<PairingDTO> pairingOpt = pairingService.getCurrentPairing(discordUserId);
            if (pairingOpt.isEmpty()) {
                logger.debug("User {} has no active pairing for breakup", discordUserId);
                event.reply("💔 **You are not currently matched with anyone**\n" +
                    "You need to be in an active match to use this command.\n" +
                    "Visit the web application to join the matchmaking queue: " + frontendBaseUrl + "/pairings")
                    .setEphemeral(true).queue();
                return;
            }
            
            PairingDTO pairing = pairingOpt.get();
            
            // Validate pairing is active
            if (!pairing.isActive()) {
                logger.warn("User {} has an inactive pairing for /breakup command", discordUserId);
                event.reply("💔 **You are not currently matched with anyone**\n" +
                    "Your previous pairing has ended. Use the web application to find a new match!\n" +
                    "Visit: " + frontendBaseUrl + "/pairings")
                    .setEphemeral(true).queue();
                return;
            }
            
            // Get partner information for the modal
            String partnerId = pairing.getUser1Id().equals(discordUserId) ? 
                              pairing.getUser2Id() : pairing.getUser1Id();
            User partner = userService.getUserById(partnerId);
            String partnerName = partner != null ? 
                (partner.getDisplayName() != null ? partner.getDisplayName() : partner.getUsername()) : 
                "Unknown";
            
            // Create and show the breakup confirmation modal
            showBreakupModal(event, pairing, partnerName);
            
        } catch (Exception e) {
            logger.error("Error processing breakup command for user {}: {}", event.getUser().getId(), e.getMessage(), e);
            try {
                event.reply("❌ **Error Processing Request**\n" +
                    "An unexpected error occurred. Please try again later or use the web application.\n" +
                    "Visit: " + frontendBaseUrl + "/pairings")
                    .setEphemeral(true).queue();
            } catch (Exception ignored) {
                logger.error("Failed to send error message for breakup command", ignored);
            }
        }
    }
    
    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        if (!event.getModalId().startsWith("breakup-modal-")) {
            return; // Not our modal
        }
        
        logger.debug("Breakup modal submitted by user: {}", event.getUser().getId());
        
        // Acknowledge the modal submission immediately
        event.deferReply(true).queue(); // Ephemeral response for privacy
        
        try {
            String discordUserId = event.getUser().getId();
            
            // Extract pairing ID from modal ID (format: "breakup-modal-{pairingId}")
            String modalId = event.getModalId();
            Long pairingId = Long.parseLong(modalId.substring("breakup-modal-".length()));
            
            // Get the reason from the modal
            String reason = event.getValue("breakup-reason").getAsString();
            
            // Validate and sanitize reason
            if (reason == null || reason.trim().isEmpty()) {
                reason = "No reason provided";
            } else if (reason.length() > 500) {
                reason = reason.substring(0, 500) + "...";
            }
            
            // Re-validate the pairing still exists and is active
            Optional<PairingDTO> pairingOpt = pairingService.getCurrentPairing(discordUserId);
            if (pairingOpt.isEmpty() || !pairingOpt.get().getId().equals(pairingId)) {
                logger.warn("Pairing {} no longer exists or user {} not in pairing", pairingId, discordUserId);
                event.getHook().editOriginal("❌ **Match No Longer Active**\n" +
                    "Your match has already ended or you are no longer in this pairing.").queue();
                return;
            }
            
            PairingDTO pairing = pairingOpt.get();
            
            // Get partner information before breaking up
            String partnerId = pairing.getUser1Id().equals(discordUserId) ? 
                              pairing.getUser2Id() : pairing.getUser1Id();
            User partner = userService.getUserById(partnerId);
            String partnerName = partner != null ? 
                (partner.getDisplayName() != null ? partner.getDisplayName() : partner.getUsername()) : 
                "Unknown";
            
            // Store pairing stats for the confirmation message
            PairingStats stats = extractPairingStats(pairing, discordUserId);
            
            logger.info("Processing Discord breakup for pairing {} by user {} with reason: {}", 
                       pairingId, discordUserId, reason);
            
            // Create breakup request DTO
            BreakupRequestDTO breakupRequest = BreakupRequestDTO.builder()
                    .initiatorId(discordUserId)
                    .reason(reason)
                    .mutualBreakup(false) // Discord breakups are not mutual
                    .build();
            
            // Process the breakup using existing service method
            PairingDTO updatedPairing = pairingService.breakupPairing(pairingId, breakupRequest);
            
            // Build success confirmation embed
            MessageEmbed successEmbed = buildBreakupSuccessEmbed(stats, partnerName, reason, event.getUser().getName());
            
            // Send success response
            event.getHook().editOriginalEmbeds(successEmbed).queue(
                success -> logger.info("Breakup processed successfully via Discord for user {}", discordUserId),
                error -> logger.error("Failed to send breakup confirmation for user {}: {}", discordUserId, error.getMessage())
            );
            
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid breakup request from user {}: {}", event.getUser().getId(), e.getMessage());
            event.getHook().editOriginal("❌ **Invalid Request**\n" + e.getMessage()).queue();
        } catch (IllegalStateException e) {
            logger.warn("Invalid state for breakup from user {}: {}", event.getUser().getId(), e.getMessage());
            event.getHook().editOriginal("❌ **Cannot End Match**\n" + e.getMessage()).queue();
        } catch (Exception e) {
            logger.error("Error processing breakup modal for user {}: {}", event.getUser().getId(), e.getMessage(), e);
            try {
                event.getHook().editOriginal("❌ **Error Processing Breakup**\n" +
                    "An unexpected error occurred while ending your match. Please try again later or use the web application.\n" +
                    "Visit: " + frontendBaseUrl + "/pairings").queue();
            } catch (Exception ignored) {
                logger.error("Failed to send error message for breakup modal", ignored);
            }
        }
    }
    
    /**
     * Create and display the breakup confirmation modal
     */
    private void showBreakupModal(SlashCommandInteractionEvent event, PairingDTO pairing, String partnerName) {
        // Create text input for the breakup reason
        TextInput reasonInput = TextInput.create("breakup-reason", "Reason for ending the match", TextInputStyle.PARAGRAPH)
                .setPlaceholder("Enter your reason for ending this match (optional)...")
                .setMinLength(0)
                .setMaxLength(500)
                .setRequired(false)
                .build();
        
        // Create the modal with the pairing ID in the modal ID for reference
        Modal breakupModal = Modal.create("breakup-modal-" + pairing.getId(), "💔 End Match Confirmation")
                .addComponents(ActionRow.of(reasonInput))
                .build();
        
        // Show the modal to the user
        event.replyModal(breakupModal).queue(
            success -> logger.debug("Breakup modal shown successfully to user {}", event.getUser().getId()),
            error -> {
                logger.error("Failed to show breakup modal to user {}: {}", event.getUser().getId(), error.getMessage());
                // Fallback to immediate reply if modal fails
                event.reply("❌ **Error showing breakup form**\n" +
                    "Please try again or use the web application to end your match.\n" +
                    "Visit: " + frontendBaseUrl + "/pairings")
                    .setEphemeral(true).queue();
            }
        );
        
        logger.info("Breakup modal displayed for user {} (pairing {} with {})", 
                   event.getUser().getId(), pairing.getId(), partnerName);
    }
    
    /**
     * Extract relevant pairing statistics for display
     */
    private PairingStats extractPairingStats(PairingDTO pairing, String userId) {
        boolean isUser1 = pairing.getUser1Id().equals(userId);
        int userMessageCount = isUser1 ? pairing.getUser1MessageCount() : pairing.getUser2MessageCount();
        int partnerMessageCount = isUser1 ? pairing.getUser2MessageCount() : pairing.getUser1MessageCount();
        
        // Calculate match duration
        Duration matchDuration = Duration.between(pairing.getMatchedAt(), java.time.LocalDateTime.now());
        long days = matchDuration.toDays();
        long hours = matchDuration.toHours() % 24;
        
        return new PairingStats(
            pairing.getMessageCount(),
            userMessageCount,
            partnerMessageCount,
            pairing.getVoiceTimeMinutes(),
            pairing.getCompatibilityScore(),
            pairing.getMatchedAt().format(DATE_FORMATTER),
            String.format("%d days, %d hours", days, hours),
            pairing.getDiscordChannelName()
        );
    }
    
    /**
     * Build the success confirmation embed with pairing statistics
     */
    private MessageEmbed buildBreakupSuccessEmbed(PairingStats stats, String partnerName, 
                                                 String reason, String userName) {
        EmbedBuilder embed = new EmbedBuilder();
        
        // Set title and color
        embed.setTitle("💔 Match Ended Successfully");
        embed.setColor(SUCCESS_COLOR);
        
        // Format voice time
        int totalMinutes = stats.voiceTimeMinutes;
        int hours = totalMinutes / 60;
        int minutes = totalMinutes % 60;
        String voiceTimeFormatted = String.format("%d hours %d minutes", hours, minutes);
        
        // Build description with final statistics
        StringBuilder description = new StringBuilder();
        description.append("Your match with **").append(partnerName).append("** has been ended.\n\n");
        
        description.append("**📊 Final Statistics:**\n");
        description.append("• **Total Messages:** ").append(stats.totalMessages).append(" messages\n");
        description.append("• **Your Messages:** ").append(stats.userMessages).append(" messages\n");
        description.append("• **Partner's Messages:** ").append(stats.partnerMessages).append(" messages\n");
        description.append("• **Voice Time:** ").append(voiceTimeFormatted).append("\n");
        description.append("• **Compatibility:** ").append(stats.compatibilityScore).append("%\n");
        description.append("• **Match Duration:** ").append(stats.matchDuration).append("\n");
        description.append("• **Matched Since:** ").append(stats.matchedDate).append("\n");
        
        if (stats.discordChannelName != null && !stats.discordChannelName.isEmpty()) {
            description.append("• **Discord Channel:** #").append(stats.discordChannelName).append(" (deleted)\n");
        }
        
        description.append("\n**Reason:** ").append(reason).append("\n\n");
        description.append("Both users have been notified and your Discord channel has been deleted. ");
        description.append("You can join the matchmaking queue again to find a new match.");
        
        embed.setDescription(description.toString());
        
        // Set footer with link to web app
        embed.setFooter("Visit the web application to find a new match", frontendBaseUrl + "/pairings");
        
        // Add timestamp
        embed.setTimestamp(java.time.Instant.now());
        
        return embed.build();
    }
    
    /**
     * Helper class to store pairing statistics
     */
    private static class PairingStats {
        final int totalMessages;
        final int userMessages;
        final int partnerMessages;
        final int voiceTimeMinutes;
        final int compatibilityScore;
        final String matchedDate;
        final String matchDuration;
        final String discordChannelName;
        
        public PairingStats(int totalMessages, int userMessages, int partnerMessages, 
                           int voiceTimeMinutes, int compatibilityScore, String matchedDate, 
                           String matchDuration, String discordChannelName) {
            this.totalMessages = totalMessages;
            this.userMessages = userMessages;
            this.partnerMessages = partnerMessages;
            this.voiceTimeMinutes = voiceTimeMinutes;
            this.compatibilityScore = compatibilityScore;
            this.matchedDate = matchedDate;
            this.matchDuration = matchDuration;
            this.discordChannelName = discordChannelName;
        }
    }
} 