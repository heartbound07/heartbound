package com.app.heartbound.services.shop;

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
import com.app.heartbound.exceptions.shop.CaseNotFoundException;
import com.app.heartbound.exceptions.shop.CaseNotOwnedException;
import com.app.heartbound.exceptions.shop.EmptyCaseException;
import com.app.heartbound.exceptions.shop.InvalidCaseContentsException;
import com.app.heartbound.repositories.UserRepository;
import com.app.heartbound.repositories.shop.ShopRepository;
import com.app.heartbound.repositories.shop.CaseItemRepository;
import com.app.heartbound.repositories.ItemInstanceRepository;
import com.app.heartbound.repositories.RollAuditRepository;
import com.app.heartbound.entities.RollAudit;
import com.app.heartbound.services.UserService;
import com.app.heartbound.services.SecureRandomService;
import com.app.heartbound.services.RollVerificationService;
import com.app.heartbound.services.AuditService;
import com.app.heartbound.config.CacheConfig;
import com.app.heartbound.dto.CreateAuditDTO;
import com.app.heartbound.enums.AuditSeverity;
import com.app.heartbound.enums.AuditCategory;
import com.app.heartbound.mappers.ShopMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

@Service
public class CaseService {
    
    private final ShopRepository shopRepository;
    private final UserRepository userRepository;
    private final ItemInstanceRepository itemInstanceRepository;
    private final CaseItemRepository caseItemRepository;
    private final SecureRandomService secureRandomService;
    private final RollAuditRepository rollAuditRepository;
    private final RollVerificationService rollVerificationService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final CacheConfig cacheConfig;
    private final ShopMapper shopMapper;
    private static final Logger logger = LoggerFactory.getLogger(CaseService.class);

    public CaseService(
        ShopRepository shopRepository,
        UserRepository userRepository,
        ItemInstanceRepository itemInstanceRepository,
        CaseItemRepository caseItemRepository,
        @Lazy UserService userService,
        SecureRandomService secureRandomService,
        RollAuditRepository rollAuditRepository,
        RollVerificationService rollVerificationService,
        AuditService auditService,
        CacheConfig cacheConfig,
        ShopMapper shopMapper
    ) {
        this.shopRepository = shopRepository;
        this.userRepository = userRepository;
        this.itemInstanceRepository = itemInstanceRepository;
        this.caseItemRepository = caseItemRepository;
        this.secureRandomService = secureRandomService;
        this.rollAuditRepository = rollAuditRepository;
        this.rollVerificationService = rollVerificationService;
        this.auditService = auditService;
        this.cacheConfig = cacheConfig;
        this.objectMapper = new ObjectMapper();
        this.shopMapper = shopMapper;
    }
    
    /**
     * Open a case and receive a random item
     * @param userId User ID opening the case
     * @param caseId Case ID to open
     * @return RollResultDTO with the won item details
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
        
        // 3. Verify and consume one case atomically to prevent race conditions
        ItemInstance caseToConsume = user.getItemInstances().stream()
                .filter(instance -> instance.getBaseItem().getId().equals(caseId))
                .findFirst()
                .orElseThrow(() -> new CaseNotOwnedException("You do not own this case or have no cases left to open"));

        // Perform atomic deletion - if another transaction already deleted this instance,
        // the delete operation will fail gracefully
        try {
            itemInstanceRepository.delete(caseToConsume);
            user.getItemInstances().remove(caseToConsume);
        } catch (Exception e) {
            // If deletion fails (likely due to concurrent access), re-check ownership
            boolean stillOwnsCase = user.getItemInstances().stream()
                    .anyMatch(instance -> instance.getBaseItem().getId().equals(caseId));
            if (!stillOwnsCase) {
                throw new CaseNotOwnedException("Case was already consumed by another operation");
            }
            throw e; // Re-throw if it's a different error
        }
        
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
        
        // 10. Store credits before operation
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
        
        // 15. Invalidate user profile cache within transaction to prevent stale cache issues
        cacheConfig.invalidateUserProfileCache(userId);
        
        // 16. Calculate processing time
        long processingTime = System.currentTimeMillis() - startTime;
        
        // 17. Create audit record
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
        
        // 18. Save roll audit record (for specialized gambling compliance)
        rollAuditRepository.save(auditRecord);
        
        // 19. Create main audit entry for admin visibility
        createCaseRollAuditEntry(userId, caseId, caseItem, wonItem, alreadyOwned, 
                                compensationAwarded, compensatedCredits, compensatedXp, 
                                rollValue, wonItemDropRate.doubleValue(), creditsAfter - creditsBefore);
        
        logger.info("User {} opened case {} and won item {} (roll: {}, already owned: {}{}) - Seed: {}", 
                   userId, caseId, wonItem.getId(), rollValue, alreadyOwned, 
                   compensationAwarded ? ", compensation awarded: " + compensatedCredits + " credits, " + compensatedXp + " XP" : "",
                   rollSeedHash.substring(0, 8) + "...");
        
        // 20. Return result
        return RollResultDTO.builder()
            .caseId(caseId)
            .caseName(caseItem.getName())
            .wonItem(shopMapper.mapToShopDTO(wonItem, user))
            .rollValue(rollValue) // Return the roll value for frontend animation
            .rolledAt(LocalDateTime.now())
            .alreadyOwned(alreadyOwned)
            .compensationAwarded(compensationAwarded)
            .compensatedCredits(compensationAwarded ? compensatedCredits : null)
            .compensatedXp(compensationAwarded ? compensatedXp : null)
            .build();
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

    // ===== PRIVATE HELPER METHODS =====
    
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
        // Use proper rounding to prevent precision loss for very small drop rates.
        for (CaseItem item : caseItems) {
            long itemWeight = item.getDropRate().multiply(new BigDecimal("10000"))
                    .setScale(0, java.math.RoundingMode.HALF_UP).longValue();
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
     * Map a CaseItem entity to a CaseItemDTO
     * @param caseItem CaseItem entity
     * @return CaseItemDTO
     */
    private CaseItemDTO mapToCaseItemDTO(CaseItem caseItem) {
        return CaseItemDTO.builder()
            .id(caseItem.getId())
            .caseId(caseItem.getCaseShopItem().getId())
            .containedItem(shopMapper.mapToShopDTO(caseItem.getContainedItem(), null))
            .dropRate(caseItem.getDropRate())
            .build();
    }
    
    /**
     * Creates an audit entry for case roll events
     * 
     * @param userId User ID opening the case
     * @param caseId Case ID being opened
     * @param caseItem Case shop item
     * @param wonItem Item won from the case
     * @param alreadyOwned Whether the user already owned the won item
     * @param compensationAwarded Whether compensation was awarded
     * @param compensatedCredits Credits awarded as compensation
     * @param compensatedXp XP awarded as compensation
     * @param rollValue The roll value generated
     * @param dropRate Drop rate of the won item
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
                .source("CaseService")
                .build();
            
            // Use createSystemAuditEntry for internal operations
            auditService.createSystemAuditEntry(auditDTO);
            
        } catch (Exception e) {
            // Log the error but don't let audit failures break the case roll flow
            logger.error("Failed to create audit entry for case roll - userId: {}, caseId: {}, error: {}", 
                userId, caseId, e.getMessage(), e);
        }
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
