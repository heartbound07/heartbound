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
import com.app.heartbound.repositories.TradeItemRepository;
import com.app.heartbound.repositories.TradeRepository;
import com.app.heartbound.repositories.UserRepository;
import com.app.heartbound.services.shop.ShopService;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.LockModeType;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.HashMap;
import java.util.Collections;

@Service
@Slf4j
public class TradeService {

    private final TradeRepository tradeRepository;
    private final UserRepository userRepository;
    private final ItemInstanceRepository itemInstanceRepository;
    private final TradeItemRepository tradeItemRepository;
    private final UserInventoryService userInventoryService;
    
    @PersistenceContext
    private EntityManager entityManager;

    public TradeService(TradeRepository tradeRepository, UserRepository userRepository,
                        ItemInstanceRepository itemInstanceRepository, TradeItemRepository tradeItemRepository, 
                        ShopService shopService, UserInventoryService userInventoryService) {
        this.tradeRepository = tradeRepository;
        this.userRepository = userRepository;
        this.itemInstanceRepository = itemInstanceRepository;
        this.tradeItemRepository = tradeItemRepository;
        this.userInventoryService = userInventoryService;
    }

    // ==================== Public API ====================

    @Transactional
    public Trade createTrade(CreateTradeDto tradeDto, String initiatorId) {
        User initiator = userRepository.findById(initiatorId)
                .orElseThrow(() -> new ResourceNotFoundException("Initiator not found"));
        User receiver = userRepository.findById(tradeDto.getReceiverId())
                .orElseThrow(() -> new ResourceNotFoundException("Receiver not found"));

        // Shared validations
        validateUsersCanStartTrade(initiator, receiver);

        Trade trade = Trade.builder()
                .initiator(initiator)
                .receiver(receiver)
                .status(TradeStatus.PENDING)
                .expiresAt(Instant.now().plus(5, ChronoUnit.MINUTES))
                .build();

        // Gather the offered items
        List<ItemInstance> offeredInstances = new ArrayList<>();
        for (UUID itemInstanceId : tradeDto.getOfferedItemInstanceIds()) {
            ItemInstance instance = itemInstanceRepository.findById(itemInstanceId)
                    .orElseThrow(() -> new ResourceNotFoundException("Item instance with id " + itemInstanceId + " not found"));
            offeredInstances.add(instance);
        }

        // Validate items for initial trade creation (preserve original behavior: no tradability check here)
        // Ownership error message must include the ID in this flow
        Set<UUID> rodsBeingAdded = extractRodInstanceIds(offeredInstances);
        validateItemsForTrade(
                initiator,
                offeredInstances,
                rodsBeingAdded,
                /*checkTradable*/ false,
                OwnershipErrorStyle.DETAILED_WITH_ID,
                /*strictEquipValidation*/ false
        );

        // Add items to the trade and auto-include equipped parts for rods
        for (ItemInstance instance : offeredInstances) {
            addTradeItem(trade, instance);
            if (instance.getBaseItem().getCategory() == ShopCategory.FISHING_ROD) {
                addRodEquippedPartsToTrade(trade, instance);
            }
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

        // Shared validations
        validateUsersCanStartTrade(initiator, receiver);

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
        log.debug("Adding items to trade - tradeId: {}, userId: {}, itemCount: {}", tradeId, userId, itemInstanceIds.size());
        
        Trade trade = tradeRepository.findById(tradeId)
                .orElseThrow(() -> new TradeNotFoundException("Trade not found with id: " + tradeId));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        assertUserIsParticipant(trade, userId);
        assertTradeIsPending(trade);
        assertUserOfferNotLocked(trade, userId);

        // Remove duplicates from input list to prevent constraint violations
        List<UUID> uniqueItemIds = new ArrayList<>(new HashSet<>(itemInstanceIds));
        
        // Replace user's current offer (delete then re-add)
        clearUserItemsFromTrade(tradeId, userId);
        
        // Reload trade with remaining items to get current database state
        trade = tradeRepository.findByIdWithItems(tradeId)
                .orElseThrow(() -> new TradeNotFoundException("Trade not found with id: " + tradeId));

        // Determine which fishing rods are being added (for part validation)
        Set<UUID> rodInstanceIdsBeingAdded = new HashSet<>();
        for (UUID instanceId : uniqueItemIds) {
            ItemInstance instance = itemInstanceRepository.findById(instanceId).orElse(null);
            if (instance != null && instance.getBaseItem().getCategory() == ShopCategory.FISHING_ROD) {
                rodInstanceIdsBeingAdded.add(instance.getId());
            }
        }

        // Load and validate instances for this user's new offer
        List<ItemInstance> instancesToAdd = new ArrayList<>();
        for (UUID instanceId : uniqueItemIds) {
            ItemInstance instance = itemInstanceRepository.findByIdWithLock(instanceId)
                .orElseThrow(() -> new ResourceNotFoundException("Item instance with id " + instanceId + " not found"));
            instancesToAdd.add(instance);
        }

        // Validate all items for this user (ownership, tradability, equipped constraints)
        validateItemsForTrade(
                user,
                instancesToAdd,
                rodInstanceIdsBeingAdded,
                /*checkTradable*/ true,
                OwnershipErrorStyle.GENERIC,
                /*strictEquipValidation*/ true
        );

        // Persist new selection
        for (ItemInstance instance : instancesToAdd) {
            // Skip if this instance is already included (e.g., auto-added as an equipped part of a previously processed rod)
            boolean alreadyIncluded = trade.getItems().stream()
                    .anyMatch(ti -> ti.getItemInstance() != null
                            && ti.getItemInstance().getId() != null
                            && ti.getItemInstance().getId().equals(instance.getId()));
            if (alreadyIncluded) {
                continue;
            }

            addTradeItem(trade, instance);
            if (instance.getBaseItem().getCategory() == ShopCategory.FISHING_ROD) {
                addRodEquippedPartsToTrade(trade, instance);
            }
        }

        Trade savedTrade = tradeRepository.save(trade);
        log.debug("Successfully added {} items to trade {} for user {}", uniqueItemIds.size(), tradeId, userId);
        return savedTrade;
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

    @Transactional(noRollbackFor = { InvalidTradeActionException.class, ItemEquippedException.class })
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

        try {
            // If both accepted, pre-validate then execute atomically
            if (trade.getInitiatorAccepted() && trade.getReceiverAccepted()) {
                validateTradeReadyForExecution(trade.getId());
                return executeTrade(tradeId); 
            }
            return tradeRepository.save(trade);
        } catch (InvalidTradeActionException | ItemEquippedException e) {
            // Mark trade as cancelled so participants are unblocked for new trades
            trade.setStatus(TradeStatus.CANCELLED);
            tradeRepository.save(trade);
            throw e;
        }
    }

    @Transactional
    private Trade executeTrade(Long tradeId) {
        // Lock the trade and participants
        Trade trade = tradeRepository.findByIdWithLock(tradeId)
                .orElseThrow(() -> new TradeNotFoundException("Trade not found"));

        if (trade.getStatus() != TradeStatus.PENDING) {
            throw new InvalidTradeActionException("This trade is no longer pending.");
        }

        User initiator = userRepository.findByIdWithLock(trade.getInitiator().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Initiator not found"));
        User receiver = userRepository.findByIdWithLock(trade.getReceiver().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Receiver not found"));

        // Process parts first, rods last to avoid conflicts
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
            
            // Re-fetch and lock the item instance to ensure it hasn't been changed concurrently
            ItemInstance lockedInstance = itemInstanceRepository.findByIdWithLock(instance.getId())
                .orElseThrow(() -> new InvalidTradeActionException("An item in the trade no longer exists."));

            User fromUser = lockedInstance.getOwner();
            User toUser;
            
            // Determine who is receiving the item and verify ownership one last time
            if (fromUser.getId().equals(initiator.getId())) {
                toUser = receiver;
            } else if (fromUser.getId().equals(receiver.getId())) {
                toUser = initiator;
            } else {
                throw new InvalidTradeActionException("An item in the trade does not belong to either participant.");
            }

            Shop shopItem = lockedInstance.getBaseItem();
            ShopCategory category = shopItem.getCategory();

            // Transfer equipped parts when trading a fishing rod (parts are skipped if already transferred earlier)
            if (category == ShopCategory.FISHING_ROD) {
                transferEquippedPartsWithRod(lockedInstance, fromUser, toUser);
            }

            // Check for unique item ownership just before transfer, to account for prior transfers in this loop
            if (!shopItem.getCategory().isStackable()) {
                if (userInventoryService.getItemQuantity(toUser.getId(), shopItem.getId()) > 0) {
                    throw new InvalidTradeActionException("Trade failed: " + toUser.getUsername() + " already owns the unique item '" + shopItem.getName() + "'.");
                }
            }

            // Transfer ownership
            lockedInstance.setOwner(toUser);
            itemInstanceRepository.save(lockedInstance);
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

                // Check uniqueness for non-stackable parts before transfer
                Shop partItem = lockedPart.getBaseItem();
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

    // ==================== Private helpers ====================

    private enum OwnershipErrorStyle {
        DETAILED_WITH_ID,
        GENERIC
    }

    private void validateUsersCanStartTrade(User initiator, User receiver) {
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
    }

    private void assertUserIsParticipant(Trade trade, String userId) {
        if (!trade.getInitiator().getId().equals(userId) && !trade.getReceiver().getId().equals(userId)) {
            throw new InvalidTradeActionException("User is not part of this trade.");
        }
    }

    private void assertTradeIsPending(Trade trade) {
        if (trade.getStatus() != TradeStatus.PENDING) {
            throw new InvalidTradeActionException("This trade is no longer pending and cannot be modified.");
        }
    }

    private void assertUserOfferNotLocked(Trade trade, String userId) {
        if ((trade.getInitiator().getId().equals(userId) && trade.getInitiatorLocked()) || (trade.getReceiver().getId().equals(userId) && trade.getReceiverLocked())) {
            throw new InvalidTradeActionException("You have locked your offer and cannot change it.");
        }
    }

    private void clearUserItemsFromTrade(Long tradeId, String userId) {
        log.debug("Deleting existing trade items for tradeId: {}, userId: {}", tradeId, userId);
        tradeItemRepository.deleteByTradeIdAndUserId(tradeId, userId);
        // Ensure deletes are applied before re-adding to avoid constraint issues
        entityManager.flush();
        log.debug("Database delete operations flushed for tradeId: {}", tradeId);
    }

    private void addTradeItem(Trade trade, ItemInstance instance) {
        TradeItem tradeItem = TradeItem.builder()
                .trade(trade)
                .itemInstance(instance)
                .build();
        trade.getItems().add(tradeItem);
    }

    private void addRodEquippedPartsToTrade(Trade trade, ItemInstance rodInstance) {
        List<ItemInstance> equippedParts = userInventoryService.getEquippedParts(rodInstance);

        // Collect already-added item instance IDs to prevent duplicates
        Set<UUID> alreadyAddedInstanceIds = new HashSet<>();
        for (TradeItem existingItem : trade.getItems()) {
            if (existingItem.getItemInstance() != null && existingItem.getItemInstance().getId() != null) {
                alreadyAddedInstanceIds.add(existingItem.getItemInstance().getId());
            }
        }

        for (ItemInstance part : equippedParts) {
            if (part == null || part.getId() == null) {
                continue;
            }
            if (alreadyAddedInstanceIds.contains(part.getId())) {
                // This part is already included in the trade (possibly explicitly by the user); skip
                continue;
            }

            TradeItem partTradeItem = TradeItem.builder()
                    .trade(trade)
                    .itemInstance(part)
                    .build();
            trade.getItems().add(partTradeItem);
            alreadyAddedInstanceIds.add(part.getId());
        }
    }

    private Set<UUID> extractRodInstanceIds(List<ItemInstance> instances) {
        Set<UUID> rodIds = new HashSet<>();
        for (ItemInstance instance : instances) {
            if (instance.getBaseItem().getCategory() == ShopCategory.FISHING_ROD) {
                rodIds.add(instance.getId());
            }
        }
        return rodIds;
    }

    private void validateItemsForTrade(
            User owner,
            List<ItemInstance> items,
            Set<UUID> rodsBeingAdded,
            boolean checkTradable,
            OwnershipErrorStyle ownershipErrorStyle,
            boolean strictEquipValidation
    ) {
        for (ItemInstance instance : items) {
            Shop item = instance.getBaseItem();
            ShopCategory category = item.getCategory();

            // Ownership validation
            if (!instance.getOwner().getId().equals(owner.getId())) {
                if (ownershipErrorStyle == OwnershipErrorStyle.DETAILED_WITH_ID) {
                    throw new InvalidTradeActionException("You do not own the item instance " + instance.getId());
                }
                throw new InvalidTradeActionException("You do not own one of the items you are trying to trade.");
            }

            // Tradability validation
            if (checkTradable) {
                if (category == null || !category.isTradable()) {
                    throw new InvalidTradeActionException("The item '" + item.getName() + "' is not tradable.");
                }
            }

            // Equipped validations
            if (category == ShopCategory.FISHING_ROD) {
                if (strictEquipValidation) {
                    if (instance.getId().equals(owner.getEquippedFishingRodInstanceId())) {
                        throw new ItemEquippedException("You cannot trade an item that is currently equipped. Please unequip '" + item.getName() + "' first.");
                    }
                }
            } else if (category == ShopCategory.FISHING_ROD_PART) {
                if (strictEquipValidation) {
                    // A part can be traded while equipped ONLY if its rod is also being traded by the same owner
                    if (itemInstanceRepository.isPartAlreadyEquipped(instance.getId())) {
                        List<ItemInstance> rodsWithThisPart = itemInstanceRepository.findRodsWithEquippedParts(Collections.singletonList(instance));
                        boolean rodAlsoBeingTraded = false;
                        for (ItemInstance rod : rodsWithThisPart) {
                            if (rod.getOwner().getId().equals(owner.getId()) && rodsBeingAdded.contains(rod.getId())) {
                                rodAlsoBeingTraded = true;
                                break;
                            }
                        }
                        if (!rodAlsoBeingTraded) {
                            throw new ItemEquippedException("You cannot trade a part that is currently equipped on a fishing rod. Please unequip '" + item.getName() + "' first, or include the fishing rod in the trade.");
                        }
                    }
                }
            } else if (category != null && category.isEquippable()) {
                UUID equippedItemId = owner.getEquippedItemIdByCategory(category);
                if (equippedItemId != null && equippedItemId.equals(instance.getBaseItem().getId())) {
                    throw new ItemEquippedException("You cannot trade an item that is currently equipped. Please unequip '" + item.getName() + "' first.");
                }
            }
        }
    }

    private void validateTradeReadyForExecution(Long tradeId) {
        // Load trade with items and lock the row
        Trade trade = tradeRepository.findByIdWithItems(tradeId)
                .orElseThrow(() -> new TradeNotFoundException("Trade not found"));
        entityManager.lock(trade, LockModeType.PESSIMISTIC_WRITE);

        if (trade.getStatus() != TradeStatus.PENDING) {
            throw new InvalidTradeActionException("This trade is no longer pending.");
        }   

        // Lock participants
        User initiator = userRepository.findByIdWithLock(trade.getInitiator().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Initiator not found"));
        User receiver = userRepository.findByIdWithLock(trade.getReceiver().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Receiver not found"));

        // Lock all item instances referenced by the trade, and build rod recipient mapping
        List<TradeItem> tradeItems = new ArrayList<>(trade.getItems());
        Map<UUID, String> rodRecipientById = new HashMap<>(); // rod instance id -> toUserId
        Map<Long, ItemInstance> lockedInstanceByTradeItemId = new HashMap<>();

        for (TradeItem tradeItem : tradeItems) {
            ItemInstance original = tradeItem.getItemInstance();
            ItemInstance lockedInstance = itemInstanceRepository.findByIdWithLock(original.getId())
                    .orElseThrow(() -> new InvalidTradeActionException("An item in the trade no longer exists."));
            lockedInstanceByTradeItemId.put(tradeItem.getId(), lockedInstance);

            User fromUser = lockedInstance.getOwner();
            User toUser;
            if (fromUser.getId().equals(initiator.getId())) {
                toUser = receiver;
            } else if (fromUser.getId().equals(receiver.getId())) {
                toUser = initiator;
            } else {
                throw new InvalidTradeActionException("An item in the trade does not belong to either participant.");
            }

            if (lockedInstance.getBaseItem().getCategory() == ShopCategory.FISHING_ROD) {
                rodRecipientById.put(lockedInstance.getId(), toUser.getId());
            }
        }

        // Sort items: parts first, rods last
        tradeItems.sort((a, b) -> {
            ShopCategory ca = lockedInstanceByTradeItemId.get(a.getId()).getBaseItem().getCategory();
            ShopCategory cb = lockedInstanceByTradeItemId.get(b.getId()).getBaseItem().getCategory();
            boolean aRod = ca == ShopCategory.FISHING_ROD;
            boolean bRod = cb == ShopCategory.FISHING_ROD;
            if (aRod == bRod) return 0;
            return aRod ? 1 : -1;
        });

        // Final validations before execution
        for (TradeItem tradeItem : tradeItems) {
            ItemInstance instance = lockedInstanceByTradeItemId.get(tradeItem.getId());
            Shop shopItem = instance.getBaseItem();
            ShopCategory category = shopItem.getCategory();

            User fromUser = instance.getOwner();
            User toUser = fromUser.getId().equals(initiator.getId()) ? receiver : initiator;

            // Equipped constraints (mirror executeTrade checks, but now as pre-validation)
            if (category == ShopCategory.FISHING_ROD) {
                if (instance.getId().equals(fromUser.getEquippedFishingRodInstanceId())) {
                    throw new InvalidTradeActionException("Trade failed: The item '" + shopItem.getName() + "' is currently equipped by " + fromUser.getUsername() + " and cannot be traded.");
                }

                // Validate uniqueness for equipped parts that will be auto-transferred
                List<ItemInstance> equippedParts = userInventoryService.getEquippedParts(instance);
                for (ItemInstance part : equippedParts) {
                    // Lock each part for consistency
                    ItemInstance lockedPart = itemInstanceRepository.findByIdWithLock(part.getId())
                            .orElseThrow(() -> new InvalidTradeActionException("Trade failed: An equipped part is no longer available."));
                    Shop partItem = lockedPart.getBaseItem();
                    if (!partItem.getCategory().isStackable()) {
                        if (userInventoryService.getItemQuantity(toUser.getId(), partItem.getId()) > 0) {
                            throw new InvalidTradeActionException("Trade failed: " + toUser.getUsername() + " already owns the unique part '" + partItem.getName() + "'.");
                        }
                    }
                    // Ownership sanity check
                    String partOwnerId = lockedPart.getOwner().getId();
                    if (!partOwnerId.equals(fromUser.getId()) && !partOwnerId.equals(toUser.getId())) {
                        throw new InvalidTradeActionException("Trade failed: An equipped part is not owned by the current rod owner.");
                    }
                }
            } else if (category == ShopCategory.FISHING_ROD_PART) {
                if (itemInstanceRepository.isPartAlreadyEquipped(instance.getId())) {
                    List<ItemInstance> rodsWithPart = itemInstanceRepository.findRodsWithEquippedParts(Collections.singletonList(instance));
                    boolean rodIncludedForSameRecipient = false;
                    for (ItemInstance rod : rodsWithPart) {
                        if (!rod.getOwner().getId().equals(fromUser.getId())) {
                            continue;
                        }
                        String intendedRecipientId = rodRecipientById.get(rod.getId());
                        if (intendedRecipientId != null && intendedRecipientId.equals(toUser.getId())) {
                            rodIncludedForSameRecipient = true;
                            break;
                        }
                    }
                    if (!rodIncludedForSameRecipient) {
                        throw new InvalidTradeActionException("Trade failed: The item '" + shopItem.getName() + "' is currently equipped on a rod by " + fromUser.getUsername() + " and cannot be traded.");
                    }
                }
            } else if (category != null && category.isEquippable()) {
                UUID equippedItemId = fromUser.getEquippedItemIdByCategory(category);
                if (equippedItemId != null && equippedItemId.equals(instance.getBaseItem().getId())) {
                    throw new InvalidTradeActionException("Trade failed: The item '" + shopItem.getName() + "' is currently equipped by " + fromUser.getUsername() + " and cannot be traded.");
                }
            }

            // Unique item constraint (non-stackable)
            if (!shopItem.getCategory().isStackable()) {
                if (userInventoryService.getItemQuantity(toUser.getId(), shopItem.getId()) > 0) {
                    throw new InvalidTradeActionException("Trade failed: " + toUser.getUsername() + " already owns the unique item '" + shopItem.getName() + "'.");
                }
            }
        }
    }
} 