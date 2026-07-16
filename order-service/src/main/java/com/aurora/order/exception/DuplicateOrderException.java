package com.aurora.order.exception;

// Aynı Idempotency-Key ile sipariş zaten var (DB unique index yakaladı).
// Controller bu anahtarla orijinal siparişi bulup 200 döner — asla çift sipariş oluşmaz.
public class DuplicateOrderException extends RuntimeException {
    private final String idempotencyKey;

    public DuplicateOrderException(String idempotencyKey) {
        super("duplicate_order");
        this.idempotencyKey = idempotencyKey;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }
}
