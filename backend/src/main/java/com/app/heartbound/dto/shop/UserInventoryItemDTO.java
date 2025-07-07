package com.app.heartbound.dto.shop;

import com.app.heartbound.enums.ShopCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserInventoryItemDTO {
    private UUID itemId;
    private String name;
    private String description;
    private ShopCategory category;
    private String thumbnailUrl;
    private String imageUrl;
    private Integer quantity;
    private Integer price;
} 