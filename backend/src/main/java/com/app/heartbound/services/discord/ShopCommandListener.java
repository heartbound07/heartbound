package com.app.heartbound.services.discord;

import com.app.heartbound.dto.shop.ShopDTO;
import com.app.heartbound.enums.ItemRarity;
import com.app.heartbound.enums.ShopCategory;
import com.app.heartbound.services.shop.ShopService;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ShopCommandListener extends ListenerAdapter {
    
    private static final Logger logger = LoggerFactory.getLogger(ShopCommandListener.class);
    private static final int PAGE_SIZE = 10; // Show 10 shop items per page
    private static final Color EMBED_COLOR = new Color(88, 101, 242); // Discord Blurple
    
    // Define rarity colors similar to frontend
    private static final Map<String, Color> RARITY_COLORS = new HashMap<>();
    static {
        RARITY_COLORS.put("LEGENDARY", new Color(255, 215, 0)); // Gold/Yellow
        RARITY_COLORS.put("EPIC", new Color(160, 32, 240));     // Purple
        RARITY_COLORS.put("RARE", new Color(0, 123, 255));      // Blue
        RARITY_COLORS.put("UNCOMMON", new Color(40, 167, 69));  // Green
        RARITY_COLORS.put("COMMON", new Color(108, 117, 125));  // Grey
    }

    private final ShopService shopService;
    
    @Value("${frontend.base.url}")
    private String frontendBaseUrl;

    @Autowired
    public ShopCommandListener(@Lazy ShopService shopService) {
        this.shopService = shopService;
        logger.info("ShopCommandListener initialized");
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("shop")) {
            return; // Not our command
        }

        logger.debug("Shop command received from user: {}", event.getUser().getId());
        
        // Acknowledge the interaction quickly to prevent timeout
        event.deferReply().queue();
        
        try {
            // Fetch all available shop items
            List<ShopDTO> shopItems = shopService.getAvailableShopItems(null, null);
            
            if (shopItems == null || shopItems.isEmpty()) {
                // Handle empty shop
                event.getHook().sendMessage("The shop is currently empty. Check back later for new items!").queue();
                return;
            }
            
            // Calculate total pages
            int totalPages = (int) Math.ceil((double) shopItems.size() / PAGE_SIZE);
            int currentPage = 1; // Start with page 1
            
            // Build the initial embed for page 1
            Guild guild = event.getGuild(); // Get the guild from the event
            MessageEmbed embed = buildShopEmbed(shopItems, currentPage, totalPages, guild);
            
            // Create pagination buttons
            Button prevButton = Button.secondary("shop_prev:1", "◀️").withDisabled(true); // Disabled on page 1
            Button pageIndicator = Button.secondary("shop_page_indicator", "1/" + totalPages).withDisabled(true);
            Button nextButton = Button.secondary("shop_next:1", "▶️").withDisabled(totalPages <= 1);
            
            // Send the initial response with buttons
            event.getHook().sendMessageEmbeds(embed)
                .setActionRow(prevButton, pageIndicator, nextButton)
                .queue(
                    success -> logger.debug("Shop items displayed successfully"),
                    error -> logger.error("Failed to send shop items", error)
                );
            
        } catch (Exception e) {
            logger.error("Error processing shop command", e);
            event.getHook().sendMessage("An error occurred while fetching the shop data.").queue();
        }
    }
    
    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String componentId = event.getComponentId();
        
        if (!componentId.startsWith("shop_")) {
            return; // Not our button
        }
        
        // Acknowledge the button click immediately
        event.deferEdit().queue();
        
        try {
            // Extract the current page from the button ID
            String[] parts = componentId.split(":");
            String action = parts[0]; // "shop_prev" or "shop_next"
            int currentPage = Integer.parseInt(parts[1]);
            
            // Determine the target page based on the button clicked
            int tempTargetPage = currentPage;
            if (action.equals("shop_prev")) {
                tempTargetPage = currentPage - 1;
            } else if (action.equals("shop_next")) {
                tempTargetPage = currentPage + 1;
            }
            
            // Fetch all available shop items
            List<ShopDTO> shopItems = shopService.getAvailableShopItems(null, null);
            
            if (shopItems == null || shopItems.isEmpty()) {
                // Handle empty shop
                event.getHook().sendMessage("The shop is currently empty. Check back later for new items!").queue();
                return;
            }
            
            // Calculate total pages
            int totalPages = (int) Math.ceil((double) shopItems.size() / PAGE_SIZE);
            
            // Safety check for valid page number
            if (tempTargetPage < 1) tempTargetPage = 1;
            if (tempTargetPage > totalPages) tempTargetPage = totalPages;
            
            // Create final variable for use in lambda
            final int targetPage = tempTargetPage;
            
            // Build the new embed for the target page
            Guild guild = event.getGuild(); // Get the guild from the event
            MessageEmbed embed = buildShopEmbed(shopItems, targetPage, totalPages, guild);
            
            // Create updated pagination buttons
            Button prevButton = Button.secondary("shop_prev:" + targetPage, "◀️").withDisabled(targetPage <= 1);
            Button pageIndicator = Button.secondary("shop_page_indicator", targetPage + "/" + totalPages).withDisabled(true);
            Button nextButton = Button.secondary("shop_next:" + targetPage, "▶️").withDisabled(targetPage >= totalPages);
            
            // Update the original message
            event.getHook().editOriginalEmbeds(embed)
                .setActionRow(prevButton, pageIndicator, nextButton)
                .queue();
            
        } catch (Exception e) {
            logger.error("Error processing shop pagination", e);
            // The interaction acknowledgment already happened, so just log the error
        }
    }
    
    /**
     * Builds a Discord embed for displaying a page of shop items.
     *
     * @param items The full list of shop items
     * @param page The current page (1-based)
     * @param totalPages The total number of pages
     * @param guild The Discord guild (server)
     * @return A MessageEmbed containing the formatted shop items
     */
    private MessageEmbed buildShopEmbed(List<ShopDTO> items, int page, int totalPages, Guild guild) {
        // Calculate start and end indices for the current page
        int startIndex = (page - 1) * PAGE_SIZE;
        int endIndex = Math.min(startIndex + PAGE_SIZE, items.size());
        
        // Get sublist for current page
        List<ShopDTO> pageItems = items.subList(startIndex, endIndex);
        
        // Build the embed
        EmbedBuilder embed = new EmbedBuilder();
        
        // Set server icon and name as the embed's author
        if (guild != null) {
            embed.setAuthor(
                guild.getName(), 
                null, 
                guild.getIconUrl() != null ? guild.getIconUrl() : null
            );
        }
        
        // Set "Shop" as the title
        embed.setTitle("Shop");
        embed.setColor(EMBED_COLOR);
        
        // Create clickable link to the web shop
        String shopUrl = frontendBaseUrl + "/dashboard/shop";
        embed.setDescription("Browse and purchase items in the [web shop](" + shopUrl + ")");
        
        // Build a consolidated shop items list
        StringBuilder shopContent = new StringBuilder();
        
        for (ShopDTO item : pageItems) {
            // Special handling for USER_COLOR items with role IDs
            if (item.getCategory() != null && 
                item.getCategory() == ShopCategory.USER_COLOR && 
                item.getDiscordRoleId() != null && 
                !item.getDiscordRoleId().isEmpty()) {
                
                // Add role mention with price - remove coin emoji entirely
                shopContent.append("<@&").append(item.getDiscordRoleId()).append("> - ");
                shopContent.append(item.getPrice()).append("\n");
            } else {
                // Regular item with name and price - remove coin emoji entirely
                shopContent.append("**").append(item.getName()).append("** - ");
                shopContent.append(item.getPrice()).append("\n");
            }
        }
        
        // Add all shop items as a single field with empty name
        embed.addField("", shopContent.toString(), false);
         
        return embed.build();
    }
    
    /**
     * Format a category string for display (capitalize first letter of each word)
     */
    private String formatCategoryDisplay(String category) {
        if (category == null || category.isEmpty()) {
            return "Miscellaneous";
        }
        
        // Replace underscores with spaces and lowercase everything
        String formatted = category.replace('_', ' ').toLowerCase();
        
        // Capitalize first letter of each word
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
     * Format a rarity enum value for display
     */
    private String formatRarityLabel(ItemRarity rarity) {
        if (rarity == null) {
            return "Common";
        }
        
        String rarityName = rarity.name();
        return rarityName.charAt(0) + rarityName.substring(1).toLowerCase();
    }
} 