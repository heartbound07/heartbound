package com.app.heartbound.dto.shop;

import com.app.heartbound.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShopDTO {
    private UUID id;
    private String name;
    private String description;
    private Integer price;
    private String category;
    private String imageUrl;
    private Role requiredRole;
    private boolean owned;  // Indicates if the current user owns this item
    private boolean active = true;
}
