package com.example.tx;

import com.example.tx.entity.InventoryEntity;
import com.example.tx.repository.AuditLogRepository;
import com.example.tx.repository.InventoryRepository;
import com.example.tx.repository.OrderRepository;
import com.example.tx.service.InsufficientStockException;
import com.example.tx.service.OrderService;
import com.example.tx.service.PaymentService;
import com.example.tx.support.TxProbe;
import com.example.tx.support.TxProbe.Observation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.UnexpectedRollbackException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Deliberately NOT annotated with @Transactional: a test-managed transaction would wrap
 * every call and hide the real commit/rollback behavior under test.
 */
@SpringBootTest
class NestedTransactionalIT {

    private static final String SKU = "SKU-1";

    @Autowired OrderService orderService;
    @Autowired PaymentService paymentService;
    @Autowired OrderRepository orderRepository;
    @Autowired InventoryRepository inventoryRepository;
    @Autowired AuditLogRepository auditLogRepository;
    @Autowired TxProbe probe;

    @BeforeEach
    void reset() {
        orderRepository.deleteAll();
        auditLogRepository.deleteAll();
        inventoryRepository.deleteAll();
        probe.clear();
        inventoryRepository.save(new InventoryEntity(SKU, 1)); // only 1 in stock
    }

    // ---------------- Scenario 1 ----------------

    @Test
    void testRequired_WithExceptionHandled_ThrowsUnexpectedRollbackException() {
        assertThatThrownBy(() -> orderService.placeOrderSwallowingInventoryFailure(SKU, 5))
                .isInstanceOf(UnexpectedRollbackException.class)
                .hasMessageContaining("rollback-only");

        // The order insert was rolled back even though the exception was "handled".
        assertThat(orderRepository.count()).isZero();

        // Both methods ran inside the SAME physical transaction.
        Observation outer = probe.find("OrderService.placeOrderSwallowingInventoryFailure").orElseThrow();
        Observation inner = probe.find("InventoryService.deductStock").orElseThrow();
        assertThat(outer.transactionActive()).isTrue();
        assertThat(inner.transactionName()).isEqualTo(outer.transactionName());
    }

    @Test
    void testRequired_WithoutCatch_PropagatesOriginalExceptionAndRollsBack() {
        assertThatThrownBy(() -> orderService.placeOrderPropagatingInventoryFailure(SKU, 5))
                .isInstanceOf(InsufficientStockException.class);
        assertThat(orderRepository.count()).isZero();
    }

    @Test
    void testRequiresNew_OnRiskyCall_AllowsParentToCatchAndCommit() {
        orderService.placeOrderWithIsolatedInventory(SKU, 5);

        assertThat(orderRepository.findAll()).singleElement()
                .satisfies(o -> assertThat(o.getStatus()).isEqualTo("BACKORDERED"));
        assertThat(inventoryRepository.findBySku(SKU).orElseThrow().getQuantity()).isEqualTo(1);

        Observation outer = probe.find("OrderService.placeOrderWithIsolatedInventory").orElseThrow();
        Observation inner = probe.find("InventoryService.deductStockRequiresNew").orElseThrow();
        assertThat(inner.transactionName()).isNotEqualTo(outer.transactionName());
    }

    // ---------------- Scenario 2 ----------------

    @Test
    void testRequiresNew_CommitsIndependently_WhenParentRollsBack() {
        assertThatThrownBy(() -> orderService.placeOrderThenFail(SKU, 1))
                .isInstanceOf(IllegalStateException.class);

        assertThat(orderRepository.count()).isZero();                                  // parent rolled back
        assertThat(auditLogRepository.findByAction("ORDER_CREATED")).hasSize(1);       // audit survived

        Observation outer = probe.find("OrderService.placeOrderThenFail").orElseThrow();
        Observation audit = probe.find("AuditLogService.logAction").orElseThrow();
        assertThat(audit.transactionName()).isNotEqualTo(outer.transactionName());
    }

    // ---------------- Scenario 3 ----------------

    @Test
    void testSelfInvocation_BypassesProxy() {
        // The bean IS proxied. External calls are intercepted; this.xxx() calls are not.
        assertThat(AopUtils.isAopProxy(paymentService)).isTrue();

        assertThatThrownBy(() -> paymentService.processPayment(42L))
                .isInstanceOf(IllegalStateException.class);

        Observation outer = probe.find("PaymentService.processPayment").orElseThrow();
        Observation inner = probe.find("PaymentService.applyDiscount").orElseThrow();

        // REQUIRES_NEW was ignored: inner still reports the OUTER transaction's name.
        assertThat(outer.transactionName()).endsWith("PaymentService.processPayment");
        assertThat(inner.transactionName()).isEqualTo(outer.transactionName());

        // Consequently the discount row rolled back with the outer tx (0 rows).
        assertThat(auditLogRepository.findByAction("DISCOUNT_APPLIED:42")).isEmpty();
    }

    @Test
    void testSelfInvocationFix_SplitBean_HonorsRequiresNew() {
        assertThatThrownBy(() -> paymentService.processPaymentSplitBean(42L))
                .isInstanceOf(IllegalStateException.class);

        Observation outer = probe.find("PaymentService.processPaymentSplitBean").orElseThrow();
        Observation inner = probe.find("DiscountService.applyDiscount").orElseThrow();
        assertThat(inner.transactionName()).isNotEqualTo(outer.transactionName());
        assertThat(auditLogRepository.findByAction("DISCOUNT_APPLIED:42")).hasSize(1);
    }

    @Test
    void testSelfInvocationFix_SelfInjection_HonorsRequiresNew() {
        assertThatThrownBy(() -> paymentService.processPaymentSelfInjected(42L))
                .isInstanceOf(IllegalStateException.class);

        Observation outer = probe.find("PaymentService.processPaymentSelfInjected").orElseThrow();
        Observation inner = probe.find("PaymentService.applyDiscount").orElseThrow();
        assertThat(inner.transactionName()).isNotEqualTo(outer.transactionName());
        assertThat(inner.transactionName()).endsWith("PaymentService.applyDiscount");
        assertThat(auditLogRepository.findByAction("DISCOUNT_APPLIED:42")).hasSize(1);
    }
}
