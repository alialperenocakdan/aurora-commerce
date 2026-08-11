package com.aurora.order.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Swagger arayüzü: /swagger-ui.html
//
// Buradaki asıl iş "Authorize" düğmesini çalışır hale getirmek. Uçların çoğu
// JWT istiyor; şema tanımlanmazsa Swagger'dan denenen her istek 401 döner ve
// doküman gezilebilir olmaktan çıkar.
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI orderServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Aurora Commerce — Sipariş Servisi")
                        .version("1.0.0")
                        .description("""
                                Sepet, sipariş, kupon, favori, bildirim ve satış raporları.

                                **Kimlik doğrulama:** Sağ üstteki *Authorize* düğmesine
                                `/auth/login`'den aldığın `accessToken` değerini yapıştır
                                (başına `Bearer ` yazmana gerek yok).

                                **Yetki:** `/admin/**` altındaki uçlar yalnızca admin
                                hesabına açıktır. `/internal/**` uçları JWT ile değil
                                servisler arası `X-Internal-Token` başlığıyla korunur.

                                **Para birimi:** tüm tutarlar **kuruş** cinsinden tam sayıdır
                                (ör. `2450` = 24,50 ₺).
                                """))
                .addSecurityItem(new SecurityRequirement().addList("bearer-jwt"))
                .components(new Components().addSecuritySchemes("bearer-jwt",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
