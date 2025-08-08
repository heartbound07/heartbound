package com.app.heartbound.services.shop;

import com.app.heartbound.dto.shop.ShopDTO;
import com.app.heartbound.dto.shop.PurchaseResponseDTO;

import com.app.heartbound.entities.Shop;
import com.app.heartbound.entities.User;
import com.app.heartbound.entities.CaseItem;
import com.app.heartbound.entities.ItemInstance;
import com.app.heartbound.entities.UserDailyShopItem;
import com.app.heartbound.enums.ShopCategory;
import com.app.heartbound.enums.ItemRarity;
import com.app.heartbound.enums.FishingRodPart;
import com.app.heartbound.exceptions.ResourceNotFoundException;
import com.app.heartbound.exceptions.shop.InsufficientCreditsException;
import com.app.heartbound.exceptions.shop.ItemAlreadyOwnedException;
import com.app.heartbound.exceptions.shop.RoleRequirementNotMetException;
import com.app.heartbound.exceptions.shop.ItemDeletionException;
import com.app.heartbound.exceptions.shop.ItemReferencedInCasesException;
import com.app.heartbound.exceptions.shop.InsufficientStockException;
import com.app.heartbound.exceptions.shop.ItemNotPurchasableException;
import com.app.heartbound.repositories.UserRepository;
import com.app.heartbound.repositories.shop.ShopRepository;
import com.app.heartbound.repositories.shop.CaseItemRepository;
import com.app.heartbound.repositories.shop.UserDailyShopItemRepository;
import com.app.heartbound.repositories.ItemInstanceRepository;
import com.app.heartbound.services.UserService;
import com.app.heartbound.services.discord.DiscordService;
import com.app.heartbound.services.HtmlSanitizationService;
import com.app.heartbound.services.AuditService;
import com.app.heartbound.dto.CreateAuditDTO;
import com.app.heartbound.enums.AuditSeverity;
import com.app.heartbound.enums.AuditCategory;
import com.app.heartbound.mappers.ShopMapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.persistence.EntityManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;

import java.util.*;
import java.util.stream.Collectors;
import java.util.Objects;
import java.time.LocalDateTime;
import java.time.LocalDate;

@Service
public class ShopService {
    
    private final ShopRepository shopRepository;
    private final UserRepository userRepository;
    private final ItemInstanceRepository itemInstanceRepository;
    private final UserDailyShopItemRepository userDailyShopItemRepository;
    private final UserService userService;
    private final DiscordService discordService;
    private final CaseItemRepository caseItemRepository;
    private final HtmlSanitizationService htmlSanitizationService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final ShopMapper shopMapper;
    private static final Logger logger = LoggerFactory.getLogger(ShopService.class);
    
    public ShopService(
        ShopRepository shopRepository,
        UserRepository userRepository,
        ItemInstanceRepository itemInstanceRepository,
        UserDailyShopItemRepository userDailyShopItemRepository,
        UserService userService,
        @Lazy DiscordService discordService,
        CaseItemRepository caseItemRepository,
        HtmlSanitizationService htmlSanitizationService,
        AuditService auditService,
        EntityManager entityManager,
        ShopMapper shopMapper
    ) {
        this.shopRepository = shopRepository;
        this.userRepository = userRepository;
        this.itemInstanceRepository = itemInstanceRepository;
        this.userDailyShopItemRepository = userDailyShopItemRepository;
        this.userService = userService;
        this.discordService = discordService;
        this.caseItemRepository = caseItemRepository;
        this.htmlSanitizationService = htmlSanitizationService;
        this.auditService = auditService;
        this.objectMapper = new ObjectMapper();
        this.shopMapper = shopMapper;

    }
    
    /**
     * Get all available shop items
     * @param userId Optional user ID to check ownership status
     * @param categoryStr Optional category filter as string
     * @return List of shop items
     */
    @Transactional(readOnly = true)
    public List<ShopDTO> getAvailableShopItems(String userId, String categoryStr) {
        List<Shop> items;
        LocalDateTime now = LocalDateTime.now();
        
        // Filter by category if provided
        if (categoryStr != null && !categoryStr.isEmpty()) {
            try {
                ShopCategory category = ShopCategory.valueOf(categoryStr);
                // Get active items that either have no expiry or haven't expired yet
                items = shopRepository.findByCategoryAndIsActiveTrueAndIsDailyTrue(category)
                    .stream()
                    .filter(item -> item.getExpiresAt() == null || item.getExpiresAt().isAfter(now))
                    .filter(item -> item.getMaxCopies() == null || item.getCopiesSold() == null || item.getCopiesSold() < item.getMaxCopies())
                    .collect(Collectors.toList());
            } catch (IllegalArgumentException e) {
                // Invalid category string, return empty list
                logger.warn("Invalid category filter: {}", categoryStr);
                return Collections.emptyList();
            }
        } else {
            // Get all active items that either have no expiry or haven't expired yet
            items = shopRepository.findByIsActiveTrueAndIsDailyTrue()
                .stream()
                .filter(item -> item.getExpiresAt() == null || item.getExpiresAt().isAfter(now))
                .filter(item -> item.getMaxCopies() == null || item.getCopiesSold() == null || item.getCopiesSold() < item.getMaxCopies())
                .collect(Collectors.toList());
        }
    
        // Convert to DTOs and check ownership if userId is provided
        User user = null;
        if (userId != null) {
            user = userRepository.findById(userId).orElse(null);
        }
        
        final User finalUser = user;
        return items.stream()
            .map(item -> mapToShopDTO(item, finalUser))
            .collect(Collectors.toList());
    }
    
