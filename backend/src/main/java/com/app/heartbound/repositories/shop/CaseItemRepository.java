package com.app.heartbound.repositories.shop;

import com.app.heartbound.entities.CaseItem;
import com.app.heartbound.entities.Shop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CaseItemRepository extends JpaRepository<CaseItem, UUID> {
    
    /**
     * Find all items contained in a specific case
     */
    List<CaseItem> findByCaseShopItem(Shop caseShopItem);
    
    /**
     * Find all items contained in a case by case ID
     */
    @Query("SELECT ci FROM CaseItem ci WHERE ci.caseShopItem.id = :caseId")
    List<CaseItem> findByCaseId(@Param("caseId") UUID caseId);
    
    /**
     * Find all cases that contain a specific item
     */
    List<CaseItem> findByContainedItem(Shop containedItem);
    
    /**
     * Find all case items for a specific case ordered by drop rate (highest first)
     */
    @Query("SELECT ci FROM CaseItem ci WHERE ci.caseShopItem.id = :caseId ORDER BY ci.dropRate DESC")
    List<CaseItem> findByCaseIdOrderByDropRateDesc(@Param("caseId") UUID caseId);
    
    /**
     * Delete all case items for a specific case
     */
    void deleteByCaseShopItem(Shop caseShopItem);
    
    /**
     * Delete all case items by case ID
     */
    @Modifying
    @Query("DELETE FROM CaseItem ci WHERE ci.caseShopItem.id = :caseId")
    void deleteByCaseId(@Param("caseId") UUID caseId);
    
    /**
     * Delete all case items that contain a specific item
     */
    void deleteByContainedItem(Shop containedItem);
    
    /**
     * Check if a case has any items
     */
    boolean existsByCaseShopItem(Shop caseShopItem);
    
    /**
     * Count items in a specific case
     */
    long countByCaseShopItem(Shop caseShopItem);
    
    /**
     * Get total drop rate sum for a case (should be 100 for validation)
     */
    @Query("SELECT SUM(ci.dropRate) FROM CaseItem ci WHERE ci.caseShopItem.id = :caseId")
    Integer sumDropRatesByCaseId(@Param("caseId") UUID caseId);
} 