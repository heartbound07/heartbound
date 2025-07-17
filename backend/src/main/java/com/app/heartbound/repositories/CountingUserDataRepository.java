package com.app.heartbound.repositories;

import com.app.heartbound.entities.CountingUserData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface CountingUserDataRepository extends JpaRepository<CountingUserData, String> {
    
    /**
     * Find users whose timeout has expired and should be un-timed out
     */
    @Query("SELECT cud FROM CountingUserData cud WHERE cud.timeoutExpiry IS NOT NULL AND cud.timeoutExpiry <= :currentTime")
    List<CountingUserData> findExpiredTimeouts(@Param("currentTime") LocalDateTime currentTime);
    
    /**
     * Find users who are currently timed out (timeout expiry is in the future)
     */
    @Query("SELECT cud FROM CountingUserData cud WHERE cud.timeoutExpiry IS NOT NULL AND cud.timeoutExpiry > :currentTime")
    List<CountingUserData> findActiveTimeouts(@Param("currentTime") LocalDateTime currentTime);
    
    /**
     * Check if user is currently timed out
     */
    @Query("SELECT COUNT(cud) > 0 FROM CountingUserData cud WHERE cud.userId = :userId AND cud.timeoutExpiry IS NOT NULL AND cud.timeoutExpiry > :currentTime")
    boolean isUserTimedOut(@Param("userId") String userId, @Param("currentTime") LocalDateTime currentTime);
} 