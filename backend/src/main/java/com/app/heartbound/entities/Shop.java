package com.app.heartbound.entities;

import com.app.heartbound.enums.ItemRarity;
import com.app.heartbound.enums.Role;
import com.app.heartbound.enums.ShopCategory;
import com.app.heartbound.validation.NoScript;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "shop_items")
@EntityListeners(ShopEntityListener.class)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Shop {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @NotNull
    @NoScript(allowPunctuation = true)
    @Size(min = 3, max = 100, message = "Item name must be between 3 and 100 characters")
    @Column(name = "name", nullable = false, length = 100)
    private String name;
    
    @Size(max = 500, message = "Description cannot exceed 500 characters")
    @Column(name = "description", length = 500)
    private String description;
    
    @NotNull
    @Min(0)
    private Integer price;
    
    @NotNull
    @Enumerated(EnumType.STRING)
    private ShopCategory category;
    
    private String imageUrl;
    
    @NotNull
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private ItemRarity rarity = ItemRarity.COMMON;
    
    @NotNull
    @Builder.Default
    private Boolean isActive = true;
    
    @Enumerated(EnumType.STRING)
    private Role requiredRole;
    
    @NotNull
    @Builder.Default
    private Boolean isFeatured = false;
    
    @NotNull
    @Builder.Default
    private Boolean isDaily = false;
    
    @CreationTimestamp
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    private LocalDateTime updatedAt;
    
    private LocalDateTime expiresAt;
    
    private String discordRoleId;
    
    private String thumbnailUrl;

    @DecimalMin(value = "0.1", message = "Multiplier must be at least 0.1")
    @DecimalMax(value = "10.0", message = "Multiplier must not exceed 10.0")
    @Column(name = "fishing_rod_multiplier")
    private Double fishingRodMultiplier;

    @Column(name = "gradient_end_color")
    private String gradientEndColor;

    @Column(name = "max_copies")
    private Integer maxCopies;

    @Builder.Default
    @Column(name = "copies_sold")
    private Integer copiesSold = 0;
}
