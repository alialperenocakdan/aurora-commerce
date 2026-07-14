package com.aurora.product.config;

import com.aurora.product.config.JwtAuthFilter; // Kendi yazdığın filtrenin yolu
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter; // Eklendi

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // 1. Yazdığımız filtreyi buraya enjekte ediyoruz
    private final JwtAuthFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 1. Müşteri vitrini herkese açık
                        .requestMatchers(HttpMethod.GET, "/products", "/products/**").permitAll()

                        // Arka kapıyı Spring Security'den muaf tutuyoruz.
                        // Çünkü şifre kontrolünü (X-Internal-Token) Controller içinde bizzat biz yapıyoruz!
                        .requestMatchers("/internal/**").permitAll()

                        .requestMatchers("/error").permitAll()

                        // Diğer her şey korumalıdır (POST, PUT, DELETE vb.)
                        .anyRequest().authenticated()
                )
                // 2. KRİTİK NOKTA: Token okuma gözlüğümüzü filtre zincirine ekliyoruz!
                // Bu sayede Spring Security "authenticated" yapmadan önce bizim filtremiz token'ı çözecek.
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}