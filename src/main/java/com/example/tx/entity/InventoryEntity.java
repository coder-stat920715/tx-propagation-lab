package com.example.tx.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "inventory")
public class InventoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String sku;

    private int quantity;

    protected InventoryEntity() { }

    public InventoryEntity(String sku, int quantity) {
        this.sku = sku;
        this.quantity = quantity;
    }

    public Long getId() { return id; }
    public String getSku() { return sku; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
}
