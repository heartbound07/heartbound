package com.app.heartbound.dto.shop;

import com.app.heartbound.enums.Role;
import com.app.heartbound.enums.ShopCategory;
import com.app.heartbound.enums.ItemRarity;
import com.app.heartbound.validation.NoScript;
import com.app.heartbound.validation.SanitizedHtml;
import com.app.heartbound.services.HtmlSanitizationService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShopDTO {
    private UUID id;
    
    @NotBlank(message = "Item name is required")
    @NoScript(allowPunctuation = true)
    @Size(min = 3, max = 100, message = "Item name must be between 3 and 100 characters")
    private String name;
    
    @SanitizedHtml(policy = HtmlSanitizationService.SanitizationPolicy.BASIC, maxLength = 500)
    @Size(max = 500, message = "Description cannot exceed 500 characters")
    private String description;
    
    @NotNull(message = "Price is required")
    @Min(value = 0, message = "Price cannot be negative")
    private Integer price;
    private ShopCategory category;
    private String imageUrl;
    private String thumbnailUrl;
    private Role requiredRole;
    private boolean owned;  // Indicates if the current user owns this item
    private ItemRarity rarity = ItemRarity.COMMON;  // Default to COMMON
    private boolean active = true;
    private LocalDateTime expiresAt;
    private boolean expired = false;
    private boolean equipped;
    private String discordRoleId;
    
    // Case-specific fields
    private boolean isCase = false;  // True if this is a case item
    private Integer caseContentsCount = 0;  // Number of items in this case
    
    // Quantity field for inventory items (especially cases)
    private Integer quantity = 1;  // Default to 1 for compatibility

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
}
