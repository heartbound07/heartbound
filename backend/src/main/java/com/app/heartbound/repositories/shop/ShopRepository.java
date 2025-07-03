package com.app.heartbound.repositories.shop;

import com.app.heartbound.entities.Shop;
import com.app.heartbound.enums.ShopCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface ShopRepository extends JpaRepository<Shop, UUID> {
    
    List<Shop> findByIsActiveTrue();
    
    List<Shop> findByCategoryAndIsActiveTrue(ShopCategory category);
    
    List<Shop> findByRequiredRoleIsNullAndIsActiveTrue();

    List<Shop> findByIsActiveTrueAndExpiresAtBeforeAndExpiresAtIsNotNull(LocalDateTime dateTime);
    
    List<Shop> findAllByIdIn(Collection<UUID> ids);
    
    // Featured and Daily items queries
    List<Shop> findByIsFeaturedTrueAndIsActiveTrueOrderByCreatedAtDesc();
    
    List<Shop> findByIsDailyTrueAndIsActiveTrueOrderByCreatedAtDesc();
}
