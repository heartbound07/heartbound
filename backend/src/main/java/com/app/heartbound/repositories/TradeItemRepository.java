package com.app.heartbound.repositories;

import com.app.heartbound.entities.TradeItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TradeItemRepository extends JpaRepository<TradeItem, Long> {
} 