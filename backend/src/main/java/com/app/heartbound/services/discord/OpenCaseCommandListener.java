package com.app.heartbound.services.discord;

import com.app.heartbound.dto.shop.CaseContentsDTO;
import com.app.heartbound.dto.shop.CaseItemDTO;
import com.app.heartbound.dto.shop.RollResultDTO;
import com.app.heartbound.dto.shop.ShopDTO;
import com.app.heartbound.dto.shop.UserInventoryDTO;
import com.app.heartbound.enums.ItemRarity;
import com.app.heartbound.enums.ShopCategory;
import com.app.heartbound.exceptions.ResourceNotFoundException;
import com.app.heartbound.exceptions.shop.CaseNotFoundException;
import com.app.heartbound.exceptions.shop.CaseNotOwnedException;
import com.app.heartbound.exceptions.shop.EmptyCaseException;
import com.app.heartbound.exceptions.shop.InvalidCaseContentsException;
import com.app.heartbound.services.UserService;
import com.app.heartbound.services.UserInventoryService;
import com.app.heartbound.services.shop.CaseService;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PreDestroy;
import net.dv8tion.jda.api.JDA;
import javax.annotation.Nonnull;
import java.awt.Color;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

/**
 * OpenCaseCommandListener
 * 
 * Discord slash command listener for opening cases via Discord.
 * Integrates with the existing CaseService.openCase() method and provides
 * a Discord-native interface for the case opening system.
 * 
 * Command: /open case:<autocomplete from user's cases>
 * 
 * Features:
 * - Autocomplete case selection from user's inventory
 * - 3-stage embed flow: confirmation → rolling → result
 * - Integration with existing backend security and validation
 * - Proper error handling for all edge cases
 * - Button interaction restrictions to command executor only
 */
@Component
public class OpenCaseCommandListener extends ListenerAdapter {
    
    private static final Logger logger = LoggerFactory.getLogger(OpenCaseCommandListener.class);
    
    // Discord embed colors
    private static final Color EMBED_COLOR = new Color(88, 101, 242); // Discord Blurple
    private static final Color ERROR_COLOR = new Color(220, 53, 69); // Bootstrap danger red
    private static final Color WARNING_COLOR = new Color(255, 193, 7); // Bootstrap warning yellow
    
    // Rarity colors mapping - matches frontend and other Discord commands
    private static final Map<String, Color> RARITY_COLORS = new HashMap<>();
    static {
        RARITY_COLORS.put("LEGENDARY", new Color(255, 215, 0)); // Gold/Yellow
        RARITY_COLORS.put("EPIC", new Color(160, 32, 240));     // Purple
        RARITY_COLORS.put("RARE", new Color(0, 123, 255));      // Blue
        RARITY_COLORS.put("UNCOMMON", new Color(40, 167, 69));  // Green
        RARITY_COLORS.put("COMMON", new Color(108, 117, 125));  // Grey
    }
    
    private final CaseService caseService;
    private final UserInventoryService userInventoryService;
    
    @Value("${discord.main.guild.id}")
    private String mainGuildId;

    @Value("${frontend.base.url}")
    private String frontendBaseUrl;
    
    // Manual registration fields following the pattern of other command listeners
    private boolean isRegistered = false;
    private JDA jdaInstance;
    
    public OpenCaseCommandListener(@Lazy CaseService caseService, UserInventoryService userInventoryService, UserService userService) {
        this.caseService = caseService;
        this.userInventoryService = userInventoryService;
        logger.info("OpenCaseCommandListener initialized");
    }
    
    /**
     * Register this listener with JDA manually due to circular dependency.
     * This follows the same pattern as ShopCommandListener, StatsCommandListener, etc.
     */
    public void registerWithJDA(JDA jda) {
        if (isRegistered) {
            logger.warn("OpenCaseCommandListener is already registered with JDA");
            return;
        }
        
        this.jdaInstance = jda;
        jda.addEventListener(this);
        this.isRegistered = true;
        logger.info("OpenCaseCommandListener registered with JDA");
    }
    
    /**
     * Clean up method called before bean destruction.
     * Ensures this listener is removed from JDA to prevent events during shutdown.
     */
    @PreDestroy
    public void cleanup() {
        logger.debug("OpenCaseCommandListener cleanup started");
        if (isRegistered && jdaInstance != null) {
            try {
                jdaInstance.removeEventListener(this);
                logger.info("OpenCaseCommandListener successfully unregistered from JDA");
            } catch (Exception e) {
                logger.warn("Error while unregistering OpenCaseCommandListener: {}", e.getMessage());
            }
            isRegistered = false;
            jdaInstance = null;
        }
        logger.debug("OpenCaseCommandListener cleanup completed");
    }
    
