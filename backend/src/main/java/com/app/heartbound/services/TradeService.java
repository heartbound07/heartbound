package com.app.heartbound.services;

import com.app.heartbound.dto.CreateTradeDto;
import com.app.heartbound.entities.*;
import com.app.heartbound.enums.TradeStatus;
import com.app.heartbound.exceptions.InvalidTradeActionException;
import com.app.heartbound.exceptions.ItemEquippedException;
import com.app.heartbound.exceptions.ResourceNotFoundException;
import com.app.heartbound.exceptions.TradeNotFoundException;
import com.app.heartbound.repositories.ItemInstanceRepository;
import com.app.heartbound.repositories.TradeRepository;
import com.app.heartbound.repositories.UserRepository;
import com.app.heartbound.services.shop.ShopService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class TradeService {

    private final TradeRepository tradeRepository;
    private final UserRepository userRepository;
    private final ItemInstanceRepository itemInstanceRepository;
    private final UserInventoryService userInventoryService;

    public TradeService(TradeRepository tradeRepository, UserRepository userRepository,
                        ItemInstanceRepository itemInstanceRepository, ShopService shopService, UserInventoryService userInventoryService) {
        this.tradeRepository = tradeRepository;
        this.userRepository = userRepository;
        this.itemInstanceRepository = itemInstanceRepository;
        this.userInventoryService = userInventoryService;
    }

    @Transactional
    public Trade createTrade(CreateTradeDto tradeDto, String initiatorId) {
        User initiator = userRepository.findById(initiatorId)
                .orElseThrow(() -> new ResourceNotFoundException("Initiator not found"));
        User receiver = userRepository.findById(tradeDto.getReceiverId())
                .orElseThrow(() -> new ResourceNotFoundException("Receiver not found"));

        if (initiator.getId().equals(receiver.getId())) {
            throw new InvalidTradeActionException("You cannot trade with yourself.");
        }

        Trade trade = Trade.builder()
                .initiator(initiator)
                .receiver(receiver)
                .status(TradeStatus.PENDING)
                .build();

        for (UUID itemInstanceId : tradeDto.getOfferedItemInstanceIds()) {
            ItemInstance instance = itemInstanceRepository.findById(itemInstanceId)
                    .orElseThrow(() -> new ResourceNotFoundException("Item instance with id " + itemInstanceId + " not found"));

            if (!instance.getOwner().getId().equals(initiatorId)) {
                throw new InvalidTradeActionException("You do not own the item instance " + itemInstanceId);
            }
            
            // Check if the item is equipped
            Shop item = instance.getBaseItem();
            if (item.getCategory().isEquippable()) {
                UUID equippedItemId = initiator.getEquippedItemIdByCategory(item.getCategory());
                if (equippedItemId != null && equippedItemId.equals(instance.getBaseItem().getId())) {
                    throw new ItemEquippedException("You cannot trade an item that is currently equipped. Please unequip '" + item.getName() + "' first.");
                }
            }
            
            // Further validation to ensure the item is tradable can be added here if needed
            // For example, checking instance.getBaseItem().getCategory().isTradable()

            TradeItem tradeItem = TradeItem.builder()
                    .trade(trade)
                    .itemInstance(instance)
                    .build();
            trade.getItems().add(tradeItem);
        }

        return tradeRepository.save(trade);
    }

    @Transactional
    public Trade initiateTrade(String initiatorId, String receiverId) {
        User initiator = userRepository.findById(initiatorId)
                .orElseThrow(() -> new ResourceNotFoundException("Initiator not found with id: " + initiatorId));
        User receiver = userRepository.findById(receiverId)
                .orElseThrow(() -> new ResourceNotFoundException("Receiver not found with id: " + receiverId));

        if (initiator.getId().equals(receiver.getId())) {
            throw new InvalidTradeActionException("You cannot trade with yourself.");
        }

        Trade trade = Trade.builder()
                .initiator(initiator)
                .receiver(receiver)
                .status(TradeStatus.PENDING)
                .build();

        return tradeRepository.save(trade);
    }

    @Transactional
    public Trade setTradeMessageInfo(long tradeId, String messageId, String channelId, Instant expiresAt) {
        Trade trade = tradeRepository.findById(tradeId)
            .orElseThrow(() -> new TradeNotFoundException("Trade not found with id: " + tradeId));
        
        trade.setDiscordMessageId(messageId);
        trade.setDiscordChannelId(channelId);
        trade.setExpiresAt(expiresAt);

        return tradeRepository.save(trade);
    }

    @Transactional
    public Trade addItemsToTrade(Long tradeId, String userId, List<UUID> itemInstanceIds) {
        Trade trade = tradeRepository.findById(tradeId)
                .orElseThrow(() -> new TradeNotFoundException("Trade not found with id: " + tradeId));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        if (!trade.getInitiator().getId().equals(userId) && !trade.getReceiver().getId().equals(userId)) {
            throw new InvalidTradeActionException("User is not part of this trade.");
        }

        if (trade.getStatus() != TradeStatus.PENDING) {
            throw new InvalidTradeActionException("This trade is no longer pending and cannot be modified.");
        }

        if ((trade.getInitiator().getId().equals(userId) && trade.getInitiatorLocked()) || (trade.getReceiver().getId().equals(userId) && trade.getReceiverLocked())) {
            throw new InvalidTradeActionException("You have locked your offer and cannot change it.");
        }

        // Atomically remove previous items offered by this user and add new ones
        trade.getItems().removeIf(item -> item.getItemInstance().getOwner().getId().equals(userId));

        for (UUID instanceId : itemInstanceIds) {
            ItemInstance instance = itemInstanceRepository.findById(instanceId)
                .orElseThrow(() -> new ResourceNotFoundException("Item instance with id " + instanceId + " not found"));
            
            if (!instance.getOwner().getId().equals(userId)) {
                throw new InvalidTradeActionException("You do not own one of the items you are trying to trade.");
            }
            
            Shop item = instance.getBaseItem();
            // Validate the item is actually tradable
            if (item.getCategory() == null || !item.getCategory().isTradable()) {
                 throw new InvalidTradeActionException("The item '" + item.getName() + "' is not tradable.");
            }

            // Check if the item is equipped
            if (item.getCategory().isEquippable()) {
                UUID equippedItemId = user.getEquippedItemIdByCategory(item.getCategory());
                if (equippedItemId != null && equippedItemId.equals(instance.getBaseItem().getId())) {
                    throw new ItemEquippedException("You cannot trade an item that is currently equipped. Please unequip '" + item.getName() + "' first.");
                }
            }

            TradeItem tradeItem = TradeItem.builder()
                    .trade(trade)
                    .itemInstance(instance)
                    .build();
            trade.getItems().add(tradeItem);
        }

        return tradeRepository.save(trade);
    }

    @Transactional
    public Trade lockOffer(Long tradeId, String userId) {
        Trade trade = tradeRepository.findById(tradeId)
            .orElseThrow(() -> new TradeNotFoundException("Trade not found with id: " + tradeId));
        
        if (trade.getStatus() != TradeStatus.PENDING) {
            throw new InvalidTradeActionException("This trade is no longer pending.");
        }

        if (trade.getInitiator().getId().equals(userId)) {
            if (trade.getInitiatorLocked()) throw new InvalidTradeActionException("You have already locked your offer.");
            trade.setInitiatorLocked(true);
        } else if (trade.getReceiver().getId().equals(userId)) {
            if (trade.getReceiverLocked()) throw new InvalidTradeActionException("You have already locked your offer.");
            trade.setReceiverLocked(true);
        } else {
            throw new InvalidTradeActionException("User is not part of this trade.");
        }
        return tradeRepository.save(trade);
    }

    @Transactional
    public Trade acceptFinalTrade(Long tradeId, String userId) {
        Trade trade = tradeRepository.findById(tradeId)
            .orElseThrow(() -> new TradeNotFoundException("Trade not found with id: " + tradeId));
        
        if (trade.getStatus() != TradeStatus.PENDING) {
            throw new InvalidTradeActionException("This trade is no longer pending.");
        }

        if (!trade.getInitiatorLocked() || !trade.getReceiverLocked()) {
            throw new InvalidTradeActionException("Both parties must lock their offers before accepting.");
        }

        if (trade.getInitiator().getId().equals(userId)) {
            if (trade.getInitiatorAccepted()) throw new InvalidTradeActionException("You have already accepted the trade.");
            trade.setInitiatorAccepted(true);
        } else if (trade.getReceiver().getId().equals(userId)) {
            if (trade.getReceiverAccepted()) throw new InvalidTradeActionException("You have already accepted the trade.");
            trade.setReceiverAccepted(true);
        } else {
            throw new InvalidTradeActionException("User is not part of this trade.");
        }

        // If both accepted, process the trade
        if (trade.getInitiatorAccepted() && trade.getReceiverAccepted()) {
            return executeTrade(tradeId); 
        }

        return tradeRepository.save(trade);
    }

    @Transactional
    private Trade executeTrade(Long tradeId) {
        Trade trade = tradeRepository.findByIdWithLock(tradeId)
                .orElseThrow(() -> new TradeNotFoundException("Trade not found"));

        if (trade.getStatus() != TradeStatus.PENDING) {
            throw new InvalidTradeActionException("This trade is no longer pending.");
        }

        User initiator = userRepository.findByIdWithLock(trade.getInitiator().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Initiator not found"));
        User receiver = userRepository.findByIdWithLock(trade.getReceiver().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Receiver not found"));


        // Atomically transfer items
        for (TradeItem item : trade.getItems()) {
            ItemInstance instance = item.getItemInstance();
            
            // Re-fetch and lock the item instance to ensure it hasn't been traded/sold
            itemInstanceRepository.findByIdWithLock(instance.getId())
                .orElseThrow(() -> new InvalidTradeActionException("An item in the trade no longer exists."));

            User fromUser = instance.getOwner();
            User toUser;
            
            // Determine who is receiving the item and verify ownership one last time
            if (fromUser.getId().equals(initiator.getId())) {
                toUser = receiver;
            } else if (fromUser.getId().equals(receiver.getId())) {
                toUser = initiator;
            } else {
                throw new InvalidTradeActionException("An item in the trade does not belong to either participant.");
            }

            Shop shopItem = instance.getBaseItem();
            // Final check to ensure item is not equipped before transfer
            if (shopItem.getCategory().isEquippable()) {
                UUID equippedItemId = fromUser.getEquippedItemIdByCategory(shopItem.getCategory());
                if (equippedItemId != null && equippedItemId.equals(instance.getId())) {
                    throw new InvalidTradeActionException("Trade failed: The item '" + shopItem.getName() + "' is currently equipped by one of the users and cannot be traded.");
                }
            }

            // Check for unique item ownership
            if (!shopItem.getCategory().isStackable()) {
                if (userInventoryService.getItemQuantity(toUser.getId(), shopItem.getId()) > 0) {
                    throw new InvalidTradeActionException("Trade failed: " + toUser.getUsername() + " already owns the unique item '" + shopItem.getName() + "'.");
                }
            }

            // Transfer ownership
            instance.setOwner(toUser);
            itemInstanceRepository.save(instance);
        }

        trade.setStatus(TradeStatus.ACCEPTED);
        return tradeRepository.save(trade);
    }

    @Transactional
    public Trade declineTrade(Long tradeId, String currentUserId) {
        Trade trade = tradeRepository.findById(tradeId)
                .orElseThrow(() -> new TradeNotFoundException("Trade not found"));

        if (!trade.getReceiver().getId().equals(currentUserId) && !trade.getInitiator().getId().equals(currentUserId)) {
            throw new InvalidTradeActionException("Only a participant can decline the trade.");
        }

        if (trade.getStatus() != TradeStatus.PENDING) {
            throw new InvalidTradeActionException("This trade is no longer pending.");
        }

        trade.setStatus(TradeStatus.DECLINED);
        return tradeRepository.save(trade);
    }

    @Transactional
    public Trade cancelTrade(Long tradeId, String currentUserId) {
        Trade trade = tradeRepository.findById(tradeId)
                .orElseThrow(() -> new TradeNotFoundException("Trade not found"));

        if (!trade.getInitiator().getId().equals(currentUserId) && !trade.getReceiver().getId().equals(currentUserId)) {
            throw new InvalidTradeActionException("Only a participant can cancel the trade.");
        }

        if (trade.getStatus() != TradeStatus.PENDING) {
            throw new InvalidTradeActionException("This trade is no longer pending.");
        }

        trade.setStatus(TradeStatus.CANCELLED);
        return tradeRepository.save(trade);
    }

    public Trade getTradeDetails(long tradeId) {
        return tradeRepository.findByIdWithItems(tradeId)
                .orElseThrow(() -> new ResourceNotFoundException("Trade not found with id: " + tradeId));
    }

    public List<Trade> getUserTrades(String userId) {
        return tradeRepository.findByInitiatorIdOrReceiverId(userId, userId);
    }
} 