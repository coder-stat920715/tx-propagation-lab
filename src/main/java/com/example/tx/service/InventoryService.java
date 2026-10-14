package com.example.tx.service;

import com.example.tx.entity.InventoryEntity;
import com.example.tx.repository.InventoryRepository;
import com.example.tx.support.TxProbe;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final TxProbe probe;

    public InventoryService(InventoryRepository inventoryRepository, TxProbe probe) {
        this.inventoryRepository = inventoryRepository;
        this.probe = probe;
    }

    /**
     * Default Propagation.REQUIRED: JOINS the caller's physical transaction.
     * Throwing a RuntimeException here makes the TransactionInterceptor call
     * setRollbackOnly() on the SHARED transaction, even if the caller catches it.
     */
    @Transactional
    public void deductStock(String sku, int quantity) {
        probe.record("InventoryService.deductStock");
        doDeduct(sku, quantity);
    }

    /**
     * REQUIRES_NEW: runs in its own physical transaction. If it fails, only IT rolls back;
     * the caller's transaction is not poisoned and may safely catch the exception.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deductStockRequiresNew(String sku, int quantity) {
        probe.record("InventoryService.deductStockRequiresNew");
        doDeduct(sku, quantity);
    }

    private void doDeduct(String sku, int quantity) {
        InventoryEntity inventory = inventoryRepository.findBySku(sku)
                .orElseThrow(() -> new InsufficientStockException("Unknown SKU " + sku));
        if (inventory.getQuantity() < quantity) {
            throw new InsufficientStockException(
                    "Insufficient stock for " + sku + ": requested=" + quantity
                            + ", available=" + inventory.getQuantity());
        }
        inventory.setQuantity(inventory.getQuantity() - quantity);
        inventoryRepository.save(inventory);
    }
}
