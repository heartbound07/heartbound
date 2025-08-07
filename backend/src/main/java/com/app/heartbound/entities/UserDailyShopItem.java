package com.app.heartbound.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity representing a user's daily shop item selection.
 * Stores the personalized daily shop items for each user to ensure 
 * persistence across application restarts.
 */
@Data
@Entity
@Table(name = "user_daily_shop_items", 
       uniqueConstraints = @UniqueConstraint(
           columnNames = {"user_id", "shop_item_id", "selection_date"},
           name = "uk_user_daily_shop_selection"
       ),
       indexes = {
           @Index(name = "idx_user_selection_date", columnList = "user_id, selection_date"),
           @Index(name = "idx_selection_date", columnList = "selection_date")
       })
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDailyShopItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * The user ID for whom this daily selection was made
     */
    @Column(name = "user_id", nullable = false, length = 255)
    private String userId;

    /**
     * Reference to the shop item that was selected for the user's daily shop
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shop_item_id", nullable = false)
    private Shop shopItem;

    /**
     * The date for which this selection is valid (LocalDate to ensure daily consistency)
     */
    @Column(name = "selection_date", nullable = false)
    private LocalDate selectionDate;

    /**
     * When this selection record was created
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
} 