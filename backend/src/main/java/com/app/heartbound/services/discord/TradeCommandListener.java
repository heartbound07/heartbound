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

@Component
@Slf4j
@RequiredArgsConstructor
public class TradeCommandListener extends ListenerAdapter {

    private final TradeService tradeService;
    private final UserService userService;
    private final UserInventoryService userInventoryService;
    private JDA jdaInstance;

    private final ConcurrentHashMap<String, Long> pendingTradeRequests = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, ActiveTradeState> activeTrades = new ConcurrentHashMap<>();

    public record ActiveTradeState(long messageId, Instant expiresAt, String initiatorId, String receiverId,
                                   boolean initiatorLocked, boolean receiverLocked,
                                   boolean initiatorAccepted, boolean receiverAccepted) {
        public ActiveTradeState withInitiatorLocked(boolean locked) {
            return new ActiveTradeState(messageId, expiresAt, initiatorId, receiverId, locked, receiverLocked, initiatorAccepted, receiverAccepted);
        }
        public ActiveTradeState withReceiverLocked(boolean locked) {
            return new ActiveTradeState(messageId, expiresAt, initiatorId, receiverId, initiatorLocked, locked, initiatorAccepted, receiverAccepted);
        }
        public ActiveTradeState withInitiatorAccepted(boolean accepted) {
            return new ActiveTradeState(messageId, expiresAt, initiatorId, receiverId, initiatorLocked, receiverLocked, accepted, receiverAccepted);
        }
        public ActiveTradeState withReceiverAccepted(boolean accepted) {
            return new ActiveTradeState(messageId, expiresAt, initiatorId, receiverId, initiatorLocked, receiverLocked, initiatorAccepted, accepted);
        }
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
        } else if (activeTrades.containsKey(tradeId)) {
            ActiveTradeState state = activeTrades.get(tradeId);
            if (!clickerId.equals(state.initiatorId()) && !clickerId.equals(state.receiverId())) {
                event.reply("You are not part of this trade.").setEphemeral(true).queue();
                return;
            }
        }


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
    }

    @Override
    public void onStringSelectInteraction(@Nonnull StringSelectInteractionEvent event) {
        String[] parts = event.getComponentId().split("_");
        if (!parts[0].equals("trade") || !parts[1].equals("select-items")) return;
        event.deferEdit().queue();

        long tradeId = Long.parseLong(parts[2]);
        String userId = event.getUser().getId();
        List<String> selectedItemIds = event.getValues();

        List<CreateTradeDto.TradeItemDto> itemDtos = selectedItemIds.stream().map(itemId -> {
            CreateTradeDto.TradeItemDto dto = new CreateTradeDto.TradeItemDto();
            dto.setItemId(UUID.fromString(itemId));
            dto.setQuantity(1); // Assuming quantity of 1 for simplicity. A modal could ask for quantity.
            return dto;
        }).collect(Collectors.toList());

        try {
            tradeService.addItemsToTrade(tradeId, userId, itemDtos);
            updateTradeUI(event.getMessageIdLong(), tradeId);
        } catch (Exception e) {
            event.getHook().sendMessage("Error adding items: " + e.getMessage()).setEphemeral(true).queue();
        }
    }


    private void handleInitialAccept(ButtonInteractionEvent event, long tradeId, String initiatorId, String receiverId) {
        event.getMessage().delete().queue();
        String requestKey = getRequestKey(initiatorId, receiverId);
        pendingTradeRequests.remove(requestKey);

        Trade trade = tradeService.getTradeDetails(tradeId);

        User initiatorUser = jdaInstance.retrieveUserById(initiatorId).complete();
        User receiverUser = jdaInstance.retrieveUserById(receiverId).complete();

        event.getChannel().sendMessageEmbeds(buildTradeEmbed(trade, initiatorUser, receiverUser, null))
                .setComponents(getTradeActionRows(tradeId, false, false))
                .queue(message -> {
                    activeTrades.put(tradeId, new ActiveTradeState(message.getIdLong(), Instant.now().plusSeconds(120), initiatorId, receiverId, false, false, false, false));
                    message.editMessageComponents().setComponents()
                            .setEmbeds(new EmbedBuilder().setDescription("Trade has expired.").setColor(Color.RED).build())
                            .queueAfter(2, TimeUnit.MINUTES, s -> {
                                activeTrades.remove(tradeId);
                                tradeService.cancelTrade(tradeId, initiatorId);
                            });
                });
    }

    private void handleInitialDecline(ButtonInteractionEvent event, long tradeId) {
        tradeService.declineTrade(tradeId, event.getUser().getId());
        event.getHook().editOriginalEmbeds(new EmbedBuilder().setDescription("Trade Declined.").setColor(Color.RED).build())
                .setComponents().queue();
    }

    private void handleAddItem(ButtonInteractionEvent event, long tradeId, String userId) {
        ActiveTradeState state = activeTrades.get(tradeId);
        if (state != null) {
            boolean isInitiator = userId.equals(state.initiatorId());
            if ((isInitiator && state.initiatorLocked()) || (!isInitiator && state.receiverLocked())) {
                event.getHook().sendMessage("You have already locked your offer and cannot add more items.").setEphemeral(true).queue();
                return;
            }
        }

        List<UserInventoryItem> inventory = userInventoryService.getUserInventory(userId);
        if (inventory.isEmpty()) {
            event.getHook().sendMessage("Your inventory is empty.").setEphemeral(true).queue();
            return;
        }

        StringSelectMenu.Builder menuBuilder = StringSelectMenu.create("trade_select-items_" + tradeId)
                .setPlaceholder("Select items to offer...")
                .setRequiredRange(1, Math.min(25, inventory.size()))
                .setMaxValues(Math.min(25, inventory.size()));

        inventory.stream()
            .filter(invItem -> invItem.getItem().getCategory().isTradable())
            .forEach(invItem -> menuBuilder.addOption(
                invItem.getItem().getName(),
                invItem.getItem().getId().toString(),
                "Quantity: " + invItem.getQuantity()
            ));

        if (menuBuilder.getOptions().isEmpty()) {
            event.getHook().sendMessage("You have no tradable items in your inventory.").setEphemeral(true).queue();
            return;
        }

        event.getHook().sendMessage("Please select which items you would like to offer.").setComponents(ActionRow.of(menuBuilder.build())).setEphemeral(true).queue();
    }

    private void handleLockOffer(ButtonInteractionEvent event, long tradeId, String clickerId) {
        ActiveTradeState state = activeTrades.get(tradeId);
        if (state == null) return;

        boolean isInitiator = clickerId.equals(state.initiatorId());
        if ((isInitiator && state.initiatorLocked()) || (!isInitiator && state.receiverLocked())) {
            event.getHook().sendMessage("You have already locked your offer.").setEphemeral(true).queue();
            return;
        }

        if (isInitiator) {
            activeTrades.put(tradeId, state.withInitiatorLocked(true));
        } else {
            activeTrades.put(tradeId, state.withReceiverLocked(true));
        }
        updateTradeUI(event.getMessageIdLong(), tradeId);
    }

    private void handleFinalAccept(ButtonInteractionEvent event, long tradeId, String clickerId) {
        ActiveTradeState state = activeTrades.get(tradeId);
        if (state == null) return;

        boolean isInitiator = clickerId.equals(state.initiatorId());
        ActiveTradeState newState;
        if (isInitiator) {
            newState = state.withInitiatorAccepted(true);
        } else {
            newState = state.withReceiverAccepted(true);
        }
        activeTrades.put(tradeId, newState);

        if (newState.initiatorAccepted() && newState.receiverAccepted()) {
            try {
                tradeService.acceptTrade(tradeId, clickerId);
                event.getHook().editOriginalEmbeds(new EmbedBuilder().setTitle("Trade Successful!").setColor(Color.GREEN).build())
                        .setComponents().queue();
                activeTrades.remove(tradeId);
            } catch (Exception e) {
                event.getHook().editOriginalEmbeds(new EmbedBuilder().setTitle("Trade Failed!").setDescription(e.getMessage()).setColor(Color.RED).build())
                        .setComponents().queue();
                activeTrades.remove(tradeId);
            }
        } else {
             updateTradeUI(event.getMessageIdLong(), tradeId);
        }
    }

    private void handleCancel(ButtonInteractionEvent event, long tradeId, String clickerId) {
        tradeService.cancelTrade(tradeId, clickerId);
        event.getHook().editOriginalEmbeds(new EmbedBuilder().setTitle("Trade Cancelled").setColor(Color.RED).build())
                .setComponents().queue();
        activeTrades.remove(tradeId);
    }


    private void updateTradeUI(long messageId, long tradeId) {
        ActiveTradeState state = activeTrades.get(tradeId);
        if (state == null) return;
        Trade trade = tradeService.getTradeDetails(tradeId);
        User initiatorUser = jdaInstance.retrieveUserById(state.initiatorId()).complete();
        User receiverUser = jdaInstance.retrieveUserById(state.receiverId()).complete();

        // Retrieve the channel from an active trade state, which would have originated from an interaction
        // This is a simplification. A more robust solution might store the channel ID in the ActiveTradeState
        // or retrieve it from the initial interaction if this listener were stateful across reboots.
        jdaInstance.getGuilds().forEach(guild -> {
            guild.getTextChannels().forEach(channel -> {
                channel.retrieveMessageById(messageId).queue(message -> {
                    message.editMessageEmbeds(buildTradeEmbed(trade, initiatorUser, receiverUser, state))
                           .setComponents(getTradeActionRows(tradeId, state.initiatorLocked(), state.receiverLocked()))
                           .queue();
                }, failure -> {}); // Ignore if message not found in this channel
            });
        });
    }

    private MessageEmbed buildTradeEmbed(Trade trade, User initiator, User receiver, ActiveTradeState state) {
        EmbedBuilder embed = new EmbedBuilder().setTitle("Trade between " + initiator.getEffectiveName() + " and " + receiver.getEffectiveName());

        String initiatorStatus = "";
        String receiverStatus = "";

        if(state != null) {
            if(state.initiatorLocked()) initiatorStatus += "✅ Locked ";
            if(state.initiatorAccepted()) initiatorStatus += "Accepted";
            if(state.receiverLocked()) receiverStatus += "✅ Locked ";
            if(state.receiverAccepted()) receiverStatus += "Accepted";
            if(!state.initiatorAccepted() && state.receiverAccepted()) receiverStatus += " (Waiting for you)";
            if(state.initiatorAccepted() && !state.receiverAccepted()) initiatorStatus += " (Waiting for other user)";
        }


        String initiatorItems = trade.getItems().stream()
                .filter(i -> i.getUser().getId().equals(initiator.getId()))
                .map(i -> i.getQuantity() + "x " + i.getItem().getName())
                .collect(Collectors.joining("\n"));
        if(initiatorItems.isEmpty()) initiatorItems = "No items offered.";

        String receiverItems = trade.getItems().stream()
                .filter(i -> i.getUser().getId().equals(receiver.getId()))
                .map(i -> i.getQuantity() + "x " + i.getItem().getName())
                .collect(Collectors.joining("\n"));
        if(receiverItems.isEmpty()) receiverItems = "No items offered.";

        embed.addField(initiator.getEffectiveName() + "'s Offer " + initiatorStatus, initiatorItems, true);
        embed.addField(receiver.getEffectiveName() + "'s Offer " + receiverStatus, receiverItems, true);

        if(state != null) {
            embed.setFooter("This trade expires in " + (state.expiresAt().getEpochSecond() - Instant.now().getEpochSecond()) + " seconds.");
        }

        return embed.build();
    }

    private List<ActionRow> getTradeActionRows(long tradeId, boolean initiatorLocked, boolean receiverLocked) {
        ActiveTradeState state = activeTrades.get(tradeId);
        boolean bothLocked = initiatorLocked && receiverLocked;

        Button addItems = Button.primary("trade_add-items_" + tradeId, "Add/Edit Offer");
        Button lockOffer = Button.secondary("trade_lock-offer_" + tradeId, "Lock Offer");
        Button acceptFinal = Button.success("trade_accept-final_" + tradeId, "Accept Trade").withDisabled(!bothLocked || (state != null && state.initiatorAccepted() && state.receiverAccepted()));
        Button cancel = Button.danger("trade_cancel_" + tradeId, "Cancel");

        return List.of(
                ActionRow.of(addItems, lockOffer),
                ActionRow.of(acceptFinal, cancel)
        );
    }

    private String getRequestKey(String id1, String id2) {
        return id1.compareTo(id2) < 0 ? id1 + ":" + id2 : id2 + ":" + id1;
    }
} 