package com.app.heartbound.services;

import com.app.heartbound.dtos.CreateTradeDto;
import com.app.heartbound.entities.*;
import com.app.heartbound.enums.TradeStatus;
import com.app.heartbound.exceptions.InsufficientItemsException;
import com.app.heartbound.exceptions.InvalidTradeActionException;
import com.app.heartbound.exceptions.ResourceNotFoundException;
import com.app.heartbound.exceptions.TradeNotFoundException;
import com.app.heartbound.repositories.TradeRepository;
import com.app.heartbound.repositories.UserRepository;
import com.app.heartbound.services.shop.ShopService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.LockModeType;

import java.time.Instant;
import java.util.List;

@Service
public class TradeService {

    private final TradeRepository tradeRepository;
    private final UserRepository userRepository;
    private final ShopService shopService;
    private final UserInventoryService userInventoryService;

    public TradeService(TradeRepository tradeRepository, UserRepository userRepository,
                        ShopService shopService, UserInventoryService userInventoryService) {
        this.tradeRepository = tradeRepository;
        this.userRepository = userRepository;
        this.shopService = shopService;
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

        for (CreateTradeDto.TradeItemDto itemDto : tradeDto.getOfferedItems()) {
            Shop item = shopService.findById(itemDto.getItemId())
                    .orElseThrow(() -> new ResourceNotFoundException("Item with id " + itemDto.getItemId() + " not found"));

            int ownedQuantity = userInventoryService.getItemQuantity(initiator.getId(), item.getId());
            if (ownedQuantity < itemDto.getQuantity()) {
                throw new InsufficientItemsException("You do not have enough of " + item.getName() + " to trade.");
            }

            TradeItem tradeItem = TradeItem.builder()
                    .trade(trade)
                    .user(initiator)
                    .item(item)
                    .quantity(itemDto.getQuantity())
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
    public Trade addItemsToTrade(Long tradeId, String userId, List<CreateTradeDto.TradeItemDto> itemDtos) {
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
        trade.getItems().removeIf(item -> item.getUser().getId().equals(userId));

        for (CreateTradeDto.TradeItemDto itemDto : itemDtos) {
            Shop item = shopService.findById(itemDto.getItemId())
                    .orElseThrow(() -> new ResourceNotFoundException("Item with id " + itemDto.getItemId() + " not found"));

            // Validate the item is actually tradable
            if (item.getCategory() == null || !item.getCategory().isTradable()) {
                 throw new InvalidTradeActionException("The item '" + item.getName() + "' is not tradable.");
            }

            int ownedQuantity = userInventoryService.getItemQuantity(user.getId(), item.getId());
            if (ownedQuantity < itemDto.getQuantity()) {
                throw new InsufficientItemsException("You do not have enough of '" + item.getName() + "' to trade. You have " + ownedQuantity + ", but offered " + itemDto.getQuantity() + ".");
            }

            TradeItem tradeItem = TradeItem.builder()
                    .trade(trade)
                    .user(user)
                    .item(item)
                    .quantity(itemDto.getQuantity())
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
        Trade trade = tradeRepository.findByIdWithItems(tradeId)
                .orElseThrow(() -> new TradeNotFoundException("Trade not found"));

        if (trade.getStatus() != TradeStatus.PENDING) {
            throw new InvalidTradeActionException("This trade is no longer pending.");
        }

        // Lock both users to prevent concurrent inventory modifications
        User initiator = userRepository.findByIdWithLock(trade.getInitiator().getId(), LockModeType.PESSIMISTIC_WRITE)
            .orElseThrow(() -> new ResourceNotFoundException("Initiator not found."));
        User receiver = userRepository.findByIdWithLock(trade.getReceiver().getId(), LockModeType.PESSIMISTIC_WRITE)
            .orElseThrow(() -> new ResourceNotFoundException("Receiver not found."));


        // Atomically transfer items
        for (TradeItem item : trade.getItems()) {
            User fromUser = item.getUser().getId().equals(initiator.getId()) ? initiator : receiver;
            User toUser = item.getUser().getId().equals(initiator.getId()) ? receiver : initiator;

            Shop shopItem = item.getItem();
            // Check for unique item ownership
            if (!shopItem.getCategory().isStackable()) {
                if (userInventoryService.getItemQuantity(toUser.getId(), shopItem.getId()) > 0) {
                    throw new InvalidTradeActionException("Trade failed: " + toUser.getUsername() + " already owns the unique item '" + shopItem.getName() + "'.");
                }
            }

            userInventoryService.transferItem(
                    fromUser.getId(),
                    toUser.getId(),
                    item.getItem().getId(),
                    item.getQuantity()
            );
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