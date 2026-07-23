package com.aurora.order.exception;

// Gemini API'ye ulaşılamıyor/timeout/hata — 502 (product-service'in 503'ünden ayrı bir kod)
public class UpstreamUnavailableException extends RuntimeException {
    public UpstreamUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
