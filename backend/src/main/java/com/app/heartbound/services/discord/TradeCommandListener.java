package com.app.heartbound.services.discord;

import com.app.heartbound.entities.Trade;
import com.app.heartbound.entities.ItemInstance;
import com.app.heartbound.services.TradeService;
import com.app.heartbound.services.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import org.springframework.stereotype.Component;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;

import javax.annotation.Nonnull;
import java.awt.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CancellationException;
import java.util.stream.Collectors;
import java.util.Set;
import java.util.HashSet;
import com.app.heartbound.enums.TradeStatus;
import com.app.heartbound.entities.TradeItem;
import com.app.heartbound.enums.ItemRarity;
import com.app.heartbound.enums.ShopCategory;
import com.app.heartbound.exceptions.InvalidTradeActionException;

@Component
@Slf4j
@RequiredArgsConstructor
public class TradeCommandListener extends ListenerAdapter {

    private final TradeService tradeService;
    private final UserService userService;
    private final TermsOfServiceService termsOfServiceService;
    private JDA jdaInstance;

    // Existing state management
    private final ConcurrentHashMap<String, Long> pendingTradeRequests = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, ScheduledFuture<?>> tradeExpirationTasks = new ConcurrentHashMap<>();
    
    // New state management for paginated item selection
    private final ConcurrentHashMap<String, Set<UUID>> userItemSelections = new ConcurrentHashMap<>();
    
    // Constants for pagination
    private static final int ITEMS_PER_PAGE = 25;
    
    // Component ID constants
    private static final String ID_SEP = ":";
    private static final String PREFIX_ITEM_PAGE = "trade-item-page";
    private static final String PREFIX_CONFIRM_ITEMS = "trade-confirm-items";
    private static final String PREFIX_CANCEL_SELECTION = "trade-cancel-selection";
    private static final String PREFIX_ITEM_SELECT = "trade-item-select";

    // Helper methods for building component IDs
    private static String buildItemPageId(long tradeId, int page) {
        return PREFIX_ITEM_PAGE + ID_SEP + tradeId + ID_SEP + page;
    }
    
    private static String buildConfirmItemsId(long tradeId) {
        return PREFIX_CONFIRM_ITEMS + ID_SEP + tradeId;
    }
    
    private static String buildCancelSelectionId(long tradeId) {
        return PREFIX_CANCEL_SELECTION + ID_SEP + tradeId;
    }
    
    private static String buildItemSelectId(long tradeId) {
        return PREFIX_ITEM_SELECT + ID_SEP + tradeId;
    }

    public void registerWithJDA(JDA jda) {
        if (jda != null) {
            this.jdaInstance = jda;
            jda.addEventListener(this);
            log.info("TradeCommandListener registered with JDA");
        }
    }

    @Override
    public void onSlashCommandInteraction(@Nonnull SlashCommandInteractionEvent event) {
        if (!event.getName().equals("trade")) return;

        log.info("User {} requested /trade", event.getUser().getId());
        
        // Check Terms of Service agreement before proceeding
        termsOfServiceService.requireAgreement(event, (user) -> {
            // ToS check passed, continue with trade logic
            continueTradeCommand(event, user);
        });
    }

    /**
     * Continues the trade command logic after Terms of Service agreement is confirmed.
     */
    private void continueTradeCommand(@Nonnull SlashCommandInteractionEvent event, com.app.heartbound.entities.User initiator) {
        event.deferReply(true).queue();

        User initiatorUser = event.getUser();
        net.dv8tion.jda.api.interactions.commands.OptionMapping userOption = event.getOption("user");
        if (userOption == null) {
            event.getHook().sendMessage("You must specify a user to trade with.").setEphemeral(true).queue();
            return;
        }
        User receiverUser = userOption.getAsUser();

        if (initiatorUser.isBot() || receiverUser.isBot() || initiatorUser.getId().equals(receiverUser.getId())) {
            event.getHook().sendMessage("You cannot trade with a bot or yourself.").queue();
            return;
        }

        com.app.heartbound.entities.User receiver = userService.getUserById(receiverUser.getId());

        if (receiver == null) {
            event.getHook().sendMessage("The target user must be registered to trade.").queue();
            return;
        }

        String requestKey = getRequestKey(initiator.getId(), receiver.getId());
        if (pendingTradeRequests.containsKey(requestKey)) {
            event.getHook().sendMessage("There is already a pending trade request with this user.").queue();
            return;
        }

        try {
            Trade trade = tradeService.initiateTrade(initiator.getId(), receiver.getId());
            long tradeId = trade.getId();

            EmbedBuilder embed = new EmbedBuilder()
                    .setDescription(receiverUser.getAsMention() + ", " + initiatorUser.getEffectiveName() + " wants to trade with you!")
                    .setColor(new Color(0x5865F2));

            Button accept = Button.success("trade_accept-initial_" + tradeId + "_" + initiator.getId() + "_" + receiver.getId(), "Accept");
            Button decline = Button.danger("trade_decline-initial_" + tradeId + "_" + initiator.getId() + "_" + receiver.getId(), "Decline");

            event.getChannel().sendMessage(receiverUser.getAsMention())
                    .setEmbeds(embed.build())
                    .setActionRow(accept, decline)
                    .queue(message -> {
                        pendingTradeRequests.put(requestKey, message.getIdLong());
                        message.editMessageComponents().setComponents()
                                .setEmbeds(new EmbedBuilder().setDescription("Trade request expired.").setColor(Color.GRAY).build())
                                .queueAfter(15, TimeUnit.SECONDS, success -> {
                                    pendingTradeRequests.remove(requestKey);
                                    try {
                                        log.debug("Expiring trade request with ID: {}", tradeId);
                                        // Use tradeService to cancel the trade, marking it as expired in the database
                                        tradeService.cancelTrade(tradeId, null); // null for system-level cancellation
                                        log.info("Successfully cancelled expired trade request with ID: {}", tradeId);
                                    } catch (Exception e) {
                                        // Log error if cancellation fails (e.g., trade was already accepted/declined)
                                        log.error("Error cancelling expired trade request with ID: {}. It might have already been processed.", tradeId, e);
                                    }
                                },
                                        failure -> log.warn("Failed to expire trade request message {}.", message.getId()));
                    });
            event.getHook().deleteOriginal().queue();

        } catch (InvalidTradeActionException e) {
            log.warn("Trade initiation blocked: {}", e.getMessage());
            event.getHook().sendMessage(e.getMessage()).setEphemeral(true).queue();
        } catch (Exception e) {
            log.error("Error initiating trade", e);
            event.getHook().sendMessage("An error occurred while initiating the trade.").setEphemeral(true).queue();
        }
    }

