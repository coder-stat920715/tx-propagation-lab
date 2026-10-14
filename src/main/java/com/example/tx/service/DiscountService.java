package com.example.tx.service;

import com.example.tx.entity.AuditLogEntity;
import com.example.tx.repository.AuditLogRepository;
import com.example.tx.support.TxProbe;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** FIX #1 for self-invocation: extract the REQUIRES_NEW method into its own bean. */
@Service
public class DiscountService {

    private final AuditLogRepository auditLogRepository;
    private final TxProbe probe;

    public DiscountService(AuditLogRepository auditLogRepository, TxProbe probe) {
        this.auditLogRepository = auditLogRepository;
        this.probe = probe;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void applyDiscount(Long orderId) {
        probe.record("DiscountService.applyDiscount");
        auditLogRepository.save(new AuditLogEntity("DISCOUNT_APPLIED:" + orderId));
    }
}
