package com.app.heartbound.dto.shop;

import com.app.heartbound.enums.Role;
import com.app.heartbound.enums.ShopCategory;
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
    private String name;
    private String description;
    private Integer price;
    private ShopCategory category;
    private String imageUrl;
    private Role requiredRole;
    private boolean owned;  // Indicates if the current user owns this item
    private boolean active = true;
    private LocalDateTime expiresAt;
    private boolean expired = false;
    private boolean equipped;
    private String discordRoleId;

    public boolean isEquipped() {
        return equipped;
    }

    public void setEquipped(boolean equipped) {
        this.equipped = equipped;
    }
}
