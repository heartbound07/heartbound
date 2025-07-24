package com.app.heartbound.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "trade_items")
public class TradeItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trade_id", nullable = false)
    private Trade trade;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_instance_id", referencedColumnName = "id", nullable = false)
    private ItemInstance itemInstance;

} 