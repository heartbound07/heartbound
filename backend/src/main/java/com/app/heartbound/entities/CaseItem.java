package com.app.heartbound.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * CaseItem Entity
 * 
 * Represents an item that can be obtained from a case with a specific drop rate.
 * Links cases to their possible contents with probability weights.
 */
@Data
@Entity
@Table(name = "case_items",
       indexes = {
           @Index(name = "idx_case_item_case_id", columnList = "case_id"),
           @Index(name = "idx_case_item_contained_item_id", columnList = "contained_item_id"),
           @Index(name = "idx_case_item_drop_rate", columnList = "drop_rate")
       })
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CaseItem {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    /**
     * The case (Shop item with category = CASE) that contains this item
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_id", nullable = false)
    @NotNull
    private Shop caseShopItem;
    
    /**
     * The item that can be won from this case
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contained_item_id", nullable = false)
    @NotNull
    private Shop containedItem;
    
    /**
     * Drop rate as a weight value.
     * Higher values = more likely to drop relative to other items in the same case.
     */
    @NotNull
    @DecimalMin(value = "0.0", inclusive = false)
    @Column(name = "drop_rate", nullable = false, precision = 10, scale = 4)
    private BigDecimal dropRate;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
} 