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

    // Bu gizli anahtarı application.yml dosyasından okuyacağız
    @Value("${jwt.secret}")
    private String secret;

    // Token 1 saat (3600000 milisaniye) geçerli olacak
    @Value("${jwt.expiration:3600000}")
    private long expirationTime;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    // Müşteri ID'si ve mailini alıp kriptolu bir Token (bilet) üretiyoruz
    public String generateToken(Long customerId, String email) {
        return Jwts.builder()
                .subject(customerId.toString()) // Token'ın sahibi (sub)
                .claim("email", email)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationTime))
                .signWith(getSigningKey()) // Kriptografik imza
                .compact();
    }
}