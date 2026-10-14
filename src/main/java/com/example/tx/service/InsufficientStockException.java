package com.example.tx.service;

/** Unchecked on purpose: Spring rolls back on RuntimeException by default. */
public class InsufficientStockException extends RuntimeException {
    public InsufficientStockException(String message) {
        super(message);
    }
}
