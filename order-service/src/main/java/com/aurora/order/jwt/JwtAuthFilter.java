package com.aurora.order.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {


    @Value("${jwt.secret}")
    private String secret;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        // Bearer <Token>" bilet varsa
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7); // "Bearer " kısmını at
            try {
                SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
                Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();

                // Biletten müşteri ID'sini çıkar ve sisteme "Bu müşteri giriş yaptı" de.
                // "admin" claim'i varsa ROLE_ADMIN veriyoruz — kupon yönetimi
                // uçları bunu ister (product-service ile aynı desen).
                String customerId = claims.getSubject();
                boolean isAdmin = Boolean.TRUE.equals(claims.get("admin", Boolean.class));
                var authorities = isAdmin
                        ? java.util.List.of(
                            new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"))
                        : java.util.List.<org.springframework.security.core.authority.SimpleGrantedAuthority>of();
                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(customerId, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (Exception e) {
                // Token geçersizse, süresi dolmuşsa veya sahteyse sessizce reddeder (401 döner)
            }
        }
        filterChain.doFilter(request, response);
    }
}