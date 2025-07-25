package com.app.heartbound.services.shop;

import com.app.heartbound.dto.UserProfileDTO;
import com.app.heartbound.dto.shop.ShopDTO;
import com.app.heartbound.dto.shop.UserInventoryDTO;
import com.app.heartbound.dto.shop.PurchaseResponseDTO;
import com.app.heartbound.dto.shop.CaseContentsDTO;
import com.app.heartbound.dto.shop.CaseItemDTO;
import com.app.heartbound.dto.shop.RollResultDTO;
import com.app.heartbound.entities.Shop;
import com.app.heartbound.entities.User;
import com.app.heartbound.entities.CaseItem;
import com.app.heartbound.entities.ItemInstance;
import com.app.heartbound.enums.ShopCategory;
import com.app.heartbound.enums.ItemRarity;
import com.app.heartbound.exceptions.ResourceNotFoundException;
import com.app.heartbound.exceptions.shop.InsufficientCreditsException;
import com.app.heartbound.exceptions.shop.ItemAlreadyOwnedException;
import com.app.heartbound.exceptions.shop.ItemNotEquippableException;
import com.app.heartbound.exceptions.shop.ItemNotOwnedException;
import com.app.heartbound.exceptions.shop.RoleRequirementNotMetException;
import com.app.heartbound.exceptions.shop.CaseNotFoundException;
import com.app.heartbound.exceptions.shop.CaseNotOwnedException;
import com.app.heartbound.exceptions.shop.EmptyCaseException;
import com.app.heartbound.exceptions.shop.InvalidCaseContentsException;
import com.app.heartbound.exceptions.shop.ItemDeletionException;
import com.app.heartbound.exceptions.shop.ItemReferencedInCasesException;
import com.app.heartbound.exceptions.shop.InsufficientStockException;
import com.app.heartbound.repositories.UserRepository;
import com.app.heartbound.repositories.shop.ShopRepository;
import com.app.heartbound.repositories.shop.CaseItemRepository;
import com.app.heartbound.repositories.ItemInstanceRepository;
import com.app.heartbound.repositories.RollAuditRepository;
import com.app.heartbound.entities.RollAudit;
import com.app.heartbound.services.UserService;
import com.app.heartbound.services.discord.DiscordService;
import com.app.heartbound.services.HtmlSanitizationService;
import com.app.heartbound.services.SecureRandomService;
import com.app.heartbound.services.RollVerificationService;
import com.app.heartbound.services.AuditService;
import com.app.heartbound.config.CacheConfig;
import com.app.heartbound.dto.CreateAuditDTO;
import com.app.heartbound.enums.AuditSeverity;
import com.app.heartbound.enums.AuditCategory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

@Service
public class ShopService {
    
    private final ShopRepository shopRepository;
    private final UserRepository userRepository;
    private final ItemInstanceRepository itemInstanceRepository;
    private final UserService userService;
    private final DiscordService discordService;
    private final CaseItemRepository caseItemRepository;
    private final HtmlSanitizationService htmlSanitizationService;
    private final SecureRandomService secureRandomService;
    private final RollAuditRepository rollAuditRepository;
    private final RollVerificationService rollVerificationService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final CacheConfig cacheConfig;
    private final EntityManager entityManager;
    private static final Logger logger = LoggerFactory.getLogger(ShopService.class);
    
