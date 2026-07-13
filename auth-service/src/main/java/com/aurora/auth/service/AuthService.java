package com.aurora.auth.service;

import com.aurora.auth.domain.Customer;
import com.aurora.auth.jwt.JwtService;
import com.aurora.auth.repo.CustomerRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class AuthService {

    private final CustomerRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(CustomerRepository repository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public Long register(String email, String password) {
        // E-posta daha önce alınmış mı kontrol et
        if (repository.existsByEmail(email)) {
            throw new RuntimeException("email_taken");
        }

        Customer customer = new Customer();
        customer.setEmail(email);
        customer.setPasswordHash(passwordEncoder.encode(password)); // Şifreyi kriptolayıp saklıyoruz

        repository.save(customer);
        return customer.getId();
    }

    public Map<String, Object> login(String email, String password) {
        Customer customer = repository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("invalid_credentials"));

        // Gelen şifre ile veritabanındaki kriptolu şifre eşleşiyor mu?
        if (!passwordEncoder.matches(password, customer.getPasswordHash())) {
            throw new RuntimeException("invalid_credentials");
        }

        // Eşleşiyorsa bileti (JWT) kesip gönderiyoruz
        String token = jwtService.generateToken(customer.getId(), customer.getEmail());
        return Map.of("accessToken", token, "expiresIn", 3600);
    }
}