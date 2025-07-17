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
    private static final int PAGE_SIZE = 10; // Show 5 shop items per page
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
            
            // Fetch featured and daily items separately
            List<ShopDTO> featuredItems = shopService.getFeaturedItems(userId)
                .stream()
                .filter(item -> !item.isOwned())
                .collect(Collectors.toList());
            
            List<ShopDTO> dailyItems = shopService.getDailyItems(userId)
                .stream()
                .filter(item -> !item.isOwned())
                .collect(Collectors.toList());

            // Combine lists for pagination
            List<ShopDTO> allItems = new ArrayList<>();
            allItems.addAll(featuredItems);
            allItems.addAll(dailyItems);
            
            final int featuredItemsCount = featuredItems.size();
            
            if (allItems.isEmpty()) {
                // Handle empty shop
                event.getHook().sendMessage("The shop is currently empty or you've purchased all available items!").queue();
                return;
            }
            
            // Calculate total pages
            int totalPages = (int) Math.ceil((double) allItems.size() / PAGE_SIZE);
            int currentPage = 1; // Start with page 1

            // Determine section title for the first page
            int startIndex = (currentPage - 1) * PAGE_SIZE;
            String sectionTitle = (startIndex < featuredItemsCount) ? "Featured Items" : "Daily Items";
            
            // Build the initial embed for page 1
            Guild guild = event.getGuild(); // Get the guild from the event
            String userName = event.getUser().getName(); // Get user's name
            MessageEmbed embed = buildShopEmbed(allItems, currentPage, totalPages, guild, userId, userName, sectionTitle);
            
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
            
            // Fetch featured and daily items separately
            List<ShopDTO> featuredItems = shopService.getFeaturedItems(userId)
                .stream()
                .filter(item -> !item.isOwned())
                .collect(Collectors.toList());
            
            List<ShopDTO> dailyItems = shopService.getDailyItems(userId)
                .stream()
                .filter(item -> !item.isOwned())
                .collect(Collectors.toList());

            // Combine lists for pagination
            List<ShopDTO> allItems = new ArrayList<>();
            allItems.addAll(featuredItems);
            allItems.addAll(dailyItems);
            
            final int featuredItemsCount = featuredItems.size();
            
            if (allItems.isEmpty()) {
                // Handle empty shop
                event.getHook().editOriginal("The shop is currently empty or you've purchased all available items!").queue();
                return;
            }
            
            // Calculate total pages
            int totalPages = (int) Math.ceil((double) allItems.size() / PAGE_SIZE);
            
            // Safety check for valid page number
            if (tempTargetPage < 1) tempTargetPage = 1;
            if (tempTargetPage > totalPages) tempTargetPage = totalPages;
            
            // Create final variable for use in lambda
            final int targetPage = tempTargetPage;
            
            // Determine section title based on the new page's content
            int startIndex = (targetPage - 1) * PAGE_SIZE;
            String sectionTitle = (startIndex < featuredItemsCount) ? "Featured Items" : "Daily Items";
            
            // Build the new embed for the target page
            Guild guild = event.getGuild(); // Get the guild from the event
            String userName = event.getUser().getName(); // Get user's name
            MessageEmbed embed = buildShopEmbed(allItems, targetPage, totalPages, guild, userId, userName, sectionTitle);
            
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
     * Builds a Discord embed for displaying a page of shop items.
     *
     * @param items The full list of shop items
     * @param page The current page (1-based)
     * @param totalPages The total number of pages
     * @param guild The Discord guild (server)
     * @param userId The ID of the user viewing the shop
     * @param userName The display name of the user viewing the shop
     * @param sectionTitle The title for the shop section (e.g., "Featured Items")
     * @return A MessageEmbed containing the formatted shop items
     */
    private MessageEmbed buildShopEmbed(List<ShopDTO> items, int page, int totalPages, Guild guild, String userId, String userName, String sectionTitle) {
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
        String shopUrl = frontendBaseUrl + "/shop";
        embed.setDescription("Browse and purchase items in the [shop](" + shopUrl + ")");
        
        // Build a consolidated shop items list
        StringBuilder shopContent = new StringBuilder();
        
        for (ShopDTO item : pageItems) {
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
        
        // Add all shop items as a single field with a dynamic title
        embed.addField(sectionTitle, shopContent.toString(), false);
        
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