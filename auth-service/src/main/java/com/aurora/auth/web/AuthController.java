package com.aurora.auth.web;

import com.aurora.auth.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

// Hata eşlemesi GlobalExceptionHandler'da: controller yalnızca mutlu yolu bilir.
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> request) {
        Long customerId = authService.register(request.get("email"), request.get("password"));
        return ResponseEntity.status(201).body(Map.of("customerId", customerId));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> request) {
        return ResponseEntity.ok(authService.login(request.get("email"), request.get("password")));
    }
}
