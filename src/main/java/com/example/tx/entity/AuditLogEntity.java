package com.example.tx.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "audit_log")
public class AuditLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String action;
    private Instant createdAt = Instant.now();

    protected AuditLogEntity() { }

    public AuditLogEntity(String action) {
        this.action = action;
    }

    public Long getId() { return id; }
    public String getAction() { return action; }
    public Instant getCreatedAt() { return createdAt; }
}
