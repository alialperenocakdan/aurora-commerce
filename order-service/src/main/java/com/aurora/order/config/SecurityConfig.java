package com.aurora.order.config;

import com.aurora.order.jwt.JwtAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

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
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/error").permitAll()
                        // Vitrindeki kampanya şeridi: giriş yapmamış ziyaretçi de görebilmeli
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/coupons/active").permitAll()
                        // Servisler arası kapı: JWT değil, X-Internal-Token ile korunur.
                        // Doğrulamayı controller kendisi yapar (product-service'teki
                        // /internal/stock ile aynı desen) — o yüzden Spring Security'den muaf.
                        .requestMatchers("/internal/**").permitAll()
                        // Kupon yönetimi yalnızca admin (ürün yazma uçlarıyla aynı kural)
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        // Sepete ürün eklemek veya sipariş vermek için kesinlikle giriş yapılmalıdır.
                        .anyRequest().authenticated()
                )
                // Token'sız/geçersiz istek: whitelabel yerine sözleşmedeki 401 gövdesi
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> {
                            res.setStatus(401);
                            res.setContentType("application/json");
                            res.getWriter().write("{\"error\":\"unauthorized\"}");
                        })
                        // Giriş yapmış ama admin olmayan istek → 403
                        .accessDeniedHandler((req, res, ex) -> {
                            res.setStatus(403);
                            res.setContentType("application/json");
                            res.getWriter().write("{\"error\":\"forbidden\"}");
                        }))
                //bilet kontrolcüsü
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // Tarayıcıdan gelecek arayüz istekleri için CORS izni.
    
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