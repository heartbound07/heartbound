package com.app.heartbound.services.discord;

import com.app.heartbound.entities.Shop;
import com.app.heartbound.entities.User;
import com.app.heartbound.enums.ItemRarity;
import com.app.heartbound.enums.ShopCategory;
import com.app.heartbound.services.UserService;
import jakarta.annotation.PreDestroy;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional; // Import for potential future use

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;

@Component
public class InventoryCommandListener extends ListenerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(InventoryCommandListener.class);
    private static final int PAGE_SIZE = 10; // Items per page
    private static final Color EMBED_COLOR = new Color(88, 101, 242); // Discord Blurple

    private final UserService userService;

    // JDA registration management
    private boolean isRegistered = false;
    private JDA jdaInstance;

    // Add this static map to store rarity role IDs
    private static final Map<ItemRarity, String> RARITY_ROLE_IDS = new HashMap<>();
    static {
        RARITY_ROLE_IDS.put(ItemRarity.LEGENDARY, "1367568858797314118");
        RARITY_ROLE_IDS.put(ItemRarity.EPIC, "1367568919220719668");
        RARITY_ROLE_IDS.put(ItemRarity.RARE, "1367569009628938301");
        RARITY_ROLE_IDS.put(ItemRarity.UNCOMMON, "1367569085788848189");
        RARITY_ROLE_IDS.put(ItemRarity.COMMON, "1367569157469765662");
    }

    public InventoryCommandListener(@Lazy UserService userService) {
        this.userService = userService;
        logger.info("InventoryCommandListener initialized");
    }

    /**
     * Register this listener with the JDA instance.
     * Can be called by DiscordConfig after JDA is initialized.
     */
    public void registerWithJDA(JDA jda) {
        if (jda != null && !isRegistered) {
            this.jdaInstance = jda;
            jda.addEventListener(this);
            this.isRegistered = true;
            logger.debug("InventoryCommandListener registered with JDA");
        }
    }

    /**
     * Clean up method called before bean destruction.
     */
    @PreDestroy
    public void cleanup() {
        logger.debug("InventoryCommandListener cleanup started");
        if (isRegistered && jdaInstance != null) {
            try {
                jdaInstance.removeEventListener(this);
                logger.info("InventoryCommandListener successfully unregistered from JDA");
            } catch (Exception e) {
                logger.warn("Error while unregistering InventoryCommandListener: {}", e.getMessage());
            }
            isRegistered = false;
            jdaInstance = null;
        }
        logger.debug("InventoryCommandListener cleanup completed");
    }

    @Override
    @Transactional(readOnly = true) // Add transactionality here to ensure lazy loading works
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("inventory")) {
            return; // Not our command
        }

        // Defer reply ephemerally
        event.deferReply(true).queue();
        String userId = event.getUser().getId();
        logger.info("User {} requested /inventory", userId);

        try {
            // Fetch user and their inventory
            User user = userService.getUserById(userId);
            if (user == null) {
                logger.warn("User {} not found in database for /inventory", userId);
                event.getHook().editOriginal("Could not find your account. Please log in to the web application first.").queue();
                return;
            }

            // Access inventory (should work due to @Transactional)
            Set<Shop> inventorySet = user.getInventory();
            List<Shop> inventoryList = new ArrayList<>(inventorySet);

            // Sort inventory items (e.g., by name)
            inventoryList.sort(Comparator.comparing(Shop::getName));

            if (inventoryList.isEmpty()) {
                logger.debug("User {} has an empty inventory", userId);
                EmbedBuilder embed = new EmbedBuilder()
                        .setTitle("Your Inventory")
                        .setAuthor(event.getUser().getName(), null, event.getUser().getEffectiveAvatarUrl())
                        .setColor(EMBED_COLOR)
                        .setDescription("You don't own any items yet. Visit the `/shop`!");
                event.getHook().editOriginalEmbeds(embed.build()).queue();
                return;
            }

            // Pagination
            int totalPages = (int) Math.ceil((double) inventoryList.size() / PAGE_SIZE);
            int currentPage = 1;

            // Build initial embed and buttons
            MessageEmbed embed = buildInventoryEmbed(inventoryList, currentPage, totalPages, event.getUser());
            Button prevButton = Button.secondary("inventory_prev:" + userId + ":" + currentPage, "◀️").withDisabled(true);
            Button pageIndicator = Button.secondary("inventory_page_indicator", "1/" + totalPages).withDisabled(true);
            Button nextButton = Button.secondary("inventory_next:" + userId + ":" + currentPage, "▶️").withDisabled(totalPages <= 1);

            // Send response
            event.getHook().editOriginalEmbeds(embed)
                    .setActionRow(prevButton, pageIndicator, nextButton)
                    .queue();

        } catch (Exception e) {
            logger.error("Error processing /inventory command for user {}", userId, e);
            event.getHook().editOriginal("An error occurred while fetching your inventory.").queue();
        }
    }

    @Override
    @Transactional(readOnly = true) // Also needed for fetching user inventory again
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String componentId = event.getComponentId();
        if (!componentId.startsWith("inventory_")) {
            return; // Not our button
        }

        // Defer edit immediately
        event.deferEdit().queue();

        String[] parts = componentId.split(":");
        if (parts.length != 3) {
            logger.warn("Invalid inventory button ID format: {}", componentId);
            event.getHook().editOriginal("Invalid button interaction.").queue();
            return;
        }

        String action = parts[0];
        String originalUserId = parts[1];
        int currentPage;
        try {
            currentPage = Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            logger.error("Error parsing page number from button ID: {}", componentId, e);
            event.getHook().editOriginal("Invalid button configuration.").queue();
            return;
        }

        // Security check: Only the original command user can paginate
        if (!event.getUser().getId().equals(originalUserId)) {
            event.reply("You cannot interact with this inventory display.").setEphemeral(true).queue();
            return;
        }

        logger.debug("Inventory pagination: User {}, Action {}, CurrentPage {}", originalUserId, action, currentPage);

        try {
            // Fetch user and inventory again
            User user = userService.getUserById(originalUserId);
            if (user == null) {
                // Should ideally not happen if they could trigger the command initially
                logger.error("User {} not found during inventory pagination", originalUserId);
                event.getHook().editOriginal("Could not find your account data.").queue();
                return;
            }

            Set<Shop> inventorySet = user.getInventory();
            List<Shop> inventoryList = new ArrayList<>(inventorySet);
            inventoryList.sort(Comparator.comparing(Shop::getName)); // Ensure consistent sorting

            if (inventoryList.isEmpty()) {
                // Should also not happen if pagination buttons exist, but handle defensively
                EmbedBuilder embed = new EmbedBuilder()
                        .setTitle("Your Inventory")
                        .setAuthor(event.getUser().getName(), null, event.getUser().getEffectiveAvatarUrl())
                        .setColor(EMBED_COLOR)
                        .setDescription("Your inventory appears empty now.");
                event.getHook().editOriginalEmbeds(embed.build()).setComponents().queue(); // Remove buttons
                return;
            }

            int totalPages = (int) Math.ceil((double) inventoryList.size() / PAGE_SIZE);
            int targetPage = currentPage;

            if (action.equals("inventory_prev")) {
                targetPage = Math.max(1, currentPage - 1);
            } else if (action.equals("inventory_next")) {
                targetPage = Math.min(totalPages, currentPage + 1);
            }

            // Build updated embed and buttons
            MessageEmbed newEmbed = buildInventoryEmbed(inventoryList, targetPage, totalPages, event.getUser());
            Button prevButton = Button.secondary("inventory_prev:" + originalUserId + ":" + targetPage, "◀️").withDisabled(targetPage <= 1);
            Button pageIndicator = Button.secondary("inventory_page_indicator", targetPage + "/" + totalPages).withDisabled(true);
            Button nextButton = Button.secondary("inventory_next:" + originalUserId + ":" + targetPage, "▶️").withDisabled(targetPage >= totalPages);

            // Update the original message
            event.getHook().editOriginalEmbeds(newEmbed)
                    .setActionRow(prevButton, pageIndicator, nextButton)
                    .queue();

        } catch (Exception e) {
            logger.error("Error processing inventory pagination for user {}", originalUserId, e);
            event.getHook().editOriginal("An error occurred while updating your inventory display.").queue();
        }
    }

    /**
     * Builds a Discord embed for displaying a page of the user's inventory.
     */
    private MessageEmbed buildInventoryEmbed(List<Shop> items, int page, int totalPages, net.dv8tion.jda.api.entities.User discordUser) {
        // Calculate start and end indices for the current page
        int startIndex = (page - 1) * PAGE_SIZE;
        int endIndex = Math.min(startIndex + PAGE_SIZE, items.size());
        
        // Get sublist for current page
        List<Shop> pageItems = items.subList(startIndex, endIndex);
        
        // Build the embed
        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("Your Inventory");
        embed.setAuthor(discordUser.getName(), null, discordUser.getEffectiveAvatarUrl());
        embed.setColor(EMBED_COLOR);
        
        // Build inventory content
        StringBuilder inventoryContent = new StringBuilder();
        
        if (items.isEmpty()) {
            inventoryContent.append("You don't own any items yet. Visit the `/shop`!");
        } else if (pageItems.isEmpty()) {
             inventoryContent.append("No items on this page."); // Should not happen with correct pagination logic
        } else {
            for (Shop item : pageItems) {
                // Special handling for USER_COLOR items with role IDs
                if (item.getCategory() == ShopCategory.USER_COLOR && 
                    item.getDiscordRoleId() != null && 
                    !item.getDiscordRoleId().isEmpty()) {
                    
                    // For USER_COLOR, display role ID and rarity role ID
                    inventoryContent.append("<@&").append(item.getDiscordRoleId()).append("> - ");
                    
                    // Add rarity role mention instead of text description
                    String rarityRoleId = RARITY_ROLE_IDS.get(item.getRarity());
                    if (rarityRoleId != null) {
                        inventoryContent.append("<@&").append(rarityRoleId).append(">");
                    } else {
                        // Fallback if rarity role isn't mapped
                        inventoryContent.append(formatRarityLabel(item.getRarity()));
                    }
                } else {
                    // Regular item with name
                    inventoryContent.append("**").append(item.getName()).append("** - ");
                    
                    // Add category and rarity for non-USER_COLOR items
                    inventoryContent.append(formatCategoryDisplay(item.getCategory() != null ? item.getCategory().name() : "Unknown"));
                    inventoryContent.append(" (").append(formatRarityLabel(item.getRarity())).append(")");
                }
                inventoryContent.append("\n");
            }
        }

        embed.addField("", inventoryContent.toString(), false); // Use empty field name for cleaner look

        // Footer with only total count (no pagination info)
        embed.setFooter(String.format("Total Items: %d", items.size()), null);

        return embed.build();
    }

    /**
     * Format a category string for display (capitalize first letter of each word)
     * Adapted from ShopCommandListener.
     */
    private String formatCategoryDisplay(String category) {
        if (category == null || category.isEmpty()) {
            return "Miscellaneous";
        }

        // Custom mappings like in InventoryPage.tsx
        switch (category) {
            case "USER_COLOR": return "Nameplate";
            case "LISTING": return "Listing Color";
            case "ACCENT": return "Profile Accent";
            // Add other custom mappings if needed
        }

        // General formatting for other categories
        String formatted = category.replace('_', ' ').toLowerCase();
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = true;

        for (char c : formatted.toCharArray()) {
            if (Character.isWhitespace(c)) {
                capitalizeNext = true;
                result.append(c);
            } else if (capitalizeNext) {
                result.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    /**
     * Format a rarity enum value for display.
     * Adapted from ShopCommandListener.
     */
    private String formatRarityLabel(ItemRarity rarity) {
        if (rarity == null) {
            return "Common"; // Default if rarity is somehow null
        }
        String rarityName = rarity.name();
        // Capitalize first letter, lowercase the rest
        return rarityName.charAt(0) + rarityName.substring(1).toLowerCase();
    }
} 