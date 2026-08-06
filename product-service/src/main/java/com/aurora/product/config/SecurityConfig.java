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
                .cors(org.springframework.security.config.Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Müşteri vitrini herkese açık
                        .requestMatchers(HttpMethod.GET, "/products", "/products/**").permitAll()

                        // Ürün resimleri (kendi sunucumuzdan barındırılıyor, static klasör) herkese açık
                        .requestMatchers(HttpMethod.GET, "/images/**").permitAll()

                        // Kategori listesi vitrinde de kullanılabilir; oluşturma/silme admin
                        .requestMatchers(HttpMethod.GET, "/categories").permitAll()
                        .requestMatchers(HttpMethod.POST, "/categories").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/categories/**").hasRole("ADMIN")

                        // Arka kapıyı Spring Security'den muaf tutuyoruz.
                        // Çünkü şifre kontrolünü (X-Internal-Token) Controller içinde bizzat biz yapıyoruz!
                        .requestMatchers("/internal/**").permitAll()

                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/error").permitAll()

                        // Ürün yazma işlemleri (ekleme/güncelleme/silme) SADECE admin
                        // hesabına açık — ayrı yönetim uygulaması bunu kullanır.
                        // Normal giriş yapmış bir müşteri (Postman'dan bile) artık bunları çağıramaz.
                        .requestMatchers(HttpMethod.POST, "/products/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/products/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/products/**").hasRole("ADMIN")

                        // Diğer her şey korumalıdır
                        .anyRequest().authenticated()
                )
                // Token'sız/geçersiz istek: whitelabel yerine sözleşmedeki 401 gövdesi.
                // Giriş yapmış ama admin olmayan istek (ör. sıradan müşteri POST dener) → 403.
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> {
                            res.setStatus(401);
                            res.setContentType("application/json");
                            res.getWriter().write("{\"error\":\"unauthorized\"}");
                        })
                        .accessDeniedHandler((req, res, ex) -> {
                            res.setStatus(403);
                            res.setContentType("application/json");
                            res.getWriter().write("{\"error\":\"forbidden\"}");
                        }))
                //  Token okuma gözlüğümüzü filtre zincirine ekliyoruz!
                // Bu sayede Spring Security "authenticated" yapmadan önce bizim filtremiz token'ı çözecek.
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // Tarayıcıdan gelecek arayüz istekleri için CORS izni.
    // Üretimde CORS_ALLOWED_ORIGINS ile gerçek origin listesine daraltın
    // (virgülle ayrılmış, ör. "https://app.example.com,https://admin.example.com").
    @Bean
    public org.springframework.web.cors.CorsConfigurationSource corsConfigurationSource(
            @org.springframework.beans.factory.annotation.Value("${CORS_ALLOWED_ORIGINS:*}") String allowedOrigins) {
        org.springframework.web.cors.CorsConfiguration config = new org.springframework.web.cors.CorsConfiguration();
        config.setAllowedOriginPatterns(java.util.Arrays.stream(allowedOrigins.split(",")).map(String::trim).toList());
        config.setAllowedMethods(java.util.List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(java.util.List.of("*"));
        org.springframework.web.cors.UrlBasedCorsConfigurationSource source = new org.springframework.web.cors.UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}