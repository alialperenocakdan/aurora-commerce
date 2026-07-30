package com.aurora.auth.web;

import com.aurora.auth.exception.EmailTakenException;
import com.aurora.auth.exception.InvalidCredentialsException;
import com.aurora.auth.exception.InvalidRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;


@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<?> invalidRequest(InvalidRequestException e) {
        return ResponseEntity.status(422).body(Map.of("error", "invalid_request"));
    }

    @ExceptionHandler(EmailTakenException.class)
    public ResponseEntity<?> emailTaken(EmailTakenException e) {
        return ResponseEntity.status(409).body(Map.of("error", "email_taken"));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<?> invalidCredentials(InvalidCredentialsException e) {
        return ResponseEntity.status(401).body(Map.of("error", "invalid_credentials"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> unexpected(Exception e) {
        log.error("Beklenmeyen hata: {}", e.getMessage(), e);
        return ResponseEntity.status(500).body(Map.of("error", "internal_error"));
    }
}
