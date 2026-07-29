package com.aurora.auth.exception;

public class EmailTakenException extends RuntimeException {
    public EmailTakenException() { super("email_taken"); }
}
