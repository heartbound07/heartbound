package com.app.heartbound.repositories;

import com.app.heartbound.entities.TradeItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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
} 