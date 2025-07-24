package com.app.heartbound.repositories;

import com.app.heartbound.entities.Trade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TradeRepository extends JpaRepository<Trade, Long> {
    List<Trade> findByInitiatorIdOrReceiverId(String initiatorId, String receiverId);

    @Query("SELECT t FROM Trade t LEFT JOIN FETCH t.items ti LEFT JOIN FETCH ti.itemInstance ii LEFT JOIN FETCH ii.owner LEFT JOIN FETCH ii.baseItem WHERE t.id = :id")
    Optional<Trade> findByIdWithItems(@Param("id") Long id);
} 