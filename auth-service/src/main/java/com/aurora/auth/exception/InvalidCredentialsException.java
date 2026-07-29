package com.aurora.auth.exception;

public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException() { super("invalid_credentials"); }
}