    @Override
    public void onCommandAutoCompleteInteraction(@Nonnull CommandAutoCompleteInteractionEvent event) {
        if (!event.getName().equals("open")) {
            return; // Not our command
        }
        
        String userId = event.getUser().getId();
        logger.debug("Autocomplete request for /open from user: {}", userId);
        
        try {
            // Use Discord-safe inventory method that eagerly fetches collections
            UserInventoryDTO inventory = userInventoryService.getUserInventoryForDiscord(userId);
            
            // DEBUG: Log what items the user has
            logger.debug("User {} inventory contains {} items", userId, inventory.getItems().size());
            
            List<Command.Choice> choices = new ArrayList<>();
            
            // Find cases in user's inventory
            for (ShopDTO item : inventory.getItems()) {
                if (item.getCategory() == ShopCategory.CASE && item.getQuantity() > 0) {
                    String choiceName = String.format("%s | %s (x%d)", 
                        item.getName(), 
                        formatRarityForDisplay(item.getRarity()),
                        item.getQuantity());
                    choices.add(new Command.Choice(choiceName, item.getId().toString()));
                    logger.debug("Added case to autocomplete: {} (ID: {})", choiceName, item.getId());
                }
            }
            
            // DEBUG: Log categories found
            Set<ShopCategory> categories = inventory.getItems().stream()
                .map(ShopDTO::getCategory)
                .collect(Collectors.toSet());
            logger.debug("User {} inventory categories: {}", userId, categories);
            
            if (choices.isEmpty()) {
                logger.debug("No cases found for user {}", userId);
                choices.add(new Command.Choice("No cases available - Visit the shop!", "none"));
            }
            
            // Limit to 25 choices (Discord limit)
            if (choices.size() > 25) {
                choices = choices.subList(0, 25);
            }
            
            event.replyChoices(choices).queue();
            
        } catch (ResourceNotFoundException e) {
            // User not found in system
            List<Command.Choice> choices = List.of(
                new Command.Choice("Account not found - Please log in to the web app first", "no_account")
            );
            event.replyChoices(choices).queue();
            logger.debug("User {} not found in database for autocomplete", userId);
        } catch (org.hibernate.LazyInitializationException e) {
            logger.error("LazyInitializationException in autocomplete for /open: {}", e.getMessage());
            List<Command.Choice> choices = List.of(
                new Command.Choice("Database connection issue - Please try again", "retry")
            );
            event.replyChoices(choices).queue();
        } catch (Exception e) {
            logger.error("Error processing autocomplete for /open: {}", e.getMessage(), e);
            List<Command.Choice> choices = List.of(
                new Command.Choice("Error loading cases - Please try again later", "error")
            );
            event.replyChoices(choices).queue();
        }
    }

