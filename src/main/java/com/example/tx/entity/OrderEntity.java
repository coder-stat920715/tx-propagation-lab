package com.example.tx.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "orders") // "order" is a reserved word
public class OrderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String sku;
    private int quantity;
    private String status;

    protected OrderEntity() { }

    public OrderEntity(String sku, int quantity, String status) {
        this.sku = sku;
        this.quantity = quantity;
        this.status = status;
    }

    public Long getId() { return id; }
    public String getSku() { return sku; }
    public int getQuantity() { return quantity; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
