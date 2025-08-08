package com.app.heartbound.repositories;

import com.app.heartbound.entities.TradeItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TradeItemRepository extends JpaRepository<TradeItem, Long> {
    
    /**
     * Delete all trade items for a specific trade and user
     */
    @Modifying
    @Query("DELETE FROM TradeItem ti WHERE ti.trade.id = :tradeId AND ti.itemInstance.owner.id = :userId")
    void deleteByTradeIdAndUserId(@Param("tradeId") Long tradeId, @Param("userId") String userId);
    
    /**
     * Delete all trade items for a specific trade
     */
    @Modifying
    @Query("DELETE FROM TradeItem ti WHERE ti.trade.id = :tradeId")
    void deleteByTradeId(@Param("tradeId") Long tradeId);

    // Debug helpers
    @Query("SELECT COUNT(ti) FROM TradeItem ti WHERE ti.itemInstance.id = :instanceId")
    long countByItemInstanceId(@Param("instanceId") UUID instanceId);

    @Query("SELECT ti.trade.id FROM TradeItem ti WHERE ti.itemInstance.id = :instanceId")
    List<Long> findTradeIdsByItemInstanceId(@Param("instanceId") UUID instanceId);

    @Query("SELECT COUNT(ti) FROM TradeItem ti WHERE ti.trade.id = :tradeId AND ti.itemInstance.owner.id = :userId")
    long countByTradeIdAndUserId(@Param("tradeId") Long tradeId, @Param("userId") String userId);
} 