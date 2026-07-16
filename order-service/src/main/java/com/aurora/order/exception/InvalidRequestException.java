package com.aurora.order.exception;

// Bozuk istek gövdesi: boş lines, quantity <= 0 vb. → 422
public class InvalidRequestException extends RuntimeException {
    public InvalidRequestException() {
        super("invalid_request");
    }
}
