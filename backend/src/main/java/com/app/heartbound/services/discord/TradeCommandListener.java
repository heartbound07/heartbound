package com.app.heartbound.services.discord;

import com.app.heartbound.dtos.CreateTradeDto;
import com.app.heartbound.entities.Trade;
import com.app.heartbound.entities.UserInventoryItem;
import com.app.heartbound.services.TradeService;
import com.app.heartbound.services.UserInventoryService;
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

import javax.annotation.Nonnull;
import java.awt.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.ArrayList;
import com.app.heartbound.enums.TradeStatus;

@Component
@Slf4j
@RequiredArgsConstructor
public class TradeCommandListener extends ListenerAdapter {

    private final TradeService tradeService;
    private final UserService userService;
    private final UserInventoryService userInventoryService;
    private JDA jdaInstance;

    private final ConcurrentHashMap<String, Long> pendingTradeRequests = new ConcurrentHashMap<>();

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

        com.app.heartbound.entities.User initiator = userService.getUserById(initiatorUser.getId());
        com.app.heartbound.entities.User receiver = userService.getUserById(receiverUser.getId());

        if (initiator == null || receiver == null) {
            event.getHook().sendMessage("Both you and the target user must be registered.").queue();
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
                    .setTitle(receiverUser.getEffectiveName() + "! " + initiatorUser.getEffectiveName() + " wants to trade with you!")
                    .setFooter("Do you want to trade with them?")
                    .setColor(Color.CYAN);

            Button accept = Button.success("trade_accept-initial_" + tradeId + "_" + initiator.getId() + "_" + receiver.getId(), "Accept");
            Button decline = Button.danger("trade_decline-initial_" + tradeId + "_" + initiator.getId() + "_" + receiver.getId(), "Decline");

            event.getChannel().sendMessage(receiverUser.getAsMention())
                    .setEmbeds(embed.build())
                    .setActionRow(accept, decline)
                    .queue(message -> {
                        pendingTradeRequests.put(requestKey, message.getIdLong());
                        message.editMessageComponents().setComponents()
                                .setEmbeds(new EmbedBuilder().setDescription("Trade request expired.").setColor(Color.GRAY).build())
                                .queueAfter(15, TimeUnit.SECONDS, success -> pendingTradeRequests.remove(requestKey),
                                        failure -> log.warn("Failed to expire trade request message {}.", message.getId()));
                    });
            event.getHook().deleteOriginal().queue();

        } catch (Exception e) {
            log.error("Error initiating trade", e);
            event.getHook().sendMessage("An error occurred while initiating the trade.").queue();
        }
    }

    @Override
    public void onButtonInteraction(@Nonnull ButtonInteractionEvent event) {
        String[] parts = event.getComponentId().split("_");
        if (!parts[0].equals("trade")) return;

        String action = parts[1];
        long tradeId = Long.parseLong(parts[2]);
        String initiatorId = parts.length > 3 ? parts[3] : null;
        String receiverId = parts.length > 4 ? parts[4] : null;

        String clickerId = event.getUser().getId();

        // Security Check
        if (initiatorId != null && receiverId != null && !clickerId.equals(initiatorId) && !clickerId.equals(receiverId)) {
            event.reply("You are not part of this trade.").setEphemeral(true).queue();
            return;
        }

        try {
            // Defer the edit to acknowledge the interaction
            event.deferEdit().queue();

            // Reroute button clicks to the correct handlers.
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
                    handleLockOffer(event, tradeId, clickerId);
                    break;
                case "accept-final":
                    handleFinalAccept(event, tradeId, clickerId);
                    break;
                case "cancel":
                    handleCancel(event, tradeId, clickerId);
                    break;
            }
        } catch (Exception e) {
            log.error("Error handling button interaction for tradeId: {}", tradeId, e);
            event.getHook().sendMessage("An error occurred: " + e.getMessage()).setEphemeral(true).queue();
        }
    }

    @Override
    public void onStringSelectInteraction(@Nonnull StringSelectInteractionEvent event) {
        String componentId = event.getComponentId();
        if (!componentId.startsWith("trade-item-select:")) {
            return;
        }

        // Acknowledge the interaction immediately to prevent a timeout.
        // This is crucial, especially if the database operation takes time.
        event.deferEdit().queue();

        long tradeId = Long.parseLong(componentId.split(":")[1]);
        String userId = event.getUser().getId();
        List<String> selectedItemIds = event.getValues(); // item UUIDs

        try {
            log.debug("Processing item selection for tradeId: {} by userId: {}. Selected items: {}", tradeId, userId, selectedItemIds);
            List<CreateTradeDto.TradeItemDto> itemDtos = new ArrayList<>();
            for (String itemIdStr : selectedItemIds) {
                // For simplicity, we're adding quantity 1.
                // A more complex implementation would ask for quantity.
                itemDtos.add(new CreateTradeDto.TradeItemDto(UUID.fromString(itemIdStr), 1));
            }

            tradeService.addItemsToTrade(tradeId, userId, itemDtos);
            log.info("User {} added {} items to tradeId: {}", userId, itemDtos.size(), tradeId);

            // Update the main trade UI
            updateTradeUI(event.getChannel(), tradeId);

        } catch (Exception e) {
            log.error("Error processing item selection for tradeId: {}", tradeId, e);
            // After deferring, we must use the hook to send messages.
            event.getHook().sendMessage("An error occurred while adding your items: " + e.getMessage()).setEphemeral(true).queue();
        }
    }


    private void handleInitialAccept(ButtonInteractionEvent event, long tradeId, String initiatorId, String receiverId) {
        event.getMessage().delete().queue();
        String requestKey = getRequestKey(initiatorId, receiverId);
        pendingTradeRequests.remove(requestKey);

        Trade trade = tradeService.getTradeDetails(tradeId);

        User initiatorUser = jdaInstance.retrieveUserById(initiatorId).complete();
        User receiverUser = jdaInstance.retrieveUserById(receiverId).complete();

        Instant expiresAt = Instant.now().plusSeconds(120);

        event.getChannel().sendMessageEmbeds(buildTradeEmbed(trade, initiatorUser, receiverUser))
                .setComponents(getTradeActionRows(trade))
                .queue(message -> {
                    tradeService.setTradeMessageInfo(tradeId, message.getId(), message.getChannelId(), expiresAt);
                    // The expiration message can still be queued locally without complex state
                    message.editMessageComponents().setComponents()
                            .setEmbeds(new EmbedBuilder().setDescription("Trade has expired.").setColor(Color.RED).build())
                            .queueAfter(120, TimeUnit.SECONDS, s -> {
                                try {
                                     tradeService.cancelTrade(tradeId, initiatorId);
                                } catch (Exception e) {
                                    log.warn("Trade {} already completed or cancelled.", tradeId);
                                }
                            });
                });
    }

    private void handleInitialDecline(ButtonInteractionEvent event, long tradeId) {
        Trade trade = tradeService.declineTrade(tradeId, event.getUser().getId());
        String requestKey = getRequestKey(trade.getInitiator().getId(), trade.getReceiver().getId());
        pendingTradeRequests.remove(requestKey);
        event.getHook().editOriginalEmbeds(new EmbedBuilder().setDescription("Trade Declined.").setColor(Color.RED).build())
                .setComponents().queue();
    }

    private void handleAddItem(ButtonInteractionEvent event, long tradeId, String userId) {
        log.info("Handling 'add-items' action for tradeId: {} by userId: {}", tradeId, userId);

        // Fetch the latest trade state from the database
        Trade trade = tradeService.getTradeDetails(tradeId);
        boolean isInitiator = userId.equals(trade.getInitiator().getId());

        if ((isInitiator && trade.getInitiatorLocked()) || (!isInitiator && trade.getReceiverLocked())) {
            log.warn("User {} tried to add items to a locked trade (tradeId: {}).", userId, tradeId);
            event.getHook().sendMessage("You have already locked your offer and cannot add more items.").setEphemeral(true).queue();
            return;
        }

        List<UserInventoryItem> inventory = userInventoryService.getUserInventory(userId);
        log.debug("User {} inventory count: {}", userId, inventory.size());
        if (inventory.isEmpty()) {
            event.getHook().sendMessage("Your inventory is empty.").setEphemeral(true).queue();
            return;
        }

        StringSelectMenu.Builder menuBuilder = StringSelectMenu.create("trade-item-select:" + tradeId)
                .setPlaceholder("Select items to offer...")
                .setRequiredRange(1, Math.min(25, inventory.size()))
                .setMaxValues(Math.min(25, inventory.size()));

        inventory.stream()
            .filter(invItem -> {
                boolean tradable = invItem.getItem().getCategory().isTradable();
                log.trace("Item '{}' (Category: {}) is tradable: {}", invItem.getItem().getName(), invItem.getItem().getCategory(), tradable);
                return tradable;
            })
            .forEach(invItem -> menuBuilder.addOption(
                invItem.getItem().getName(),
                invItem.getItem().getId().toString(),
                "Quantity: " + invItem.getQuantity()
            ));

        if (menuBuilder.getOptions().isEmpty()) {
            log.warn("User {} has inventory, but no tradable items were found for tradeId: {}", userId, tradeId);
            event.getHook().sendMessage("You have no tradable items in your inventory.").setEphemeral(true).queue();
            return;
        }
        log.info("Presenting item selection menu to user {} for tradeId: {}. Options count: {}", userId, tradeId, menuBuilder.getOptions().size());
        event.getHook().sendMessage("Please select which items you would like to offer.").setComponents(ActionRow.of(menuBuilder.build())).setEphemeral(true).queue();
    }

    private void handleLockOffer(ButtonInteractionEvent event, long tradeId, String clickerId) {
        tradeService.lockOffer(tradeId, clickerId);
        updateTradeUI(event.getChannel(), tradeId);
    }

    private void handleFinalAccept(ButtonInteractionEvent event, long tradeId, String clickerId) {
        try {
            Trade trade = tradeService.acceptFinalTrade(tradeId, clickerId);

            if (trade.getStatus() == TradeStatus.ACCEPTED) {
                // The trade is complete, show success message
                event.getHook().editOriginalEmbeds(new EmbedBuilder().setTitle("Trade Successful!").setColor(Color.GREEN).build())
                        .setComponents().queue();
            } else {
                // The trade is not yet complete, just update the UI
                updateTradeUI(event.getChannel(), tradeId);
            }
        } catch (Exception e) {
            log.error("Final acceptance failed for tradeId: {}", tradeId, e);
            event.getHook().editOriginalEmbeds(new EmbedBuilder().setTitle("Trade Failed!").setDescription(e.getMessage()).setColor(Color.RED).build())
                    .setComponents().queue();
        }
    }

    private void handleCancel(ButtonInteractionEvent event, long tradeId, String clickerId) {
        tradeService.cancelTrade(tradeId, clickerId);
        event.getHook().editOriginalEmbeds(new EmbedBuilder().setTitle("Trade Cancelled").setColor(Color.RED).build())
                .setComponents().queue();
    }


    private void updateTradeUI(net.dv8tion.jda.api.entities.channel.middleman.MessageChannel channel, long tradeId) {
        Trade trade = tradeService.getTradeDetails(tradeId);
        if (trade == null || trade.getStatus() != TradeStatus.PENDING) {
            log.warn("updateTradeUI called for non-pending or non-existent tradeId: {}", tradeId);
            // Optionally edit the message to show it's no longer active
            return;
        }

        long messageId = Long.parseLong(trade.getDiscordMessageId());
        log.debug("Attempting to update UI for tradeId: {}, messageId: {}, in channel: {}", tradeId, messageId, channel.getId());

        User initiatorUser = jdaInstance.retrieveUserById(trade.getInitiator().getId()).complete();
        User receiverUser = jdaInstance.retrieveUserById(trade.getReceiver().getId()).complete();

        channel.retrieveMessageById(messageId).queue(message -> {
            log.debug("Found message {} to update for tradeId: {}", messageId, tradeId);
            message.editMessageEmbeds(buildTradeEmbed(trade, initiatorUser, receiverUser))
                   .setComponents(getTradeActionRows(trade))
                   .queue(
                       success -> log.info("Successfully updated UI for tradeId: {}", tradeId),
                       failure -> log.error("Failed to update message for tradeId: {}", tradeId, failure)
                   );
        }, failure -> {
            log.error("Failed to retrieve message with ID: {} in channel {} for tradeId: {}", messageId, channel.getId(), tradeId, failure);
        });
    }

    private MessageEmbed buildTradeEmbed(Trade trade, User initiator, User receiver) {
        log.debug("Building trade embed for tradeId: {}. Initiator: {}, Receiver: {}", trade.getId(), initiator.getId(), receiver.getId());
        EmbedBuilder embed = new EmbedBuilder().setTitle("Trade between " + initiator.getEffectiveName() + " and " + receiver.getEffectiveName());

        String initiatorStatus = "";
        String receiverStatus = "";

        if(trade.getInitiatorLocked()) initiatorStatus += "✅ Locked ";
        if(trade.getInitiatorAccepted()) initiatorStatus += "Accepted";
        if(trade.getReceiverLocked()) receiverStatus += "✅ Locked ";
        if(trade.getReceiverAccepted()) receiverStatus += "Accepted";
        if(!trade.getInitiatorAccepted() && trade.getReceiverAccepted()) receiverStatus += " (Waiting for you)";
        if(trade.getInitiatorAccepted() && !trade.getReceiverAccepted()) initiatorStatus += " (Waiting for other user)";


        String initiatorItems = trade.getItems().stream()
                .filter(i -> i.getUser().getId().equals(initiator.getId()))
                .map(i -> i.getQuantity() + "x " + i.getItem().getName())
                .collect(Collectors.joining("\n"));
        if(initiatorItems.isEmpty()) initiatorItems = "No items offered.";
        log.trace("Initiator ({}): {} items found. Rendered text length: {}", initiator.getId(),
            trade.getItems().stream().filter(i -> i.getUser().getId().equals(initiator.getId())).count(),
            initiatorItems.length());

        String receiverItems = trade.getItems().stream()
                .filter(i -> {
                    if (i == null || i.getUser() == null || i.getItem() == null) {
                        log.warn("Null TradeItem, User, or Item found in tradeId: {}", trade.getId());
                        return false;
                    }
                    return i.getUser().getId().equals(receiver.getId());
                })
                .map(i -> i.getQuantity() + "x " + i.getItem().getName())
                .collect(Collectors.joining("\n"));
        if(receiverItems.isEmpty()) receiverItems = "No items offered.";
        log.trace("Receiver ({}): {} items found. Rendered text length: {}", receiver.getId(),
            trade.getItems().stream().filter(i -> i.getUser().getId().equals(receiver.getId())).count(),
            receiverItems.length());

        embed.addField(initiator.getEffectiveName() + "'s Offer " + initiatorStatus, initiatorItems, true);
        embed.addField(receiver.getEffectiveName() + "'s Offer " + receiverStatus, receiverItems, true);

        if(trade.getExpiresAt() != null) {
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
        boolean bothLocked = trade.getInitiatorLocked() && trade.getReceiverLocked();
        boolean tradeComplete = trade.getStatus() != TradeStatus.PENDING;

        Button addItems = Button.primary("trade_add-items_" + trade.getId(), "Add/Edit Offer").withDisabled(tradeComplete);
        Button lockOffer = Button.secondary("trade_lock-offer_" + trade.getId(), "Lock Offer").withDisabled(tradeComplete);
        Button acceptFinal = Button.success("trade_accept-final_" + trade.getId(), "Accept Trade").withDisabled(tradeComplete || !bothLocked);
        Button cancel = Button.danger("trade_cancel_" + trade.getId(), "Cancel").withDisabled(tradeComplete);

        return List.of(
                ActionRow.of(addItems, lockOffer),
                ActionRow.of(acceptFinal, cancel)
        );
    }

    private String getRequestKey(String id1, String id2) {
        return id1.compareTo(id2) < 0 ? id1 + ":" + id2 : id2 + ":" + id1;
    }
} 