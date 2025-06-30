package com.app.heartbound.repositories;

import com.app.heartbound.entities.CountingGameState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
 
@Repository
public interface CountingGameStateRepository extends JpaRepository<CountingGameState, Long> {
} 