    public ShopService(
        ShopRepository shopRepository,
        UserRepository userRepository,
        ItemInstanceRepository itemInstanceRepository,
        UserService userService,
        @Lazy DiscordService discordService,
        CaseItemRepository caseItemRepository,
        HtmlSanitizationService htmlSanitizationService,
        SecureRandomService secureRandomService,
        RollAuditRepository rollAuditRepository,
        RollVerificationService rollVerificationService,
        AuditService auditService,
        CacheConfig cacheConfig,
        EntityManager entityManager
    ) {
        this.shopRepository = shopRepository;
        this.userRepository = userRepository;
        this.itemInstanceRepository = itemInstanceRepository;
        this.userService = userService;
        this.discordService = discordService;
        this.caseItemRepository = caseItemRepository;
        this.htmlSanitizationService = htmlSanitizationService;
        this.secureRandomService = secureRandomService;
        this.rollAuditRepository = rollAuditRepository;
        this.rollVerificationService = rollVerificationService;
        this.auditService = auditService;
        this.cacheConfig = cacheConfig;
        this.entityManager = entityManager;
        this.objectMapper = new ObjectMapper();
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
                items = shopRepository.findByCategoryAndIsActiveTrue(category)
                    .stream()
                    .filter(item -> item.getExpiresAt() == null || item.getExpiresAt().isAfter(now))
                    .collect(Collectors.toList());
            } catch (IllegalArgumentException e) {
                // Invalid category string, return empty list
                logger.warn("Invalid category filter: {}", categoryStr);
                return Collections.emptyList();
            }
        } else {
            // Get all active items that either have no expiry or haven't expired yet
            items = shopRepository.findByIsActiveTrue()
                .stream()
                .filter(item -> item.getExpiresAt() == null || item.getExpiresAt().isAfter(now))
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
     * Get daily shop items for the main layout
     * @param userId User ID to check ownership status
     * @return List of daily shop items
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "userDailyItems", key = "#userId")
    public List<ShopDTO> getDailyItems(String userId) {
        logger.debug("Generating daily items for user {}", userId);

        // 1. Create a deterministic seed for the user and the current day (using UTC)
        String seedString = userId + java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString();
        long seed = seedString.hashCode();
        Random random = new Random(seed);

        // 2. Fetch all eligible items for the daily pool, excluding cases
        List<Shop> itemPool = shopRepository.findByIsDailyTrueAndIsActiveTrueAndExpiresAtAfterOrExpiresAtIsNull(LocalDateTime.now())
                .stream()
                .filter(item -> item.getCategory() != ShopCategory.CASE)
                .collect(Collectors.toList());

        // 3. Group items by rarity
        Map<ItemRarity, List<Shop>> itemsByRarity = itemPool.stream()
                .collect(Collectors.groupingBy(Shop::getRarity));

        // Initialize lists for each rarity, handling null cases
        List<Shop> legendaryItems = itemsByRarity.getOrDefault(ItemRarity.LEGENDARY, Collections.emptyList());
        List<Shop> epicItems = itemsByRarity.getOrDefault(ItemRarity.EPIC, Collections.emptyList());
        List<Shop> rareItems = itemsByRarity.getOrDefault(ItemRarity.RARE, Collections.emptyList());
        List<Shop> uncommonItems = itemsByRarity.getOrDefault(ItemRarity.UNCOMMON, Collections.emptyList());
        List<Shop> commonItems = itemsByRarity.getOrDefault(ItemRarity.COMMON, Collections.emptyList());

        List<Shop> lowerTierItems = new ArrayList<>();
        lowerTierItems.addAll(rareItems);
        lowerTierItems.addAll(uncommonItems);
        lowerTierItems.addAll(commonItems);

        List<Shop> selectedItems = new ArrayList<>();

        // 4. Select items based on rarity distribution with fallback logic
        // Select 1 Legendary item
        selectAndAddItem(selectedItems, legendaryItems, 1, random, 
            Arrays.asList(epicItems, lowerTierItems));
        
        // Select 1 Epic item
        selectAndAddItem(selectedItems, epicItems, 1, random, 
            Arrays.asList(legendaryItems, lowerTierItems));

        // Select 2 from lower tiers
        selectAndAddItem(selectedItems, lowerTierItems, 2, random, 
            Arrays.asList(epicItems, legendaryItems));
        
        // Enforce the 'max one fishing rod' rule
        long fishingRodCount = selectedItems.stream()
            .filter(item -> item.getCategory() == ShopCategory.FISHING_ROD)
            .count();

        if (fishingRodCount > 1) {
            List<Shop> rodsToReplace = selectedItems.stream()
                .filter(item -> item.getCategory() == ShopCategory.FISHING_ROD)
                .skip(1) // Keep the first one, collect the rest
                .collect(Collectors.toList());

            for (Shop rodToReplace : rodsToReplace) {
                ItemRarity rarityToMatch = rodToReplace.getRarity();

                // Attempt to find a replacement of the same rarity
                List<Shop> replacementPool = itemsByRarity.getOrDefault(rarityToMatch, Collections.emptyList())
                    .stream()
                    .filter(item -> item.getCategory() != ShopCategory.FISHING_ROD && !selectedItems.contains(item))
                    .collect(Collectors.toList());

                Shop replacement = null;
                if (!replacementPool.isEmpty()) {
                    replacement = replacementPool.get(random.nextInt(replacementPool.size()));
                } else {
                    // If no same-rarity replacement, try the general pool
                    List<Shop> generalReplacementPool = itemPool.stream()
                        .filter(item -> item.getCategory() != ShopCategory.FISHING_ROD && !selectedItems.contains(item))
                        .collect(Collectors.toList());
                    if (!generalReplacementPool.isEmpty()) {
                        replacement = generalReplacementPool.get(random.nextInt(generalReplacementPool.size()));
                    }
                }

                // Perform the replacement or removal
                int index = selectedItems.indexOf(rodToReplace);
                if (index != -1) {
                    if (replacement != null) {
                        selectedItems.set(index, replacement);
                        logger.debug("Replaced extra fishing rod with {} for user {}", replacement.getName(), userId);
                    } else {
                        selectedItems.remove(index);
                        logger.warn("Removed extra fishing rod because no replacement could be found. User {} may see fewer than 4 daily items.", userId);
                    }
                }
            }
        }
        
        // Final fallback to ensure 4 items if possible
        if (selectedItems.size() < 4) {
            List<Shop> remainingPool = new ArrayList<>(itemPool);
            remainingPool.removeAll(selectedItems);
            
            while (selectedItems.size() < 4 && !remainingPool.isEmpty()) {
                int randomIndex = random.nextInt(remainingPool.size());
                Shop item = remainingPool.remove(randomIndex);
                if (!selectedItems.contains(item)) {
                    selectedItems.add(item);
                }
            }
        }

        // 5. Convert to DTOs
        User user = userRepository.findById(userId).orElse(null);
        final User finalUser = user;
        return selectedItems.stream()
            .map(item -> mapToShopDTO(item, finalUser))
            .collect(Collectors.toList());
    }

    private void selectAndAddItem(List<Shop> selectedItems, List<Shop> sourceList, int count, Random random, List<List<Shop>> fallbacks) {
        List<Shop> pool = new ArrayList<>(sourceList);
        for (int i = 0; i < count; i++) {
            if (!pool.isEmpty()) {
                int randomIndex = random.nextInt(pool.size());
                Shop selected = pool.remove(randomIndex);
                if (!selectedItems.contains(selected)) {
                    selectedItems.add(selected);
                }
            } else {
                // Fallback logic
                for (List<Shop> fallbackPool : fallbacks) {
                    List<Shop> availableFallbacks = new ArrayList<>(fallbackPool);
                    availableFallbacks.removeAll(selectedItems);
                    if (!availableFallbacks.isEmpty()) {
                        int fallbackIndex = random.nextInt(availableFallbacks.size());
                        Shop fallbackItem = availableFallbacks.get(fallbackIndex);
                        if (!selectedItems.contains(fallbackItem)) {
                             selectedItems.add(fallbackItem);
                             // We found one, so break from the fallback loop
                             break;
                        }
                    }
                }
            }
        }
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
     * An item is purchasable only if it's currently displayed in either featured or daily sections.
     * This logic is critical for preventing users from purchasing expired, inactive, or unlisted items.
     * 
     * @param item The Shop item to validate
     * @return true if item can be purchased, false otherwise
     */
    private boolean isItemPurchasable(Shop item) {
        if (item == null) {
            return false;
        }

        // Check if item is active
        if (!item.getIsActive()) {
            logger.warn("Purchase validation failed: Item {} is not active", item.getId());
            return false;
        }
        
        // Check if item has expired
        if (item.getExpiresAt() != null && item.getExpiresAt().isBefore(LocalDateTime.now())) {
            logger.warn("Purchase validation failed: Item {} has expired", item.getId());
            return false;
        }
        
        // Security Check: Item must be either featured or daily to be purchasable
        boolean isFeatured = item.getIsFeatured();
        boolean isDaily = item.getIsDaily();
        
        if (!isFeatured && !isDaily) {
            logger.warn("Purchase validation failed: Item {} is not currently displayed in shop layout (not featured or daily)", item.getId());
            return false;
        }
        
        logger.debug("Purchase validation passed: Item {} is purchasable (featured: {}, daily: {})", 
                    item.getId(), isFeatured, isDaily);
        return true;
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
    
        User user = userRepository.findByIdWithLock(userId, LockModeType.PESSIMISTIC_WRITE)
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
    
        if (!isItemPurchasable(item)) {
            createPurchaseAuditEntry(userId, itemId, item, quantity, 0,
                user.getCredits(), user.getCredits(),
                "PURCHASE_FAILED", "Item is not available for purchase (failed isItemPurchasable check)", AuditSeverity.HIGH);
            throw new ResourceNotFoundException("This item is not currently available for purchase");
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
            if (currentCopiesSold >= item.getMaxCopies()) {
                throw new InsufficientStockException("This item is sold out.");
            }
            if (currentCopiesSold + quantity > item.getMaxCopies()) {
                throw new InsufficientStockException("Not enough stock available. Only " + (item.getMaxCopies() - currentCopiesSold) + " left.");
            }
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
        for (int i = 0; i < quantity; i++) {
            Long serialNumber = null;
            if (item.getMaxCopies() != null) {
                int currentCopiesSold = item.getCopiesSold() != null ? item.getCopiesSold() : 0;
                item.setCopiesSold(currentCopiesSold + 1);
                serialNumber = (long) item.getCopiesSold();
            }
    
            ItemInstance newInstance = ItemInstance.builder()
                .owner(user)
                .baseItem(item)
                .serialNumber(serialNumber)
                .build();
            newInstances.add(newInstance);
        }
    
        if (item.getMaxCopies() != null && item.getCopiesSold() >= item.getMaxCopies()) {
            item.setIsActive(false);
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
     * Creates an audit entry for case roll events
     * This method creates entries in the main audit system for admin visibility
     * 
     * @param userId User ID performing the case roll
     * @param caseId Case ID that was opened
     * @param caseItem The case item that was opened
     * @param wonItem The item that was won
     * @param alreadyOwned Whether the user already owned the item
     * @param compensationAwarded Whether compensation was awarded
     * @param compensatedCredits Credits awarded as compensation
     * @param compensatedXp XP awarded as compensation
     * @param rollValue The roll value (0-99)
     * @param dropRate The drop rate of the won item
     * @param creditsSpent Credits spent on the case (negative if compensation awarded)
     */
    private void createCaseRollAuditEntry(String userId, UUID caseId, Shop caseItem, Shop wonItem, 
                                        boolean alreadyOwned, boolean compensationAwarded, 
                                        int compensatedCredits, int compensatedXp, int rollValue, 
                                        int dropRate, int creditsSpent) {
        try {
            // Build human-readable description
            String description;
            if (alreadyOwned && compensationAwarded) {
                description = String.format("Opened case '%s' and received duplicate item '%s' (rarity: %s) - awarded %d credits and %d XP as compensation", 
                    caseItem.getName(), wonItem.getName(), wonItem.getRarity().name(), compensatedCredits, compensatedXp);
            } else {
                description = String.format("Opened case '%s' and received item '%s' (rarity: %s)", 
                    caseItem.getName(), wonItem.getName(), wonItem.getRarity().name());
            }
            
            // Build detailed JSON for audit trail
            Map<String, Object> details = new HashMap<>();
            details.put("caseId", caseId.toString());
            details.put("caseName", caseItem.getName());
            details.put("wonItemId", wonItem.getId().toString());
            details.put("wonItemName", wonItem.getName());
            details.put("wonItemRarity", wonItem.getRarity().name());
            details.put("wonItemPrice", wonItem.getPrice());
            details.put("rollValue", rollValue);
            details.put("dropRate", dropRate);
            details.put("alreadyOwned", alreadyOwned);
            details.put("creditsSpent", creditsSpent);
            details.put("timestamp", LocalDateTime.now().toString());
            
            if (compensationAwarded) {
                details.put("compensationAwarded", true);
                details.put("compensatedCredits", compensatedCredits);
                details.put("compensatedXp", compensatedXp);
            }
            
            String detailsJson;
            try {
                detailsJson = objectMapper.writeValueAsString(details);
            } catch (JsonProcessingException e) {
                logger.error("Failed to serialize case roll details to JSON: {}", e.getMessage());
                detailsJson = "{\"error\": \"Failed to serialize case roll details\"}";
            }
            
            // Determine severity based on outcome
            AuditSeverity severity = alreadyOwned ? AuditSeverity.WARNING : AuditSeverity.INFO;
            
            // Create audit entry
            CreateAuditDTO auditDTO = CreateAuditDTO.builder()
                .userId(userId)
                .action("CASE_ROLL")
                .entityType("Shop")
                .entityId(caseId.toString())
                .description(description)
                .severity(severity)
                .category(AuditCategory.FINANCIAL)
                .details(detailsJson)
                .source("ShopService")
                .build();
            
            // Use createSystemAuditEntry for internal operations
            auditService.createSystemAuditEntry(auditDTO);
            
        } catch (Exception e) {
            // Log the error but don't let audit failures break the case roll flow
            logger.error("Failed to create audit entry for case roll - userId: {}, caseId: {}, error: {}", 
                userId, caseId, e.getMessage(), e);
        }
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
     * Equips an item for a user
     * @param userId User ID
     * @param itemId Item ID
     * @return Updated UserProfileDTO
     */
    @Transactional
    public UserProfileDTO equipItem(String userId, UUID itemId) {
        logger.debug("Equipping item {} for user {}", itemId, userId);
        
        // Get user and item
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));
        
        Shop item = shopRepository.findById(itemId)
            .orElseThrow(() -> new ResourceNotFoundException("Shop item not found with ID: " + itemId));
        
        // Perform equip logic
        performEquipItem(user, item);
        
        // Save user changes
        user = userRepository.save(user);
        
        // Return updated profile
        return userService.mapToProfileDTO(user);
    }
    
    /**
     * Equips multiple items for a user in a single atomic transaction
     * @param userId User ID
     * @param itemIds List of item IDs to equip
     * @return Updated UserProfileDTO
     */
    @Transactional
    public UserProfileDTO equipBatch(String userId, List<UUID> itemIds) {
        logger.debug("Batch equipping {} items for user {}", itemIds.size(), userId);
        
        // Validate input
        if (itemIds == null || itemIds.isEmpty()) {
            throw new IllegalArgumentException("Item IDs list cannot be empty");
        }
        
        // Remove duplicates while preserving order
        List<UUID> uniqueItemIds = itemIds.stream()
            .distinct()
            .collect(Collectors.toList());
            
        if (uniqueItemIds.size() != itemIds.size()) {
            logger.debug("Removed {} duplicate item IDs from batch equip request", 
                        itemIds.size() - uniqueItemIds.size());
        }
        
        // Get user
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));
        
        // Fetch all items and validate them first
        List<Shop> itemsToEquip = new ArrayList<>();
        int badgeCount = 0;
        for (UUID itemId : uniqueItemIds) {
            Shop item = shopRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Shop item not found with ID: " + itemId));
            itemsToEquip.add(item);
            
            // Count badges to ensure only one badge is included in batch request
            if (item.getCategory() == ShopCategory.BADGE) {
                badgeCount++;
            }
        }
        
        // Validate that only one badge is included in the batch request
        if (badgeCount > 1) {
            throw new IllegalArgumentException("Only one badge can be equipped at a time. Please select only one badge for batch equipping.");
        }
        
        // Validate all items before equipping any
        for (Shop item : itemsToEquip) {
            validateItemForEquipping(user, item);
        }
        
        // Perform equip operations for all items
        for (Shop item : itemsToEquip) {
            try {
                performEquipItem(user, item);
                logger.debug("Successfully equipped item {} for user {}", item.getId(), userId);
            } catch (Exception e) {
                logger.error("Failed to equip item {} for user {}: {}", item.getId(), userId, e.getMessage(), e);
                throw new RuntimeException("Failed to equip item: " + item.getName(), e);
            }
        }
        
        // Save user changes once after all equips
        user = userRepository.save(user);
        
        logger.info("Successfully batch equipped {} items for user {}", itemsToEquip.size(), userId);
        
        // Return updated profile
        return userService.mapToProfileDTO(user);
    }
    
    /**
     * Validates that an item can be equipped by a user
     * @param user User attempting to equip
     * @param item Item to validate
     * @throws ItemNotOwnedException if user doesn't own the item
     * @throws ItemNotEquippableException if item cannot be equipped
     */
    private void validateItemForEquipping(User user, Shop item) {
        // Check if user owns the item
        if (!user.hasItem(item.getId())) {
            throw new ItemNotOwnedException("You don't own this item: " + item.getName());
        }
        
        // Check if item can be equipped
        ShopCategory category = item.getCategory();
        if (category == null) {
            throw new ItemNotEquippableException("This item cannot be equipped: " + item.getName());
        }
        
        // Cases cannot be equipped
        if (category == ShopCategory.CASE) {
            throw new ItemNotEquippableException("Cases cannot be equipped: " + item.getName());
        }
    }
    
    /**
     * Performs the actual equip operation for a single item
     * This method contains the core equip logic shared between single and batch operations
     * @param user User to equip item for
     * @param item Item to equip
     */
    private void performEquipItem(User user, Shop item) {
        // Validate the item can be equipped
        validateItemForEquipping(user, item);
        
        // Set the item as equipped based on its category
        ShopCategory category = item.getCategory();
        
        // Special handling for BADGE category - single badge only
        if (category == ShopCategory.BADGE) {
            // Check if badge is already equipped
            if (user.isBadgeEquipped(item.getId())) {
                logger.debug("Badge {} is already equipped for user {}", item.getId(), user.getId());
                return; // Already equipped, nothing to do
            }
            
            // Handle Discord role management for previously equipped badge
            UUID previousBadgeId = user.getEquippedBadgeId();
            if (previousBadgeId != null && !previousBadgeId.equals(item.getId())) {
                // Find the previous badge to get its Discord role ID and handle removal
                shopRepository.findById(previousBadgeId).ifPresent(previousBadge -> {
                    String previousRoleId = previousBadge.getDiscordRoleId();
                    if (previousRoleId != null && !previousRoleId.isEmpty()) {
                        logger.debug("Removing previous Discord role {} from user {} before equipping new badge", 
                                previousRoleId, user.getId());
                        
                        boolean removalSuccess = discordService.removeRole(user.getId(), previousRoleId);
                        if (!removalSuccess) {
                            logger.warn("Failed to remove previous Discord role {} from user {}. " +
                                    "Continuing with equipping new badge.", previousRoleId, user.getId());
                        } else {
                            logger.debug("Successfully removed previous Discord role {} from user {}", 
                                    previousRoleId, user.getId());
                        }
                    }
                });
            }
            
            // Set the new badge as equipped (replaces any existing badge)
            user.setEquippedBadge(item.getId());
            logger.debug("Set badge {} as equipped for user {}", item.getId(), user.getId());
            
            // Apply Discord role if applicable
            if (item.getDiscordRoleId() != null && !item.getDiscordRoleId().isEmpty()) {
                logger.debug("Adding Discord role {} for user {} for badge", 
                           item.getDiscordRoleId(), user.getId());
                boolean grantSuccess = discordService.grantRole(user.getId(), item.getDiscordRoleId());
                if (!grantSuccess) {
                    logger.warn("Failed to grant Discord role {} to user {} for badge", 
                              item.getDiscordRoleId(), user.getId());
                }
            }
        } else if (category == ShopCategory.USER_COLOR) {
            // Handle Discord role management for USER_COLOR items
            // Check if there was a previously equipped item of the same category
            UUID previousItemId = user.getEquippedItemIdByCategory(category);
            if (previousItemId != null && !previousItemId.equals(item.getId())) {
                // Find the previous item to get its Discord role ID and handle removal synchronously
                shopRepository.findById(previousItemId).ifPresent(previousItem -> {
                    String previousRoleId = previousItem.getDiscordRoleId();
                    if (previousRoleId != null && !previousRoleId.isEmpty()) {
                        logger.debug("Removing previous Discord role {} from user {} before equipping new item", 
                                previousRoleId, user.getId());
                        
                        // Ensure role removal occurs and log any failures
                        boolean removalSuccess = discordService.removeRole(user.getId(), previousRoleId);
                        if (!removalSuccess) {
                            // Log the issue but continue with equipping the new item
                            logger.warn("Failed to remove previous Discord role {} from user {}. " +
                                    "Continuing with equipping new item.", previousRoleId, user.getId());
                        } else {
                            logger.debug("Successfully removed previous Discord role {} from user {}", 
                                    previousRoleId, user.getId());
                        }
                    }
                });
            }
            
            // Now unequip the previous item and set the new one
            user.setEquippedItemIdByCategory(category, item.getId());
            
            // Grant the new role if it has a discordRoleId
            String newRoleId = item.getDiscordRoleId();
            if (newRoleId != null && !newRoleId.isEmpty()) {
                logger.debug("Granting Discord role {} to user {} for equipped item", newRoleId, user.getId());
                boolean grantSuccess = discordService.grantRole(user.getId(), newRoleId);
                if (!grantSuccess) {
                    logger.warn("Failed to grant Discord role {} to user {}", newRoleId, user.getId());
                }
            }
        } else if (category == ShopCategory.FISHING_ROD) {
            // Handle Discord role management for FISHING_ROD items
            // Check if there was a previously equipped item of the same category
            UUID previousItemId = user.getEquippedItemIdByCategory(category);
            if (previousItemId != null && !previousItemId.equals(item.getId())) {
                // Find the previous item to get its Discord role ID and handle removal synchronously
                shopRepository.findById(previousItemId).ifPresent(previousItem -> {
                    String previousRoleId = previousItem.getDiscordRoleId();
                    if (previousRoleId != null && !previousRoleId.isEmpty()) {
                        logger.debug("Removing previous Discord role {} from user {} before equipping new item", 
                                previousRoleId, user.getId());
                        
                        // Ensure role removal occurs and log any failures
                        boolean removalSuccess = discordService.removeRole(user.getId(), previousRoleId);
                        if (!removalSuccess) {
                            // Log the issue but continue with equipping the new item
                            logger.warn("Failed to remove previous Discord role {} from user {}. " +
                                    "Continuing with equipping new item.", previousRoleId, user.getId());
                        } else {
                            logger.debug("Successfully removed previous Discord role {} from user {}", 
                                    previousRoleId, user.getId());
                        }
                    }
                });
            }
            
            // Now unequip the previous item and set the new one
            user.setEquippedItemIdByCategory(category, item.getId());
            
            // Grant the new role if it has a discordRoleId
            String newRoleId = item.getDiscordRoleId();
            if (newRoleId != null && !newRoleId.isEmpty()) {
                logger.debug("Granting Discord role {} to user {} for equipped item", newRoleId, user.getId());
                boolean grantSuccess = discordService.grantRole(user.getId(), newRoleId);
                if (!grantSuccess) {
                    logger.warn("Failed to grant Discord role {} to user {}", newRoleId, user.getId());
                }
            }
        } else {
            // For other categories, simply update the equipped item
            user.setEquippedItemIdByCategory(category, item.getId());
        }
    }
    
    /**
     * Unequips an item for a user by category
     * @param userId User ID
     * @param category Shop category to unequip
     * @return Updated UserProfileDTO
     */
    @Transactional
    public UserProfileDTO unequipItem(String userId, ShopCategory category) {
        logger.debug("Unequipping item of category {} for user {}", category, userId);
        
        // Get user
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));
        
        // Badge category requires specific badge ID to unequip
        if (category == ShopCategory.BADGE) {
            throw new UnsupportedOperationException("BADGE category requires a specific badge ID to unequip. Use unequipBadge(userId, badgeId) instead.");
        }
        
        // Get the currently equipped item ID BEFORE unequipping
        UUID currentlyEquippedItemId = user.getEquippedItemIdByCategory(category);
        
        // Unequip the item in the specified category
        user.setEquippedItemIdByCategory(category, null);
        
        // If this is a USER_COLOR category, remove associated Discord role
        if (category == ShopCategory.USER_COLOR && currentlyEquippedItemId != null) {
            shopRepository.findById(currentlyEquippedItemId).ifPresent(equippedItem -> {
                if (equippedItem.getDiscordRoleId() != null && !equippedItem.getDiscordRoleId().isEmpty()) {
                    logger.debug("Removing Discord role {} from user {} for unequipped item", 
                                equippedItem.getDiscordRoleId(), userId);
                    discordService.removeRole(userId, equippedItem.getDiscordRoleId());
                }
            });
        }
        
        // Save user changes
        user = userRepository.save(user);
        
        // Return updated profile
        return userService.mapToProfileDTO(user);
    }
    
    /**
     * Unequips a specific badge for a user
     * @param userId User ID
     * @param badgeId Badge ID to unequip
     * @return Updated UserProfileDTO
     */
    @Transactional
    public UserProfileDTO unequipBadge(String userId, UUID badgeId) {
        logger.debug("Unequipping badge {} for user {}", badgeId, userId);
        
        // Get user
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));
        
        // Check if user has the badge equipped
        if (!user.isBadgeEquipped(badgeId)) {
            logger.debug("Badge {} is not equipped for user {}", badgeId, userId);
            return userService.mapToProfileDTO(user);
        }
        
        // Get badge item for possible Discord role handling
        Shop badge = shopRepository.findById(badgeId)
            .orElseThrow(() -> new ResourceNotFoundException("Badge not found with ID: " + badgeId));
        
        // Check that it's actually a badge
        if (badge.getCategory() != ShopCategory.BADGE) {
            throw new ItemNotEquippableException("Item is not a badge");
        }
        
        // Remove the equipped badge
        user.removeEquippedBadge();
        
        // Handle Discord role if applicable
        if (badge.getDiscordRoleId() != null && !badge.getDiscordRoleId().isEmpty()) {
            logger.debug("Removing Discord role {} from user {} for unequipped badge", 
                        badge.getDiscordRoleId(), userId);
            boolean removalSuccess = discordService.removeRole(userId, badge.getDiscordRoleId());
            if (!removalSuccess) {
                logger.warn("Failed to remove Discord role {} from user {} for badge", 
                          badge.getDiscordRoleId(), userId);
            }
        }
        
        // Save user changes
        user = userRepository.save(user);
        
        // Return updated profile
        return userService.mapToProfileDTO(user);
    }
    
    /**
     * Gets a user's inventory with equipped status
     * @param userId User ID
     * @return User's inventory with equipped status
     */
    @Transactional(readOnly = true)
    public UserInventoryDTO getUserInventory(String userId) {
        logger.debug("Getting inventory for user {}", userId);
        
        User user = userRepository.findByIdWithInventory(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));

        Map<Shop, Long> itemCounts = user.getItemInstances().stream()
            .collect(Collectors.groupingBy(ItemInstance::getBaseItem, Collectors.counting()));
            
        Set<ShopDTO> itemDTOs = itemCounts.entrySet().stream().map(entry -> {
            Shop item = entry.getKey();
            long quantity = entry.getValue();
            
            ShopDTO dto = mapToShopDTO(item, user);
            dto.setQuantity((int) quantity);
            
            // Add equipped status, skipping for non-equippable categories like CASE
            if (item.getCategory() != null && item.getCategory() != ShopCategory.CASE) {
                if (item.getCategory() == ShopCategory.BADGE) {
                    dto.setEquipped(user.isBadgeEquipped(item.getId()));
                } else {
                    UUID equippedItemId = user.getEquippedItemIdByCategory(item.getCategory());
                    dto.setEquipped(equippedItemId != null && equippedItemId.equals(item.getId()));
                }
            } else {
                // For CASE or null category, it's not equipped
                dto.setEquipped(false);
            }
            
            return dto;
        }).collect(Collectors.toSet());
        
        return UserInventoryDTO.builder()
            .items(itemDTOs)
            .build();
    }
    
    /**
     * Gets a user's inventory specifically for Discord commands
     * Uses eager fetching to avoid LazyInitializationException
     * @param userId User ID
     * @return User's inventory with equipped status
     */
    public UserInventoryDTO getUserInventoryForDiscord(String userId) {
        logger.debug("Getting inventory for Discord command for user {}", userId);
        
        User user = userRepository.findByIdWithInventory(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));
        
        Map<Shop, Long> itemCounts = user.getItemInstances().stream()
            .collect(Collectors.groupingBy(ItemInstance::getBaseItem, Collectors.counting()));
            
        Set<ShopDTO> itemDTOs = itemCounts.entrySet().stream().map(entry -> {
            Shop item = entry.getKey();
            long quantity = entry.getValue();
            
            ShopDTO dto = mapToShopDTO(item, user);
            dto.setQuantity((int) quantity);
            
            // Add equipped status, skipping for non-equippable categories like CASE
            if (item.getCategory() != null && item.getCategory() != ShopCategory.CASE) {
                if (item.getCategory() == ShopCategory.BADGE) {
                    dto.setEquipped(user.isBadgeEquipped(item.getId()));
                } else {
                    UUID equippedItemId = user.getEquippedItemIdByCategory(item.getCategory());
                    dto.setEquipped(equippedItemId != null && equippedItemId.equals(item.getId()));
                }
            } else {
                // For CASE or null category, it's not equipped
                dto.setEquipped(false);
            }
            
            return dto;
        }).collect(Collectors.toSet());
        
        return UserInventoryDTO.builder()
            .items(itemDTOs)
            .build();
    }
    
    /**
     * Map a Shop entity to a ShopDTO
     * @param shop Shop entity
     * @param user Optional user to check ownership status
     * @return ShopDTO
     */
    private ShopDTO mapToShopDTO(Shop shop, User user) {
        boolean owned = false;
        
        // For cases, never show as owned since they can be purchased multiple times
        if (shop.getCategory() != ShopCategory.CASE && user != null) {
            owned = user.getItemInstances().stream()
                .anyMatch(instance -> instance.getBaseItem().getId().equals(shop.getId()));
        }
        
        // Check if this is a case and get contents count
        boolean isCase = shop.getCategory() == ShopCategory.CASE;
        Integer caseContentsCount = 0;
        if (isCase) {
            caseContentsCount = Math.toIntExact(caseItemRepository.countByCaseShopItem(shop));
        }
        
        return ShopDTO.builder()
            .id(shop.getId())
            .name(shop.getName())
            .description(shop.getDescription())
            .price(shop.getPrice())
            .category(shop.getCategory())
            .imageUrl(shop.getImageUrl())
            .thumbnailUrl(shop.getThumbnailUrl())
            .requiredRole(shop.getRequiredRole())
            .owned(owned)
            .expiresAt(shop.getExpiresAt())
            .discordRoleId(shop.getDiscordRoleId())
            .rarity(shop.getRarity() != null ? shop.getRarity() : ItemRarity.COMMON)
            .isCase(isCase)
            .caseContentsCount(caseContentsCount)
            .isFeatured(shop.getIsFeatured())
            .isDaily(shop.getIsDaily())
            .fishingRodMultiplier(shop.getFishingRodMultiplier())
            .gradientEndColor(shop.getGradientEndColor())
            .maxCopies(shop.getMaxCopies())
            .copiesSold(shop.getCopiesSold())
            .build();
    }
    
    /**
     * Create a new shop item
     * @param shopDTO Shop item DTO
     * @return Created shop item
     */
    @Transactional
    @CacheEvict(value = {"featuredItems", "userDailyItems"}, allEntries = true)
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
            .build();
        
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
    @CacheEvict(value = {"featuredItems", "userDailyItems"}, allEntries = true)
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
        
        logger.debug("Updating shop item with ID: {} with sanitized content", existingItem.getId());
        
        return shopRepository.save(existingItem);
    }

    /**
     * Update an item's price (admin only)
     * @param itemId Item ID
     * @param newPrice New price
     */
    @Transactional
    @CacheEvict(value = {"featuredItems", "userDailyItems"}, allEntries = true)
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
    @CacheEvict(value = {"featuredItems", "userDailyItems"}, allEntries = true)
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
    @CacheEvict(value = {"featuredItems", "userDailyItems"}, allEntries = true)
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
        
        // Clean up the new ItemInstance system by finding all instances of the base item and deleting them.
        List<ItemInstance> instancesToDelete = itemInstanceRepository.findByBaseItem(item);
        if (!instancesToDelete.isEmpty()) {
            logger.info("Removing {} instances of item {} from user inventories.", instancesToDelete.size(), item.getId());
            itemInstanceRepository.deleteAll(instancesToDelete);
        }
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
    
    // ===== CASE-RELATED METHODS =====
    
    /**
     * Open a case and return a random item based on drop rates
     * @param userId User ID
     * @param caseId Case ID to open
     * @return RollResultDTO with the won item and roll details
     */
    @Transactional
    public RollResultDTO openCase(String userId, UUID caseId) {
        logger.debug("Opening case {} for user {}", caseId, userId);
        long startTime = System.currentTimeMillis();
        
        // 1. Verify user exists
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));
        
        // 2. Verify case exists and is actually a case
        Shop caseItem = shopRepository.findById(caseId)
            .orElseThrow(() -> new CaseNotFoundException("Case not found with ID: " + caseId));
        
        if (caseItem.getCategory() != ShopCategory.CASE) {
            throw new IllegalArgumentException("Item is not a case");
        }
        
        // 3. Verify and consume one case to prevent race conditions
        ItemInstance caseToConsume = user.getItemInstances().stream()
                .filter(instance -> instance.getBaseItem().getId().equals(caseId))
                .findFirst()
                .orElseThrow(() -> new CaseNotOwnedException("You do not own this case or have no cases left to open"));

        itemInstanceRepository.delete(caseToConsume);
        user.getItemInstances().remove(caseToConsume);
        
        // 4. Get case contents with drop rates and validate
        List<CaseItem> caseItems = caseItemRepository.findByCaseIdOrderByDropRateDesc(caseId);
        
        if (caseItems.isEmpty()) {
            throw new EmptyCaseException("Case has no contents to roll");
        }
        
        // 5. Validate case contents (drop rates should sum to 100)
        Integer totalDropRate = caseItemRepository.sumDropRatesByCaseId(caseId);
        if (totalDropRate == null || totalDropRate != 100) {
            throw new InvalidCaseContentsException(
                "Invalid case contents - drop rates sum to " + totalDropRate + "% instead of 100%"
            );
        }
        
        // 6. Generate secure random seed for this roll
        String rollSeed = secureRandomService.generateRollSeed();
        String rollSeedHash = generateSeedHash(rollSeed);
        
        // 7. Generate secure roll value for animation sync (0-99)
        int rollValue = secureRandomService.getSecureInt(100); // Secure random for animation sync
        
        // 8. Perform secure weighted random selection using the pre-generated roll value
        Shop wonItem = selectItemByDropRateSecureWithRoll(caseItems, rollValue);
        
        // 9. Find the drop rate for the won item
        int wonItemDropRate = caseItems.stream()
            .filter(item -> item.getContainedItem().getId().equals(wonItem.getId()))
            .mapToInt(CaseItem::getDropRate)
            .findFirst()
            .orElse(0);
        
        // 10. Check if user already owns the won item
        boolean alreadyOwned = user.hasItem(wonItem.getId());
        
        // 11. Store credits and XP before operation
        int creditsBefore = user.getCredits();
        
        // 12. Calculate and award compensation for duplicate items
        boolean compensationAwarded = false;
        int compensatedCredits = 0;
        int compensatedXp = 0;
        
        if (alreadyOwned) {
            // Calculate compensation based on item rarity
            compensatedCredits = calculateCompensationCredits(wonItem.getRarity());
            compensatedXp = calculateCompensationXp(wonItem.getRarity());
            
            // Atomically update credits and XP to prevent race conditions
            userRepository.incrementCreditsAndXp(userId, compensatedCredits, compensatedXp);
            
            // Refresh the user entity to reflect the database changes in the persistence context
            entityManager.refresh(user);
            
            compensationAwarded = true;
            
            logger.info("Awarded compensation for duplicate item {} to user {}: {} credits, {} XP", 
                       wonItem.getId(), userId, compensatedCredits, compensatedXp);
        }
        
        // 13. Case was already consumed atomically at the beginning (step 3) to prevent race conditions
        // The specific instance has been deleted, so no zero-quantity cleanup is needed.
        
        // 14. Add won item to user inventory (only if not already owned)
        if (!alreadyOwned) {
            ItemInstance newInstance = ItemInstance.builder()
                .owner(user)
                .baseItem(wonItem)
                .build();
            user.getItemInstances().add(newInstance);
        }
        
        // 15. Save user changes
        user = userRepository.save(user);
        int creditsAfter = user.getCredits();
        
        // 16. Calculate processing time
        long processingTime = System.currentTimeMillis() - startTime;
        
        // 17. Create audit record
        RollAudit auditRecord = new RollAudit(
            userId, caseId, caseItem.getName(), wonItem.getId(), wonItem.getName(),
            rollValue, rollSeedHash, wonItemDropRate, totalDropRate, caseItems.size(),
            alreadyOwned, getClientIp(), getUserAgent(), getSessionId(),
            creditsBefore, creditsAfter
        );
        auditRecord.setProcessingTimeMs(processingTime);
        
        // Set timestamp manually before generating statistical hash
        LocalDateTime now = LocalDateTime.now();
        auditRecord.setRollTimestamp(now);
        auditRecord.setStatisticalHash(rollVerificationService.generateStatisticalHash(auditRecord));
        
        // 18. Save roll audit record (for specialized gambling compliance)
        rollAuditRepository.save(auditRecord);
        
        // 19. Create main audit entry for admin visibility
        createCaseRollAuditEntry(userId, caseId, caseItem, wonItem, alreadyOwned, 
                                compensationAwarded, compensatedCredits, compensatedXp, 
                                rollValue, wonItemDropRate, creditsAfter - creditsBefore);
        
        logger.info("User {} opened case {} and won item {} (already owned: {}{}) - Roll: {}, Seed: {}", 
                   userId, caseId, wonItem.getId(), alreadyOwned, 
                   compensationAwarded ? ", compensation awarded: " + compensatedCredits + " credits, " + compensatedXp + " XP" : "",
                   rollValue, rollSeedHash.substring(0, 8) + "...");
        
        // 20. Invalidate user profile cache to reflect updated credits/xp/inventory
        cacheConfig.invalidateUserProfileCache(userId);
        
        // 21. Return result
        return RollResultDTO.builder()
            .caseId(caseId)
            .caseName(caseItem.getName())
            .wonItem(mapToShopDTO(wonItem, user))
            .rollValue(rollValue)
            .rolledAt(LocalDateTime.now())
            .alreadyOwned(alreadyOwned)
            .compensationAwarded(compensationAwarded)
            .compensatedCredits(compensationAwarded ? compensatedCredits : null)
            .compensatedXp(compensationAwarded ? compensatedXp : null)
            .build();
    }
    
    /**
     * Calculate credit compensation for duplicate items based on rarity
     * @param rarity Item rarity
     * @return Credit compensation amount
     */
    private int calculateCompensationCredits(ItemRarity rarity) {
        if (rarity == null) {
            rarity = ItemRarity.COMMON;
        }
        
        switch (rarity) {
            case LEGENDARY:
                return 500;
            case EPIC:
                return 300;
            case RARE:
                return 150;
            case UNCOMMON:
                return 100;
            case COMMON:
            default:
                return 50;
        }
    }
    
    /**
     * Calculate XP compensation for duplicate items based on rarity
     * @param rarity Item rarity (no longer used)
     * @return XP compensation amount
     */
    private int calculateCompensationXp(ItemRarity rarity) {
        return 10;
    }
    
    /**
     * Perform secure weighted random selection based on drop rates using a pre-generated roll value
     * This method ensures animation synchronization by using the same roll value for both selection and animation
     * @param caseItems List of case items with drop rates
     * @param rollValue Pre-generated roll value (0-99)
     * @return Selected shop item
     */
    private Shop selectItemByDropRateSecureWithRoll(List<CaseItem> caseItems, int rollValue) {
        // Use secure random service for weighted selection with pre-generated roll value
        return secureRandomService.selectWeightedRandom(
            caseItems,
            100, // Total weight should always be 100 for drop rates
            rollValue, // Use the pre-generated roll value
            CaseItem::getDropRate
        ).getContainedItem();
    }
    
    /**
     * Get the contents of a case with drop rates
     * @param caseId Case ID
     * @return CaseContentsDTO with all possible items and their drop rates
     */
    @Transactional(readOnly = true)
    public CaseContentsDTO getCaseContents(UUID caseId) {
        logger.debug("Getting contents for case {}", caseId);
        
        Shop caseItem = shopRepository.findById(caseId)
            .orElseThrow(() -> new ResourceNotFoundException("Case not found with ID: " + caseId));
        
        if (caseItem.getCategory() != ShopCategory.CASE) {
            throw new IllegalArgumentException("Item is not a case");
        }
        
        List<CaseItem> caseItems = caseItemRepository.findByCaseIdOrderByDropRateDesc(caseId);
        
        List<CaseItemDTO> itemDTOs = caseItems.stream()
            .map(this::mapToCaseItemDTO)
            .collect(Collectors.toList());
        
        Integer totalDropRate = caseItemRepository.sumDropRatesByCaseId(caseId);
        
        return CaseContentsDTO.builder()
            .caseId(caseId)
            .caseName(caseItem.getName())
            .items(itemDTOs)
            .totalDropRate(totalDropRate != null ? totalDropRate : 0)
            .itemCount(itemDTOs.size())
            .build();
    }
    
    /**
     * Update case contents (admin only)
     * @param caseId Case ID
     * @param caseItems List of items to include in the case with drop rates
     */
    @Transactional
    public void updateCaseContents(UUID caseId, List<CaseItemDTO> caseItems) {
        logger.debug("Updating contents for case {} with {} items", caseId, caseItems.size());
        
        Shop caseItem = shopRepository.findById(caseId)
            .orElseThrow(() -> new ResourceNotFoundException("Case not found with ID: " + caseId));
        
        if (caseItem.getCategory() != ShopCategory.CASE) {
            throw new IllegalArgumentException("Item is not a case");
        }
        
        // Validate drop rates sum to 100
        int totalDropRate = caseItems.stream()
            .mapToInt(CaseItemDTO::getDropRate)
            .sum();
        
        if (totalDropRate != 100) {
            throw new IllegalArgumentException("Drop rates must sum to 100, current sum: " + totalDropRate);
        }
        
        // Validate all contained items exist and are not cases themselves
        for (CaseItemDTO dto : caseItems) {
            Shop containedItem = shopRepository.findById(dto.getContainedItem().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Contained item not found: " + dto.getContainedItem().getId()));
            
            if (containedItem.getCategory() == ShopCategory.CASE) {
                throw new IllegalArgumentException("Cases cannot contain other cases");
            }
        }
        
        // Remove existing case items
        caseItemRepository.deleteByCaseId(caseId);
        
        // Add new case items
        for (CaseItemDTO dto : caseItems) {
            Shop containedItem = shopRepository.findById(dto.getContainedItem().getId()).get();
            
            CaseItem newCaseItem = CaseItem.builder()
                .caseShopItem(caseItem)
                .containedItem(containedItem)
                .dropRate(dto.getDropRate())
                .build();
            
            caseItemRepository.save(newCaseItem);
        }
        
        logger.info("Updated case {} with {} items", caseId, caseItems.size());
    }
    
    /**
     * Validate case contents to ensure drop rates sum to 100
     * @param caseId Case ID
     * @return true if valid, false otherwise
     */
    public boolean validateCaseContents(UUID caseId) {
        Integer totalDropRate = caseItemRepository.sumDropRatesByCaseId(caseId);
        return totalDropRate != null && totalDropRate == 100;
    }
    
    /**
     * Map a CaseItem entity to a CaseItemDTO
     * @param caseItem CaseItem entity
     * @return CaseItemDTO
     */
    private CaseItemDTO mapToCaseItemDTO(CaseItem caseItem) {
        return CaseItemDTO.builder()
            .id(caseItem.getId())
            .caseId(caseItem.getCaseShopItem().getId())
            .containedItem(mapToShopDTO(caseItem.getContainedItem(), null))
            .dropRate(caseItem.getDropRate())
            .build();
    }
    
    // ===== SECURITY HELPER METHODS =====
    
    /**
     * Generate SHA-256 hash of the roll seed for audit trail
     * @param seed The roll seed to hash
     * @return SHA-256 hash of the seed
     */
    private String generateSeedHash(String seed) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(seed.getBytes());
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            logger.error("Failed to generate seed hash: {}", e.getMessage());
            return "";
        }
    }
    
    /**
     * Get client IP address for audit trail
     * TODO: Implement proper IP extraction from HTTP request
     * @return Client IP address or "unknown"
     */
    private String getClientIp() {
        // This would need to be injected from the controller layer
        // For now, return a placeholder
        return "unknown";
    }
    
    /**
     * Get user agent for audit trail
     * TODO: Implement proper user agent extraction from HTTP request
     * @return User agent string or "unknown"
     */
    private String getUserAgent() {
        // This would need to be injected from the controller layer
        // For now, return a placeholder
        return "unknown";
    }
    
    /**
     * Get session ID for audit trail
     * TODO: Implement proper session ID extraction
     * @return Session ID or "unknown"
     */
    private String getSessionId() {
        // This would need to be injected from the controller layer
        // For now, return a placeholder
        return "unknown";
    }
}
