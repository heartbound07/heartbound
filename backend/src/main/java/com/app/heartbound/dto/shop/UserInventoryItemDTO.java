package com.app.heartbound.dto.shop;

import com.app.heartbound.enums.ItemRarity;
import com.app.heartbound.enums.ShopCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserInventoryItemDTO {
    @NotNull
    private UUID itemId;
    private UUID instanceId;
    
    @NotNull
    @Size(max = 100, message = "Item name cannot exceed 100 characters")
    private String name;
    
    @Size(max = 500, message = "Item description cannot exceed 500 characters")
    private String description;
    
    @NotNull
    private ShopCategory category;
    
    @Size(max = 500, message = "Thumbnail URL cannot exceed 500 characters")
    private String thumbnailUrl;
    
    @Size(max = 500, message = "Image URL cannot exceed 500 characters")
    private String imageUrl;
    
    @NotNull
    @Min(value = 0, message = "Quantity cannot be negative")
    private Integer quantity;
    
    @Min(value = 0, message = "Price cannot be negative")
    private Integer price;
    
    private ItemRarity rarity;
    
    private String discordRoleId;
    
    private Integer durability;
    private Integer maxDurability;
    private Long experience;
    private boolean equipped;
} 