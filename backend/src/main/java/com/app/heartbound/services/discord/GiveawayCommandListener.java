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
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * GiveawayCommandListener
 * 
 * Discord slash command listener for giveaway management.
 * Handles /gcreate and /gdelete commands with proper authentication and validation.
 * 
 * Commands (Admin only):
 * - /gcreate: Shows modal for giveaway configuration, creates giveaway embed with entry button
 * - /gdelete: Deletes user's own giveaways with autocomplete functionality, handles refunds for active giveaways
 * 
 * Features:
 * - Modal interactions for giveaway creation
 * - Entry button interactions and validation
 * - Autocomplete for giveaway deletion
 * - Real-time embed updates
 * - Security validation (admin permissions, ownership checks)
 */
@Component
public class GiveawayCommandListener extends ListenerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(GiveawayCommandListener.class);
    
    private final GiveawayService giveawayService;
    private final UserService userService;
    private final DiscordBotSettingsService discordBotSettingsService;
    private JDA jda;

    @Value("${frontend.base.url}")
    private String frontendBaseUrl;

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
        String commandName = event.getName();
        
        if (commandName.equals("gcreate")) {
            handleGcreateCommand(event);
        } else if (commandName.equals("gdelete")) {
            handleGdeleteCommand(event);
        }
    }

    @Override
    public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
        if (!event.getName().equals("gdelete")) {
            return; // Not our command
        }

        String userId = event.getUser().getId();
        logger.debug("Autocomplete request for /gdelete from user: {}", userId);

        try {
            // Get user's giveaways for autocomplete (limit to 25 most recent)
            List<Giveaway> userGiveaways = giveawayService.getGiveawaysByHostForAutocomplete(userId, 25);
            
            List<Command.Choice> choices = userGiveaways.stream()
                    .map(giveaway -> {
                        // Format: "Prize Name - Status (Created: Date)"
                        String displayName = String.format("%s - %s (Created: %s)", 
                            giveaway.getPrize().length() > 60 ? giveaway.getPrize().substring(0, 57) + "..." : giveaway.getPrize(),
                            giveaway.getStatus().toString(),
                            giveaway.getCreatedAt().format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))
                        );
                        return new Command.Choice(displayName, giveaway.getId().toString());
                    })
                    .collect(Collectors.toList());

            event.replyChoices(choices).queue(
                success -> logger.debug("Sent {} autocomplete choices for /gdelete to user {}", choices.size(), userId),
                error -> logger.error("Failed to send autocomplete choices for /gdelete: {}", error.getMessage())
            );
        } catch (Exception e) {
            logger.error("Error processing autocomplete for /gdelete: {}", e.getMessage(), e);
            event.replyChoices(List.of()).queue(); // Send empty choices on error
        }
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
            String entryConfigStr = event.getValue("giveaway-entry-config") != null ? 
                event.getValue("giveaway-entry-config").getAsString() : "0";

            // Parse the combined entry config field
            String[] configParts = parseEntryConfig(entryConfigStr);
            String entryPriceStr = configParts[0];
            String maxEntriesStr = configParts[1];

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
            String entryMessage = String.format("✅ You are now entered in the giveaway! (Entry #%d)", 
                entry.getEntryNumber());
            
            if (entry.getCreditsPaid() > 0) {
                // Get user's current credits after deduction
                var updatedUser = userService.getUserById(userId);
                if (updatedUser != null) {
                                         entryMessage += String.format("\nYou now have **🪙 %d credits.**", updatedUser.getCredits());
                }
            }

            event.getHook().editOriginal(entryMessage).queue();

        } catch (com.app.heartbound.exceptions.UnauthorizedOperationException e) {
            // Handle eligibility errors with specific requirement message
            if ("not signed up with the bot".equals(e.getMessage())) {
                // Special handling for users not in database attempting paid entries
                String signUpMessage = String.format("You are currently not signed up with the bot! Please login through the site to join the giveaway.\n%s", frontendBaseUrl);
                event.getHook().editOriginal(signUpMessage).queue();
            } else {
                event.getHook().editOriginal("You can't enter the giveaway! You must be " + e.getMessage()).queue();
            }
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
                .setPlaceholder("e.g., '1d', '2 weeks', '30m', '45s'")
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

        TextInput entryConfigInput = TextInput.create("giveaway-entry-config", "Entry Config (Price,MaxEntries)", TextInputStyle.SHORT)
                .setPlaceholder("Examples: '0' (free), '50,3' (50 credits, max 3 entries)")
                .setMinLength(1)
                .setMaxLength(20)
                .setRequired(false)
                .build();

        // Create the modal
        Modal giveawayModal = Modal.create("giveaway-create-modal", "🎉 Create Giveaway")
                .addComponents(
                    ActionRow.of(prizeInput),
                    ActionRow.of(winnersInput),
                    ActionRow.of(durationInput),
                    ActionRow.of(restrictionsInput),
                    ActionRow.of(entryConfigInput)
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
        if (!isValidDurationFormat(normalizedDuration)) {
            throw new IllegalArgumentException("Invalid duration format. Supported formats: " +
                "days (1d, 1 day), weeks (1w, 1 week), minutes (1m, 1 minute), seconds (10s, 10 seconds)");
        }
        dto.setDuration(duration.trim()); // Pass original format to service for parsing
        
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
        int entryPrice;
        try {
            entryPrice = Integer.parseInt(entryPriceStr.trim());
            if (entryPrice < 0) {
                throw new IllegalArgumentException("Entry price cannot be negative");
            }
            dto.setEntryPrice(entryPrice);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid entry price: " + entryPriceStr);
        }

        // Edge case: If entry price is 0 (free) and no max entries specified, set max entries to 1
        if (entryPrice == 0 && (maxEntriesStr == null || maxEntriesStr.trim().isEmpty())) {
            dto.setMaxEntriesPerUser(1);
            logger.debug("Free giveaway detected - setting max entries to 1");
        }
        
        return dto;
    }

    /**
     * Parses the entry config string to extract price and max entries
     * @param entryConfigStr The config string (e.g., "0", "50", "50,3")
     * @return Array with [priceStr, maxEntriesStr]
     */
    private String[] parseEntryConfig(String entryConfigStr) {
        if (entryConfigStr == null || entryConfigStr.trim().isEmpty()) {
            return new String[]{"0", ""};
        }
        
        String cleaned = entryConfigStr.trim();
        
        // Check if it contains a comma (price,maxentries format)
        if (cleaned.contains(",")) {
            String[] parts = cleaned.split(",", 2);
            String priceStr = parts[0].trim();
            String maxEntriesStr = parts.length > 1 ? parts[1].trim() : "";
            
            // Validate format
            if (priceStr.isEmpty()) {
                throw new IllegalArgumentException("Invalid entry config format. Use 'price' or 'price,maxentries' (e.g., '0', '50,3')");
            }
            
            return new String[]{priceStr, maxEntriesStr};
        } else {
            // Only price provided, no max entries specified
            return new String[]{cleaned, ""};
        }
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
        description.append("**Hosted by:** <@").append(giveaway.getHostUserId()).append(">\n");
        description.append("**Entries:** ").append(entryCount).append("\n"); // Use the provided entry count
        description.append("**Winners:** ").append(giveaway.getNumberOfWinners()).append("\n\n");
        
        // Entry cost only
        if (giveaway.getEntryPrice() > 0) {
            description.append("**🪙 ").append(giveaway.getEntryPrice()).append(" credits entry fee!**\n");
        } else {
            description.append("**Free entry!**\n");
        }
        
        embedBuilder.setDescription(description.toString());
        embedBuilder.setColor(Color.decode("#58b9ff")); // Use blue color for giveaways
        
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

    /**
     * Handle the /gcreate slash command
     */
    private void handleGcreateCommand(SlashCommandInteractionEvent event) {
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

    /**
     * Handle the /gdelete slash command
     */
    private void handleGdeleteCommand(SlashCommandInteractionEvent event) {
        logger.debug("Giveaway delete command received from user: {}", event.getUser().getId());

        // Check if user has administrator permissions
        Member member = event.getMember();
        if (member == null || !member.hasPermission(Permission.ADMINISTRATOR)) {
            event.reply("❌ **Access Denied**\nYou need Administrator permissions to delete giveaways.")
                .setEphemeral(true).queue();
            logger.warn("Non-admin user {} attempted to use /gdelete command", event.getUser().getId());
            return;
        }

        // Get the giveaway ID from the command option
        String giveawayIdStr = event.getOption("name").getAsString();
        
        // Acknowledge the command immediately
        event.deferReply(true).queue(); // Ephemeral response

        try {
            UUID giveawayId = UUID.fromString(giveawayIdStr);
            String userId = event.getUser().getId();

            // Verify the giveaway exists and get its details before deletion
            Optional<Giveaway> giveawayOpt = giveawayService.getGiveawayById(giveawayId);
            if (giveawayOpt.isEmpty()) {
                event.getHook().editOriginal("❌ **Error**\nGiveaway not found.").queue();
                return;
            }

            Giveaway giveaway = giveawayOpt.get();
            
            // Delete the giveaway (service handles validation and refunds)
            giveawayService.deleteGiveaway(giveawayId, userId);

            // Update the Discord message to show deletion status
            updateGiveawayEmbedForDeletion(giveaway);

            // Send success message
            String successMessage = String.format("✅ **Giveaway Deleted**\nSuccessfully deleted giveaway: **%s**", 
                giveaway.getPrize());
            
            if (giveaway.getStatus() == Giveaway.GiveawayStatus.ACTIVE) {
                successMessage += "\nAll entries have been refunded.";
            }

            event.getHook().editOriginal(successMessage).queue();
            logger.info("Giveaway {} successfully deleted by admin {}", giveawayId, userId);

        } catch (IllegalArgumentException e) {
            event.getHook().editOriginal("❌ **Error**\nInvalid giveaway ID.").queue();
        } catch (com.app.heartbound.exceptions.UnauthorizedOperationException e) {
            event.getHook().editOriginal("❌ **Access Denied**\n" + e.getMessage()).queue();
        } catch (IllegalStateException e) {
            event.getHook().editOriginal("❌ **Error**\n" + e.getMessage()).queue();
        } catch (Exception e) {
            logger.error("Error deleting giveaway: {}", e.getMessage(), e);
            event.getHook().editOriginal("❌ **Error deleting giveaway**\nPlease try again later.").queue();
        }
    }

    private void updateGiveawayEmbedForDeletion(Giveaway giveaway) {
        try {
            if (giveaway.getMessageId() == null || giveaway.getMessageId().equals("temp")) {
                logger.debug("Cannot update giveaway embed for deletion: messageId is null or temporary for giveaway {}", giveaway.getId());
                return;
            }

            // Create deleted giveaway embed
            EmbedBuilder embedBuilder = new EmbedBuilder();
            embedBuilder.setTitle("❌ Giveaway Deleted");
            embedBuilder.setDescription(String.format("**Prize:** %s\n**Hosted by:** <@%s>\n\n*This giveaway has been deleted by the host.*", 
                giveaway.getPrize(), giveaway.getHostUserId()));
            embedBuilder.setColor(Color.decode("#dc3545")); // Bootstrap danger red
            
            MessageEmbed deletedEmbed = embedBuilder.build();
            
            // Get the channel and update the message
            if (jda != null) {
                TextChannel channel = jda.getTextChannelById(giveaway.getChannelId());
                if (channel != null) {
                    // Update the embed and remove the button
                    channel.editMessageEmbedsById(giveaway.getMessageId(), deletedEmbed)
                        .setComponents() // Remove all action rows (buttons)
                        .queue(
                            success -> logger.debug("Updated giveaway embed to show deletion for giveaway {}", giveaway.getId()),
                            error -> logger.error("Failed to update giveaway embed for deletion of giveaway {}: {}", 
                                                 giveaway.getId(), error.getMessage())
                        );
                } else {
                    logger.warn("Could not find channel {} to update deleted giveaway embed", giveaway.getChannelId());
                }
            }
        } catch (Exception e) {
            logger.error("Error updating giveaway embed for deletion of giveaway {}: {}", giveaway.getId(), e.getMessage(), e);
        }
    }

    /**
     * Validate if the duration format is supported
     * @param normalizedDuration The duration string in lowercase
     * @return true if the format is valid, false otherwise
     */
    private boolean isValidDurationFormat(String normalizedDuration) {
        if (normalizedDuration == null || normalizedDuration.trim().isEmpty()) {
            return false;
        }
        
        // Regex patterns for different time formats (same as in GiveawayService)
        java.util.regex.Pattern dayPattern = java.util.regex.Pattern.compile("^(\\d+)\\s*(?:d|day|days)$");
        java.util.regex.Pattern weekPattern = java.util.regex.Pattern.compile("^(\\d+)\\s*(?:w|week|weeks)$");
        java.util.regex.Pattern minutePattern = java.util.regex.Pattern.compile("^(\\d+)\\s*(?:m|minute|minutes)$");
        java.util.regex.Pattern secondPattern = java.util.regex.Pattern.compile("^(\\d+)\\s*(?:s|second|seconds)$");
        
        // Check if any pattern matches
        return dayPattern.matcher(normalizedDuration).matches() ||
               weekPattern.matcher(normalizedDuration).matches() ||
               minutePattern.matcher(normalizedDuration).matches() ||
               secondPattern.matcher(normalizedDuration).matches();
    }

} 