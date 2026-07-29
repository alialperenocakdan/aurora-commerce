package com.aurora.auth.service;

import com.aurora.auth.domain.Customer;
import com.aurora.auth.exception.EmailTakenException;
import com.aurora.auth.exception.InvalidCredentialsException;
import com.aurora.auth.exception.InvalidRequestException;
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
        // Bozuk e-posta veya 8 karakterden kısa şifre → 422.
        // Üst sınır 72 bayt: BCrypt yalnızca ilk 72 baytı işler, fazlası ya kırpılır
        // ya da yeni Spring Security sürümlerinde hata fırlatır — baştan reddetmek en netidir.
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

        // Gelen şifre ile veritabanındaki kriptolu şifre eşleşiyor mu
        if (!passwordEncoder.matches(password, customer.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        // Eşleşiyorsa bileti (JWT) kesip gönderiyoruz
        String token = jwtService.generateToken(customer.getId(), customer.getEmail());
        return Map.of("accessToken", token, "expiresIn", jwtService.getExpirationSeconds());
    }
}