    /**
     * Get featured shop items for the main layout
     * @param userId User ID to check ownership status
     * @return List of featured shop items
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "featuredItems", key = "#userId")
    public List<ShopDTO> getFeaturedItems(String userId) {
        logger.debug("Getting featured items for user {}", userId);
        
        LocalDateTime now = LocalDateTime.now();
        
        // Get featured items that are active and not expired
        List<Shop> items = shopRepository.findByIsFeaturedTrueAndIsActiveTrueOrderByCreatedAtDesc()
            .stream()
            .filter(item -> item.getExpiresAt() == null || item.getExpiresAt().isAfter(now))
            .filter(item -> item.getMaxCopies() == null || item.getCopiesSold() == null || item.getCopiesSold() < item.getMaxCopies())
            .collect(Collectors.toList());
        
        // Get user for ownership checking
        User user = null;
        if (userId != null) {
            user = userRepository.findById(userId).orElse(null);
        }
        
        final User finalUser = user;
        return items.stream()
            .map(item -> mapToShopDTO(item, finalUser))
            .collect(Collectors.toList());
    }
    
    /**
     * Get daily shop items for the main layout.
     * Returns exactly 4 items that are personalized for each user but consistent per day.
     * Selection is weighted by rarity with LEGENDARY/EPIC items being significantly rarer.
     * @param userId User ID to check ownership status for the items.
     * @return List of daily shop items available to the user (max 4 items).
     */
    @Transactional(readOnly = true)
    public List<ShopDTO> getDailyItems(String userId) {
        logger.debug("Getting daily items for user {}", userId);
        
        // Fetch the user with their inventory up-front to avoid multiple lookups and for ownership checks.
        User user = userRepository.findByIdWithInventory(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        // Get the user's personalized daily shop items
        List<Shop> selectedItems = getUserDailyShopItems(user);
        
        logger.info("Selected {} daily items for user {} from available pool", selectedItems.size(), userId);
        
        // Convert to DTOs for the response
        return selectedItems.stream()
            .map(item -> mapToShopDTO(item, user))
            .collect(Collectors.toList());
    }
    
    /**
     * Get the personalized daily shop items for a user (internal method).
     * Returns the actual Shop entities that would be shown in the user's daily shop.
     * Uses database persistence to ensure the same 4 items are shown to a user for the entire day,
     * even across application restarts.
     * 
     * @param user User with loaded inventory
     * @return List of Shop items for the user's daily selection (max 4 items)
     */
    private List<Shop> getUserDailyShopItems(User user) {
        LocalDate today = LocalDate.now();
        
        // Check database for existing daily selections
        List<UserDailyShopItem> existingSelections = userDailyShopItemRepository
                .findByUserIdAndSelectionDate(user.getId(), today);
        
        if (!existingSelections.isEmpty()) {
            logger.debug("Database hit for daily items selection: userId={}, found {} items", 
                    user.getId(), existingSelections.size());
            // Return the Shop items from the existing selections
            return existingSelections.stream()
                    .map(UserDailyShopItem::getShopItem)
                    .collect(Collectors.toList());
        }
        
        logger.debug("No existing daily items selection found: userId={}, generating new selection", user.getId());
        
        // Generate new daily selection
        List<Shop> selectedItems = generateDailyShopSelection(user);
        
        // Save the selection to database for persistence
        for (Shop item : selectedItems) {
            UserDailyShopItem dailySelection = UserDailyShopItem.builder()
                    .userId(user.getId())
                    .shopItem(item)
                    .selectionDate(today)
                    .build();
            userDailyShopItemRepository.save(dailySelection);
        }
        
        logger.info("Saved {} daily shop items for user {} to database", selectedItems.size(), user.getId());
        
        return selectedItems;
    }
    
    /**
     * Generate the actual daily shop selection for a user (called only on cache miss).
     * This contains the original logic for selecting 4 weighted random items.
     * 
     * @param user User with loaded inventory
     * @return List of Shop items for the user's daily selection (max 4 items)
     */
         private List<Shop> generateDailyShopSelection(User user) {
        LocalDateTime now = LocalDateTime.now();

        // Get all daily items that are active (use the complete pool, not filtered by ownership)
        List<Shop> dailyItems = shopRepository.findByIsActiveTrueAndIsDailyTrue()
            .stream()
            .filter(item -> item.getExpiresAt() == null || item.getExpiresAt().isAfter(now))
            .filter(item -> item.getMaxCopies() == null || item.getCopiesSold() == null || item.getCopiesSold() < item.getMaxCopies())
            .collect(Collectors.toList());

        // For daily selection generation, we do NOT filter out owned items
        // This ensures the same 4 items are selected for the user regardless of their current inventory
        // The owned status will be checked dynamically when converting to DTOs

        // If we have 4 or fewer items, return all of them
        if (dailyItems.size() <= 4) {
            return dailyItems;
        }

        // Select exactly 4 items using weighted rarity selection
        String dateString = LocalDate.now().toString();
        long seed = (user.getId() + dateString).hashCode();
        Random seededRandom = new Random(seed);
        
        // Group available items by rarity
        Map<ItemRarity, List<Shop>> itemsByRarity = dailyItems.stream()
            .collect(Collectors.groupingBy(Shop::getRarity));
        
        // Define rarity weights
        Map<ItemRarity, Double> rarityWeights = Map.of(
            ItemRarity.COMMON, 0.55,
            ItemRarity.UNCOMMON, 0.25,
            ItemRarity.RARE, 0.12,
            ItemRarity.EPIC, 0.06,
            ItemRarity.LEGENDARY, 0.02
        );
        
        // Select exactly 4 unique items
        List<Shop> selectedItems = new ArrayList<>();
        
        for (int i = 0; i < 4 && !itemsByRarity.isEmpty(); i++) {
            ItemRarity selectedRarity = selectWeightedRarity(rarityWeights, seededRandom, itemsByRarity);
            
            if (selectedRarity == null) {
                break;
            }
            
            List<Shop> itemsOfRarity = itemsByRarity.get(selectedRarity);
            int randomIndex = seededRandom.nextInt(itemsOfRarity.size());
            Shop selectedItem = itemsOfRarity.get(randomIndex);
            
            selectedItems.add(selectedItem);
            itemsOfRarity.remove(selectedItem);
            
            // Clean up empty rarity lists
            if (itemsOfRarity.isEmpty()) {
                itemsByRarity.remove(selectedRarity);
            }
        }
        
        return selectedItems;
    }

    /**
     * Select a rarity based on weighted distribution from available rarities.
     * Only considers rarities that have available items.
     * 
     * @param rarityWeights Map of rarity to weight values
     * @param random Random number generator to use
     * @param itemsByRarity Map of available items grouped by rarity
     * @return Selected ItemRarity or null if no rarities are available
     */
    private ItemRarity selectWeightedRarity(Map<ItemRarity, Double> rarityWeights, Random random, 
                                           Map<ItemRarity, List<Shop>> itemsByRarity) {
        // Only consider rarities that have available items
        List<ItemRarity> availableRarities = itemsByRarity.entrySet().stream()
            .filter(entry -> !entry.getValue().isEmpty())
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
        
        if (availableRarities.isEmpty()) {
            return null;
        }
        
        // Calculate total weight for available rarities
        double totalWeight = availableRarities.stream()
            .mapToDouble(rarity -> rarityWeights.getOrDefault(rarity, 0.0))
            .sum();
        
        if (totalWeight <= 0.0) {
            // Fallback to uniform distribution if no weights defined
            return availableRarities.get(random.nextInt(availableRarities.size()));
        }
        
        double randomValue = random.nextDouble() * totalWeight;
        double cumulativeWeight = 0.0;
        
        for (ItemRarity rarity : availableRarities) {
            cumulativeWeight += rarityWeights.getOrDefault(rarity, 0.0);
            if (randomValue <= cumulativeWeight) {
                return rarity;
            }
        }
        
        // Fallback to last available rarity
        return availableRarities.get(availableRarities.size() - 1);
    }

    /**
     * Get a shop item by ID
     * @param itemId Item ID
     * @param userId Optional user ID to check ownership status
     * @return ShopDTO
     */
    public ShopDTO getShopItemById(UUID itemId, String userId) {
        Shop item = shopRepository.findById(itemId)
            .orElseThrow(() -> new ResourceNotFoundException("Shop item not found with ID: " + itemId));
        
        User user = null;
        if (userId != null) {
            user = userRepository.findById(userId).orElse(null);
        }
        
        return mapToShopDTO(item, user);
    }

    public Optional<Shop> findById(UUID itemId) {
        return shopRepository.findById(itemId);
    }
    
    /**
     * Validates if an item can be purchased based on current shop visibility rules.
     * An item is purchasable only if it's currently displayed in either featured or daily sections for a specific user.
     * This logic is critical for preventing users from purchasing expired, inactive, or unlisted items.
     * 
     * @param item The Shop item to validate
     * @param user The User attempting the purchase
     * @return true if item can be purchased, false otherwise
     */
    private boolean isItemPurchasable(Shop item, User user) {
        if (item == null || user == null) {
            return false;
        }

        // Check for stock availability
        if (item.getMaxCopies() != null && item.getCopiesSold() != null && item.getCopiesSold() >= item.getMaxCopies()) {
            logger.warn("Purchase validation failed for user {}: Item {} is sold out", user.getId(), item.getId());
            return false;
        }

        // Check if item is active
        if (!item.getIsActive()) {
            logger.warn("Purchase validation failed for user {}: Item {} is not active", user.getId(), item.getId());
            return false;
        }
        
        // Check if item has expired
        if (item.getExpiresAt() != null && item.getExpiresAt().isBefore(LocalDateTime.now())) {
            logger.warn("Purchase validation failed for user {}: Item {} has expired", user.getId(), item.getId());
            return false;
        }
        
        // Check if the item is featured
        if (item.getIsFeatured()) {
            logger.debug("Purchase validation passed for user {}: Item {} is a featured item.", user.getId(), item.getId());
            return true;
        }

        // If not featured, check if it's a daily item in the user's personalized daily shop
        if (item.getIsDaily()) {
            // Get the user's personalized daily shop items to validate the purchase
            List<Shop> userDailyItems = getUserDailyShopItems(user);
            boolean isInDailyShop = userDailyItems.stream()
                .anyMatch(dailyItem -> dailyItem.getId().equals(item.getId()));
            
            if (isInDailyShop) {
                logger.debug("Purchase validation passed for user {}: Item {} is in user's personalized daily shop.", user.getId(), item.getId());
                return true;
            } else {
                logger.warn("Purchase validation failed for user {}: Item {} is a daily item but not in user's personalized daily shop.", user.getId(), item.getId());
                return false;
            }
        }
        
        // If it's neither featured nor a valid daily item for the user, it's not purchasable
        logger.warn("Purchase validation failed for user {}: Item {} is not featured and not a valid daily item for this user.", user.getId(), item.getId());
        return false;
    }
    
    /**
     * Purchase an item for a user
     * @param userId User ID
     * @param itemId Item ID
     * @return Updated UserProfileDTO
     */
    @Transactional
    public PurchaseResponseDTO purchaseItem(String userId, UUID itemId) {
        return purchaseItem(userId, itemId, 1);
    }
    
    /**
     * Purchase an item for a user with quantity
     * @param userId User ID
     * @param itemId Item ID
     * @param quantity Quantity to purchase (for cases)
     * @return Updated UserProfileDTO
     */
    @Transactional
    public PurchaseResponseDTO purchaseItem(String userId, UUID itemId, Integer quantity) {
        logger.debug("Processing purchase of item {} for user {} with quantity {}", itemId, userId, quantity);
    
        if (quantity == null || quantity < 1 || quantity > 100) { // Allow larger quantities
            createPurchaseAuditEntry(userId, itemId, null, quantity, 0, 0, 0,
                "PURCHASE_FAILED", "Invalid quantity: " + quantity, AuditSeverity.WARNING);
            throw new IllegalArgumentException("Quantity must be between 1 and 100");
        }
    
        User user = userRepository.findByIdWithLock(userId)
            .orElseThrow(() -> {
                createPurchaseAuditEntry(userId, itemId, null, quantity, 0, 0, 0,
                    "PURCHASE_FAILED", "User not found", AuditSeverity.WARNING);
                return new ResourceNotFoundException("User not found with ID: " + userId);
            });
    
        // Lock the shop item to prevent race conditions on copiesSold
        Shop item = shopRepository.findByIdWithLock(itemId)
            .orElseThrow(() -> {
                createPurchaseAuditEntry(userId, itemId, null, quantity, 0, 0, 0,
                    "PURCHASE_FAILED", "Shop item not found", AuditSeverity.WARNING);
                return new ResourceNotFoundException("Shop item not found with ID: " + itemId);
            });
    
        if (!isItemPurchasable(item, user)) {
            createPurchaseAuditEntry(userId, itemId, item, quantity, 0,
                user.getCredits(), user.getCredits(),
                "PURCHASE_FAILED", "Item is not available for purchase (failed isItemPurchasable check)", AuditSeverity.HIGH);
            throw new ItemNotPurchasableException("This item is not currently available for purchase.");
        }
    
        int totalCost = item.getPrice() * quantity;
        int creditsBeforeTransaction = user.getCredits();
    
        if (user.getCredits() < totalCost) {
            createPurchaseAuditEntry(userId, itemId, item, quantity, totalCost,
                creditsBeforeTransaction, creditsBeforeTransaction,
                "PURCHASE_FAILED", "Insufficient credits. Required: " + totalCost + ", Available: " + user.getCredits(), AuditSeverity.WARNING);
            throw new InsufficientCreditsException("Insufficient credits. Required: " + totalCost + ", Available: " + user.getCredits());
        }
    
        if (item.getRequiredRole() != null && !user.hasRole(item.getRequiredRole())) {
            createPurchaseAuditEntry(userId, itemId, item, quantity, totalCost,
                creditsBeforeTransaction, creditsBeforeTransaction,
                "PURCHASE_FAILED", "Role requirement not met: " + item.getRequiredRole(), AuditSeverity.WARNING);
            throw new RoleRequirementNotMetException("This item requires the " + item.getRequiredRole() + " role");
        }
    
        // Limited stock check
        if (item.getMaxCopies() != null) {
            int currentCopiesSold = item.getCopiesSold() != null ? item.getCopiesSold() : 0;
            if (currentCopiesSold + quantity > item.getMaxCopies()) {
                throw new InsufficientStockException("Not enough stock available. Only " + (item.getMaxCopies() - currentCopiesSold) + " left.");
            }
            // Atomically update the copiesSold count before creating instances
            item.setCopiesSold(currentCopiesSold + quantity);
        }
    
        // Check for non-stackable item ownership
        if (!item.getCategory().isStackable()) {
             long ownedCount = user.getItemInstances().stream()
                .filter(instance -> instance.getBaseItem().getId().equals(item.getId()))
                .count();
             if (ownedCount > 0) {
                 throw new ItemAlreadyOwnedException("You already own this unique item.");
             }
             if (quantity > 1) {
                 throw new IllegalArgumentException("Cannot purchase more than one of this unique item at a time.");
             }
        }
    
        // Process purchase
        user.setCredits(creditsBeforeTransaction - totalCost);
    
        List<ItemInstance> newInstances = new ArrayList<>();
        int initialCopiesSold = (item.getCopiesSold() != null ? item.getCopiesSold() : quantity) - quantity;

        for (int i = 0; i < quantity; i++) {
            Long serialNumber = null;
            if (item.getMaxCopies() != null) {
                // The serial number is based on the updated count
                serialNumber = (long) (initialCopiesSold + i + 1);
            }
    
            ItemInstance newInstance = ItemInstance.builder()
                .owner(user)
                .baseItem(item)
                .serialNumber(serialNumber)
                .build();

            if (item.getCategory() == ShopCategory.FISHING_ROD || item.getCategory() == ShopCategory.FISHING_ROD_PART) {
                // ROD_SHAFT parts have infinite durability, so don't set durability for them
                if (item.getCategory() == ShopCategory.FISHING_ROD_PART && 
                    item.getFishingRodPartType() == FishingRodPart.ROD_SHAFT) {
                    // ROD_SHAFT parts don't need durability initialization (infinite durability)
                } else {
                    // Validate that max_durability is set for non-ROD_SHAFT parts
                    if (item.getMaxDurability() == null) {
                        logger.error("Shop item {} ({}) is missing max_durability configuration. This must be fixed in the database.", 
                                   item.getId(), item.getName());
                        throw new IllegalStateException("Shop item " + item.getName() + " is missing required max_durability configuration");
                    }
                    newInstance.setDurability(item.getMaxDurability());
                    newInstance.setMaxDurability(item.getMaxDurability());
                }
                if (item.getCategory() == ShopCategory.FISHING_ROD) {
                    newInstance.setExperience(0L);
                }
            }

            newInstances.add(newInstance);
        }
    
        itemInstanceRepository.saveAll(newInstances);
        shopRepository.save(item);
        userRepository.save(user);
    
        int creditsAfterTransaction = user.getCredits();
    
        createPurchaseAuditEntry(userId, itemId, item, quantity, totalCost,
            creditsBeforeTransaction, creditsAfterTransaction,
            "PURCHASE_SUCCESS", "Purchase completed successfully", AuditSeverity.INFO);
    
        logger.info("User {} successfully purchased {} x{} (total cost: {})", userId, itemId, quantity, totalCost);
    
        ShopDTO purchasedItemDTO = mapToShopDTO(item, user);
        purchasedItemDTO.setQuantity(quantity);
    
        return new PurchaseResponseDTO(userService.mapToProfileDTO(user), purchasedItemDTO);
    }
    

    
    /**
     * Creates an audit entry for shop purchase events
     * This method handles both successful and failed purchases
     * 
     * @param userId User ID making the purchase
     * @param itemId Item ID being purchased
     * @param item Shop item (null if item not found)
     * @param quantity Quantity being purchased
     * @param totalCost Total cost of the purchase
     * @param creditsBeforeTransaction User's credits before transaction
     * @param creditsAfterTransaction User's credits after transaction
     * @param purchaseResult Result of the purchase (SUCCESS/FAILED)
     * @param failureReason Reason for failure (if applicable)
     * @param severity Audit severity level
     */
    private void createPurchaseAuditEntry(String userId, UUID itemId, Shop item, Integer quantity, 
                                        int totalCost, int creditsBeforeTransaction, int creditsAfterTransaction,
                                        String purchaseResult, String failureReason, AuditSeverity severity) {
        try {
            // Build human-readable description
            String description;
            if (item != null) {
                if ("PURCHASE_SUCCESS".equals(purchaseResult)) {
                    description = String.format("Purchased %s x%d for %d credits", 
                        item.getName(), quantity, totalCost);
                } else {
                    description = String.format("Failed to purchase %s x%d for %d credits - %s", 
                        item.getName(), quantity, totalCost, failureReason);
                }
            } else {
                description = String.format("Purchase attempt for item %s x%d - %s", 
                    itemId, quantity, failureReason);
            }
            
            // Build detailed JSON for audit trail
            Map<String, Object> details = new HashMap<>();
            details.put("itemId", itemId.toString());
            details.put("itemName", item != null ? item.getName() : "Unknown");
            details.put("itemCategory", item != null ? item.getCategory().name() : "Unknown");
            details.put("itemPrice", item != null ? item.getPrice() : 0);
            details.put("quantity", quantity);
            details.put("totalCost", totalCost);
            details.put("creditsBeforeTransaction", creditsBeforeTransaction);
            details.put("creditsAfterTransaction", creditsAfterTransaction);
            details.put("purchaseResult", purchaseResult);
            details.put("timestamp", LocalDateTime.now().toString());
            
            if (failureReason != null) {
                details.put("failureReason", failureReason);
            }
            
            String detailsJson;
            try {
                detailsJson = objectMapper.writeValueAsString(details);
            } catch (JsonProcessingException e) {
                logger.error("Failed to serialize purchase details to JSON: {}", e.getMessage());
                detailsJson = "{\"error\": \"Failed to serialize purchase details\"}";
            }
            
            // Create audit entry
            CreateAuditDTO auditDTO = CreateAuditDTO.builder()
                .userId(userId)
                .action("SHOP_PURCHASE")
                .entityType("Shop")
                .entityId(itemId.toString())
                .description(description)
                .severity(severity)
                .category(AuditCategory.FINANCIAL)
                .details(detailsJson)
                .source("ShopService")
                .build();
            
            // Use createSystemAuditEntry for internal operations
            auditService.createSystemAuditEntry(auditDTO);
            
        } catch (Exception e) {
            // Log the error but don't let audit failures break the purchase flow
            logger.error("Failed to create audit entry for purchase - userId: {}, itemId: {}, error: {}", 
                userId, itemId, e.getMessage(), e);
        }
    }
    

    
    /**
     * Map a Shop entity to a ShopDTO
     * @param shop Shop entity
     * @param user Optional user to check ownership status
     * @return ShopDTO
     */
    private ShopDTO mapToShopDTO(Shop shop, User user) {
        return shopMapper.mapToShopDTO(shop, user);
    }

    
    /**
     * Create a new shop item
     * @param shopDTO Shop item DTO
     * @return Created shop item
     */
    @Transactional
    public Shop createShopItem(ShopDTO shopDTO) {
        logger.debug("Creating new shop item: {} with active status: {}", shopDTO.getName(), shopDTO.isActive());
        
        // Sanitize input data before creating entity
        String sanitizedName = htmlSanitizationService.sanitizeStrict(shopDTO.getName());
        String sanitizedDescription = htmlSanitizationService.sanitizeBasic(shopDTO.getDescription());
        
        // Handle imageUrl sanitization based on category
        String sanitizedImageUrl;
        if (shopDTO.getCategory() == ShopCategory.USER_COLOR) {
            // For USER_COLOR items, imageUrl contains a hex color value, not a URL
            sanitizedImageUrl = sanitizeColorValue(shopDTO.getImageUrl());
        } else {
            // For other categories, treat as URL
            sanitizedImageUrl = htmlSanitizationService.sanitizeUrl(shopDTO.getImageUrl());
        }
        
        String sanitizedGradientEndColor = null;
        if (shopDTO.getCategory() == ShopCategory.USER_COLOR) {
            sanitizedGradientEndColor = sanitizeColorValue(shopDTO.getGradientEndColor());
        }
        
        String sanitizedThumbnailUrl = htmlSanitizationService.sanitizeUrl(shopDTO.getThumbnailUrl());
        
        // Validate required fields after sanitization
        if (sanitizedName == null || sanitizedName.trim().isEmpty()) {
            throw new IllegalArgumentException("Item name cannot be empty after sanitization");
        }
        
        if (sanitizedName.length() > 100) {
            throw new IllegalArgumentException("Item name exceeds maximum length of 100 characters after sanitization");
        }
        
        if (sanitizedDescription != null && sanitizedDescription.length() > 500) {
            throw new IllegalArgumentException("Description exceeds maximum length of 500 characters after sanitization");
        }
        
        // Log sanitization if content was modified
        if (!sanitizedName.equals(shopDTO.getName())) {
            logger.info("Shop item name sanitized during creation: '{}' -> '{}'", shopDTO.getName(), sanitizedName);
        }
        if (shopDTO.getDescription() != null && !Objects.equals(sanitizedDescription, shopDTO.getDescription())) {
            logger.info("Shop item description sanitized during creation");
        }
        
        Shop newItem = Shop.builder()
            .name(sanitizedName)
            .description(sanitizedDescription)
            .price(shopDTO.getPrice())
            .category(shopDTO.getCategory())
            .imageUrl(sanitizedImageUrl)
            .requiredRole(shopDTO.getRequiredRole())
            .isActive(shopDTO.isActive())
            .expiresAt(shopDTO.getExpiresAt())
            .discordRoleId(shopDTO.getDiscordRoleId())
            .rarity(shopDTO.getRarity() != null ? shopDTO.getRarity() : ItemRarity.COMMON)
            .thumbnailUrl(sanitizedThumbnailUrl)
            .isFeatured(shopDTO.getIsFeatured())
            .isDaily(shopDTO.getIsDaily())
            .fishingRodMultiplier(shopDTO.getFishingRodMultiplier())
            .gradientEndColor(sanitizedGradientEndColor)
            .maxCopies(shopDTO.getMaxCopies())
            .maxDurability(shopDTO.getMaxDurability())
            .fishingRodPartType(shopDTO.getFishingRodPartType())
            .durabilityIncrease(shopDTO.getDurabilityIncrease())
            .bonusLootChance(shopDTO.getBonusLootChance())
            .rarityChanceIncrease(shopDTO.getRarityChanceIncrease())
            .multiplierIncrease(shopDTO.getMultiplierIncrease())
            .negationChance(shopDTO.getNegationChance())
            .maxRepairs(shopDTO.getMaxRepairs())
            .build();
        
        // Set infinite durability for ROD_SHAFT parts
        if (shopDTO.getCategory() == ShopCategory.FISHING_ROD_PART && 
            shopDTO.getFishingRodPartType() == FishingRodPart.ROD_SHAFT) {
            newItem.setMaxDurability(null);
            newItem.setMaxRepairs(null);
        }
        
        logger.debug("Creating new shop item with sanitized content");
        
        return shopRepository.save(newItem);
    }
    
    /**
     * Update an existing shop item
     * @param itemId Item ID
     * @param shopDTO Updated shop item data
     * @return Updated shop item
     */
    @Transactional
    public Shop updateShopItem(UUID itemId, ShopDTO shopDTO) {
        logger.debug("Updating shop item {}: {} with active status: {}", itemId, shopDTO.getName(), shopDTO.isActive());
        
        Shop existingItem = shopRepository.findById(itemId)
            .orElseThrow(() -> new ResourceNotFoundException("Shop item not found with ID: " + itemId));
        
        // Sanitize input data before updating entity
        String sanitizedName = htmlSanitizationService.sanitizeStrict(shopDTO.getName());
        String sanitizedDescription = htmlSanitizationService.sanitizeBasic(shopDTO.getDescription());
        
        // Handle imageUrl sanitization based on category
        String sanitizedImageUrl;
        if (shopDTO.getCategory() == ShopCategory.USER_COLOR) {
            // For USER_COLOR items, imageUrl contains a hex color value, not a URL
            sanitizedImageUrl = sanitizeColorValue(shopDTO.getImageUrl());
        } else {
            // For other categories, treat as URL
            sanitizedImageUrl = htmlSanitizationService.sanitizeUrl(shopDTO.getImageUrl());
        }
        
        String sanitizedGradientEndColor = null;
        if (shopDTO.getCategory() == ShopCategory.USER_COLOR) {
            sanitizedGradientEndColor = sanitizeColorValue(shopDTO.getGradientEndColor());
        }
        
        String sanitizedThumbnailUrl = htmlSanitizationService.sanitizeUrl(shopDTO.getThumbnailUrl());
        
        // Validate required fields after sanitization
        if (sanitizedName == null || sanitizedName.trim().isEmpty()) {
            throw new IllegalArgumentException("Item name cannot be empty after sanitization");
        }
        
        if (sanitizedName.length() > 100) {
            throw new IllegalArgumentException("Item name exceeds maximum length of 100 characters after sanitization");
        }
        
        if (sanitizedDescription != null && sanitizedDescription.length() > 500) {
            throw new IllegalArgumentException("Description exceeds maximum length of 500 characters after sanitization");
        }
        
        // Log sanitization if content was modified
        if (!sanitizedName.equals(shopDTO.getName())) {
            logger.info("Shop item name sanitized during update: '{}' -> '{}'", shopDTO.getName(), sanitizedName);
        }
        if (shopDTO.getDescription() != null && !Objects.equals(sanitizedDescription, shopDTO.getDescription())) {
            logger.info("Shop item description sanitized during update");
        }
        
        // Update fields with sanitized content
        existingItem.setName(sanitizedName);
        existingItem.setDescription(sanitizedDescription);
        existingItem.setPrice(shopDTO.getPrice());
        existingItem.setCategory(shopDTO.getCategory());
        existingItem.setImageUrl(sanitizedImageUrl);
        existingItem.setRequiredRole(shopDTO.getRequiredRole());
        existingItem.setExpiresAt(shopDTO.getExpiresAt());
        existingItem.setIsActive(shopDTO.isActive());
        existingItem.setDiscordRoleId(shopDTO.getDiscordRoleId());
        existingItem.setRarity(shopDTO.getRarity() != null ? shopDTO.getRarity() : ItemRarity.COMMON);
        existingItem.setThumbnailUrl(sanitizedThumbnailUrl);
        existingItem.setIsFeatured(shopDTO.getIsFeatured());
        existingItem.setIsDaily(shopDTO.getIsDaily());
        existingItem.setFishingRodMultiplier(shopDTO.getFishingRodMultiplier());
        existingItem.setGradientEndColor(sanitizedGradientEndColor);
        existingItem.setMaxCopies(shopDTO.getMaxCopies());
        existingItem.setMaxDurability(shopDTO.getMaxDurability());
        existingItem.setFishingRodPartType(shopDTO.getFishingRodPartType());
        existingItem.setDurabilityIncrease(shopDTO.getDurabilityIncrease());
        existingItem.setBonusLootChance(shopDTO.getBonusLootChance());
        existingItem.setRarityChanceIncrease(shopDTO.getRarityChanceIncrease());
        existingItem.setMultiplierIncrease(shopDTO.getMultiplierIncrease());
        existingItem.setNegationChance(shopDTO.getNegationChance());
        existingItem.setMaxRepairs(shopDTO.getMaxRepairs());
        
        // Set infinite durability for ROD_SHAFT parts
        if (shopDTO.getCategory() == ShopCategory.FISHING_ROD_PART && 
            shopDTO.getFishingRodPartType() == FishingRodPart.ROD_SHAFT) {
            existingItem.setMaxDurability(null);
            existingItem.setMaxRepairs(null);
        }
        
        logger.debug("Updating shop item with ID: {} with sanitized content", existingItem.getId());
        
        return shopRepository.save(existingItem);
    }

    /**
     * Update an item's price (admin only)
     * @param itemId Item ID
     * @param newPrice New price
     */
    @Transactional
    public void updateItemPrice(UUID itemId, int newPrice) {
        if (newPrice < 0) {
            throw new IllegalArgumentException("Price cannot be negative.");
        }
        
        Shop item = shopRepository.findById(itemId)
            .orElseThrow(() -> new ResourceNotFoundException("Shop item not found with ID: " + itemId));
        
        item.setPrice(newPrice);
        shopRepository.save(item);
        logger.info("Updated price for item {} to {}", itemId, newPrice);
    }

    /**
     * Update an item's active status (admin only)
     * @param itemId Item ID
     * @param newStatus New active status
     */
    @Transactional
    public void updateItemStatus(UUID itemId, boolean newStatus) {
        Shop item = shopRepository.findById(itemId)
            .orElseThrow(() -> new ResourceNotFoundException("Shop item not found with ID: " + itemId));
        
        item.setIsActive(newStatus);
        shopRepository.save(item);
        logger.info("Updated active status for item {} to {}", itemId, newStatus);
    }
    
    /**
     * Delete a shop item completely with cascade delete handling
     * @param itemId Item ID
     * @throws ItemDeletionException if the item cannot be deleted
     * @throws ItemReferencedInCasesException if the item is referenced in cases (with cascade info)
     */
    @Transactional
    public void deleteShopItem(UUID itemId) {
        logger.debug("Attempting to delete shop item {}", itemId);
        
        Shop item = shopRepository.findById(itemId)
            .orElseThrow(() -> new ResourceNotFoundException("Shop item not found with ID: " + itemId));
        
        try {
            // Step 1: Check for case references and handle cascade deletion
            handleCaseReferences(item);
            
            // Step 2: Remove from user inventories (both old and new inventory systems)
            cleanupUserInventories(item);
            
            // Step 3: Perform the actual deletion
            shopRepository.delete(item);
            
            logger.info("Successfully deleted shop item {} with cascade cleanup", itemId);
            
        } catch (Exception e) {
            logger.error("Failed to delete shop item {}: {}", itemId, e.getMessage(), e);
            
            // If it's one of our custom exceptions, re-throw it
            if (e instanceof ItemDeletionException) {
                throw e;
            }
            
            // For any other exceptions, wrap in our custom exception
            throw new ItemDeletionException(
                "Failed to delete item due to an unexpected error: " + e.getMessage(), e
            );
        }
    }
    
    /**
     * Handle case references during item deletion
     * @param item The item being deleted
     * @throws ItemReferencedInCasesException if the item has case references that need attention
     */
    private void handleCaseReferences(Shop item) {
        logger.debug("Checking case references for item {}", item.getId());
        
        // Check if this item is a case itself
        if (item.getCategory() == ShopCategory.CASE) {
            List<CaseItem> caseContents = caseItemRepository.findByCaseShopItem(item);
            if (!caseContents.isEmpty()) {
                logger.info("Deleting {} case items for case {}", caseContents.size(), item.getId());
                caseItemRepository.deleteByCaseShopItem(item);
            }
        }
        
        // Check if this item is contained in any cases
        List<CaseItem> containingCases = caseItemRepository.findByContainedItem(item);
        if (!containingCases.isEmpty()) {
            // Extract the case IDs for reporting
            List<UUID> caseIds = containingCases.stream()
                .map(caseItem -> caseItem.getCaseShopItem().getId())
                .distinct()
                .collect(Collectors.toList());
                
            logger.info("Item {} is contained in {} cases. Removing from cases: {}", 
                       item.getId(), caseIds.size(), caseIds);
            
            // Remove the item from all cases that contain it
            caseItemRepository.deleteByContainedItem(item);
            
            // Log the cascade operation for audit purposes
            logger.warn("Cascade deletion: Removed item {} from {} cases during deletion. " +
                       "This may affect case drop rates and should be reviewed.", 
                       item.getId(), caseIds.size());
        }
    }
    
    /**
     * Clean up user inventories for the item being deleted
     * @param item The item being deleted
     */
    private void cleanupUserInventories(Shop item) {
        logger.debug("Cleaning up user inventories for item {}", item.getId());

        List<ItemInstance> instancesToDelete = itemInstanceRepository.findByBaseItem(item);
        if (instancesToDelete.isEmpty()) {
            return;
        }

        // Unequip parts from any rods that have them equipped
        if (item.getCategory() == ShopCategory.FISHING_ROD_PART) {
            List<ItemInstance> rodsToUpdate = itemInstanceRepository.findRodsWithEquippedParts(instancesToDelete);
            for (ItemInstance rod : rodsToUpdate) {
                // Handle durability adjustments for each part being removed
                Integer currentMaxDurability = rod.getMaxDurability() != null ? rod.getMaxDurability() : rod.getBaseItem().getMaxDurability();
                
                if (currentMaxDurability != null) {
                    boolean isDurabilityIncreasePart = item.getDurabilityIncrease() != null && item.getDurabilityIncrease() > 0;
                    
                    if (isDurabilityIncreasePart) {
                        // Decrease max durability by removing the part's contribution
                        int newMaxDurability = currentMaxDurability - item.getDurabilityIncrease();
                        rod.setMaxDurability(newMaxDurability);
                        
                        // Cap current durability if it exceeds new max durability
                        if (rod.getDurability() != null && rod.getDurability() > newMaxDurability) {
                            rod.setDurability(newMaxDurability);
                        }
                    }
                }
                
                // Unequip the parts
                if (rod.getEquippedRodShaft() != null && instancesToDelete.contains(rod.getEquippedRodShaft())) {
                    rod.setEquippedRodShaft(null);
                }
                if (rod.getEquippedReel() != null && instancesToDelete.contains(rod.getEquippedReel())) {
                    rod.setEquippedReel(null);
                }
                if (rod.getEquippedFishingLine() != null && instancesToDelete.contains(rod.getEquippedFishingLine())) {
                    rod.setEquippedFishingLine(null);
                }
                if (rod.getEquippedHook() != null && instancesToDelete.contains(rod.getEquippedHook())) {
                    rod.setEquippedHook(null);
                }
                if (rod.getEquippedGrip() != null && instancesToDelete.contains(rod.getEquippedGrip())) {
                    rod.setEquippedGrip(null);
                }
            }
            itemInstanceRepository.saveAll(rodsToUpdate);
        }

        Integer refundAmount = item.getPrice();
        boolean needsRefund = refundAmount != null && refundAmount > 0;

        // Group instances by user ID to process refunds and unequip logic.
        Map<String, List<ItemInstance>> instancesByUser = instancesToDelete.stream()
                .collect(Collectors.groupingBy(instance -> instance.getOwner().getId()));

        if (!instancesByUser.isEmpty()) {
            List<User> affectedUsers = userRepository.findAllById(instancesByUser.keySet());

            for (User user : affectedUsers) {
                List<ItemInstance> userInstances = instancesByUser.get(user.getId());
                long ownedCount = userInstances.size();
                
                // 1. Issue refund if necessary
                if (needsRefund) {
                    int totalRefund = (int) (refundAmount * ownedCount);
                    user.setCredits(user.getCredits() + totalRefund);
                }

                // 2. Unequip the item if it was equipped
                if (item.getCategory() != null && item.getCategory().isEquippable()) {
                    // For non-instanced items, we check the base item ID
                    if (!item.getCategory().equals(ShopCategory.FISHING_ROD)) {
                        UUID equippedId = user.getEquippedItemIdByCategory(item.getCategory());
                        if (item.getId().equals(equippedId)) {
                            user.setEquippedItemIdByCategory(item.getCategory(), null);
                            logger.debug("Unequipped deleted item {} for user {}", item.getId(), user.getId());
                        }
                    } else { // For fishing rods, we check the instance ID
                        UUID equippedInstanceId = user.getEquippedFishingRodInstanceId();
                        if (equippedInstanceId != null && userInstances.stream().anyMatch(inst -> inst.getId().equals(equippedInstanceId))) {
                            user.setEquippedFishingRodInstanceId(null);
                            logger.debug("Unequipped deleted fishing rod instance for user {}", user.getId());
                        }
                    }
                    
                    // Also handle Discord role removal if applicable
                    if (item.getDiscordRoleId() != null && !item.getDiscordRoleId().isEmpty()) {
                        discordService.removeRole(user.getId(), item.getDiscordRoleId());
                        logger.debug("Removed Discord role {} for deleted item from user {}", item.getDiscordRoleId(), user.getId());
                    }
                }
                
                // 3. Remove the instances from the user's collection to prevent TransientObjectException
                user.getItemInstances().removeAll(userInstances);
            }

            // Save all user changes in a batch.
            userRepository.saveAll(affectedUsers);
            logger.info("Processed refunds and unequips for {} users affected by deletion of item '{}'.", affectedUsers.size(), item.getName());
        }

        // Finally, remove all instances of the item from the inventory.
        itemInstanceRepository.deleteAllInBatch(instancesToDelete);
        logger.info("Removing {} instances of item {} from all user inventories.", instancesToDelete.size(), item.getId());
    }
    
    /**
     * Sanitize and validate a hex color value for USER_COLOR items
     * @param colorValue The color value to sanitize
     * @return Sanitized color value or null if invalid
     */
    private String sanitizeColorValue(String colorValue) {
        if (colorValue == null || colorValue.trim().isEmpty()) {
            return null;
        }
        
        String trimmed = colorValue.trim();
        
        // Check if it's a valid hex color format
        if (trimmed.matches("^#[0-9A-Fa-f]{6}$")) {
            return trimmed.toUpperCase(); // Normalize to uppercase
        }
        
        // Log invalid color attempts for security monitoring
        logger.warn("Invalid color value rejected for USER_COLOR item: {}", trimmed);
        return null;
    }
    
    /**
     * Get all shop items including inactive ones (admin only)
     * @return List of all shop items
     */
    public List<ShopDTO> getAllShopItems() {
        List<Shop> items = shopRepository.findAll();
        LocalDateTime now = LocalDateTime.now();
        
        return items.stream()
            .map(item -> {
                ShopDTO dto = mapToShopDTO(item, null);
                // Add isActive status to the DTO for admin UI
                dto.setActive(item.getIsActive());
                
                // Determine if item is expired
                boolean expired = item.getExpiresAt() != null && 
                                 item.getExpiresAt().isBefore(now);
                dto.setExpired(expired);
                
                return dto;
            })
            .collect(Collectors.toList());
    }
    
    /**
     * Get all distinct shop categories from active items
     * @return List of unique category names as strings
     */
    public List<String> getShopCategories() {
        logger.debug("Fetching all distinct shop categories");
        
        // Get all active shop items
        List<Shop> items = shopRepository.findByIsActiveTrue();
        
        // Extract unique categories and convert to strings
        return items.stream()
            .map(Shop::getCategory)
            .filter(Objects::nonNull)
            .distinct()
            .map(ShopCategory::name)  // Convert enum to string
            .sorted()  // Optional: sort categories alphabetically
            .collect(Collectors.toList());
    }
}