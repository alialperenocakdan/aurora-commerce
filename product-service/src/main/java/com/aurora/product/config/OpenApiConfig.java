package com.aurora.product.config;

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
    public OpenAPI productServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Aurora Commerce — Ürün Servisi")
                        .version("1.0.0")
                        .description("""
                                Katalog, kategoriler, stok ve ürün yorumları.

                                **Kimlik doğrulama:** Sağ üstteki *Authorize* düğmesine
                                `/auth/login`'den aldığın `accessToken` değerini yapıştır.

                                **Yetki:** katalog okuma (GET) herkese açıktır. Ürün ekleme,
                                güncelleme ve silme yalnızca admin hesabına açıktır. Yorum
                                yazmak için giriş yapmak *ve* o ürünü satın almış olmak gerekir —
                                bu şart sipariş servisine sorularak doğrulanır.

                                **Para birimi:** tüm tutarlar **kuruş** cinsinden tam sayıdır.
                                """))
                .addSecurityItem(new SecurityRequirement().addList("bearer-jwt"))
                .components(new Components().addSecuritySchemes("bearer-jwt",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
