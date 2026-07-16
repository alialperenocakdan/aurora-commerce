package com.aurora.order.exception;

// İş kuralı hatası: stok yetersiz. Circuit breaker bunu "hata" olarak SAYMAZ.
public class OutOfStockException extends RuntimeException {
    public OutOfStockException() {
        super("out_of_stock");
    }
}
