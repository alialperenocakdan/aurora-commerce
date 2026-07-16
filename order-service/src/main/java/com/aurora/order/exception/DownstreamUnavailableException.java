package com.aurora.order.exception;

// Altyapı hatası: product-service'e ulaşılamıyor. Circuit breaker'ın şartelini indiren hata budur.
public class DownstreamUnavailableException extends RuntimeException {
    public DownstreamUnavailableException(String message) {
        super(message);
    }
}
