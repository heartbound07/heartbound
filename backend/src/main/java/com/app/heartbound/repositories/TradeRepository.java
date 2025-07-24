package com.app.heartbound.repositories;

import com.app.heartbound.entities.Trade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TradeRepository extends JpaRepository<Trade, Long> {
    List<Trade> findByInitiatorIdOrReceiverId(String initiatorId, String receiverId);
} 