package com.aurora.auth.service;

import com.aurora.auth.domain.Customer;
import com.aurora.auth.exception.EmailTakenException;
import com.aurora.auth.exception.InvalidCredentialsException;
import com.aurora.auth.exception.InvalidRequestException;
import com.aurora.auth.exception.SamePasswordException;
import com.aurora.auth.exception.WrongPasswordException;
import com.aurora.auth.jwt.JwtService;
import com.aurora.auth.repo.CustomerRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
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

        if (email == null || !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
                || password == null || password.length() < 8
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new InvalidRequestException();
        }

        // E-posta daha önce alınmış mı kontrol et
        if (repository.existsByEmail(email)) {
            throw new EmailTakenException();
        }

        Customer customer = new Customer();
        customer.setEmail(email);
        customer.setPasswordHash(passwordEncoder.encode(password)); // Şifreyi kriptolayıp saklıyoruz

        repository.save(customer);
        return customer.getId();
    }

    public Map<String, Object> login(String email, String password) {
        if (email == null || password == null) {
            throw new InvalidCredentialsException();
        }

        Customer customer = repository.findByEmail(email)
                .orElseThrow(InvalidCredentialsException::new);


        if (!passwordEncoder.matches(password, customer.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }


        String token = jwtService.generateToken(customer.getId(), customer.getEmail(), customer.isAdmin());
        return Map.of("accessToken", token, "expiresIn", jwtService.getExpirationSeconds());
    }

    // "Hesabım" ekranının okuduğu profil bilgisi. Şifre hash'i ASLA dönülmez.
    public Map<String, Object> getProfile(Long customerId) {
        Customer customer = repository.findById(customerId)
                .orElseThrow(InvalidCredentialsException::new);
        return Map.of(
                "customerId", customer.getId(),
                "email", customer.getEmail(),
                "createdAt", customer.getCreatedAt().toString(),
                "admin", customer.isAdmin()
        );
    }

    public void changePassword(Long customerId, String currentPassword, String newPassword) {
        // Yeni şifrenin kuralları kayıttakiyle birebir aynı olmalı
        if (currentPassword == null || newPassword == null
                || newPassword.length() < 8
                || newPassword.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new InvalidRequestException();
        }

        Customer customer = repository.findById(customerId)
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(currentPassword, customer.getPasswordHash())) {
            throw new WrongPasswordException();
        }
        if (passwordEncoder.matches(newPassword, customer.getPasswordHash())) {
            throw new SamePasswordException();
        }

        customer.setPasswordHash(passwordEncoder.encode(newPassword));
        repository.save(customer);
    }
}
