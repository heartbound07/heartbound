package com.app.heartbound.dto.shop;

import com.app.heartbound.config.security.Views;
import com.app.heartbound.enums.Role;
import com.app.heartbound.enums.ShopCategory;
import com.app.heartbound.enums.ItemRarity;
import com.app.heartbound.enums.FishingRodPart;
import com.app.heartbound.validation.NoScript;
import com.app.heartbound.validation.SanitizedHtml;
import com.app.heartbound.services.HtmlSanitizationService;
import com.fasterxml.jackson.annotation.JsonView;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShopDTO {
    @JsonView(Views.Public.class)
    private UUID id;
    
    @NotBlank(message = "Item name is required")
    @NoScript(allowPunctuation = true)
    @Size(min = 3, max = 100, message = "Item name must be between 3 and 100 characters")
    @JsonView(Views.Public.class)
    private String name;
    
    @SanitizedHtml(policy = HtmlSanitizationService.SanitizationPolicy.BASIC, maxLength = 500)
    @Size(max = 500, message = "Description cannot exceed 500 characters")
    @JsonView(Views.Public.class)
    private String description;
    
    @NotNull(message = "Price is required")
    @Min(value = 0, message = "Price cannot be negative")
    @JsonView(Views.Public.class)
    private Integer price;
    
    @JsonView(Views.Public.class)
    private ShopCategory category;
    
    @JsonView(Views.Public.class)
    private String imageUrl;
    
    @JsonView(Views.Public.class)
    private String thumbnailUrl;
    
    @JsonView(Views.Admin.class)
    private Role requiredRole;

    @JsonView(Views.Public.class)
    private boolean owned;  // Indicates if the current user owns this item

    @Builder.Default
    @JsonView(Views.Public.class)
    private ItemRarity rarity = ItemRarity.COMMON;  // Default to COMMON
    
    @Builder.Default
    @JsonView(Views.Admin.class)
    private boolean active = true;

    @JsonView(Views.Admin.class)
    private LocalDateTime expiresAt;

    @Builder.Default
    @JsonView(Views.Admin.class)
    private boolean expired = false;

    @JsonView(Views.Public.class)
    private boolean equipped;

    @JsonView(Views.Admin.class)
    private String discordRoleId;
    
    // Visibility flags for layout sections
    @Builder.Default
    @JsonView(Views.Admin.class)
    private boolean isFeatured = false;
    @Builder.Default
    @JsonView(Views.Admin.class)
    private boolean isDaily = false;
    
    // Case-specific fields
    @Builder.Default
    @JsonView(Views.Public.class)
    private boolean isCase = false;  // True if this is a case item
    
    @Builder.Default
    @JsonView(Views.Public.class)
    private Integer caseContentsCount = 0;  // Number of items in this case
    
    // Quantity field for inventory items (especially cases)
    @Builder.Default
    @JsonView(Views.Public.class)
    private Integer quantity = 1;  // Default to 1 for compatibility

    @DecimalMin(value = "0.1", message = "Multiplier must be at least 0.1")
    @DecimalMax(value = "10.0", message = "Multiplier must not exceed 10.0")
    @JsonView(Views.Public.class)
    private Double fishingRodMultiplier;

    @JsonView(Views.Public.class)
    private FishingRodPart fishingRodPartType;

    @JsonView(Views.Public.class)
    private String gradientEndColor;

    @JsonView(Views.Admin.class)
    private Integer maxCopies;
    @JsonView(Views.Admin.class)
    private Integer copiesSold;
    @JsonView(Views.Public.class)
    private Integer maxDurability;

    @JsonView(Views.Public.class)
    private Integer maxRepairs;

    @JsonView(Views.Public.class)
    private Integer durabilityIncrease;

    @DecimalMin(value = "0.0", message = "Bonus loot chance must be non-negative")
    @DecimalMax(value = "100.0", message = "Bonus loot chance cannot exceed 100")
    @JsonView(Views.Public.class)
    private Double bonusLootChance;

    @DecimalMin(value = "0.0", message = "Rarity chance increase must be non-negative")
    @DecimalMax(value = "100.0", message = "Rarity chance increase cannot exceed 100")
    @JsonView(Views.Public.class)
    private Double rarityChanceIncrease;

    @DecimalMin(value = "0.0", message = "Multiplier increase must be non-negative")
    @DecimalMax(value = "5.0", message = "Multiplier increase cannot exceed 5.0")
    @JsonView(Views.Public.class)
    private Double multiplierIncrease;

    @DecimalMin(value = "0.0", message = "Negation chance must be non-negative")
    @DecimalMax(value = "100.0", message = "Negation chance cannot exceed 100")
    @JsonView(Views.Public.class)
    private Double negationChance;

    // Instance-specific fields
    @JsonView(Views.Public.class)
    private UUID instanceId;
    @JsonView(Views.Public.class)
    private Integer durability;
    @JsonView(Views.Public.class)
    private Long experience;
    @JsonView(Views.Public.class)
    private Integer level;
    @JsonView(Views.Public.class)
    private Integer repairCount;
    @JsonView(Views.Public.class)
    private Long xpForNextLevel;

    @JsonView(Views.Public.class)
    private Map<FishingRodPart, ShopDTO> equippedParts;

    public boolean isEquipped() {
        return equipped;
    }

    public void setEquipped(boolean equipped) {
        this.equipped = equipped;
    }
    
    public boolean isCase() {
        return isCase;
    }
    
    public void setCase(boolean isCase) {
        this.isCase = isCase;
    }
    
    public Integer getQuantity() {
        return quantity;
    }
    
    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }
    
    public boolean getIsFeatured() {
        return isFeatured;
    }
    
    public void setIsFeatured(boolean isFeatured) {
        this.isFeatured = isFeatured;
    }
    
    public boolean getIsDaily() {
        return isDaily;
    }
    
    public void setIsDaily(boolean isDaily) {
        this.isDaily = isDaily;
    }
}
