package com.app.heartbound.services;

import com.app.heartbound.dto.CreateTradeDto;
import com.app.heartbound.entities.*;
import com.app.heartbound.enums.ShopCategory;
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
import java.util.Set;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.HashSet;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
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

        // Check if initiator is already in an active pending trade
        List<Trade> initiatorActiveTrades = tradeRepository.findActivePendingTradesForUser(initiator.getId(), Instant.now());
        if (!initiatorActiveTrades.isEmpty()) {
            throw new InvalidTradeActionException("You are already in an active trade!");
        }

        // Check if receiver is already in an active pending trade
        List<Trade> receiverActiveTrades = tradeRepository.findActivePendingTradesForUser(receiver.getId(), Instant.now());
        if (!receiverActiveTrades.isEmpty()) {
            throw new InvalidTradeActionException(receiver.getUsername() + " is already in an active trade!");
        }

        Trade trade = Trade.builder()
                .initiator(initiator)
                .receiver(receiver)
                .status(TradeStatus.PENDING)
                .expiresAt(Instant.now().plus(5, ChronoUnit.MINUTES))
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
        // Lock users in consistent order (alphabetically) to prevent deadlocks
        String firstUserId = initiatorId.compareTo(receiverId) < 0 ? initiatorId : receiverId;
        String secondUserId = initiatorId.compareTo(receiverId) < 0 ? receiverId : initiatorId;
        
        // Lock users to prevent race conditions
        User firstUser = userRepository.findByIdWithLock(firstUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + firstUserId));
        User secondUser = userRepository.findByIdWithLock(secondUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + secondUserId));
        
        // Determine which user is initiator and which is receiver
        User initiator = initiatorId.equals(firstUserId) ? firstUser : secondUser;
        User receiver = receiverId.equals(firstUserId) ? firstUser : secondUser;

        if (initiator.getId().equals(receiver.getId())) {
            throw new InvalidTradeActionException("You cannot trade with yourself.");
        }

        // Check if initiator is already in an active pending trade
        List<Trade> initiatorActiveTrades = tradeRepository.findActivePendingTradesForUser(initiator.getId(), Instant.now());
        if (!initiatorActiveTrades.isEmpty()) {
            throw new InvalidTradeActionException("You are already in an active trade!");
        }

        // Check if receiver is already in an active pending trade
        List<Trade> receiverActiveTrades = tradeRepository.findActivePendingTradesForUser(receiver.getId(), Instant.now());
        if (!receiverActiveTrades.isEmpty()) {
            throw new InvalidTradeActionException(receiver.getUsername() + " is already in an active trade!");
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

        // Build current set of items offered by this user (by instanceId)
        Set<UUID> currentInstanceIds = new HashSet<>();
        List<TradeItem> currentUserItems = new ArrayList<>();
        // Build set of all instanceIds already present in this trade (any side)
        Set<UUID> allTradeInstanceIds = new HashSet<>();
        for (TradeItem ti : trade.getItems()) {
            if (ti.getItemInstance() != null && ti.getItemInstance().getOwner() != null
                && userId.equals(ti.getItemInstance().getOwner().getId())) {
                currentUserItems.add(ti);
                currentInstanceIds.add(ti.getItemInstance().getId());
            }
            if (ti.getItemInstance() != null) {
                allTradeInstanceIds.add(ti.getItemInstance().getId());
            }
        }

        // De-duplicate requested IDs while preserving order
        Set<UUID> requestedIds = new LinkedHashSet<>(itemInstanceIds);

        // Remove items that the user no longer wants to offer
        for (TradeItem ti : new ArrayList<>(currentUserItems)) {
            UUID existingId = ti.getItemInstance().getId();
            if (!requestedIds.contains(existingId)) {
                trade.getItems().remove(ti);
                allTradeInstanceIds.remove(existingId);
                currentInstanceIds.remove(existingId);
            }
        }

        // Add only new items that are not already present for this user
        for (UUID instanceId : requestedIds) {
            if (currentInstanceIds.contains(instanceId) || allTradeInstanceIds.contains(instanceId)) {
                continue; // already included; leave as-is to avoid duplicates
            }

            ItemInstance instance = itemInstanceRepository.findByIdWithLock(instanceId)
                .orElseThrow(() -> new ResourceNotFoundException("Item instance with id " + instanceId + " not found"));

            if (!instance.getOwner().getId().equals(userId)) {
                throw new InvalidTradeActionException("You do not own one of the items you are trying to trade.");
            }

            Shop item = instance.getBaseItem();
            // Validate the item is actually tradable
            if (item.getCategory() == null || !item.getCategory().isTradable()) {
                throw new InvalidTradeActionException("The item '" + item.getName() + "' is not tradable.");
            }

            // Enhanced check for equipped items
            ShopCategory category = item.getCategory();

            if (category == ShopCategory.FISHING_ROD) {
                if (instance.getId().equals(user.getEquippedFishingRodInstanceId())) {
                    throw new ItemEquippedException("You cannot trade an item that is currently equipped. Please unequip '" + item.getName() + "' first.");
                }
            } else if (category == ShopCategory.FISHING_ROD_PART) {
                if (itemInstanceRepository.isPartAlreadyEquipped(instance.getId())) {
                    throw new ItemEquippedException("You cannot trade a part that is currently equipped on a fishing rod. Please unequip '" + item.getName() + "' first.");
                }
            } else if (category.isEquippable()) {
                java.util.UUID equippedItemId = user.getEquippedItemIdByCategory(category);
                if (equippedItemId != null && equippedItemId.equals(instance.getBaseItem().getId())) {
                    throw new ItemEquippedException("You cannot trade an item that is currently equipped. Please unequip '" + item.getName() + "' first.");
                }
            }

            TradeItem tradeItem = TradeItem.builder()
                .trade(trade)
                .itemInstance(instance)
                .build();
            trade.getItems().add(tradeItem);
            currentInstanceIds.add(instanceId);
            allTradeInstanceIds.add(instanceId);
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
        // Process parts and other items first, rods last to avoid duplicate transfers/uniqueness conflicts
        List<TradeItem> orderedItems = new java.util.ArrayList<>(trade.getItems());
        orderedItems.sort((a, b) -> {
            ShopCategory ca = a.getItemInstance().getBaseItem().getCategory();
            ShopCategory cb = b.getItemInstance().getBaseItem().getCategory();
            boolean aRod = ca == ShopCategory.FISHING_ROD;
            boolean bRod = cb == ShopCategory.FISHING_ROD;
            if (aRod == bRod) return 0;
            return aRod ? 1 : -1; // put rods after others
        });

        for (TradeItem item : orderedItems) {
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
            ShopCategory category = shopItem.getCategory();

            if (category == ShopCategory.FISHING_ROD) {
                if (instance.getId().equals(fromUser.getEquippedFishingRodInstanceId())) {
                    throw new InvalidTradeActionException("Trade failed: The item '" + shopItem.getName() + "' is currently equipped by " + fromUser.getUsername() + " and cannot be traded.");
                }
            } else if (category == ShopCategory.FISHING_ROD_PART) {
                if (itemInstanceRepository.isPartAlreadyEquipped(instance.getId())) {
                    // Allow trading an equipped part only if its rod is also part of this trade and goes to the same recipient
                    java.util.List<ItemInstance> rodsWithPart = itemInstanceRepository.findRodsWithEquippedParts(java.util.Collections.singletonList(instance));
                    boolean rodIncludedForSameRecipient = false;
                    for (ItemInstance rod : rodsWithPart) {
                        if (!rod.getOwner().getId().equals(fromUser.getId())) {
                            continue;
                        }
                        for (TradeItem other : orderedItems) {
                            ItemInstance otherInstance = other.getItemInstance();
                            if (otherInstance.getId().equals(rod.getId()) &&
                                otherInstance.getBaseItem().getCategory() == ShopCategory.FISHING_ROD) {
                                User rodFromUser = rod.getOwner();
                                User rodToUser = rodFromUser.getId().equals(initiator.getId()) ? receiver : initiator;
                                if (rodToUser.getId().equals(toUser.getId())) {
                                    rodIncludedForSameRecipient = true;
                                    break;
                                }
                            }
                        }
                        if (rodIncludedForSameRecipient) break;
                    }
                    if (!rodIncludedForSameRecipient) {
                        throw new InvalidTradeActionException("Trade failed: The item '" + shopItem.getName() + "' is currently equipped on a rod by " + fromUser.getUsername() + " and cannot be traded.");
                    }
                    // else, continue; this part will be transferred now, and the rod's helper will skip it later
                }
            } else if (category.isEquippable()) {
                UUID equippedItemId = fromUser.getEquippedItemIdByCategory(category);
                if (equippedItemId != null && equippedItemId.equals(instance.getBaseItem().getId())) {
                    throw new InvalidTradeActionException("Trade failed: The item '" + shopItem.getName() + "' is currently equipped by " + fromUser.getUsername() + " and cannot be traded.");
                }
            }

            // Transfer equipped parts when trading a fishing rod
            if (category == ShopCategory.FISHING_ROD) {
                transferEquippedPartsWithRod(instance, fromUser, toUser);
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

    private void transferEquippedPartsWithRod(ItemInstance rodInstance, User fromUser, User toUser) {
        // List of equipped parts to check and transfer
        ItemInstance[] equippedParts = {
            rodInstance.getEquippedRodShaft(),
            rodInstance.getEquippedReel(),
            rodInstance.getEquippedFishingLine(),
            rodInstance.getEquippedHook(),
            rodInstance.getEquippedGrip()
        };

        for (ItemInstance part : equippedParts) {
            if (part != null) {
                // Re-fetch and lock the part instance
                ItemInstance lockedPart = itemInstanceRepository.findByIdWithLock(part.getId())
                    .orElseThrow(() -> new InvalidTradeActionException("Trade failed: An equipped part is no longer available."));

                // Validate ownership relative to trade participants
                String partOwnerId = lockedPart.getOwner().getId();
                if (partOwnerId.equals(fromUser.getId())) {
                    // proceed with transfer below
                } else if (partOwnerId.equals(toUser.getId())) {
                    // Part was already transferred earlier (e.g., explicitly included in trade); skip
                    continue;
                } else {
                    // Neither participant owns this equipped part; abort
                    throw new InvalidTradeActionException("Trade failed: An equipped part is not owned by the current rod owner.");
                }

                Shop partItem = lockedPart.getBaseItem();
                
                // Check for unique item ownership constraint
                if (!partItem.getCategory().isStackable()) {
                    if (userInventoryService.getItemQuantity(toUser.getId(), partItem.getId()) > 0) {
                        throw new InvalidTradeActionException("Trade failed: " + toUser.getUsername() + " already owns the unique part '" + partItem.getName() + "'.");
                    }
                }

                // Transfer ownership of the part
                lockedPart.setOwner(toUser);
                itemInstanceRepository.save(lockedPart);
            }
        }
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

        // Allow system cancellation when currentUserId is null
        if (currentUserId != null && !trade.getInitiator().getId().equals(currentUserId) && !trade.getReceiver().getId().equals(currentUserId)) {
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