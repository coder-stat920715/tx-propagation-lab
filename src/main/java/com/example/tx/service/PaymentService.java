package com.example.tx.service;

import com.example.tx.entity.AuditLogEntity;
import com.example.tx.repository.AuditLogRepository;
import com.example.tx.support.TxProbe;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Spring applies @Transactional through a PROXY wrapped around this bean.
 * Only calls that enter through the proxy are intercepted. A call written as
 * this.applyDiscount(...) goes straight to the raw target object: no proxy, no
 * TransactionInterceptor, so REQUIRES_NEW is silently ignored.
 */
@Service
public class PaymentService {

    private final AuditLogRepository auditLogRepository;
    private final DiscountService discountService;
    private final TxProbe probe;
    private final PaymentService self; // proxy reference

    // @Lazy breaks the circular dependency by injecting a lazy-resolving proxy.
    public PaymentService(AuditLogRepository auditLogRepository,
                          DiscountService discountService,
                          TxProbe probe,
                          @Lazy PaymentService self) {
        this.auditLogRepository = auditLogRepository;
        this.discountService = discountService;
        this.probe = probe;
        this.self = self;
    }

    // ------------------------------------------------------------------
    // SCENARIO 3 (BROKEN): self-invocation bypasses the proxy
    // ------------------------------------------------------------------
    @Transactional
    public void processPayment(Long orderId) {
        probe.record("PaymentService.processPayment");
        this.applyDiscount(orderId); // BYPASS: REQUIRES_NEW is ignored, runs in the same tx
        throw new IllegalStateException("Payment gateway failed after discount");
        // Result: the discount row is rolled back together with everything else.
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void applyDiscount(Long orderId) {
        probe.record("PaymentService.applyDiscount");
        auditLogRepository.save(new AuditLogEntity("DISCOUNT_APPLIED:" + orderId));
    }

    // ------------------------------------------------------------------
    // FIX #1 (preferred): call a different bean
    // ------------------------------------------------------------------
    @Transactional
    public void processPaymentSplitBean(Long orderId) {
        probe.record("PaymentService.processPaymentSplitBean");
        discountService.applyDiscount(orderId); // goes through DiscountService's proxy
        throw new IllegalStateException("Payment gateway failed after discount");
    }

    // ------------------------------------------------------------------
    // FIX #2: self-injection (call through the proxy)
    // ------------------------------------------------------------------
    @Transactional
    public void processPaymentSelfInjected(Long orderId) {
        probe.record("PaymentService.processPaymentSelfInjected");
        self.applyDiscount(orderId); // goes through this bean's own proxy
        throw new IllegalStateException("Payment gateway failed after discount");
    }
}
