package com.app.heartbound.repositories.shop;

import com.app.heartbound.entities.Shop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ShopRepository extends JpaRepository<Shop, UUID> {
    
    List<Shop> findByIsActiveTrue();
    
    List<Shop> findByCategoryAndIsActiveTrue(String category);
    
    List<Shop> findByRequiredRoleIsNullAndIsActiveTrue();
}