    @Override
    public void onSlashCommandInteraction(@Nonnull SlashCommandInteractionEvent event) {
        if (!event.getName().equals("open")) {
            return; // Not our command
        }
        
        // Guild restriction check
        Guild guild = event.getGuild();
        if (guild == null || !guild.getId().equals(mainGuildId)) {
            event.reply("This command can only be used in the main Heartbound server.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        String userId = event.getUser().getId();
        logger.info("User {} requested /open command", userId);
        
        // Get the case ID from the option
        OptionMapping caseOption = event.getOption("case");
        if (caseOption == null) {
            event.reply("Please select a case to open!").setEphemeral(true).queue();
            return;
        }
        
        String caseIdStr = caseOption.getAsString();
        
        // Handle special cases
        if ("none".equals(caseIdStr)) {
            event.reply("You don't have any cases! Visit the shop to get some cases first.").setEphemeral(true).queue();
            return;
        }
        
        if ("no_account".equals(caseIdStr)) {
            event.reply("Please log in to the web application first to sync your account.").setEphemeral(true).queue();
            return;
        }
        
        if ("retry".equals(caseIdStr) || "error".equals(caseIdStr)) {
            event.reply("There was an issue loading your cases. Please try using the command again.").setEphemeral(true).queue();
            return;
        }
        
        UUID caseId;
        try {
            caseId = UUID.fromString(caseIdStr);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid case ID format from user {}: {}", userId, caseIdStr);
            event.reply("Invalid case selected. Please try again.").setEphemeral(true).queue();
            return;
        }
        
        try {
            // Use Discord-safe inventory method that eagerly fetches collections
            UserInventoryDTO inventory = userInventoryService.getUserInventoryForDiscord(userId);
            
            // Find the case in user's inventory
            ShopDTO selectedCase = null;
            for (ShopDTO item : inventory.getItems()) {
                if (item.getId().equals(caseId) && item.getCategory() == ShopCategory.CASE) {
                    selectedCase = item;
                    break;
                }
            }
            
            if (selectedCase == null) {
                logger.warn("User {} tried to open case {} they don't own", userId, caseId);
                event.reply("You don't own this case!").setEphemeral(true).queue();
                return;
            }
            
            // Check if user has enough quantity
            if (selectedCase.getQuantity() == null || selectedCase.getQuantity() <= 0) {
                logger.warn("User {} doesn't have enough quantity for case {}", userId, caseId);
                event.reply("You don't have this case in your inventory!").setEphemeral(true).queue();
                return;
            }
            
            // Get case contents for the confirmation embed
            CaseContentsDTO caseContents = caseService.getCaseContents(caseId);
            if (caseContents == null) {
                logger.error("Case contents not found for case ID: {}", caseId);
                event.reply("This case appears to be empty or corrupted. Please contact an administrator.").setEphemeral(true).queue();
                return;
            }
            
            // Build confirmation embed
            EmbedBuilder confirmEmbed = new EmbedBuilder()
                .setTitle(selectedCase.getName() + " | **" + formatRarityForDisplay(selectedCase.getRarity()) + "**")
                .setColor(getRarityColor(selectedCase.getRarity()))
                .setFooter("Do you want to open this case?");
            
            // Add case contents to description
            StringBuilder description = new StringBuilder();
            for (CaseItemDTO item : caseContents.getItems()) {
                description.append(item.getContainedItem().getName())
                    .append(" (").append(formatCategoryForDisplay(item.getContainedItem().getCategory())).append(")")
                    .append(" | **").append(formatRarityForDisplay(item.getContainedItem().getRarity())).append("**")
                    .append("\n");
            }
            confirmEmbed.setDescription(description.toString());
            
            // Create buttons
            Button confirmButton = Button.success("open_confirm:" + userId + ":" + caseId, "✅ Open Case");
            Button cancelButton = Button.danger("open_cancel:" + userId + ":" + caseId, "❌ Cancel");
            
            // Send confirmation embed
            event.replyEmbeds(confirmEmbed.build())
                .addActionRow(confirmButton, cancelButton)
                .queue();
            
        } catch (ResourceNotFoundException e) {
            event.reply("Could not find your account. Please log in to the web application first.").setEphemeral(true).queue();
            logger.warn("User {} not found in database for /open command", userId);
        } catch (Exception e) {
            logger.error("Error processing /open command for user {}", userId, e);
            event.reply("An error occurred while processing your request. Please try again.").setEphemeral(true).queue();
        }
    }
    
    @Override
    @Transactional
    public void onButtonInteraction(@Nonnull ButtonInteractionEvent event) {
        String buttonId = event.getComponentId();
        
        if (!buttonId.startsWith("open_")) {
            return; // Not our button
        }
        
        String userId = event.getUser().getId();
        logger.debug("Button interaction from user {} with button ID: {}", userId, buttonId);
        
        try {
            // Parse button ID: open_action:userId:caseId
            String[] parts = buttonId.split(":");
            if (parts.length < 3) {
                event.reply("❌ Invalid button configuration.").setEphemeral(true).queue();
                logger.warn("Invalid button ID format: {} (expected format: open_action:userId:caseId)", buttonId);
                return;
            }
            
            // Extract action from the first part (e.g., "open_confirm" -> "confirm")
            String actionPart = parts[0];
            if (!actionPart.startsWith("open_")) {
                event.reply("❌ Invalid button configuration.").setEphemeral(true).queue();
                logger.warn("Button ID doesn't start with open_: {}", buttonId);
                return;
            }
            String action = actionPart.substring(5); // Remove "open_" prefix
            String originalUserId = parts[1]; // User who ran the original command
            UUID caseId = UUID.fromString(parts[2]);
            
            logger.debug("Parsed button interaction - action: {}, originalUser: {}, caseId: {}, currentUser: {}", 
                        action, originalUserId, caseId, userId);
            
            // Security check: only the original user can interact with buttons
            if (!userId.equals(originalUserId)) {
                event.reply("❌ This case opening belongs to someone else. Use your own `/open` command.")
                    .setEphemeral(true).queue();
                return;
            }
            
            if (action.equals("confirm")) {
                handleCaseOpening(event, userId, caseId);
            } else if (action.equals("cancel")) {
                handleCancellation(event);
            }
            
        } catch (IllegalArgumentException e) {
            event.reply("❌ Invalid button configuration - UUID parsing failed.").setEphemeral(true).queue();
            logger.warn("Invalid UUID in button ID {}: {}", buttonId, e.getMessage());
        } catch (Exception e) {
            event.reply("❌ An error occurred while processing your action.").setEphemeral(true).queue();
            logger.error("Error handling button interaction for buttonId {}: {}", buttonId, e.getMessage(), e);
        }
    }
    
    private void handleCaseOpening(ButtonInteractionEvent event, String userId, UUID caseId) {
        // Acknowledge the button interaction
        event.deferEdit().queue();
        
        try {
            // Get case information for rolling embed
            CaseContentsDTO caseContents = caseService.getCaseContents(caseId);
            
            // Start rolling animation
            startRollingAnimation(event, caseContents.getCaseName(), userId, caseId);
            
        } catch (Exception e) {
            event.getHook().editOriginalEmbeds()
                .setContent("❌ An error occurred while opening the case.")
                .setComponents()
                .queue();
            logger.error("Error starting case opening for user {}: {}", userId, e.getMessage(), e);
        }
    }
    
    private void startRollingAnimation(ButtonInteractionEvent event, String caseName, String userId, UUID caseId) {
        logger.debug("Starting rolling animation for case {} for user {}", caseId, userId);
        
        // Start the rolling animation sequence
        CompletableFuture.runAsync(() -> {
            try {
                // Rolling animation phases (6 steps total, 2 seconds each = 12 seconds total)
                String[] rollingTexts = {"Rolling.", "Rolling..", "Rolling..."};
                
                // Make the loop variable effectively final by using a different approach
                for (int step = 0; step < 6; step++) {
                    final int currentStep = step; // Make it effectively final
                    String rollingText = rollingTexts[currentStep % 3];
                    
                    EmbedBuilder rollingEmbed = new EmbedBuilder()
                        .setTitle(rollingText)
                        .setColor(EMBED_COLOR)
                        .setFooter("You are opening a " + caseName + "!");
                    
                    // Update the embed with rate limiting protection
                    event.getHook().editOriginalEmbeds(rollingEmbed.build())
                        .setComponents() // Remove buttons during rolling
                        .queue(
                            success -> logger.debug("Rolling animation step {} sent", currentStep + 1),
                            error -> logger.error("Failed to send rolling animation: {}", error.getMessage())
                        );
                    
                    // Wait 2 seconds between animation frames to prevent rate limiting
                    // Discord allows ~5 requests per 2 seconds, so 2-second intervals are safe
                    Thread.sleep(2000);
                }
                
                // After animation, open the case
                processActualCaseOpening(event, userId, caseId);
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("Rolling animation interrupted for user {}: {}", userId, e.getMessage());
                
                // Send error message
                EmbedBuilder errorEmbed = new EmbedBuilder()
                    .setTitle("❌ Error")
                    .setDescription("Case opening was interrupted.")
                    .setColor(ERROR_COLOR);
                
                event.getHook().editOriginalEmbeds(errorEmbed.build()).setComponents().queue();
            } catch (Exception e) {
                logger.error("Error during rolling animation for user {}: {}", userId, e.getMessage(), e);
                
                // Send error message
                EmbedBuilder errorEmbed = new EmbedBuilder()
                    .setTitle("❌ Error")
                    .setDescription("An error occurred during case opening.")
                    .setColor(ERROR_COLOR);
                
                event.getHook().editOriginalEmbeds(errorEmbed.build()).setComponents().queue();
            }
        });
    }
    
    private void processActualCaseOpening(ButtonInteractionEvent event, String userId, UUID caseId) {
        try {
            logger.debug("Processing actual case opening for user {} and case {}", userId, caseId);
            
            // Use the existing CaseService.openCase method
            RollResultDTO result = caseService.openCase(userId, caseId);
            
            // Build result embed
            MessageEmbed resultEmbed = buildResultEmbed(result);
            
            // Send the result
            event.getHook().editOriginalEmbeds(resultEmbed)
                .setComponents()
                .queue(
                    success -> {
                        logger.info("User {} successfully opened case {} and won item {} (already owned: {})", 
                                   userId, caseId, result.getWonItem().getId(), result.isAlreadyOwned());
                        if (result.isCompensationAwarded()) {
                            logger.info("Compensation awarded to user {}: {} credits, {} XP", 
                                       userId, result.getCompensatedCredits(), result.getCompensatedXp());
                        }
                    },
                    error -> logger.error("Failed to send case opening result: {}", error.getMessage())
                );
            
        } catch (CaseNotOwnedException e) {
            sendErrorEmbed(event, "❌ You no longer own this case or have no cases remaining.");
            logger.warn("User {} attempted to open case {} they don't own", userId, caseId);
        } catch (EmptyCaseException e) {
            sendErrorEmbed(event, "❌ This case has no contents and cannot be opened.");
            logger.error("Empty case exception for case {}: {}", caseId, e.getMessage());
        } catch (InvalidCaseContentsException e) {
            sendErrorEmbed(event, "❌ This case has invalid contents and cannot be opened.");
            logger.error("Invalid case contents for case {}: {}", caseId, e.getMessage());
        } catch (CaseNotFoundException e) {
            sendErrorEmbed(event, "❌ Case not found. It may have been removed.");
            logger.warn("Case not found during opening: {}", e.getMessage());
        } catch (ResourceNotFoundException e) {
            sendErrorEmbed(event, "❌ Required resources not found. Please try again.");
            logger.warn("Resource not found during case opening: {}", e.getMessage());
        } catch (Exception e) {
            sendErrorEmbed(event, "❌ An error occurred while opening the case. Please try again.");
            logger.error("Error opening case {} for user {}: {}", caseId, userId, e.getMessage(), e);
        }
    }
    
    private void handleCancellation(ButtonInteractionEvent event) {
        // Acknowledge the button interaction
        event.deferEdit().queue();
        
        EmbedBuilder cancelEmbed = new EmbedBuilder()
            .setTitle("Cancelled")
            .setDescription("Case opening has been cancelled.")
            .setColor(WARNING_COLOR);
        
        event.getHook().editOriginalEmbeds(cancelEmbed.build())
            .setComponents()
            .queue(
                success -> logger.debug("Case opening cancelled by user"),
                error -> logger.error("Failed to send cancellation message: {}", error.getMessage())
            );
    }
    
    private void sendErrorEmbed(ButtonInteractionEvent event, String message) {
        EmbedBuilder errorEmbed = new EmbedBuilder()
            .setTitle("❌ Error")
            .setDescription(message)
            .setColor(ERROR_COLOR);
        
        event.getHook().editOriginalEmbeds(errorEmbed.build())
            .setComponents()
            .queue();
    }
    
    private MessageEmbed buildResultEmbed(RollResultDTO result) {
        EmbedBuilder embed = new EmbedBuilder();
        
        ShopDTO wonItem = result.getWonItem();
        String itemType = formatCategoryForDisplay(wonItem.getCategory());
        String rarity = formatRarityForDisplay(wonItem.getRarity());
        
        // Set color based on item rarity
        Color embedColor = RARITY_COLORS.getOrDefault(wonItem.getRarity().toString(), EMBED_COLOR);
        embed.setColor(embedColor);
        
        // Build description
        String description = String.format("You unboxed a %s (%s) | **%s**!", 
            wonItem.getName(), itemType, rarity);
        
        // Add compensation info if applicable
        if (result.isAlreadyOwned() && result.isCompensationAwarded()) {
            description += String.format("\n\n🔄 **Duplicate item!**\nReceived **%d** credits and **%d** XP as compensation.", 
                result.getCompensatedCredits(), result.getCompensatedXp());
        } else if (result.isAlreadyOwned()) {
            description += "\n\n💡 **Note:** You already owned this item.";
        }
        
        embed.setDescription(description);
        
        // Set footer with inventory link
        String inventoryUrl = frontendBaseUrl + "/inventory";
        embed.setFooter("Equip this in your Inventory! " + inventoryUrl);
        
        return embed.build();
    }
    
    private String formatRarityForDisplay(ItemRarity rarity) {
        if (rarity == null) return "Unknown";
        return rarity.toString();
    }
    
    private String formatCategoryForDisplay(ShopCategory category) {
        if (category == null) return "Item";
        
        switch (category) {
            case USER_COLOR: return "Nameplate";
            case LISTING: return "Listing Color";
            case ACCENT: return "Profile Accent";
            case BADGE: return "Badge";
            case CASE: return "Case";
            case FISHING_ROD: return "Fishing Rod";
            default: return category.toString();
        }
    }


    private Color getRarityColor(ItemRarity rarity) {
        if (rarity == null) return EMBED_COLOR;
        return RARITY_COLORS.getOrDefault(rarity.toString(), EMBED_COLOR);
    }
} 