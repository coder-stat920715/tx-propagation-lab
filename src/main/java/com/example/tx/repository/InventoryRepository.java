package com.example.tx.repository;

import com.example.tx.entity.InventoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InventoryRepository extends JpaRepository<InventoryEntity, Long> {
    Optional<InventoryEntity> findBySku(String sku);
}
