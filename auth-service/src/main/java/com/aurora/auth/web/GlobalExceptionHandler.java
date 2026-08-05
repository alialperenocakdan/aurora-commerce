package com.aurora.auth.web;

import com.aurora.auth.exception.EmailTakenException;
import com.aurora.auth.exception.InvalidCredentialsException;
import com.aurora.auth.exception.InvalidRequestException;
import com.aurora.auth.exception.SamePasswordException;
import com.aurora.auth.exception.WrongPasswordException;
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

    // 422 (401 değil): kullanıcının oturumu geçerli, sadece formdaki
    // "mevcut şifre" alanı yanlış. 401 dönersek istemci oturumu kapatır.
    @ExceptionHandler(WrongPasswordException.class)
    public ResponseEntity<?> wrongPassword(WrongPasswordException e) {
        return ResponseEntity.status(422).body(Map.of("error", "wrong_password"));
    }

    @ExceptionHandler(SamePasswordException.class)
    public ResponseEntity<?> samePassword(SamePasswordException e) {
        return ResponseEntity.status(422).body(Map.of("error", "same_password"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> unexpected(Exception e) {
        log.error("Beklenmeyen hata: {}", e.getMessage(), e);
        return ResponseEntity.status(500).body(Map.of("error", "internal_error"));
    }
}
