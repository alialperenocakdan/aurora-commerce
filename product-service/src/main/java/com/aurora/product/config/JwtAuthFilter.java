package com.aurora.product.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);

    @Value("${jwt.secret}")
    private String secret;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                // Token'ı şifreyle çözüyoruz
                Claims claims = Jwts.parserBuilder()
                        .setSigningKey(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                        .build()
                        .parseClaimsJws(token)
                        .getBody();

                // Token geçerliyse, içindeki müşteri kimliğini (customerId) alıp kapıyı açıyoruz.
                // "admin" claim'i true ise ROLE_ADMIN yetkisi veriyoruz — ürün yönetim
                // uygulaması bu yetki olmadan POST/PUT/DELETE /products'a giremez (bkz. SecurityConfig).
                String customerId = claims.getSubject();
                boolean isAdmin = Boolean.TRUE.equals(claims.get("admin", Boolean.class));
                List<SimpleGrantedAuthority> authorities = isAdmin
                        ? List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
                        : List.of();
                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        customerId, null, authorities
                );
                SecurityContextHolder.getContext().setAuthentication(authToken);

            } catch (Exception e) {
                // Token sahteyse veya süresi geçmişse sessizce reddedilecek (asla ham token'ı loglama!)
                log.warn("Token doğrulanamadı: {}", e.getMessage());
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }
}