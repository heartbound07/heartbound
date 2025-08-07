package com.app.heartbound.repositories.shop;

import com.app.heartbound.entities.UserDailyShopItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Repository interface for UserDailyShopItem entity operations.
 * Handles database interactions for user daily shop item selections.
 */
@Repository
public interface UserDailyShopItemRepository extends JpaRepository<UserDailyShopItem, UUID> {

    /**
     * Find all daily shop items for a specific user on a specific date.
     * This is the primary method used to retrieve cached daily selections.
     * 
     * @param userId The user ID to search for
     * @param selectionDate The date for which to retrieve selections
     * @return List of UserDailyShopItem for the user on the specified date
     */
    @Query("SELECT udsi FROM UserDailyShopItem udsi " +
           "JOIN FETCH udsi.shopItem " +
           "WHERE udsi.userId = :userId AND udsi.selectionDate = :selectionDate " +
           "ORDER BY udsi.createdAt ASC")
    List<UserDailyShopItem> findByUserIdAndSelectionDate(@Param("userId") String userId, 
                                                          @Param("selectionDate") LocalDate selectionDate);

    /**
     * Check if a user has any daily shop items for a specific date.
     * Used for quick existence checks without fetching full data.
     * 
     * @param userId The user ID to check for
     * @param selectionDate The date to check for
     * @return true if any selections exist for the user on the date
     */
    boolean existsByUserIdAndSelectionDate(String userId, LocalDate selectionDate);

    /**
     * Find all records older than a specific date for cleanup purposes.
     * Used by the cleanup service to remove stale data.
     * 
     * @param cutoffDate Records older than this date will be returned
     * @return List of UserDailyShopItem records to be cleaned up
     */
    List<UserDailyShopItem> findBySelectionDateBefore(LocalDate cutoffDate);

    /**
     * Delete all records older than a specific date.
     * Used by the cleanup service for efficient bulk deletion.
     * 
     * @param cutoffDate Records older than this date will be deleted
     * @return Number of records deleted
     */
    @Modifying
    @Query("DELETE FROM UserDailyShopItem udsi WHERE udsi.selectionDate < :cutoffDate")
    int deleteBySelectionDateBefore(@Param("cutoffDate") LocalDate cutoffDate);

    /**
     * Count total number of daily shop selection records.
     * Used for monitoring and maintenance purposes.
     * 
     * @return Total count of all daily shop selection records
     */
    @Query("SELECT COUNT(udsi) FROM UserDailyShopItem udsi")
    long countTotalSelections();

    /**
     * Count number of unique users with daily selections on a specific date.
     * Used for analytics and monitoring purposes.
     * 
     * @param selectionDate The date to count unique users for
     * @return Number of unique users with selections on the date
     */
    @Query("SELECT COUNT(DISTINCT udsi.userId) FROM UserDailyShopItem udsi WHERE udsi.selectionDate = :selectionDate")
    long countUniqueUsersOnDate(@Param("selectionDate") LocalDate selectionDate);
} 