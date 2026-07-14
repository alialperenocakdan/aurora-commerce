package com.aurora.product.config;

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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {


    @Value("${jwt.secret}")
    private String secret;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");
        System.out.println("🕵️ Gelen Authorization Başlığı: " + authHeader);

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                // Token'ı şifreyle çözüyoruz
                Claims claims = Jwts.parserBuilder()
                        .setSigningKey(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                        .build()
                        .parseClaimsJws(token)
                        .getBody();

                // Token geçerliyse, içindeki müşteri kimliğini (customerId) alıp kapıyı açıyoruz
                String customerId = claims.getSubject();
                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        customerId, null, new ArrayList<>()
                );
                SecurityContextHolder.getContext().setAuthentication(authToken);

            } catch (Exception e) {
                // HATAYI YAKALAMAK İÇİN BURAYI EKLİYORUZ:
                System.out.println("🚨 DİKKAT! Token çözülürken bir hata oluştu!");
                System.out.println("🚨 Hata Mesajı: " + e.getMessage());
                e.printStackTrace();

                // Token sahteyse veya süresi geçmişse sessizce reddedilecek
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }
}