package com.aurora.auth.web;

import com.aurora.auth.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
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

    // "Hesabım" ekranı: giriş yapmış kullanıcının kendi bilgileri
    @GetMapping("/me")
    public ResponseEntity<?> me() {
        return ResponseEntity.ok(authService.getProfile(currentCustomerId()));
    }

    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@RequestBody Map<String, String> request) {
        authService.changePassword(
                currentCustomerId(),
                request.get("currentPassword"),
                request.get("newPassword"));
        return ResponseEntity.ok(Map.of("changed", true));
    }

    // Kimlik token'dan gelir; istemcinin gönderdiği bir id'ye asla güvenmiyoruz.
    private Long currentCustomerId() {
        return Long.parseLong(
                SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString());
    }
}
