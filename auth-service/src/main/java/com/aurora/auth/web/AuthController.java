package com.aurora.auth.web;

import com.aurora.auth.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> request) {
        try {
            Long customerId = authService.register(request.get("email"), request.get("password"));
            return ResponseEntity.status(201).body(Map.of("customerId", customerId));
        } catch (RuntimeException e) {
            if (e.getMessage().equals("email_taken")) {
                return ResponseEntity.status(409).body(Map.of("error", "email_taken"));
            }
            return ResponseEntity.status(422).body(Map.of("error", "invalid_request"));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> request) {
        try {
            Map<String, Object> response = authService.login(request.get("email"), request.get("password"));
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.status(401).body(Map.of("error", "invalid_credentials"));
        }
    }
}