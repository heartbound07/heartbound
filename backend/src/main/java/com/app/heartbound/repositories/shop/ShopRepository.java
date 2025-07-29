package com.app.heartbound.repositories.shop;

import com.app.heartbound.entities.Shop;
import com.app.heartbound.enums.ShopCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ShopRepository extends JpaRepository<Shop, UUID> {
    
    List<Shop> findByIsActiveTrue();
    
    List<Shop> findByIsActiveTrueAndIsDailyTrue();

    List<Shop> findByCategoryAndIsActiveTrue(ShopCategory category);
    
    List<Shop> findByCategoryAndIsActiveTrueAndIsDailyTrue(ShopCategory category);

    List<Shop> findByRequiredRoleIsNullAndIsActiveTrue();

    List<Shop> findByIsActiveTrueAndExpiresAtBeforeAndExpiresAtIsNotNull(LocalDateTime dateTime);
    
    List<Shop> findAllByIdIn(Collection<UUID> ids);
    
    // Featured and Daily items queries
    List<Shop> findByIsFeaturedTrueAndIsActiveTrueOrderByCreatedAtDesc();
    
    List<Shop> findByIsDailyTrueAndIsActiveTrueAndExpiresAtAfterOrExpiresAtIsNull(LocalDateTime now);

    List<Shop> findByCategoryAndIsActiveTrueOrderByFishingRodMultiplierDesc(ShopCategory category);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Shop s WHERE s.id = :id")
    Optional<Shop> findByIdWithLock(@Param("id") UUID id);
}
