package com.app.heartbound.services.discord;

import com.app.heartbound.dto.giveaway.CreateGiveawayDTO;
import com.app.heartbound.entities.Giveaway;
import com.app.heartbound.entities.GiveawayEntry;
import com.app.heartbound.services.GiveawayService;
import com.app.heartbound.services.UserService;
import com.app.heartbound.services.discord.DiscordBotSettingsService;
import jakarta.annotation.PreDestroy;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * GiveawayCommandListener
 * 
 * Discord slash command listener for giveaway management.
 * Handles /gcreate command with modal interactions for creating giveaways.
 * 
 * Command: /gcreate (Admin only)
 * - Shows modal for giveaway configuration
 * - Creates giveaway embed with entry button
 * - Handles entry interactions and validation
 */
@Component
public class GiveawayCommandListener extends ListenerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(GiveawayCommandListener.class);
    
    private final GiveawayService giveawayService;
    private final UserService userService;
    private final DiscordBotSettingsService discordBotSettingsService;
    private JDA jda;

    @Autowired
    @Lazy
    public GiveawayCommandListener(GiveawayService giveawayService, UserService userService, DiscordBotSettingsService discordBotSettingsService) {
        this.giveawayService = giveawayService;
        this.userService = userService;
        this.discordBotSettingsService = discordBotSettingsService;
    }

    /**
     * Register this listener with JDA manually due to circular dependency
     */
    public void registerWithJDA(JDA jda) {
        this.jda = jda;
        jda.addEventListener(this);
        logger.info("GiveawayCommandListener registered with JDA");
    }

    @PreDestroy
    public void unregister() {
        if (jda != null) {
            jda.removeEventListener(this);
            logger.info("GiveawayCommandListener unregistered from JDA");
        }
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("gcreate")) {
            return; // Not our command
        }

        logger.debug("Giveaway create command received from user: {}", event.getUser().getId());

        // Check if user has administrator permissions
        Member member = event.getMember();
        if (member == null || !member.hasPermission(Permission.ADMINISTRATOR)) {
            event.reply("❌ **Access Denied**\nYou need Administrator permissions to create giveaways.")
                .setEphemeral(true).queue();
            logger.warn("Non-admin user {} attempted to use /gcreate command", event.getUser().getId());
            return;
        }

        // Show the giveaway creation modal
        showGiveawayCreationModal(event);
    }

    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        if (!event.getModalId().equals("giveaway-create-modal")) {
            return; // Not our modal
        }

        logger.debug("Giveaway creation modal submitted by user: {}", event.getUser().getId());

        // Acknowledge the modal submission immediately
        event.deferReply().queue();

        try {
            // Extract values from modal
            String prize = event.getValue("giveaway-prize").getAsString();
            String winnersStr = event.getValue("giveaway-winners").getAsString();
            String duration = event.getValue("giveaway-duration").getAsString();
            String restrictionsStr = event.getValue("giveaway-restrictions").getAsString();
            String maxEntriesStr = event.getValue("giveaway-max-entries") != null ? 
                event.getValue("giveaway-max-entries").getAsString() : "";
            String entryPriceStr = event.getValue("giveaway-entry-price") != null ? 
                event.getValue("giveaway-entry-price").getAsString() : "0";

            // Validate and parse inputs
            CreateGiveawayDTO dto = parseModalInputs(prize, winnersStr, duration, restrictionsStr, maxEntriesStr, entryPriceStr);

            // Create the giveaway (we'll set messageId after creating the embed)
            String hostUserId = event.getUser().getId();
            String hostUsername = event.getUser().getName();
            String channelId = event.getChannel().getId();
            
            // Create giveaway with temporary messageId
            Giveaway giveaway = giveawayService.createGiveaway(dto, hostUserId, hostUsername, channelId, "temp");

            // Create and send giveaway embed
            MessageEmbed embed = createGiveawayEmbed(giveaway);
            Button enterButton = Button.primary("giveaway-enter:" + giveaway.getId(), "🎉");

            event.getHook().sendMessageEmbeds(embed)
                .addActionRow(enterButton)
                .queue(
                    message -> {
                        // Update giveaway with actual message ID
                        giveawayService.updateGiveawayMessageId(giveaway.getId(), message.getId());
                        logger.info("Giveaway {} created successfully by {}", giveaway.getId(), hostUserId);
                    },
                    error -> logger.error("Failed to send giveaway embed: {}", error.getMessage())
                );

        } catch (Exception e) {
            logger.error("Error processing giveaway creation modal: {}", e.getMessage(), e);
            event.getHook().editOriginal("❌ **Error creating giveaway**\n" + e.getMessage()).queue();
        }
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String componentId = event.getComponentId();
        
        if (!componentId.startsWith("giveaway-enter:")) {
            return; // Not our button
        }

        // Acknowledge the button click immediately
        event.deferReply(true).queue(); // Ephemeral response

        try {
            // Extract giveaway ID from button ID
            String giveawayIdStr = componentId.substring("giveaway-enter:".length());
            UUID giveawayId = UUID.fromString(giveawayIdStr);

            String userId = event.getUser().getId();
            String username = event.getUser().getName();

            // Get the giveaway first to check requirements
            Optional<Giveaway> giveawayOpt = giveawayService.getGiveawayById(giveawayId);
            if (giveawayOpt.isEmpty()) {
                event.getHook().editOriginal("❌ **Error**\nGiveaway not found.").queue();
                return;
            }
            
            Giveaway giveaway = giveawayOpt.get();
            
            // Check booster requirement first (requires Discord member data)
            if (Boolean.TRUE.equals(giveaway.getBoostersOnly())) {
                Member member = event.getMember();
                if (member == null || !member.isBoosting()) {
                    event.getHook().editOriginal("You can't enter the giveaway! You must be a server booster").queue();
                    return;
                }
            }

            // Check level requirement (requires Discord member data to check roles)
            if (Boolean.TRUE.equals(giveaway.getLevelRestricted())) {
                Member member = event.getMember();
                if (member == null || !hasLevelRole(member)) {
                    event.getHook().editOriginal("You can't enter the giveaway! You must be level 5 or higher").queue();
                    return;
                }
            }

            // Attempt to enter the giveaway
            GiveawayEntry entry = giveawayService.enterGiveaway(giveawayId, userId, username);

            // Update the giveaway embed with new entry count
            updateGiveawayEmbed(giveaway);

            // Send success message
            String entryMessage = String.format("✅ **Successfully Entered!**\nYou are now entered in the giveaway (Entry #%d)", 
                entry.getEntryNumber());
            
            if (entry.getCreditsPaid() > 0) {
                entryMessage += String.format("\n💰 %d credits have been deducted from your account.", entry.getCreditsPaid());
            }

            event.getHook().editOriginal(entryMessage).queue();

        } catch (com.app.heartbound.exceptions.UnauthorizedOperationException e) {
            // Handle eligibility errors with specific requirement message
            event.getHook().editOriginal("You can't enter the giveaway! You must be " + e.getMessage()).queue();
        } catch (IllegalStateException e) {
            // Handle business logic errors (max entries, insufficient credits, etc.)
            event.getHook().editOriginal("❌ **Entry Failed**\n" + e.getMessage()).queue();
        } catch (Exception e) {
            logger.error("Error processing giveaway entry: {}", e.getMessage(), e);
            event.getHook().editOriginal("❌ **Error entering giveaway**\nPlease try again later.").queue();
        }
    }

    // Private helper methods

    private void showGiveawayCreationModal(SlashCommandInteractionEvent event) {
        // Create text inputs for the modal
        TextInput prizeInput = TextInput.create("giveaway-prize", "Prize", TextInputStyle.SHORT)
                .setPlaceholder("Enter the prize description...")
                .setMinLength(1)
                .setMaxLength(255)
                .setRequired(true)
                .build();

        TextInput winnersInput = TextInput.create("giveaway-winners", "Number of Winners", TextInputStyle.SHORT)
                .setPlaceholder("Enter number of winners (e.g., 1)")
                .setMinLength(1)
                .setMaxLength(3)
                .setRequired(true)
                .build();

        TextInput durationInput = TextInput.create("giveaway-duration", "Duration", TextInputStyle.SHORT)
                .setPlaceholder("e.g., '1 day', '1 week', '2 weeks'")
                .setMinLength(1)
                .setMaxLength(20)
                .setRequired(true)
                .build();

        TextInput restrictionsInput = TextInput.create("giveaway-restrictions", "Restrictions", TextInputStyle.SHORT)
                .setPlaceholder("Enter: 'none', 'boosters', or 'level5'")
                .setMinLength(1)
                .setMaxLength(20)
                .setRequired(true)
                .build();

        TextInput entryPriceInput = TextInput.create("giveaway-entry-price", "Entry Price (Credits)", TextInputStyle.SHORT)
                .setPlaceholder("Enter credits cost (0 for free)")
                .setMinLength(1)
                .setMaxLength(10)
                .setRequired(false)
                .build();

        // Create the modal
        Modal giveawayModal = Modal.create("giveaway-create-modal", "🎉 Create Giveaway")
                .addComponents(
                    ActionRow.of(prizeInput),
                    ActionRow.of(winnersInput),
                    ActionRow.of(durationInput),
                    ActionRow.of(restrictionsInput),
                    ActionRow.of(entryPriceInput)
                )
                .build();

        // Show the modal
        event.replyModal(giveawayModal).queue(
            success -> logger.debug("Giveaway creation modal shown to user {}", event.getUser().getId()),
            error -> {
                logger.error("Failed to show giveaway creation modal to user {}: {}", event.getUser().getId(), error.getMessage());
                event.reply("❌ **Error showing giveaway creation form**\nPlease try again.")
                    .setEphemeral(true).queue();
            }
        );
    }

    private CreateGiveawayDTO parseModalInputs(String prize, String winnersStr, String duration, 
                                              String restrictionsStr, String maxEntriesStr, String entryPriceStr) {
        CreateGiveawayDTO dto = new CreateGiveawayDTO();
        
        // Set basic fields
        dto.setPrize(prize.trim());
        
        // Parse number of winners
        try {
            int winners = Integer.parseInt(winnersStr.trim());
            if (winners < 1) {
                throw new IllegalArgumentException("Number of winners must be at least 1");
            }
            dto.setNumberOfWinners(winners);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid number of winners: " + winnersStr);
        }
        
        // Validate and set duration
        String normalizedDuration = duration.trim().toLowerCase();
        List<String> validDurations = List.of("1 day", "2 days", "3 days", "4 days", "5 days", "6 days", "1 week", "2 weeks");
        if (!validDurations.contains(normalizedDuration)) {
            throw new IllegalArgumentException("Invalid duration. Valid options: " + String.join(", ", validDurations));
        }
        dto.setDuration(normalizedDuration);
        
        // Parse restrictions
        String normalizedRestrictions = restrictionsStr.trim().toLowerCase();
        switch (normalizedRestrictions) {
            case "none":
                dto.setNoRestrictions(true);
                break;
            case "boosters":
                dto.setBoostersOnly(true);
                break;
            case "level5":
                dto.setLevelRestricted(true);
                break;
            default:
                throw new IllegalArgumentException("Invalid restrictions. Valid options: 'none', 'boosters', 'level5'");
        }
        
        // Parse max entries (optional)
        if (maxEntriesStr != null && !maxEntriesStr.trim().isEmpty()) {
            try {
                int maxEntries = Integer.parseInt(maxEntriesStr.trim());
                if (maxEntries < 1) {
                    throw new IllegalArgumentException("Max entries per user must be at least 1");
                }
                dto.setMaxEntriesPerUser(maxEntries);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid max entries: " + maxEntriesStr);
            }
        }
        
        // Parse entry price
        try {
            int entryPrice = Integer.parseInt(entryPriceStr.trim());
            if (entryPrice < 0) {
                throw new IllegalArgumentException("Entry price cannot be negative");
            }
            dto.setEntryPrice(entryPrice);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid entry price: " + entryPriceStr);
        }
        
        return dto;
    }

    private MessageEmbed createGiveawayEmbed(Giveaway giveaway) {
        // Use the shared method with initial entry count of 0
        return createGiveawayEmbedWithEntryCount(giveaway, 0);
    }

    /**
     * Updates the giveaway embed with current entry count
     * @param giveaway The giveaway to update the embed for
     */
    private void updateGiveawayEmbed(Giveaway giveaway) {
        try {
            if (giveaway.getMessageId() == null || giveaway.getMessageId().equals("temp")) {
                logger.debug("Cannot update giveaway embed: messageId is null or temporary for giveaway {}", giveaway.getId());
                return;
            }

            // Get current entry count
            long currentEntries = giveawayService.getTotalEntries(giveaway);
            
            // Create updated embed with current entry count
            MessageEmbed updatedEmbed = createGiveawayEmbedWithEntryCount(giveaway, currentEntries);
            
            // Get the channel and update the message
            if (jda != null) {
                TextChannel channel = jda.getTextChannelById(giveaway.getChannelId());
                if (channel != null) {
                    // Update the embed while preserving the button
                    Button enterButton = Button.primary("giveaway-enter:" + giveaway.getId(), "🎉");
                    
                    channel.editMessageEmbedsById(giveaway.getMessageId(), updatedEmbed)
                        .setActionRow(enterButton)
                        .queue(
                            success -> logger.debug("Updated giveaway embed for giveaway {} with {} entries", 
                                                   giveaway.getId(), currentEntries),
                            error -> logger.error("Failed to update giveaway embed for giveaway {}: {}", 
                                                 giveaway.getId(), error.getMessage())
                        );
                } else {
                    logger.warn("Could not find channel {} to update giveaway embed", giveaway.getChannelId());
                }
            }
        } catch (Exception e) {
            logger.error("Error updating giveaway embed for giveaway {}: {}", giveaway.getId(), e.getMessage(), e);
        }
    }

    /**
     * Creates a giveaway embed with a specific entry count
     * @param giveaway The giveaway
     * @param entryCount The current entry count
     * @return MessageEmbed with updated entry count
     */
    private MessageEmbed createGiveawayEmbedWithEntryCount(Giveaway giveaway, long entryCount) {
        EmbedBuilder embedBuilder = new EmbedBuilder();
        
        // Set title to the prize (without emoji)
        embedBuilder.setTitle(giveaway.getPrize());
        
        // Build description
        StringBuilder description = new StringBuilder();
        
        // End date information
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' hh:mm a");
        description.append("**Ends:** ").append(giveaway.getEndDate().format(formatter)).append("\n");
        description.append("**Hosted by:** ").append(giveaway.getHostUsername()).append("\n");
        description.append("**Entries:** ").append(entryCount).append("\n"); // Use the provided entry count
        description.append("**Winners:** ").append(giveaway.getNumberOfWinners()).append("\n\n");
        
        // Entry cost only
        if (giveaway.getEntryPrice() > 0) {
            description.append("**Entry Cost:** ").append(giveaway.getEntryPrice()).append(" credits\n");
        } else {
            description.append("**Entry Cost:** Free\n");
        }
        
        embedBuilder.setDescription(description.toString());
        embedBuilder.setColor(Color.YELLOW); // Use yellow/gold color for giveaways
        embedBuilder.setFooter("Giveaway ends " + giveaway.getEndDate().format(formatter));
        
        return embedBuilder.build();
    }

    /**
     * Check if a Discord member has any of the level roles (Level 5 and above)
     * @param member The Discord member to check
     * @return true if the member has any level role, false otherwise
     */
    private boolean hasLevelRole(Member member) {
        try {
            // Get current Discord bot settings to retrieve role IDs
            var settings = discordBotSettingsService.getCurrentSettings();
            
            // List of level role IDs to check (Level 5 and above)
            String[] levelRoleIds = {
                settings.getLevel5RoleId(),
                settings.getLevel15RoleId(),
                settings.getLevel30RoleId(),
                settings.getLevel40RoleId(),
                settings.getLevel50RoleId(),
                settings.getLevel70RoleId(),
                settings.getLevel100RoleId()
            };
            
            // Check if member has any of these roles
            for (String roleId : levelRoleIds) {
                if (roleId != null && !roleId.isEmpty()) {
                    if (member.getRoles().stream().anyMatch(role -> role.getId().equals(roleId))) {
                        logger.debug("User {} has level role: {}", member.getId(), roleId);
                        return true;
                    }
                }
            }
            
            logger.debug("User {} does not have any level roles", member.getId());
            return false;
        } catch (Exception e) {
            logger.error("Error checking level roles for user {}: {}", member.getId(), e.getMessage(), e);
            return false; // Fail safely by denying access
        }
    }


} 