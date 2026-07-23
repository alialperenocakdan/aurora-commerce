package com.aurora.order.exception;

public class ForbiddenException extends RuntimeException {
    public ForbiddenException() {
        super("forbidden");
    }
}
