package com.aurora.auth.jwt;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class JwtService {


    @Value("${jwt.secret}")
    private String secret;


    @Value("${jwt.expiration:3600000}")
    private long expirationTime;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }


    public long getExpirationSeconds() {
        return expirationTime / 1000;
    }


    public String generateToken(Long customerId, String email, boolean isAdmin) {
        return Jwts.builder()
                .subject(customerId.toString()) // Token'ın sahibi (sub)
                .claim("email", email)
                // Yönetim uygulaması sadece bu claim true olan token'larla ürün
                // yazma uçlarına erişebilir (bkz. product-service SecurityConfig).
                .claim("admin", isAdmin)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationTime))
                .signWith(getSigningKey()) // Kriptografik imza
                .compact();
    }
}