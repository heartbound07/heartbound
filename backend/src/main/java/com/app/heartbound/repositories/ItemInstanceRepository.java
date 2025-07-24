package com.app.heartbound.repositories;

import com.app.heartbound.entities.ItemInstance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ItemInstanceRepository extends JpaRepository<ItemInstance, UUID> {
} 