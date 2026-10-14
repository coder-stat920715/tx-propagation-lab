package com.example.tx.service;

import com.example.tx.entity.OrderEntity;
import com.example.tx.repository.OrderRepository;
import com.example.tx.support.TxProbe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final InventoryService inventoryService;
    private final AuditLogService auditLogService;
    private final TxProbe probe;

    public OrderService(OrderRepository orderRepository,
                        InventoryService inventoryService,
                        AuditLogService auditLogService,
                        TxProbe probe) {
        this.orderRepository = orderRepository;
        this.inventoryService = inventoryService;
        this.auditLogService = auditLogService;
        this.probe = probe;
    }

    // ------------------------------------------------------------------
    // SCENARIO 1: REQUIRED + rollback-only trap
    // ------------------------------------------------------------------

    /**
     * THE TRAP. The inner exception is caught, so the business flow "looks" fine, but the
     * shared transaction is already marked rollback-only by InventoryService's proxy.
     * On commit, Spring throws UnexpectedRollbackException and the order is NOT saved.
     */
    @Transactional
    public OrderEntity placeOrderSwallowingInventoryFailure(String sku, int quantity) {
        probe.record("OrderService.placeOrderSwallowingInventoryFailure");
        OrderEntity order = orderRepository.save(new OrderEntity(sku, quantity, "CREATED"));
        try {
            inventoryService.deductStock(sku, quantity);
        } catch (RuntimeException ex) {
            boolean rollbackOnly = TransactionAspectSupport.currentTransactionStatus().isRollbackOnly();
            log.warn("Caught '{}' but the shared tx is rollbackOnly={} -> commit WILL fail",
                    ex.getMessage(), rollbackOnly);
        }
        return order; // commit attempted here -> UnexpectedRollbackException
    }

    /** Baseline: exception propagates, original exception reaches the caller, full rollback. */
    @Transactional
    public OrderEntity placeOrderPropagatingInventoryFailure(String sku, int quantity) {
        probe.record("OrderService.placeOrderPropagatingInventoryFailure");
        OrderEntity order = orderRepository.save(new OrderEntity(sku, quantity, "CREATED"));
        inventoryService.deductStock(sku, quantity);
        return order;
    }

    /**
     * FIX for the trap when failure is genuinely recoverable: the risky call runs in its own
     * transaction (REQUIRES_NEW), so catching the exception is safe and the order commits.
     */
    @Transactional
    public OrderEntity placeOrderWithIsolatedInventory(String sku, int quantity) {
        probe.record("OrderService.placeOrderWithIsolatedInventory");
        OrderEntity order = orderRepository.save(new OrderEntity(sku, quantity, "CREATED"));
        try {
            inventoryService.deductStockRequiresNew(sku, quantity);
        } catch (InsufficientStockException ex) {
            log.warn("Inventory failed in its own tx: {}. Order downgraded to BACKORDERED.", ex.getMessage());
            order.setStatus("BACKORDERED");
            orderRepository.save(order);
        }
        return order;
    }

    // ------------------------------------------------------------------
    // SCENARIO 2: REQUIRES_NEW audit survives parent rollback
    // ------------------------------------------------------------------

    @Transactional
    public void placeOrderThenFail(String sku, int quantity) {
        probe.record("OrderService.placeOrderThenFail");
        orderRepository.save(new OrderEntity(sku, quantity, "CREATED"));
        auditLogService.logAction("ORDER_CREATED"); // commits independently, right now
        throw new IllegalStateException("Simulated failure after audit log was written");
    }
}
