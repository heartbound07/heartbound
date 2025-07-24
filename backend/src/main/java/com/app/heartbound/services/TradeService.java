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
    public Trade acceptTrade(Long tradeId, String currentUserId) {
        Trade trade = tradeRepository.findById(tradeId)
                .orElseThrow(() -> new TradeNotFoundException("Trade not found"));

        if (!trade.getReceiver().getId().equals(currentUserId)) {
            throw new InvalidTradeActionException("Only the receiver can accept the trade.");
        }

        if (trade.getStatus() != TradeStatus.PENDING) {
            throw new InvalidTradeActionException("This trade is no longer pending.");
        }

        // Atomically transfer items
        for (TradeItem item : trade.getItems()) {
            userInventoryService.transferItem(
                    item.getUser().getId(),
                    trade.getReceiver().getId().equals(item.getUser().getId()) ? trade.getInitiator().getId() : trade.getReceiver().getId(),
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

        if (!trade.getReceiver().getId().equals(currentUserId)) {
            throw new InvalidTradeActionException("Only the receiver can decline the trade.");
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

        if (!trade.getInitiator().getId().equals(currentUserId)) {
            throw new InvalidTradeActionException("Only the initiator can cancel the trade.");
        }

        if (trade.getStatus() != TradeStatus.PENDING) {
            throw new InvalidTradeActionException("This trade is no longer pending.");
        }

        trade.setStatus(TradeStatus.CANCELLED);
        return tradeRepository.save(trade);
    }

    public Trade getTradeDetails(Long tradeId) {
        return tradeRepository.findById(tradeId)
                .orElseThrow(() -> new TradeNotFoundException("Trade not found"));
    }

    public List<Trade> getUserTrades(String userId) {
        return tradeRepository.findByInitiatorIdOrReceiverId(userId, userId);
    }
} 