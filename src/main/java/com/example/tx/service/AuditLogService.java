package com.example.tx.service;

import com.example.tx.entity.AuditLogEntity;
import com.example.tx.repository.AuditLogRepository;
import com.example.tx.support.TxProbe;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final TxProbe probe;

    public AuditLogService(AuditLogRepository auditLogRepository, TxProbe probe) {
        this.auditLogRepository = auditLogRepository;
        this.probe = probe;
    }

    /**
     * REQUIRES_NEW: suspends the caller's transaction, opens a NEW physical transaction
     * (separate connection) and commits it when this method returns. The audit row is
     * durable even if the caller rolls back afterwards.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logAction(String action) {
        probe.record("AuditLogService.logAction");
        auditLogRepository.save(new AuditLogEntity(action));
    }
}
