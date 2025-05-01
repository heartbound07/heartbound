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
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional; // Import for potential future use
import org.springframework.beans.factory.annotation.Value;

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

    // Add frontend base URL from application.properties
    @Value("${frontend.base.url}")
    private String frontendBaseUrl;

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

    private enum SortMode {
        DEFAULT, // Alphabetical by name
        RARITY_DESC, // Legendary to Common
        RARITY_ASC   // Common to Legendary
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

        // Defer reply publicly (removed 'true' parameter that made it ephemeral)
        event.deferReply().queue();
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
            SortMode sortMode = SortMode.DEFAULT; // Default sort mode

            // Build initial embed and buttons
            MessageEmbed embed = buildInventoryEmbed(inventoryList, currentPage, totalPages, event.getUser());
            
            // Filter button - shows current sort mode
            Button filterButton = Button.secondary("inventory_filter:" + userId + ":" + currentPage + ":" + sortMode, "Filter: A-Z");
            
            // Previous, page indicator, next buttons
            Button prevButton = Button.secondary("inventory_prev:" + userId + ":" + currentPage + ":" + sortMode, "◀️").withDisabled(true);
            Button pageIndicator = Button.secondary("inventory_page_indicator", "1/" + totalPages).withDisabled(true);
            Button nextButton = Button.secondary("inventory_next:" + userId + ":" + currentPage + ":" + sortMode, "▶️").withDisabled(totalPages <= 1);

            // Send response with pagination buttons in first row, filter button in second row
            event.getHook().editOriginalEmbeds(embed)
                    .setComponents(
                        ActionRow.of(prevButton, pageIndicator, nextButton),
                        ActionRow.of(filterButton)
                    )
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
        if (parts.length != 4) {
            logger.warn("Invalid inventory button ID format: {}", componentId);
            event.getHook().editOriginal("Invalid button interaction.").queue();
            return;
        }

        String action = parts[0];
        String originalUserId = parts[1];
        int currentPage;
        SortMode sortMode;
        
        try {
            currentPage = Integer.parseInt(parts[2]);
            sortMode = SortMode.valueOf(parts[3]);
        } catch (IllegalArgumentException e) {
            logger.error("Error parsing page number or sort mode from button ID: {}", componentId, e);
            event.getHook().editOriginal("Invalid button configuration.").queue();
            return;
        }

        // Security check: Only the original command user can interact
        if (!event.getUser().getId().equals(originalUserId)) {
            event.reply("You cannot interact with this inventory display.").setEphemeral(true).queue();
            return;
        }

        logger.debug("Inventory interaction: User {}, Action {}, CurrentPage {}, SortMode {}", 
                    originalUserId, action, currentPage, sortMode);

        try {
            // Fetch user and inventory again
            User user = userService.getUserById(originalUserId);
            if (user == null) {
                logger.error("User {} not found during inventory interaction", originalUserId);
                event.getHook().editOriginal("Could not find your account data.").queue();
                return;
            }

            Set<Shop> inventorySet = user.getInventory();
            List<Shop> inventoryList = new ArrayList<>(inventorySet);
            
            // Apply sorting based on sortMode
            applySorting(inventoryList, sortMode);

            if (inventoryList.isEmpty()) {
                // Handle empty inventory...
                return;
            }

            int totalPages = (int) Math.ceil((double) inventoryList.size() / PAGE_SIZE);
            int targetPage = currentPage;
            SortMode targetSortMode = sortMode;

            // Handle button actions
            if (action.equals("inventory_prev")) {
                targetPage = Math.max(1, currentPage - 1);
            } else if (action.equals("inventory_next")) {
                targetPage = Math.min(totalPages, currentPage + 1);
            } else if (action.equals("inventory_filter")) {
                // Cycle through sort modes
                targetSortMode = getNextSortMode(sortMode);
                // Reset to page 1 when changing sort mode
                targetPage = 1;
                // Re-sort the list with the new mode
                applySorting(inventoryList, targetSortMode);
            }

            // Build updated embed and buttons
            MessageEmbed newEmbed = buildInventoryEmbed(inventoryList, targetPage, totalPages, event.getUser());
            
            // Filter button label based on current sort mode
            String filterLabel = getFilterLabel(targetSortMode);
            Button filterButton = Button.secondary("inventory_filter:" + originalUserId + ":" + targetPage + ":" + targetSortMode, filterLabel);
            
            Button prevButton = Button.secondary("inventory_prev:" + originalUserId + ":" + targetPage + ":" + targetSortMode, "◀️")
                    .withDisabled(targetPage <= 1);
            Button pageIndicator = Button.secondary("inventory_page_indicator", targetPage + "/" + totalPages)
                    .withDisabled(true);
            Button nextButton = Button.secondary("inventory_next:" + originalUserId + ":" + targetPage + ":" + targetSortMode, "▶️")
                    .withDisabled(targetPage >= totalPages);

            // Update the original message with two rows of buttons
            event.getHook().editOriginalEmbeds(newEmbed)
                    .setComponents(
                        ActionRow.of(prevButton, pageIndicator, nextButton),
                        ActionRow.of(filterButton)
                    )
                    .queue();

        } catch (Exception e) {
            logger.error("Error processing inventory interaction for user {}", originalUserId, e);
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
        
        // Add description with clickable link to web inventory
        String inventoryUrl = frontendBaseUrl + "/dashboard/inventory";
        embed.setDescription("To equip an item go to your [inventory](" + inventoryUrl + ")");
        
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
                    inventoryContent.append("<@&").append(item.getDiscordRoleId()).append("> | ");
                    
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
                    inventoryContent.append("**").append(item.getName()).append("** | ");
                    
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

    /**
     * Get the next sort mode in the cycle
     */
    private SortMode getNextSortMode(SortMode currentMode) {
        switch (currentMode) {
            case DEFAULT:
                return SortMode.RARITY_DESC;
            case RARITY_DESC:
                return SortMode.RARITY_ASC;
            case RARITY_ASC:
            default:
                return SortMode.DEFAULT;
        }
    }

    /**
     * Get the filter button label based on the current sort mode
     */
    private String getFilterLabel(SortMode sortMode) {
        switch (sortMode) {
            case RARITY_DESC:
                return "Filter: ⭐→⭐⭐⭐"; // Legendary to Common
            case RARITY_ASC:
                return "Filter: ⭐⭐⭐→⭐"; // Common to Legendary
            case DEFAULT:
            default:
                return "Filter: A-Z"; // Alphabetical
        }
    }

    /**
     * Apply the appropriate sorting to the inventory list
     */
    private void applySorting(List<Shop> items, SortMode sortMode) {
        switch (sortMode) {
            case RARITY_DESC:
                // Sort by rarity (highest to lowest)
                items.sort((a, b) -> {
                    if (a.getRarity() == null) return 1;
                    if (b.getRarity() == null) return -1;
                    // Legendary is 0, Common is 4 in enum ordinal - so we need to reverse
                    return Integer.compare(a.getRarity().ordinal(), b.getRarity().ordinal());
                });
                break;
            case RARITY_ASC:
                // Sort by rarity (lowest to highest)
                items.sort((a, b) -> {
                    if (a.getRarity() == null) return -1;
                    if (b.getRarity() == null) return 1;
                    return Integer.compare(b.getRarity().ordinal(), a.getRarity().ordinal());
                });
                break;
            case DEFAULT:
            default:
                // Sort alphabetically by name (default)
                items.sort(Comparator.comparing(Shop::getName));
                break;
        }
    }
} 