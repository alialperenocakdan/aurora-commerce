package com.aurora.auth.exception;

public class InvalidRequestException extends RuntimeException {
    public InvalidRequestException() { super("invalid_request"); }
}
