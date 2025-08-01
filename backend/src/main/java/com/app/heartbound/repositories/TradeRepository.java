package com.app.heartbound.repositories;

import com.app.heartbound.entities.Trade;
import com.app.heartbound.enums.TradeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface TradeRepository extends JpaRepository<Trade, Long> {
    List<Trade> findByInitiatorIdOrReceiverId(String initiatorId, String receiverId);

    @Query("SELECT t FROM Trade t LEFT JOIN FETCH t.items ti LEFT JOIN FETCH ti.itemInstance ii LEFT JOIN FETCH ii.owner LEFT JOIN FETCH ii.baseItem WHERE t.id = :id")
    Optional<Trade> findByIdWithItems(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Trade t WHERE t.id = :id")
    Optional<Trade> findByIdWithLock(@Param("id") Long id);
    
    List<Trade> findByStatusAndExpiresAtBefore(TradeStatus status, Instant expiresAt);
    
    @Query("SELECT t FROM Trade t WHERE (t.initiator.id = :userId OR t.receiver.id = :userId) AND t.status = 'PENDING' AND (t.expiresAt IS NULL OR t.expiresAt > :now)")
    List<Trade> findActivePendingTradesForUser(@Param("userId") String userId, @Param("now") Instant now);
} 