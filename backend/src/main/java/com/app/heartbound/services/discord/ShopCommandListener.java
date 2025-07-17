package com.app.heartbound.services.discord;

import com.app.heartbound.dto.shop.ShopDTO;
import com.app.heartbound.enums.ShopCategory;
import com.app.heartbound.services.shop.ShopService;
import com.app.heartbound.services.UserService;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.annotation.PreDestroy;
import net.dv8tion.jda.api.JDA;

@Component
public class ShopCommandListener extends ListenerAdapter {
    
    private static final Logger logger = LoggerFactory.getLogger(ShopCommandListener.class);
    private static final int PAGE_SIZE = 5; // Show 5 shop items per page section
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
    private final UserService userService;
    
    @Value("${frontend.base.url}")
    private String frontendBaseUrl;
    
    // Add field to track registration status
    private boolean isRegistered = false;
    private JDA jdaInstance;

    public ShopCommandListener(@Lazy ShopService shopService, UserService userService) {
        this.shopService = shopService;
        this.userService = userService;
        logger.info("ShopCommandListener initialized");
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
            logger.debug("ShopCommandListener registered with JDA");
        }
    }
    
    /**
     * Clean up method called before bean destruction.
     * Ensures this listener is removed from JDA to prevent events during shutdown.
     */
    @PreDestroy
    public void cleanup() {
        logger.debug("ShopCommandListener cleanup started");
        if (isRegistered && jdaInstance != null) {
            try {
                jdaInstance.removeEventListener(this);
                logger.info("ShopCommandListener successfully unregistered from JDA");
            } catch (Exception e) {
                logger.warn("Error while unregistering ShopCommandListener: {}", e.getMessage());
            }
            isRegistered = false;
            jdaInstance = null;
        }
        logger.debug("ShopCommandListener cleanup completed");
    }

    @Override
    public void onSlashCommandInteraction(@Nonnull SlashCommandInteractionEvent event) {
        if (!event.getName().equals("shop")) {
            return; // Not our command
        }

        logger.debug("Shop command received from user: {}", event.getUser().getId());
        
        // Acknowledge the interaction immediately
        event.deferReply().queue();
        
        try {
            // Get the user ID for ownership checking
            String userId = event.getUser().getId();
            
            // Fetch featured and daily items, filtering out owned items
            List<ShopDTO> featuredItems = shopService.getFeaturedItems(userId)
                .stream().filter(item -> !item.isOwned()).collect(Collectors.toList());
            List<ShopDTO> dailyItems = shopService.getDailyItems(userId)
                .stream().filter(item -> !item.isOwned()).collect(Collectors.toList());

            if (featuredItems.isEmpty() && dailyItems.isEmpty()) {
                // Handle empty shop
                event.getHook().sendMessage("The shop is currently empty or you've purchased all available items!").queue();
                return;
            }
            
            // Calculate total pages based on the longer of the two lists
            int totalPagesFeatured = (int) Math.ceil((double) featuredItems.size() / PAGE_SIZE);
            int totalPagesDaily = (int) Math.ceil((double) dailyItems.size() / PAGE_SIZE);
            int totalPages = Math.max(totalPagesFeatured, totalPagesDaily);
            
            int currentPage = 1; // Start with page 1
            
            // Get the items for the current page from both lists
            List<ShopDTO> featuredPageItems = getPageSublist(featuredItems, currentPage);
            List<ShopDTO> dailyPageItems = getPageSublist(dailyItems, currentPage);
            
            // Build the initial embed for page 1
            Guild guild = event.getGuild(); // Get the guild from the event
            String userName = event.getUser().getName(); // Get user's name
            MessageEmbed embed = buildShopEmbed(featuredPageItems, dailyPageItems, currentPage, totalPages, guild, userId, userName);
            
            // Create pagination buttons - include original user ID in button IDs
            Button prevButton = Button.secondary("shop_prev:" + userId + ":" + currentPage, "◀️").withDisabled(true); // Disabled on page 1
            Button pageIndicator = Button.secondary("shop_page_indicator", "1/" + totalPages).withDisabled(true);
            Button nextButton = Button.secondary("shop_next:" + userId + ":" + currentPage, "▶️").withDisabled(totalPages <= 1);
            
            // Send the response with buttons
            event.getHook().sendMessageEmbeds(embed)
                .addActionRow(prevButton, pageIndicator, nextButton)
                .queue(success -> logger.debug("Shop displayed successfully"),
                      error -> logger.error("Failed to send shop", error));
            
        } catch (Exception e) {
            logger.error("Error processing shop command", e);
            try {
                event.getHook().sendMessage("An error occurred while fetching the shop data.").queue();
            } catch (Exception ignored) {
                logger.error("Failed to send error message", ignored);
            }
        }
    }
    
    @Override
    public void onButtonInteraction(@Nonnull ButtonInteractionEvent event) {
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
            String originalUserId = parts[1]; // User ID stored in the button
            int currentPage = Integer.parseInt(parts[2]);
            
            // Only allow the original user to interact with buttons
            if (!event.getUser().getId().equals(originalUserId)) {
                // Cancel the deferred edit
                event.getHook().deleteOriginal().queue();
                
                // Send an ephemeral message that only the clicking user can see
                event.reply("This shop menu belongs to someone else. Please use your own /shop command.")
                    .setEphemeral(true) // Makes the message private to the user
                    .queue();
                return;
            }
            
            // Determine the target page based on the button clicked
            int tempTargetPage = currentPage;
            if (action.equals("shop_prev")) {
                tempTargetPage = currentPage - 1;
            } else if (action.equals("shop_next")) {
                tempTargetPage = currentPage + 1;
            }
            
            // Get the user ID for ownership checking
            String userId = event.getUser().getId();
            
            // Fetch and filter items for both sections
            List<ShopDTO> featuredItems = shopService.getFeaturedItems(userId)
                .stream().filter(item -> !item.isOwned()).collect(Collectors.toList());
            List<ShopDTO> dailyItems = shopService.getDailyItems(userId)
                .stream().filter(item -> !item.isOwned()).collect(Collectors.toList());
            
            if (featuredItems.isEmpty() && dailyItems.isEmpty()) {
                // Handle empty shop
                event.getHook().editOriginal("The shop is currently empty or you've purchased all available items!").queue();
                return;
            }
            
            // Calculate total pages based on the longer list
            int totalPagesFeatured = (int) Math.ceil((double) featuredItems.size() / PAGE_SIZE);
            int totalPagesDaily = (int) Math.ceil((double) dailyItems.size() / PAGE_SIZE);
            int totalPages = Math.max(totalPagesFeatured, totalPagesDaily);
            
            // Safety check for valid page number
            if (tempTargetPage < 1) tempTargetPage = 1;
            if (tempTargetPage > totalPages) tempTargetPage = totalPages;
            
            // Create final variable for use in lambda
            final int targetPage = tempTargetPage;
            
            // Get items for the target page from both lists
            List<ShopDTO> featuredPageItems = getPageSublist(featuredItems, targetPage);
            List<ShopDTO> dailyPageItems = getPageSublist(dailyItems, targetPage);
            
            // Build the new embed for the target page
            Guild guild = event.getGuild(); // Get the guild from the event
            String userName = event.getUser().getName(); // Get user's name
            MessageEmbed embed = buildShopEmbed(featuredPageItems, dailyPageItems, targetPage, totalPages, guild, userId, userName);
            
            // Create updated pagination buttons with original author ID
            Button prevButton = Button.secondary("shop_prev:" + originalUserId + ":" + targetPage, "◀️").withDisabled(targetPage <= 1);
            Button pageIndicator = Button.secondary("shop_page_indicator", targetPage + "/" + totalPages).withDisabled(true);
            Button nextButton = Button.secondary("shop_next:" + originalUserId + ":" + targetPage, "▶️").withDisabled(targetPage >= totalPages);
            
            // Update the original message
            event.getHook().editOriginalEmbeds(embed)
                .setActionRow(prevButton, pageIndicator, nextButton)
                .queue(success -> logger.debug("Pagination updated to page {}", targetPage),
                      error -> logger.error("Failed to update pagination", error));
            
        } catch (NumberFormatException e) {
            logger.error("Error parsing page number from button ID", e);
            event.reply("Invalid button configuration.").setEphemeral(true).queue();
        } catch (Exception e) {
            logger.error("Error processing shop pagination", e);
            event.reply("An error occurred while updating the shop.").setEphemeral(true).queue();
        }
    }
    
    /**
     * Extracts a sublist of items for a specific page.
     * @param items The full list of items
     * @param page The page number (1-based)
     * @return A sublist of items for the given page
     */
    private List<ShopDTO> getPageSublist(List<ShopDTO> items, int page) {
        int startIndex = (page - 1) * PAGE_SIZE;
        if (startIndex >= items.size()) {
            return new ArrayList<>(); // Return empty list if page is out of bounds
        }
        int endIndex = Math.min(startIndex + PAGE_SIZE, items.size());
        return items.subList(startIndex, endIndex);
    }
    
    /**
     * Builds the string content for a list of shop items.
     * @param items The list of items to format
     * @return A formatted string for the embed field
     */
    private String buildShopItemContent(List<ShopDTO> items) {
        StringBuilder shopContent = new StringBuilder();
        for (ShopDTO item : items) {
            // Special handling for USER_COLOR items with role IDs
            if (item.getCategory() != null && 
                item.getCategory() == ShopCategory.USER_COLOR && 
                item.getDiscordRoleId() != null && 
                !item.getDiscordRoleId().isEmpty()) {
                
                // Add role mention with price
                shopContent.append("<@&").append(item.getDiscordRoleId()).append("> - ");
                shopContent.append(item.getPrice());
                shopContent.append("\n");
            } else {
                // Regular item with name and price
                shopContent.append("**").append(item.getName()).append("** - ");
                shopContent.append(item.getPrice());
                shopContent.append("\n");
            }
        }
        return shopContent.toString();
    }
    
    /**
     * Builds a Discord embed for displaying a page of shop items with separate sections.
     *
     * @param featuredItems The list of featured items for the current page
     * @param dailyItems The list of daily items for the current page
     * @param page The current page (1-based)
     * @param totalPages The total number of pages
     * @param guild The Discord guild (server)
     * @param userId The ID of the user viewing the shop
     * @param userName The display name of the user viewing the shop
     * @return A MessageEmbed containing the formatted shop items
     */
    private MessageEmbed buildShopEmbed(List<ShopDTO> featuredItems, List<ShopDTO> dailyItems, int page, int totalPages, Guild guild, String userId, String userName) {
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
        String shopUrl = frontendBaseUrl + "/shop";
        embed.setDescription("Browse and purchase items in the [shop](" + shopUrl + ")");
        
        // Add featured items field if there are any for the current page
        if (!featuredItems.isEmpty()) {
            embed.addField("__Featured Items__", buildShopItemContent(featuredItems), false);
        }
        
        // Add daily items field if there are any for the current page
        if (!dailyItems.isEmpty()) {
            embed.addField("__Daily Items__", buildShopItemContent(dailyItems), false);
        }
        
        // Add user's credits to footer
        try {
            // Get user's credits
            com.app.heartbound.entities.User user = userService.getUserById(userId);
            if (user != null) {
                String username = user.getUsername();
                Integer credits = user.getCredits();
                int userCredits = (credits != null) ? credits : 0;
                
                // Set footer with user's credits
                embed.setFooter(username + ", you have " + userCredits + " credits", null);
            }
        } catch (Exception e) {
            logger.debug("Could not retrieve credits info for user {}: {}", userId, e.getMessage());
            // If we can't get credits info, don't add a footer
        }

        return embed.build();
    }
} 