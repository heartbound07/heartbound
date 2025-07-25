package com.app.heartbound.services.discord;

import com.app.heartbound.dto.shop.UserInventoryItemDTO;
import com.app.heartbound.enums.ItemRarity;
import com.app.heartbound.enums.ShopCategory;
import com.app.heartbound.services.UserInventoryService;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import java.awt.*;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@Component
public class InventoryCommandListener extends ListenerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(InventoryCommandListener.class);
    private static final int PAGE_SIZE = 10;
    private static final Color EMBED_COLOR = new Color(88, 101, 242);

    private final UserInventoryService userInventoryService;

    @Value("${frontend.base.url}")
    private String frontendBaseUrl;

    private boolean isRegistered = false;
    private JDA jdaInstance;

    private static final Map<ItemRarity, String> RARITY_ROLE_IDS = new HashMap<>();
    static {
        RARITY_ROLE_IDS.put(ItemRarity.LEGENDARY, "1367568858797314118");
        RARITY_ROLE_IDS.put(ItemRarity.EPIC, "1367568919220719668");
        RARITY_ROLE_IDS.put(ItemRarity.RARE, "1367569009628938301");
        RARITY_ROLE_IDS.put(ItemRarity.UNCOMMON, "1367569085788848189");
        RARITY_ROLE_IDS.put(ItemRarity.COMMON, "1367569157469765662");
    }

    private enum SortMode {
        DEFAULT,
        RARITY_DESC,
        RARITY_ASC
    }

    public InventoryCommandListener(@Lazy UserInventoryService userInventoryService) {
        this.userInventoryService = userInventoryService;
        logger.info("InventoryCommandListener initialized");
    }

    public void registerWithJDA(JDA jda) {
        if (jda != null && !isRegistered) {
            this.jdaInstance = jda;
            jda.addEventListener(this);
            this.isRegistered = true;
            logger.debug("InventoryCommandListener registered with JDA");
        }
    }

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
    public void onSlashCommandInteraction(@Nonnull SlashCommandInteractionEvent event) {
        if (!event.getName().equals("inventory")) {
            return;
        }

        event.deferReply().queue();
        String userId = event.getUser().getId();
        logger.info("User {} requested /inventory", userId);

        try {
            List<UserInventoryItemDTO> inventoryList = userInventoryService.getUserInventory(userId);
            inventoryList.sort(Comparator.comparing(UserInventoryItemDTO::getName));

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

            int totalPages = (int) Math.ceil((double) inventoryList.size() / PAGE_SIZE);
            int currentPage = 1;
            SortMode sortMode = SortMode.DEFAULT;

            MessageEmbed embed = buildInventoryEmbed(inventoryList, currentPage, totalPages, event.getUser());
            
            Button filterButton = Button.secondary("inventory_filter:" + userId + ":" + currentPage + ":" + sortMode, "A-Z");
            Button prevButton = Button.secondary("inventory_prev:" + userId + ":" + currentPage + ":" + sortMode, "◀️").withDisabled(true);
            Button pageIndicator = Button.secondary("inventory_page_indicator", "1/" + totalPages).withDisabled(true);
            Button nextButton = Button.secondary("inventory_next:" + userId + ":" + currentPage + ":" + sortMode, "▶️").withDisabled(totalPages <= 1);

            event.getHook().editOriginalEmbeds(embed)
                    .setActionRow(filterButton, prevButton, pageIndicator, nextButton)
                    .queue();

        } catch (Exception e) {
            logger.error("Error processing /inventory command for user {}", userId, e);
            event.getHook().editOriginal("An error occurred while fetching your inventory.").queue();
        }
    }

    @Override
    public void onButtonInteraction(@Nonnull ButtonInteractionEvent event) {
        String componentId = event.getComponentId();
        if (!componentId.startsWith("inventory_")) {
            return;
        }

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

        if (!event.getUser().getId().equals(originalUserId)) {
            event.reply("You cannot interact with this inventory display.").setEphemeral(true).queue();
            return;
        }

        logger.debug("Inventory interaction: User {}, Action {}, CurrentPage {}, SortMode {}", 
                    originalUserId, action, currentPage, sortMode);

        try {
            List<UserInventoryItemDTO> inventoryList = userInventoryService.getUserInventory(originalUserId);
            
            applySorting(inventoryList, sortMode);

            if (inventoryList.isEmpty()) {
                return;
            }

            int totalPages = (int) Math.ceil((double) inventoryList.size() / PAGE_SIZE);
            int targetPage = currentPage;
            SortMode targetSortMode = sortMode;

            if (action.equals("inventory_prev")) {
                targetPage = Math.max(1, currentPage - 1);
            } else if (action.equals("inventory_next")) {
                targetPage = Math.min(totalPages, currentPage + 1);
            } else if (action.equals("inventory_filter")) {
                targetSortMode = getNextSortMode(sortMode);
                targetPage = 1;
                applySorting(inventoryList, targetSortMode);
            }

            MessageEmbed newEmbed = buildInventoryEmbed(inventoryList, targetPage, totalPages, event.getUser());
            
            String filterLabel = getFilterLabel(targetSortMode);
            Button filterButton = Button.secondary("inventory_filter:" + originalUserId + ":" + targetPage + ":" + targetSortMode, filterLabel);
            
            Button prevButton = Button.secondary("inventory_prev:" + originalUserId + ":" + targetPage + ":" + targetSortMode, "◀️")
                    .withDisabled(targetPage <= 1);
            Button pageIndicator = Button.secondary("inventory_page_indicator", targetPage + "/" + totalPages)
                    .withDisabled(true);
            Button nextButton = Button.secondary("inventory_next:" + originalUserId + ":" + targetPage + ":" + targetSortMode, "▶️")
                    .withDisabled(targetPage >= totalPages);

            event.getHook().editOriginalEmbeds(newEmbed)
                    .setActionRow(filterButton, prevButton, pageIndicator, nextButton)
                    .queue();

        } catch (Exception e) {
            logger.error("Error processing inventory interaction for user {}", originalUserId, e);
            event.getHook().editOriginal("An error occurred while updating your inventory display.").queue();
        }
    }

    private MessageEmbed buildInventoryEmbed(List<UserInventoryItemDTO> items, int page, int totalPages, net.dv8tion.jda.api.entities.User discordUser) {
        int startIndex = (page - 1) * PAGE_SIZE;
        int endIndex = Math.min(startIndex + PAGE_SIZE, items.size());
        
        List<UserInventoryItemDTO> pageItems = items.subList(startIndex, endIndex);
        
        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("Your Inventory");
        embed.setAuthor(discordUser.getName(), null, discordUser.getEffectiveAvatarUrl());
        embed.setColor(EMBED_COLOR);
        
        String inventoryUrl = frontendBaseUrl + "/inventory";
        embed.setDescription("To equip an item go to your [inventory](" + inventoryUrl + ")");
        
        StringBuilder inventoryContent = new StringBuilder();
        
        if (items.isEmpty()) {
            inventoryContent.append("You don't own any items yet. Visit the `/shop`!");
        } else if (pageItems.isEmpty()) {
             inventoryContent.append("No items on this page.");
        } else {
            for (UserInventoryItemDTO item : pageItems) {
                if (item.getCategory() == ShopCategory.USER_COLOR && 
                    item.getDiscordRoleId() != null && 
                    !item.getDiscordRoleId().isEmpty()) {
                    
                    inventoryContent.append("<@&").append(item.getDiscordRoleId()).append("> | ");
                    
                    String rarityRoleId = RARITY_ROLE_IDS.get(item.getRarity());
                    if (rarityRoleId != null) {
                        inventoryContent.append("<@&").append(rarityRoleId).append(">");
                    } else {
                        inventoryContent.append(formatRarityLabel(item.getRarity()));
                    }
                } else {
                    inventoryContent.append("**").append(item.getName()).append("** | ");
                    
                    inventoryContent.append(formatCategoryDisplay(item.getCategory() != null ? item.getCategory().name() : "Unknown"));
                    inventoryContent.append(" (").append(formatRarityLabel(item.getRarity())).append(")");
                }
                inventoryContent.append("\n");
            }
        }

        embed.addField("", inventoryContent.toString(), false);

        embed.setFooter(String.format("Total Items: %d", items.size()), null);

        return embed.build();
    }

    private String formatCategoryDisplay(String category) {
        if (category == null || category.isEmpty()) {
            return "Miscellaneous";
        }

        switch (category) {
            case "USER_COLOR": return "Nameplate";
            case "LISTING": return "Listing Color";
            case "ACCENT": return "Profile Accent";
        }

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

    private String formatRarityLabel(ItemRarity rarity) {
        if (rarity == null) {
            return "Common";
        }
        String rarityName = rarity.name();
        return rarityName.charAt(0) + rarityName.substring(1).toLowerCase();
    }

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

    private String getFilterLabel(SortMode sortMode) {
        switch (sortMode) {
            case RARITY_DESC:
                return "⭐→⭐⭐⭐";
            case RARITY_ASC:
                return "⭐⭐⭐→⭐";
            case DEFAULT:
            default:
                return "A-Z";
        }
    }

    private void applySorting(List<UserInventoryItemDTO> items, SortMode sortMode) {
        switch (sortMode) {
            case RARITY_DESC:
                items.sort((a, b) -> {
                    if (a.getRarity() == null) return 1;
                    if (b.getRarity() == null) return -1;
                    return Integer.compare(a.getRarity().ordinal(), b.getRarity().ordinal());
                });
                break;
            case RARITY_ASC:
                items.sort((a, b) -> {
                    if (a.getRarity() == null) return -1;
                    if (b.getRarity() == null) return 1;
                    return Integer.compare(b.getRarity().ordinal(), a.getRarity().ordinal());
                });
                break;
            case DEFAULT:
            default:
                items.sort(Comparator.comparing(UserInventoryItemDTO::getName));
                break;
        }
    }
} 