    @Override
    public void onButtonInteraction(@Nonnull ButtonInteractionEvent event) {
        String componentId = event.getComponentId();
        
        // Check if this is any trade-related interaction
        if (!componentId.startsWith("trade")) {
            // Not a trade interaction, ignoring. This prevents logging every button click in the server.
            return;
        }

        // Now that we know it's a trade interaction, log it.
        log.info("=== TRADE BUTTON INTERACTION === ComponentID: '{}', User: {}", componentId, event.getUser().getId());
        
        String[] parts = componentId.split("_");
        log.debug("Component ID parts: {}", String.join(", ", parts));

        log.debug("Deferring edit for trade interaction...");
        event.deferEdit().queue(
            success -> log.debug("Successfully deferred edit for componentId: {}", componentId),
            failure -> log.error("Failed to defer edit for componentId: {}", componentId, failure)
        );

        // Handle new colon-separated format first (trade-action:id or trade-action:id:param)
        if (componentId.startsWith(PREFIX_ITEM_PAGE)) {
            log.info("=== HANDLING PAGINATION === ComponentID: {}", componentId);
            String[] pageParts = componentId.split(ID_SEP);
            log.debug("Pagination parts: {}", String.join(", ", pageParts));
            
            if (pageParts.length >= 3) {
                try {
                    long pageTradeId = Long.parseLong(pageParts[1]);
                    int targetPage = Integer.parseInt(pageParts[2]);
                    String clickerId = event.getUser().getId();
                    log.info("Pagination: tradeId={}, targetPage={}, userId={}", pageTradeId, targetPage, clickerId);
                    handleItemPageNavigation(event, pageTradeId, clickerId, targetPage);
                    return;
                } catch (Exception e) {
                    log.error("Error parsing pagination parameters from: {}", componentId, e);
                    event.getHook().sendMessage("An error occurred while navigating pages.").setEphemeral(true).queue();
                    return;
                }
            } else {
                log.error("Invalid pagination component ID format: {}", componentId);
                event.getHook().sendMessage("Invalid pagination format.").setEphemeral(true).queue();
                return;
            }
        } else if (componentId.startsWith(PREFIX_CONFIRM_ITEMS)) {
            log.info("=== HANDLING CONFIRM SELECTION === ComponentID: {}", componentId);
            String[] confirmParts = componentId.split(ID_SEP);
            log.debug("Confirm parts: {}", String.join(", ", confirmParts));
            
            if (confirmParts.length >= 2) {
                try {
                    long confirmTradeId = Long.parseLong(confirmParts[1]);
                    String clickerId = event.getUser().getId();
                    log.info("Confirm selection: tradeId={}, userId={}", confirmTradeId, clickerId);
                    handleConfirmItemSelection(event, confirmTradeId, clickerId);
                    return;
                } catch (Exception e) {
                    log.error("Error parsing confirm parameters from: {}", componentId, e);
                    event.getHook().sendMessage("An error occurred while confirming your selection.").setEphemeral(true).queue();
                    return;
                }
            } else {
                log.error("Invalid confirm component ID format: {}", componentId);
                event.getHook().sendMessage("Invalid confirmation format.").setEphemeral(true).queue();
                return;
            }
        } else if (componentId.startsWith(PREFIX_CANCEL_SELECTION)) {
            log.info("=== HANDLING CANCEL SELECTION === ComponentID: {}", componentId);
            String[] cancelParts = componentId.split(ID_SEP);
            log.debug("Cancel parts: {}", String.join(", ", cancelParts));
            
            if (cancelParts.length >= 2) {
                try {
                    long cancelTradeId = Long.parseLong(cancelParts[1]);
                    String clickerId = event.getUser().getId();
                    log.info("Cancel selection: tradeId={}, userId={}", cancelTradeId, clickerId);
                    handleCancelItemSelection(event, cancelTradeId, clickerId);
                    return;
                } catch (Exception e) {
                    log.error("Error parsing cancel parameters from: {}", componentId, e);
                    event.getHook().sendMessage("An error occurred while canceling your selection.").setEphemeral(true).queue();
                    return;
                }
            } else {
                log.error("Invalid cancel component ID format: {}", componentId);
                event.getHook().sendMessage("Invalid cancellation format.").setEphemeral(true).queue();
                return;
            }
        }
        
        // Handle legacy trade actions (trade_action_id_initiator_receiver format)
        if (parts.length >= 3) {
            String action = parts[1];
            log.debug("Processing legacy trade action: {}", action);

            long tradeId = Long.parseLong(parts[2]);
            String initiatorId = parts.length > 3 ? parts[3] : null;
            String receiverId = parts.length > 4 ? parts[4] : null;

            String clickerId = event.getUser().getId();

            if (action.equals("accept-initial") || action.equals("decline-initial")) {
            // This is a security check to ensure only the receiver of the trade can accept or decline.
            if (!clickerId.equals(receiverId)) {
                event.reply("You cannot interact with this initial request. Please wait for the recipient to respond.").setEphemeral(true).queue();
                return;
            }
        }

        if (initiatorId != null && receiverId != null && !clickerId.equals(initiatorId) && !clickerId.equals(receiverId)) {
            event.reply("You are not part of this trade.").setEphemeral(true).queue();
            return;
        }

        try {
            switch (action) {
                case "accept-initial":
                    handleInitialAccept(event, tradeId, initiatorId, receiverId);
                    break;
                case "decline-initial":
                    handleInitialDecline(event, tradeId);
                    break;
                case "add-items":
                    handleAddItem(event, tradeId, clickerId);
                    break;
                case "lock-offer":
                    handleLockOffer(event, tradeId, clickerId, event.getGuild());
                    break;
                case "accept-final":
                    handleFinalAccept(event, tradeId, clickerId, event.getGuild());
                    break;
                case "cancel":
                    handleCancel(event, tradeId, clickerId);
                    break;
            }
            } catch (Exception e) {
                log.error("Error handling button interaction for tradeId: {}", tradeId, e);
                event.getHook().sendMessage("An unexpected error occurred. Please try again later.").setEphemeral(true).queue();
            }
        } else {
            log.warn("Invalid legacy trade component ID format: {}", componentId);
            event.getHook().sendMessage("Invalid interaction format.").setEphemeral(true).queue();
        }
    }

