package com.aurora.order.exception;

public class OrderNotFoundException extends RuntimeException {
    public OrderNotFoundException() {
        super("not_found");
    }
}
