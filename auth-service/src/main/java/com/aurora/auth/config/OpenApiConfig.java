package com.aurora.auth.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Swagger arayüzü: /swagger-ui.html
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI authServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Aurora Commerce — Kimlik Servisi")
                        .version("1.0.0")
                        .description("""
                                Kayıt, giriş, profil ve şifre değiştirme.

                                Diğer iki servisin kabul ettiği JWT buradan üretilir.
                                `/auth/login` cevabındaki `accessToken` değerini alıp
                                *Authorize* düğmesine yapıştırarak tüm servislerin
                                Swagger sayfalarında kullanabilirsin.

                                Token içinde müşteri numarası (`sub`) ve admin bayrağı
                                (`admin`) taşınır; yetki kararları bu iki alana bakar.
                                """))
                .addSecurityItem(new SecurityRequirement().addList("bearer-jwt"))
                .components(new Components().addSecuritySchemes("bearer-jwt",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
