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
import com.app.heartbound.exceptions.shop.ItemNotPurchasableException;
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
import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.*;
import java.util.stream.Collectors;
import java.util.Objects;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;

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
    private static final Logger logger = LoggerFactory.getLogger(ShopService.class);
    private volatile List<Shop> dailyItemPool;
    
    @Value("${shop.daily.seed-salt}")
    private String serverSeedSalt;
    
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
        this.objectMapper = new ObjectMapper();
        
        // Initialize server-side salt for secure seed generation - REMOVED
        // this.serverSeedSalt = secureRandomService.generateRollSeed();
    }
    
    @PostConstruct
    public void initializeDailyItems() {
        updateDailyItemPool();
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
     * The items are unique for each user and refresh daily.
     * @param userId User ID to check ownership status for the global items.
     * @return List of daily shop items for the user.
     */
    @Transactional(readOnly = true)
    public List<ShopDTO> getDailyItems(String userId) {
        // Fetch the user with their inventory up-front to avoid multiple lookups and for ownership checks.
        User user = userRepository.findByIdWithInventory(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        List<Shop> userDailyItems = generateDailyItemsForUser(user);

        // Now, filter the daily items list to exclude any items the user owns.
        // This is necessary because of the fallback logic where the original pool might be used.
        Set<UUID> finalOwnedItemIds = user.getItemInstances().stream()
                .map(instance -> instance.getBaseItem().getId())
                .collect(Collectors.toSet());

        List<Shop> filteredDailyItems = userDailyItems.stream()
            .filter(item -> !finalOwnedItemIds.contains(item.getId()))
            .filter(item -> item.getMaxCopies() == null || item.getCopiesSold() == null || item.getCopiesSold() < item.getMaxCopies())
            .collect(Collectors.toList());

        // Convert the filtered list to DTOs for the response
        final User finalUser = user;
        return filteredDailyItems.stream()
            .map(item -> mapToShopDTO(item, finalUser))
            .collect(Collectors.toList());
    }

    /**
     * Thread-safe method to get the daily item pool, initializing if necessary
     * @return Current daily item pool
     */
    private synchronized List<Shop> getDailyItemPoolSafely() {
        if (dailyItemPool == null) {
            logger.warn("Daily item pool not yet populated. Triggering selection now.");
            updateDailyItemPool();
        }
        return dailyItemPool;
    }

    /**
     * Scheduled task to update the global pool of eligible daily items.
     * This runs at midnight UTC every day.
     */
    @Scheduled(cron = "0 0 0 * * *", zone = "UTC")
    public synchronized void updateDailyItemPool() {
        logger.info("Executing scheduled task to update the global daily item pool.");
        
        this.dailyItemPool = shopRepository.findByIsDailyTrueAndIsActiveTrueAndExpiresAtAfterOrExpiresAtIsNull(LocalDateTime.now())
            .stream()
            .filter(Shop::getIsActive)
            .filter(item -> item.getCategory() != ShopCategory.CASE)
            .collect(Collectors.collectingAndThen(Collectors.toList(), Collections::unmodifiableList));
        
        logger.info("Successfully updated global daily item pool with {} eligible items.", dailyItemPool.size());
    }

    private List<Shop> generateDailyItemsForUser(User user) {
        // Ensure daily item pool is available with thread-safe access
        List<Shop> currentDailyPool = getDailyItemPoolSafely();

        // Create a secure deterministic seed for the user and the current day
        String seedString = user.getId() + java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString() + serverSeedSalt;
        long seed = new Random(seedString.hashCode()).nextLong();
        Random random = new Random(seed);

        // The entire logic of pre-filtering based on ownership is removed.
        // We now operate on the full pool to ensure the daily selection is stable.
        if (currentDailyPool.isEmpty()) {
            return Collections.emptyList();
        }
        
        if (currentDailyPool.size() < 4) {
            logger.warn("Daily item pool has fewer than 4 items ({}), selection may be smaller.", currentDailyPool.size());
        }

        // Group the full pool by rarity for selection
        Map<ItemRarity, List<Shop>> itemsByRarity = currentDailyPool.stream()  
            .collect(Collectors.groupingBy(Shop::getRarity));
        
        // selectItemsForUser will now deterministically pick from the full pool.
        return selectItemsForUser(itemsByRarity, random, currentDailyPool);
    }

    private List<Shop> selectItemsForUser(Map<ItemRarity, List<Shop>> itemsByRarity, Random random, List<Shop> availableItemPool) {
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

        // Select items based on rarity distribution
        selectAndAddItem(selectedItems, legendaryItems, 1, random, Arrays.asList(epicItems, lowerTierItems));
        selectAndAddItem(selectedItems, epicItems, 1, random, Arrays.asList(legendaryItems, lowerTierItems));
        selectAndAddItem(selectedItems, lowerTierItems, 2, random, Arrays.asList(epicItems, legendaryItems));
        
        // Enforce the 'max one fishing rod' rule
        long fishingRodCount = selectedItems.stream()
            .filter(item -> item.getCategory() == ShopCategory.FISHING_ROD)
            .count();

        if (fishingRodCount > 1) {
            List<Shop> rodsToReplace = selectedItems.stream()
                .filter(item -> item.getCategory() == ShopCategory.FISHING_ROD)
                .skip(1)
                .collect(Collectors.toList());

            for (Shop rodToReplace : rodsToReplace) {
                ItemRarity rarityToMatch = rodToReplace.getRarity();
                List<Shop> replacementPool = itemsByRarity.getOrDefault(rarityToMatch, Collections.emptyList())
                    .stream()
                    .filter(item -> item.getCategory() != ShopCategory.FISHING_ROD && !selectedItems.contains(item))
                    .collect(Collectors.toList());

                Shop replacement = null;
                if (!replacementPool.isEmpty()) {
                    replacement = replacementPool.get(random.nextInt(replacementPool.size()));
                } else {
                    List<Shop> generalReplacementPool = availableItemPool.stream()
                        .filter(item -> item.getCategory() != ShopCategory.FISHING_ROD && !selectedItems.contains(item))
                        .collect(Collectors.toList());
                    if (!generalReplacementPool.isEmpty()) {
                        replacement = generalReplacementPool.get(random.nextInt(generalReplacementPool.size()));
                    }
                }

                int index = selectedItems.indexOf(rodToReplace);
                if (index != -1) {
                    if (replacement != null) {
                        selectedItems.set(index, replacement);
                    } else {
                        selectedItems.remove(index);
                    }
                }
            }
        }
        
        // Final fallback to ensure 4 items if possible
        if (selectedItems.size() < 4) {
            List<Shop> remainingPool = new ArrayList<>(availableItemPool);
            remainingPool.removeAll(selectedItems);
            
            while (selectedItems.size() < 4 && !remainingPool.isEmpty()) {
                int randomIndex = random.nextInt(remainingPool.size());
                Shop item = remainingPool.remove(randomIndex);
                if (!selectedItems.contains(item)) {
                    selectedItems.add(item);
                }
            }
        }
        return selectedItems;
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

        // If not featured, check if it's a daily item and present in the user's daily item list
        if (item.getIsDaily()) {
            List<Shop> userDailyItems = generateDailyItemsForUser(user);
            boolean isInDailyList = userDailyItems.stream().anyMatch(dailyItem -> dailyItem.getId().equals(item.getId()));
            if (isInDailyList) {
                logger.debug("Purchase validation passed for user {}: Item {} is in their daily items list.", user.getId(), item.getId());
                return true;
            }
        }
        
        // If it's neither featured nor a valid daily item for the user, it's not purchasable
        logger.warn("Purchase validation failed for user {}: Item {} is not featured and not in their active daily shop.", user.getId(), item.getId());
        return false;
    }
    
    /**
     * Purchase an item for a user
     * @param userId User ID
     * @param itemId Item ID
     * @return Updated UserProfileDTO
     */
    @Transactional
    @CacheEvict(value = "featuredItems", allEntries = true)
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
    @CacheEvict(value = "featuredItems", allEntries = true)
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

            if (item.getCategory() == ShopCategory.FISHING_ROD) {
                newInstance.setDurability(item.getMaxDurability());
                newInstance.setExperience(0L);
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
                                        double dropRate, int creditsSpent) {
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

        // For fishing rods, this endpoint is deprecated. Use equipItemInstance instead.
        if (item.getCategory() == ShopCategory.FISHING_ROD) {
            throw new UnsupportedOperationException("Fishing rods must be equipped by their instance ID. Please use the new equip endpoint.");
        }
        
        // Perform equip logic
        performEquipItem(user, item, null);
        
        // Save user changes
        user = userRepository.save(user);
        
        // Return updated profile
        return userService.mapToProfileDTO(user);
    }
    
    /**
     * Equips an item for a user by its instance ID.
     * This is the new standard for equippable items with unique properties.
     * @param userId User ID
     * @param instanceId Item Instance ID
     * @return Updated UserProfileDTO
     */
    @Transactional
    public UserProfileDTO equipItemInstance(String userId, UUID instanceId) {
        logger.debug("Equipping item instance {} for user {}", instanceId, userId);

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));

        ItemInstance instance = itemInstanceRepository.findById(instanceId)
            .orElseThrow(() -> new ResourceNotFoundException("Item instance not found with ID: " + instanceId));

        if (!instance.getOwner().getId().equals(userId)) {
            throw new ItemNotOwnedException("You do not own this item instance.");
        }

        Shop item = instance.getBaseItem();

        performEquipItem(user, item, instanceId);

        user = userRepository.save(user);

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
                performEquipItem(user, item, null);
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
     * @param instanceId The specific instance ID to equip (optional, for items like fishing rods)
     */
    private void performEquipItem(User user, Shop item, UUID instanceId) {
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
            if (instanceId == null) {
                throw new IllegalArgumentException("Fishing rod must be equipped by its instance ID.");
            }
            // Handle Discord role management for FISHING_ROD items
            // Check if there was a previously equipped item of the same category
            UUID previousInstanceId = user.getEquippedFishingRodInstanceId();
            if (previousInstanceId != null && !previousInstanceId.equals(instanceId)) {
                itemInstanceRepository.findById(previousInstanceId).ifPresent(previousInstance -> {
                    Shop previousItem = previousInstance.getBaseItem();
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
            user.setEquippedFishingRodInstanceId(instanceId);
            
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

        // This method needs significant changes to support instance-specific data.
        // We'll create a new list of DTOs.
        List<ShopDTO> itemDTOs = new ArrayList<>();

        // Group all instances by their base item to get counts for stackable items.
        Map<Shop, List<ItemInstance>> groupedInstances = user.getItemInstances().stream()
                .collect(Collectors.groupingBy(ItemInstance::getBaseItem));

        for (Map.Entry<Shop, List<ItemInstance>> entry : groupedInstances.entrySet()) {
            Shop item = entry.getKey();
            List<ItemInstance> instances = entry.getValue();

            if (item.getCategory() == ShopCategory.FISHING_ROD) {
                // For fishing rods, create a DTO for each unique instance.
                for (ItemInstance instance : instances) {
                    ShopDTO dto = mapToShopDTO(item, user);
                    dto.setInstanceId(instance.getId());
                    dto.setDurability(instance.getDurability());
                    dto.setExperience(instance.getExperience());
                    dto.setQuantity(1); // Each instance is unique

                    // Set equipped status based on the instance ID
                    UUID equippedInstanceId = user.getEquippedFishingRodInstanceId();
                    dto.setEquipped(instance.getId().equals(equippedInstanceId));
                    itemDTOs.add(dto);
                }
            } else {
                // For all other items, use the existing stacking logic.
                long quantity = instances.size();
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
                    dto.setEquipped(false);
                }
                itemDTOs.add(dto);
            }
        }
        
        return UserInventoryDTO.builder()
            .items(new HashSet<>(itemDTOs))
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
                } else if (item.getCategory() == ShopCategory.FISHING_ROD) {
                    // This is tricky because we don't have instance info here.
                    // The main inventory endpoint is better. For Discord, we can just show if ANY rod is equipped.
                    dto.setEquipped(user.getEquippedFishingRodInstanceId() != null);
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
            .maxDurability(shop.getMaxDurability())
            .build();
    }
    
    /**
     * Create a new shop item
     * @param shopDTO Shop item DTO
     * @return Created shop item
     */
    @Transactional
    @CacheEvict(value = "featuredItems", allEntries = true)
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
    @CacheEvict(value = "featuredItems", allEntries = true)
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
        
        logger.debug("Updating shop item with ID: {} with sanitized content", existingItem.getId());
        
        return shopRepository.save(existingItem);
    }

    /**
     * Update an item's price (admin only)
     * @param itemId Item ID
     * @param newPrice New price
     */
    @Transactional
    @CacheEvict(value = "featuredItems", allEntries = true)
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
    @CacheEvict(value = "featuredItems", allEntries = true)
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
    @CacheEvict(value = "featuredItems", allEntries = true)
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
        
        // 1. Verify user exists and eagerly fetch their inventory to prevent LazyInitializationException
        User user = userRepository.findByIdWithLock(userId)
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

        // 5. IMPORTANT: Validate that drop rates sum to 100 for this logic to be fair.
        BigDecimal totalWeight = caseItems.stream()
            .map(CaseItem::getDropRate)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalWeight.compareTo(new BigDecimal("100")) != 0) {
            logger.error("Invalid case contents for case {}: total drop rate is {}, but should be 100.", caseId, totalWeight);
            throw new InvalidCaseContentsException("Case contents are invalid. Total drop rate is not 100.");
        }
        
        // 6. Generate secure random seed and roll value for this roll
        String rollSeed = secureRandomService.generateRollSeed();
        String rollSeedHash = generateSeedHash(rollSeed);
        // Generate a roll value from 0-999,999 to match the drop rate precision of 4 decimal places (100.0000%)
        int rollValue = secureRandomService.getSecureInt(1000000);
        
        // 7. Perform secure weighted random selection using the roll value for animation sync
        Shop wonItem = selectItemByDropRateSecureWithRoll(caseItems, rollValue);

        // 8. Find the drop rate for the won item for auditing purposes
        BigDecimal wonItemDropRate = caseItems.stream()
            .filter(item -> item.getContainedItem().getId().equals(wonItem.getId()))
            .map(CaseItem::getDropRate)
            .findFirst()
            .orElse(BigDecimal.ZERO);

        // 9. Check if user already owns the won item
        boolean alreadyOwned = user.hasItem(wonItem.getId());
        
        // 10. Store credits and XP before operation
        int creditsBefore = user.getCredits();
        
        // 11. Calculate and award compensation for duplicate items
        boolean compensationAwarded = false;
        int compensatedCredits = 0;
        int compensatedXp = 0;
        
        if (alreadyOwned) {
            // Calculate compensation based on item rarity
            compensatedCredits = calculateCompensationCredits(wonItem.getRarity());
            compensatedXp = calculateCompensationXp(wonItem.getRarity());
            
            // Atomically update credits and XP to prevent race conditions
            user.setCredits(user.getCredits() + compensatedCredits);
            user.setExperience(user.getExperience() + compensatedXp);
            
            compensationAwarded = true;
            
            logger.info("Awarded compensation for duplicate item {} to user {}: {} credits, {} XP", 
                       wonItem.getId(), userId, compensatedCredits, compensatedXp);
        }
        
        // 12. Case was already consumed atomically at the beginning (step 3) to prevent race conditions
        // The specific instance has been deleted, so no zero-quantity cleanup is needed.
        
        // 13. Add won item to user inventory (only if not already owned)
        if (!alreadyOwned) {
            ItemInstance newInstance = ItemInstance.builder()
                .owner(user)
                .baseItem(wonItem)
                .build();
            user.getItemInstances().add(newInstance);
        }
        
        // 14. Save user changes
        user = userRepository.save(user);
        int creditsAfter = user.getCredits();
        
        // 15. Calculate processing time
        long processingTime = System.currentTimeMillis() - startTime;
        
        // 16. Create audit record
        RollAudit auditRecord = new RollAudit(
            userId, caseId, caseItem.getName(), wonItem.getId(), wonItem.getName(),
            rollValue, // Use the generated rollValue for the audit
            rollSeedHash, wonItemDropRate.doubleValue(), totalWeight.doubleValue(), caseItems.size(),
            alreadyOwned, getClientIp(), getUserAgent(), getSessionId(),
            creditsBefore, creditsAfter
        );
        auditRecord.setProcessingTimeMs(processingTime);
        
        // Set timestamp manually before generating statistical hash
        LocalDateTime now = LocalDateTime.now();
        auditRecord.setRollTimestamp(now);
        auditRecord.setStatisticalHash(rollVerificationService.generateStatisticalHash(auditRecord));
        
        // 17. Save roll audit record (for specialized gambling compliance)
        rollAuditRepository.save(auditRecord);
        
        // 18. Create main audit entry for admin visibility
        createCaseRollAuditEntry(userId, caseId, caseItem, wonItem, alreadyOwned, 
                                compensationAwarded, compensatedCredits, compensatedXp, 
                                rollValue, wonItemDropRate.doubleValue(), creditsAfter - creditsBefore);
        
        logger.info("User {} opened case {} and won item {} (roll: {}, already owned: {}{}) - Seed: {}", 
                   userId, caseId, wonItem.getId(), rollValue, alreadyOwned, 
                   compensationAwarded ? ", compensation awarded: " + compensatedCredits + " credits, " + compensatedXp + " XP" : "",
                   rollSeedHash.substring(0, 8) + "...");
        
        // 19. Invalidate user profile cache to reflect updated credits/xp/inventory
        cacheConfig.invalidateUserProfileCache(userId);
        
        // 20. Return result
        return RollResultDTO.builder()
            .caseId(caseId)
            .caseName(caseItem.getName())
            .wonItem(mapToShopDTO(wonItem, user))
            .rollValue(rollValue) // Return the roll value for frontend animation
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
     * Perform secure weighted random selection based on drop rates using a pre-generated roll value.
     * This method uses scaled integer math to prevent floating-point inaccuracies and ensure fair distribution.
     * @param caseItems List of case items with drop rates, assumed to be sorted.
     * @param rollValue Pre-generated roll value (0-999,999)
     * @return Selected shop item
     */
    private Shop selectItemByDropRateSecureWithRoll(List<CaseItem> caseItems, int rollValue) {
        long cumulativeWeight = 0L;

        // The roll is 0-999,999. Drop rates are percentages (e.g., 0.0123 for 0.0123%).
        // We scale each drop rate by 10,000 to work with integers (e.g., 0.0123 -> 123).
        for (CaseItem item : caseItems) {
            long itemWeight = item.getDropRate().multiply(new BigDecimal("10000")).longValue();
            cumulativeWeight += itemWeight;

            if (rollValue < cumulativeWeight) {
                return item.getContainedItem();
            }
        }

        // Fallback for safety. This should not be hit if total drop rates sum to 100
        // and the roll is within the 0-999,999 range. This can occur if drop rates
        // with high precision are truncated by longValue(), causing the cumulative
        // weight to be less than the max roll. The last item gets the remainder.
        logger.warn("Weighted selection algorithm fell through for case. Returning the last item. This may indicate a data issue with case contents.");
        return caseItems.get(caseItems.size() - 1).getContainedItem();
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
        
        BigDecimal totalDropRate = caseItemRepository.sumDropRatesByCaseId(caseId);
        if (totalDropRate == null) {
            totalDropRate = BigDecimal.ZERO;
        }
        
        return CaseContentsDTO.builder()
            .caseId(caseId)
            .caseName(caseItem.getName())
            .items(itemDTOs)
            .totalDropRate(totalDropRate)
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
        BigDecimal totalDropRate = caseItemRepository.sumDropRatesByCaseId(caseId);
        // Use compareTo for BigDecimal comparison. The sum must be exactly 100.
        return totalDropRate != null && totalDropRate.compareTo(new BigDecimal("100")) == 0;
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
    
    /**
     * Unequips multiple items for a user in a single atomic transaction.
     * @param userId User ID
     * @param itemIds List of item IDs to unequip
     * @return Updated UserProfileDTO
     */
    @Transactional
    public UserProfileDTO unequipBatch(String userId, List<UUID> itemIds) {
        logger.debug("Batch unequipping {} items for user {}", itemIds.size(), userId);

        if (itemIds == null || itemIds.isEmpty()) {
            throw new IllegalArgumentException("Item IDs list cannot be empty");
        }

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));

        List<Shop> itemsToUnequip = shopRepository.findAllById(itemIds);

        if (itemsToUnequip.size() != itemIds.size()) {
            logger.warn("Some items for batch unequip were not found. Requested: {}, Found: {}", itemIds.size(), itemsToUnequip.size());
        }

        for (Shop item : itemsToUnequip) {
            ShopCategory category = item.getCategory();
            if (category == null) continue;

            boolean wasEquipped = false;
            if (category == ShopCategory.BADGE) {
                if (user.isBadgeEquipped(item.getId())) {
                    user.removeEquippedBadge();
                    wasEquipped = true;
                }
            } else if (category == ShopCategory.FISHING_ROD) {
                // Unequipping a fishing rod means unequipping the instance.
                // The client should send the instance ID, but here we can only unequip whatever is equipped.
                // This batch unequip is by item ID, not instance ID, so we just clear the equipped slot.
                UUID equippedInstanceId = user.getEquippedFishingRodInstanceId();
                if (equippedInstanceId != null) {
                    // Find which item this instance belongs to
                    Optional<ItemInstance> instanceOpt = itemInstanceRepository.findById(equippedInstanceId);
                    if (instanceOpt.isPresent() && instanceOpt.get().getBaseItem().getId().equals(item.getId())) {
                        user.setEquippedFishingRodInstanceId(null);
                        wasEquipped = true;
                    }
                }
            } else {
                UUID equippedId = user.getEquippedItemIdByCategory(category);
                if (equippedId != null && equippedId.equals(item.getId())) {
                    user.setEquippedItemIdByCategory(category, null);
                    wasEquipped = true;
                }
            }

            if (wasEquipped && item.getDiscordRoleId() != null && !item.getDiscordRoleId().isEmpty()) {
                logger.debug("Removing Discord role {} from user {} for unequipped item {}", 
                            item.getDiscordRoleId(), userId, item.getId());
                discordService.removeRole(userId, item.getDiscordRoleId());
            }
        }

        userRepository.save(user);

        logger.info("Successfully batch unequipped {} items for user {}", itemsToUnequip.size(), userId);

        return userService.mapToProfileDTO(user);
    }
}