    @Override
    public void onStringSelectInteraction(@Nonnull StringSelectInteractionEvent event) {
        String componentId = event.getComponentId();
        String[] parts = componentId.split(ID_SEP);
        if (!parts[0].equals(PREFIX_ITEM_SELECT)) {
            return;
        }

        // We defer the edit, and the final UI update will be handled by updateItemSelectionUI
        event.deferEdit().queue();

        long tradeId = Long.parseLong(parts[1]);
        String userId = event.getUser().getId();
        List<String> selectedItemInstanceIds = event.getValues();

        try {
            log.debug("User {} is updating item selection for trade {}", userId, tradeId);
            
            String selectionKey = getSelectionKey(tradeId, userId);
            
            // Get all items on the current page from the menu component itself. This is more reliable.
            Set<UUID> pageItemIds = event.getComponent().getOptions().stream()
                .map(option -> UUID.fromString(option.getValue()))
                .collect(Collectors.toSet());

            // Get current selections for this trade
            Set<UUID> currentSelections = userItemSelections.getOrDefault(selectionKey, new HashSet<>());
            
            // First, remove all items that could have been on this page from the master selection list.
            // This correctly handles deselection.
            currentSelections.removeAll(pageItemIds);
            
            // Then, add back only the items that are currently selected.
            for (String itemIdStr : selectedItemInstanceIds) {
                currentSelections.add(UUID.fromString(itemIdStr));
            }
            
            // Update the selection state
            userItemSelections.put(selectionKey, currentSelections);
            
            log.debug("Updated selection for user {} trade {}: {} total items selected", 
                userId, tradeId, currentSelections.size());

            // --- Determine the current page to refresh the UI ---
            int currentPage = 0;
            if (!pageItemIds.isEmpty()) {
                 com.app.heartbound.entities.User userEntity = userService.getUserByIdWithInventory(userId);
                 List<ItemInstance> tradableItems = userEntity.getItemInstances().stream()
                    .filter(invItem -> invItem.getBaseItem().getCategory().isTradable() && invItem.getBaseItem().getCategory() != ShopCategory.FISHING_ROD_PART)
                    .collect(Collectors.toList());
                
                UUID firstItemOnPage = pageItemIds.iterator().next();
                for (int i = 0; i < tradableItems.size(); i++) {
                    if (tradableItems.get(i).getId().equals(firstItemOnPage)) {
                        currentPage = i / ITEMS_PER_PAGE;
                        break;
                    }
                }
            }
            
            // Finally, call the update method to refresh the UI for the user.
            // This fixes the "unused method" warning and provides immediate feedback.
            updateItemSelectionUI(event, tradeId, userId, currentPage);

        } catch (Exception e) {
            log.error("Error processing item selection for tradeId: {}", tradeId, e);
            event.getHook().editOriginal("An error occurred while updating your selection: " + e.getMessage()).setComponents().queue();
        }
    }


    private void handleInitialAccept(ButtonInteractionEvent event, long tradeId, String initiatorId, String receiverId) {
        event.getMessage().delete().queue();
        String requestKey = getRequestKey(initiatorId, receiverId);
        pendingTradeRequests.remove(requestKey);

        Trade trade = tradeService.getTradeDetails(tradeId);

        jdaInstance.retrieveUserById(initiatorId).queue(initiatorUser -> {
            jdaInstance.retrieveUserById(receiverId).queue(receiverUser -> {
                Instant expiresAt = Instant.now().plusSeconds(120);

                event.getChannel().sendMessageEmbeds(buildTradeEmbed(trade, initiatorUser, receiverUser, event.getGuild()))
                        .setComponents(getTradeActionRows(trade))
                        .queue(message -> {
                            tradeService.setTradeMessageInfo(tradeId, message.getId(), message.getChannelId(), expiresAt);

                            RestAction<?> editAction = message.editMessageComponents().setComponents()
                                    .setEmbeds(new EmbedBuilder().setDescription("Trade has expired.").setColor(Color.RED).build());

                            ScheduledFuture<?> expirationTask = editAction.queueAfter(120, TimeUnit.SECONDS,
                                s -> {
                                    try {
                                        tradeService.cancelTrade(tradeId, initiatorId);
                                        cleanupTradeSelectionState(tradeId);
                                    } catch (Exception e) {
                                        log.warn("Trade {} may have already been completed or cancelled.", tradeId);
                                    } finally {
                                        tradeExpirationTasks.remove(tradeId);
                                    }
                                },
                                failure -> {
                                    if (failure instanceof CancellationException) {
                                        log.info("Trade expiration task for trade {} was successfully cancelled.", tradeId);
                                    } else {
                                        log.warn("Failed to expire trade request message {}.", message.getId(), failure);
                                    }
                                    tradeExpirationTasks.remove(tradeId);
                                });

                            tradeExpirationTasks.put(tradeId, expirationTask);
                        });
            }, failure -> log.error("Could not retrieve receiver user {} for trade {}", receiverId, tradeId, failure));
        }, failure -> log.error("Could not retrieve initiator user {} for trade {}", initiatorId, tradeId, failure));
    }

    private void handleInitialDecline(ButtonInteractionEvent event, long tradeId) {
        Trade trade = tradeService.declineTrade(tradeId, event.getUser().getId());
        String requestKey = getRequestKey(trade.getInitiator().getId(), trade.getReceiver().getId());
        pendingTradeRequests.remove(requestKey);
        cleanupTradeSelectionState(tradeId);
        event.getHook().editOriginalEmbeds(new EmbedBuilder().setDescription("Trade Declined.").setColor(Color.RED).build())
                .setComponents().queue();
    }

