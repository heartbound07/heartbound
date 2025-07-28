package com.app.heartbound.repositories;

import com.app.heartbound.entities.ItemInstance;
import com.app.heartbound.entities.Shop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ItemInstanceRepository extends JpaRepository<ItemInstance, UUID> {
    List<ItemInstance> findByBaseItem(Shop baseItem);

    @Query("SELECT COUNT(i) FROM ItemInstance i")
    long countTotalInstances();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM ItemInstance i WHERE i.id = :id")
    Optional<ItemInstance> findByIdWithLock(@Param("id") UUID id);
} 