    private void handleAddItem(ButtonInteractionEvent event, long tradeId, String userId) {
        log.info("Handling 'add-items' action for tradeId: {} by userId: {}", tradeId, userId);

        Trade trade = tradeService.getTradeDetails(tradeId);
        boolean isInitiator = userId.equals(trade.getInitiator().getId());

        if ((isInitiator && trade.getInitiatorLocked()) || (!isInitiator && trade.getReceiverLocked())) {
            log.warn("User {} tried to add items to a locked trade (tradeId: {}).", userId, tradeId);
            event.getHook().sendMessage("You have already locked your offer and cannot add more items.").setEphemeral(true).queue();
            return;
        }

        // Initialize selection state for this user/trade
        String selectionKey = getSelectionKey(tradeId, userId);
        userItemSelections.put(selectionKey, new HashSet<>());

        // Start with page 0
        updateItemSelectionUI(event, tradeId, userId, 0);
    }

    /**
     * Updates the paginated item selection UI for a user (for initial display)
     */
    private void updateItemSelectionUI(ButtonInteractionEvent event, long tradeId, String userId, int page) {
        log.info("=== UPDATE SELECTION UI START (BUTTON) === User: {}, tradeId: {}, page: {}", userId, tradeId, page);
        
        try {
            log.debug("Loading user inventory...");
            com.app.heartbound.entities.User userEntity = userService.getUserByIdWithInventory(userId);
            List<ItemInstance> tradableItems = userEntity.getItemInstances().stream()
                .filter(invItem -> invItem.getBaseItem().getCategory().isTradable() && invItem.getBaseItem().getCategory() != ShopCategory.FISHING_ROD_PART)
                .collect(Collectors.toList());

            if (tradableItems.isEmpty()) {
                log.warn("User {} has no tradable items", userId);
                event.getHook().sendMessage("You have no tradable items in your inventory.").setEphemeral(true).queue();
                return;
            }

            int totalPages = (int) Math.ceil((double) tradableItems.size() / ITEMS_PER_PAGE);
            int startIndex = page * ITEMS_PER_PAGE;
            int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, tradableItems.size());
            List<ItemInstance> pageItems = tradableItems.subList(startIndex, endIndex);
            log.debug("Page {}: showing items {} to {} (total: {})", page, startIndex, endIndex - 1, tradableItems.size());

            String selectionKey = getSelectionKey(tradeId, userId);
            Set<UUID> currentSelections = userItemSelections.getOrDefault(selectionKey, new HashSet<>());
            log.debug("Current selections for user {}: {} items", userId, currentSelections.size());

            // Build the select menu for this page
            log.debug("Building select menu...");
            StringSelectMenu.Builder menuBuilder = StringSelectMenu.create(buildItemSelectId(tradeId))
                    .setPlaceholder("Select items to offer...")
                    .setRequiredRange(0, pageItems.size())
                    .setMaxValues(pageItems.size());

            // Get currently selected items on this page for default values
            List<String> defaultValues = new ArrayList<>();
            
            for (ItemInstance invItem : pageItems) {
                String label = invItem.getBaseItem().getName();
                if (invItem.getSerialNumber() != null) {
                    label += " #" + invItem.getSerialNumber();
                }
                
                String itemId = invItem.getId().toString();
                menuBuilder.addOption(
                    label,
                    itemId,
                    "Rarity: " + invItem.getBaseItem().getRarity()
                );
                
                // If this item is in current selections, mark it as default
                if (currentSelections.contains(invItem.getId())) {
                    defaultValues.add(itemId);
                }
            }

            // Set default values for previously selected items
            if (!defaultValues.isEmpty()) {
                menuBuilder.setDefaultValues(defaultValues);
                log.debug("Set {} default values for page {}", defaultValues.size(), page);
            }

            // Build action buttons
            List<Button> actionButtons = new ArrayList<>();
            actionButtons.add(Button.secondary(buildItemPageId(tradeId, page - 1), "◀ Previous").withDisabled(page <= 0));
            actionButtons.add(Button.secondary(buildItemPageId(tradeId, page + 1), "Next ▶").withDisabled(page >= totalPages - 1));
            actionButtons.add(Button.success(buildConfirmItemsId(tradeId), Emoji.fromUnicode("✅")));
            actionButtons.add(Button.danger(buildCancelSelectionId(tradeId), Emoji.fromUnicode("❌")));


            String messageContent = "Please select which items you would like to offer.";
            log.debug("Message content: {}", messageContent);

            // Always send new message for initial display from "Add Items"
            log.debug("Sending new message for item selection...");
            event.getHook().sendMessage(messageContent)
                    .setComponents(
                            ActionRow.of(menuBuilder.build()),
                            ActionRow.of(actionButtons)
                    )
                    .setEphemeral(true)
                    .queue(
                        success -> log.info("=== SELECTION UI SUCCESS (NEW) === User: {}, page: {}", userId, page),
                        failure -> log.error("=== SELECTION UI FAILED (NEW) === User: {}, page: {}", userId, page, failure)
                    );

        } catch (Exception e) {
            log.error("=== SELECTION UI ERROR === User: {}, page: {}", userId, page, e);
            event.getHook().sendMessage("An error occurred while loading your items: " + e.getMessage()).setEphemeral(true).queue();
        }
    }

    /**
     * Updates the paginated item selection UI for a user - overload for string select interactions
     */
    private void updateItemSelectionUI(StringSelectInteractionEvent event, long tradeId, String userId, int page) {
        log.info("=== UPDATE SELECTION UI START (SELECT) === User: {}, tradeId: {}, page: {}", userId, tradeId, page);
        try {
            com.app.heartbound.entities.User userEntity = userService.getUserByIdWithInventory(userId);
            List<ItemInstance> tradableItems = userEntity.getItemInstances().stream()
                .filter(invItem -> invItem.getBaseItem().getCategory().isTradable() && invItem.getBaseItem().getCategory() != ShopCategory.FISHING_ROD_PART)
                .collect(Collectors.toList());

            if (tradableItems.isEmpty()) {
                event.getHook().editOriginal("You have no tradable items in your inventory.").setComponents().queue();
                return;
            }

            int totalPages = (int) Math.ceil((double) tradableItems.size() / ITEMS_PER_PAGE);
            int startIndex = page * ITEMS_PER_PAGE;
            int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, tradableItems.size());
            List<ItemInstance> pageItems = tradableItems.subList(startIndex, endIndex);

            String selectionKey = getSelectionKey(tradeId, userId);
            Set<UUID> currentSelections = userItemSelections.getOrDefault(selectionKey, new HashSet<>());

            // Build the select menu for this page
            StringSelectMenu.Builder menuBuilder = StringSelectMenu.create(buildItemSelectId(tradeId))
                    .setPlaceholder("Select items to offer...")
                    .setRequiredRange(0, pageItems.size())
                    .setMaxValues(pageItems.size());

            // Get currently selected items on this page for default values
            List<String> defaultValues = new ArrayList<>();
            
            for (ItemInstance invItem : pageItems) {
                String label = invItem.getBaseItem().getName();
                if (invItem.getSerialNumber() != null) {
                    label += " #" + invItem.getSerialNumber();
                }
                
                String itemId = invItem.getId().toString();
                menuBuilder.addOption(
                    label,
                    itemId,
                    "Rarity: " + invItem.getBaseItem().getRarity()
                );
                
                // If this item is in current selections, mark it as default
                if (currentSelections.contains(invItem.getId())) {
                    defaultValues.add(itemId);
                }
            }

            // Set default values for previously selected items
            if (!defaultValues.isEmpty()) {
                menuBuilder.setDefaultValues(defaultValues);
            }

            // Build action buttons
            List<Button> actionButtons = new ArrayList<>();
            actionButtons.add(Button.secondary(buildItemPageId(tradeId, page - 1), "◀ Previous").withDisabled(page <= 0));
            actionButtons.add(Button.secondary(buildItemPageId(tradeId, page + 1), "Next ▶").withDisabled(page >= totalPages - 1));
            actionButtons.add(Button.success(buildConfirmItemsId(tradeId), Emoji.fromUnicode("✅")));
            actionButtons.add(Button.danger(buildCancelSelectionId(tradeId), Emoji.fromUnicode("❌")));

            event.getHook().editOriginal("Please select which items you would like to offer.")
                    .setComponents(
                            ActionRow.of(menuBuilder.build()),
                            ActionRow.of(actionButtons)
                    )
                    .queue(
                        success -> log.info("=== SELECTION UI SUCCESS (SELECT) === User: {}, page: {}", userId, page),
                        failure -> log.error("=== SELECTION UI FAILED (SELECT) === User: {}, page: {}", userId, page, failure)
                    );

        } catch (Exception e) {
            log.error("=== SELECTION UI ERROR (SELECT) === User: {}, page: {}", userId, page, e);
            event.getHook().editOriginal("An error occurred while loading your items. Please try again.").setComponents().queue();
        }
    }

    private void handleLockOffer(ButtonInteractionEvent event, long tradeId, String clickerId, Guild guild) {
        tradeService.lockOffer(tradeId, clickerId);
        updateTradeUI(event.getChannel(), tradeId, guild, clickerId);
    }

    private void handleFinalAccept(ButtonInteractionEvent event, long tradeId, String clickerId, Guild guild) {
        try {
            Trade trade = tradeService.acceptFinalTrade(tradeId, clickerId);

            if (trade.getStatus() == TradeStatus.ACCEPTED) {
                ScheduledFuture<?> task = tradeExpirationTasks.remove(tradeId);
                if (task != null) {
                    task.cancel(true);
                    log.info("Cancelled trade expiration task for completed tradeId: {}", tradeId);
                }
                cleanupTradeSelectionState(tradeId);

                String initiatorId = trade.getInitiator().getId();
                String receiverId = trade.getReceiver().getId();

                jdaInstance.retrieveUserById(initiatorId).queue(initiator -> {
                    jdaInstance.retrieveUserById(receiverId).queue(receiver -> {
                        String itemsForInitiator = trade.getItems().stream()
                                .map(TradeItem::getItemInstance)
                                .filter(instance -> instance.getOwner().getId().equals(initiator.getId()))
                                .map(this::formatItemForDisplay)
                                .collect(Collectors.joining("\n"));

                        String itemsForReceiver = trade.getItems().stream()
                                .map(TradeItem::getItemInstance)
                                .filter(instance -> instance.getOwner().getId().equals(receiver.getId()))
                                .map(this::formatItemForDisplay)
                                .collect(Collectors.joining("\n"));

                        EmbedBuilder successEmbed = new EmbedBuilder()
                                .setTitle("Trade Successful!")
                                .setColor(Color.GREEN)
                                .setFooter("Go to your Inventory to equip your new item!");

                        if (!itemsForInitiator.isEmpty()) {
                            successEmbed.addField(initiator.getEffectiveName() + " has Received", itemsForInitiator, true);
                        }

                        if (!itemsForReceiver.isEmpty()) {
                            successEmbed.addField(receiver.getEffectiveName() + " has Received", itemsForReceiver, true);
                        }

                        event.getHook().editOriginalEmbeds(successEmbed.build())
                                .setComponents().queue();
                    }, failure -> log.error("Could not retrieve receiver user {} for completed trade {}", receiverId, tradeId, failure));
                }, failure -> log.error("Could not retrieve initiator user {} for completed trade {}", initiatorId, tradeId, failure));

            } else {
                updateTradeUI(event.getChannel(), tradeId, guild, clickerId);
            }
        } catch (InvalidTradeActionException e) {
            log.error("Final acceptance failed for tradeId: {}: {}", tradeId, e.getMessage());
            String errorMessage = e.getMessage();
            if (errorMessage != null && errorMessage.contains("already owns the unique item")) {
                errorMessage = "Trade failed! A user already has one of the unique items being offered.";
            }
            event.getHook().editOriginalEmbeds(new EmbedBuilder().setTitle("Trade Failed!").setDescription(errorMessage).setColor(Color.RED).build())
                    .setComponents().queue();
        } catch (Exception e) {
            log.error("Final acceptance failed for tradeId: {}", tradeId, e);
            event.getHook().editOriginalEmbeds(new EmbedBuilder().setTitle("Trade Failed!").setDescription("An internal error occurred.").setColor(Color.RED).build())
                    .setComponents().queue();
        }
    }

    private void handleCancel(ButtonInteractionEvent event, long tradeId, String clickerId) {
        ScheduledFuture<?> task = tradeExpirationTasks.remove(tradeId);
        if (task != null) {
            task.cancel(true);
            log.info("Cancelled trade expiration task for cancelled tradeId: {}", tradeId);
        }
        cleanupTradeSelectionState(tradeId);
        tradeService.cancelTrade(tradeId, clickerId);
        event.getHook().editOriginalEmbeds(new EmbedBuilder().setTitle("Trade Cancelled").setColor(Color.RED).build())
                .setComponents().queue();
    }

    private void handleItemPageNavigation(ButtonInteractionEvent event, long tradeId, String userId, int targetPage) {
        log.info("=== PAGE NAVIGATION START === User: {}, tradeId: {}, targetPage: {}", userId, tradeId, targetPage);
        
        try {
            // Authorization check
            Trade trade = tradeService.getTradeDetails(tradeId);
            if (!trade.getInitiator().getId().equals(userId) && !trade.getReceiver().getId().equals(userId)) {
                event.getHook().editOriginal("You are not part of this trade.").setComponents().queue();
                return;
            }
            
            // Validate the target page
            log.debug("Loading user inventory for userId: {}", userId);
            com.app.heartbound.entities.User userEntity = userService.getUserByIdWithInventory(userId);
            if (userEntity == null) {
                log.error("User entity is null for userId: {}", userId);
                event.getHook().editOriginal("Error: Could not load your inventory.").setComponents().queue();
                return;
            }
            log.debug("User entity loaded successfully");
            
            List<ItemInstance> tradableItems = userEntity.getItemInstances().stream()
                .filter(invItem -> invItem.getBaseItem().getCategory().isTradable() && invItem.getBaseItem().getCategory() != ShopCategory.FISHING_ROD_PART)
                .collect(Collectors.toList());
            log.debug("Found {} tradable items for user {}", tradableItems.size(), userId);
                
            int totalPages = (int) Math.ceil((double) tradableItems.size() / ITEMS_PER_PAGE);
            log.debug("Total pages calculated: {}", totalPages);
            
            if (targetPage < 0 || targetPage >= totalPages) {
                log.warn("Invalid page {} requested for user {} trade {} (total pages: {})", targetPage, userId, tradeId, totalPages);
                event.getHook().editOriginal("Invalid page requested.").setComponents().queue();
                return;
            }
            
            log.debug("Page validation passed, calling updateItemSelectionUIForPagination...");
            updateItemSelectionUIForPagination(event, tradeId, userId, targetPage);
            log.info("=== PAGE NAVIGATION SUCCESS === User: {}, page: {}", userId, targetPage);
            
        } catch (Exception e) {
            log.error("=== PAGE NAVIGATION ERROR === User: {}, page: {}", userId, targetPage, e);
            event.getHook().editOriginal("An error occurred while loading the page: " + e.getMessage()).setComponents().queue();
        }
    }

    /**
     * Updates the paginated item selection UI for pagination (edits existing message)
     */
    private void updateItemSelectionUIForPagination(ButtonInteractionEvent event, long tradeId, String userId, int page) {
        log.info("=== UPDATE SELECTION UI START (PAGINATION) === User: {}, tradeId: {}, page: {}", userId, tradeId, page);
        
        try {
            log.debug("Loading user inventory...");
            com.app.heartbound.entities.User userEntity = userService.getUserByIdWithInventory(userId);
            List<ItemInstance> tradableItems = userEntity.getItemInstances().stream()
                .filter(invItem -> invItem.getBaseItem().getCategory().isTradable() && invItem.getBaseItem().getCategory() != ShopCategory.FISHING_ROD_PART)
                .collect(Collectors.toList());

            if (tradableItems.isEmpty()) {
                log.warn("User {} has no tradable items", userId);
                event.getHook().editOriginal("You have no tradable items in your inventory.").setComponents().queue();
                return;
            }

            int totalPages = (int) Math.ceil((double) tradableItems.size() / ITEMS_PER_PAGE);
            int startIndex = page * ITEMS_PER_PAGE;
            int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, tradableItems.size());
            List<ItemInstance> pageItems = tradableItems.subList(startIndex, endIndex);
            log.debug("Page {}: showing items {} to {} (total: {})", page, startIndex, endIndex - 1, tradableItems.size());

            String selectionKey = getSelectionKey(tradeId, userId);
            Set<UUID> currentSelections = userItemSelections.getOrDefault(selectionKey, new HashSet<>());
            log.debug("Current selections for user {}: {} items", userId, currentSelections.size());

            // Build the select menu for this page
            log.debug("Building select menu...");
            StringSelectMenu.Builder menuBuilder = StringSelectMenu.create(buildItemSelectId(tradeId))
                    .setPlaceholder("Select items to offer...")
                    .setRequiredRange(0, pageItems.size())
                    .setMaxValues(pageItems.size());

            // Get currently selected items on this page for default values
            List<String> defaultValues = new ArrayList<>();
            
            for (ItemInstance invItem : pageItems) {
                String label = invItem.getBaseItem().getName();
                if (invItem.getSerialNumber() != null) {
                    label += " #" + invItem.getSerialNumber();
                }
                
                String itemId = invItem.getId().toString();
                menuBuilder.addOption(
                    label,
                    itemId,
                    "Rarity: " + invItem.getBaseItem().getRarity()
                );
                
                // If this item is in current selections, mark it as default
                if (currentSelections.contains(invItem.getId())) {
                    defaultValues.add(itemId);
                }
            }

            // Set default values for previously selected items
            if (!defaultValues.isEmpty()) {
                menuBuilder.setDefaultValues(defaultValues);
                log.debug("Set {} default values for page {}", defaultValues.size(), page);
            }

            // Build action buttons
            log.debug("Building action buttons...");
            List<Button> actionButtons = new ArrayList<>();
            actionButtons.add(Button.secondary(buildItemPageId(tradeId, page - 1), "◀ Previous").withDisabled(page <= 0));
            actionButtons.add(Button.secondary(buildItemPageId(tradeId, page + 1), "Next ▶").withDisabled(page >= totalPages - 1));
            actionButtons.add(Button.success(buildConfirmItemsId(tradeId), Emoji.fromUnicode("✅")));
            actionButtons.add(Button.danger(buildCancelSelectionId(tradeId), Emoji.fromUnicode("❌")));

            String messageContent = "Please select which items you would like to offer.";
            log.debug("Message content: {}", messageContent);

            // Edit existing message for pagination
            log.debug("Editing existing message for pagination...");
            event.getHook().editOriginal(messageContent)
                    .setComponents(
                            ActionRow.of(menuBuilder.build()),
                            ActionRow.of(actionButtons)
                    )
                    .queue(
                        success -> log.info("=== SELECTION UI SUCCESS (PAGINATION) === User: {}, page: {}", userId, page),
                        failure -> log.error("=== SELECTION UI FAILED (PAGINATION) === User: {}, page: {}", userId, page, failure)
                    );

        } catch (Exception e) {
            log.error("=== SELECTION UI ERROR (PAGINATION) === User: {}, page: {}", userId, page, e);
            event.getHook().editOriginal("An error occurred while loading your items: " + e.getMessage()).setComponents().queue();
        }
    }

    private void handleConfirmItemSelection(ButtonInteractionEvent event, long tradeId, String userId) {
        log.info("=== CONFIRM SELECTION START === User: {}, tradeId: {}", userId, tradeId);
        
        // Authorization check
        Trade trade = tradeService.getTradeDetails(tradeId);
        if (!trade.getInitiator().getId().equals(userId) && !trade.getReceiver().getId().equals(userId)) {
            event.getHook().editOriginal("You are not part of this trade.").setComponents().queue();
            return;
        }
        
        String selectionKey = getSelectionKey(tradeId, userId);
        Set<UUID> selectedItems = userItemSelections.getOrDefault(selectionKey, new HashSet<>());
        log.debug("Found {} selected items for confirmation", selectedItems.size());
        
        try {
            if (selectedItems.isEmpty()) {
                log.warn("No items selected for confirmation");
                event.getHook().editOriginal("No items selected. Please select at least one item or cancel.")
                        .setComponents().queue();
                return;
            }

            // Convert Set to List for the service call
            List<UUID> itemInstanceIds = new ArrayList<>(selectedItems);
            log.debug("Converting {} items to trade service call", itemInstanceIds.size());
            
            // Add items to trade
            log.debug("Calling tradeService.addItemsToTrade...");
            tradeService.addItemsToTrade(tradeId, userId, itemInstanceIds);
            log.debug("Successfully added items to trade service");
            
            // Clean up selection state
            userItemSelections.remove(selectionKey);
            log.debug("Cleaned up selection state");
            
            // Update the main trade UI
            log.debug("Updating main trade UI...");
            updateTradeUI(event.getChannel(), tradeId, event.getGuild(), userId);
            log.debug("Main trade UI updated");
            
            // Close the selection UI
            log.debug("Closing selection UI...");
            event.getHook().editOriginal("✅ Items added to trade successfully!")
                    .setComponents().queue(
                        success -> log.info("=== CONFIRM SELECTION SUCCESS === User: {}, items: {}", userId, selectedItems.size()),
                        failure -> log.error("=== CONFIRM SELECTION UI EDIT FAILED === User: {}", userId, failure)
                    );
                    
        } catch (Exception e) {
            log.error("=== CONFIRM SELECTION ERROR === User: {}, tradeId: {}", userId, tradeId, e);
            event.getHook().editOriginal("An error occurred while adding your items: " + e.getMessage())
                    .setComponents().queue();
        }
    }

    private void handleCancelItemSelection(ButtonInteractionEvent event, long tradeId, String userId) {
        log.info("=== CANCEL SELECTION START === User: {}, tradeId: {}", userId, tradeId);
        
        // Authorization check
        Trade trade = tradeService.getTradeDetails(tradeId);
        if (!trade.getInitiator().getId().equals(userId) && !trade.getReceiver().getId().equals(userId)) {
            event.getHook().editOriginal("You are not part of this trade.").setComponents().queue();
            return;
        }
        
        String selectionKey = getSelectionKey(tradeId, userId);
        userItemSelections.remove(selectionKey);
        log.debug("Cleaned up selection state for cancellation");
        
        event.getHook().editOriginal("Item selection cancelled.")
                .setComponents().queue(
                    success -> log.info("=== CANCEL SELECTION SUCCESS === User: {}", userId),
                    failure -> log.error("=== CANCEL SELECTION UI EDIT FAILED === User: {}", userId, failure)
                );
    }


    private void updateTradeUI(MessageChannel channel, long tradeId, Guild guild) {
        updateTradeUI(channel, tradeId, guild, null);
    }
    
    private void updateTradeUI(MessageChannel channel, long tradeId, Guild guild, String currentUserId) {
        Trade trade = tradeService.getTradeDetails(tradeId);
        if (trade == null || trade.getStatus() != TradeStatus.PENDING) {
            log.warn("updateTradeUI called for non-pending or non-existent tradeId: {}", tradeId);
            return;
        }

        long messageId = Long.parseLong(trade.getDiscordMessageId());
        log.debug("Attempting to update UI for tradeId: {}, messageId: {}, in channel: {}", tradeId, messageId, channel.getId());

        String initiatorId = trade.getInitiator().getId();
        String receiverId = trade.getReceiver().getId();

        jdaInstance.retrieveUserById(initiatorId).queue(initiatorUser -> {
            jdaInstance.retrieveUserById(receiverId).queue(receiverUser -> {
                channel.retrieveMessageById(messageId).queue(message -> {
                    log.debug("Found message {} to update for tradeId: {}", messageId, tradeId);
                    message.editMessageEmbeds(buildTradeEmbed(trade, initiatorUser, receiverUser, guild))
                           .setComponents(getTradeActionRows(trade, currentUserId))
                           .queue(
                               success -> log.info("Successfully updated UI for tradeId: {}", tradeId),
                               failure -> log.error(
                                   "Failed to update message UI for tradeId: {}. DB State: [Status: {}, InitiatorLocked: {}, ReceiverLocked: {}]",
                                   tradeId,
                                   trade.getStatus(),
                                   trade.getInitiatorLocked(),
                                   trade.getReceiverLocked(),
                                   failure
                               )
                           );
                }, failure -> {
                    log.error("Failed to retrieve message with ID: {} in channel {} for tradeId: {}", messageId, channel.getId(), tradeId, failure);
                });
            }, failure -> log.error("Could not retrieve receiver user {} for trade UI update {}", receiverId, tradeId, failure));
        }, failure -> log.error("Could not retrieve initiator user {} for trade UI update {}", initiatorId, tradeId, failure));
    }

    private MessageEmbed buildTradeEmbed(Trade trade, User initiator, User receiver, Guild guild) {
        log.debug("Building trade embed for tradeId: {}. Initiator: {}, Receiver: {}", trade.getId(), initiator.getId(), receiver.getId());
        EmbedBuilder embed = new EmbedBuilder();

        if (guild != null) {
            embed.setAuthor(guild.getName(), null, guild.getIconUrl());
        }

        String initiatorStatus = "";
        if (trade.getInitiatorLocked()) initiatorStatus += "🔒";
        if (trade.getInitiatorAccepted()) initiatorStatus += " ✅";

        String receiverStatus = "";
        if (trade.getReceiverLocked()) receiverStatus += "🔒";
        if (trade.getReceiverAccepted()) receiverStatus += " ✅";

        embed.setDescription(initiator.getAsMention() + " and " + receiver.getAsMention() + " are now trading!");

        String itemsForInitiator = trade.getItems().stream()
                .map(TradeItem::getItemInstance)
                .filter(instance -> instance.getOwner().getId().equals(receiver.getId()))
                .map(this::formatItemForDisplay)
                .collect(Collectors.joining("\n"));
        if (itemsForInitiator.isEmpty()) {
            itemsForInitiator = "\u200B";
        }

        String itemsForReceiver = trade.getItems().stream()
                .map(TradeItem::getItemInstance)
                .filter(instance -> instance.getOwner().getId().equals(initiator.getId()))
                .map(this::formatItemForDisplay)
                .collect(Collectors.joining("\n"));
        if (itemsForReceiver.isEmpty()) {
            itemsForReceiver = "\u200B";
        }

        embed.addField("\u200B", initiator.getAsMention() + " will receive:" + initiatorStatus + "\n" + itemsForInitiator, false);
        embed.addField("\u200B", receiver.getAsMention() + " will receive:" + receiverStatus + "\n" + itemsForReceiver, false);

        if (trade.getExpiresAt() != null) {
            long remainingSeconds = trade.getExpiresAt().getEpochSecond() - Instant.now().getEpochSecond();
            if (remainingSeconds > 0) {
                embed.setFooter("This trade expires in " + remainingSeconds + " seconds.");
            } else {
                embed.setFooter("This trade has expired.");
            }
        }

        return embed.build();
    }

    private List<ActionRow> getTradeActionRows(Trade trade) {
        return getTradeActionRows(trade, null);
    }
    
    private List<ActionRow> getTradeActionRows(Trade trade, String currentUserId) {
        boolean bothLocked = trade.getInitiatorLocked() && trade.getReceiverLocked();
        boolean tradeComplete = trade.getStatus() != TradeStatus.PENDING;
        boolean bothAccepted = trade.getInitiatorAccepted() && trade.getReceiverAccepted();

        List<Button> buttons = new ArrayList<>();

        // Disable add-items if trade is complete or both users have locked
        buttons.add(Button.primary("trade_add-items_" + trade.getId(), Emoji.fromUnicode("📝"))
                .withDisabled(tradeComplete || bothLocked));

        if (bothLocked) {
            // Disable accept button only if trade is complete or both have already accepted
            buttons.add(Button.success("trade_accept-final_" + trade.getId(), Emoji.fromUnicode("✅"))
                    .withDisabled(tradeComplete || bothAccepted));
        } else {
            // Disable lock button only if trade is complete or both have locked
            buttons.add(Button.secondary("trade_lock-offer_" + trade.getId(), Emoji.fromUnicode("🔒"))
                    .withDisabled(tradeComplete || bothLocked));
        }

        buttons.add(Button.danger("trade_cancel_" + trade.getId(), Emoji.fromUnicode("❌")).withDisabled(tradeComplete));

        return List.of(ActionRow.of(buttons));
    }

    private String getRequestKey(String id1, String id2) {
        return id1.compareTo(id2) < 0 ? id1 + ":" + id2 : id2 + ":" + id1;
    }

    private String getSelectionKey(long tradeId, String userId) {
        return tradeId + ":" + userId;
    }

    /**
     * Clean up selection state for a completed or cancelled trade
     */
    private void cleanupTradeSelectionState(long tradeId) {
        userItemSelections.entrySet().removeIf(entry -> entry.getKey().startsWith(tradeId + ":"));
        log.debug("Cleaned up selection state for tradeId: {}", tradeId);
    }

    private String formatItemForDisplay(ItemInstance instance) {
        String namePart;
        if (instance.getBaseItem().getCategory() == ShopCategory.USER_COLOR && instance.getBaseItem().getDiscordRoleId() != null && !instance.getBaseItem().getDiscordRoleId().isEmpty()) {
            namePart = "<@&" + instance.getBaseItem().getDiscordRoleId() + ">";
        } else {
            namePart = instance.getBaseItem().getName();
            if (instance.getSerialNumber() != null) {
                namePart += " #" + instance.getSerialNumber();
            }
        }

        ItemRarity rarity = instance.getBaseItem().getRarity();
        String rarityPart = "";
        if (rarity != null) {
            rarityPart = " | **" + formatRarityLabel(rarity) + "**";
        }

        return namePart + rarityPart;
    }

    private String formatRarityLabel(ItemRarity rarity) {
        if (rarity == null) {
            return "Common";
        }
        String rarityName = rarity.name();
        return rarityName.charAt(0) + rarityName.substring(1).toLowerCase();
    }
} 