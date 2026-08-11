package com.aurora.product.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

// order-service'in servisler arası kapısı.
//
// Dikkat: order-service de product-service'i çağırıyor (stok düşme). Yani iki
// servis birbirini çağırıyor. Bu bir sorun değil çünkü çağrılar farklı akışlarda
// ve senkron değil-döngüsel: checkout sırasında yorum sorulmuyor, yorum
// yazarken stok düşülmüyor. Yine de order-service kapalıysa yorum yazılamaz;
// bu durum ReviewService'te açıkça 503'e çevriliyor, sessizce "izin var"
// sayılmıyor.
@FeignClient(name = "order-service", url = "${order-service.url}")
public interface OrderClient {

    @GetMapping("/internal/purchases")
    Map<String, Object> hasPurchased(
            @RequestHeader("X-Internal-Token") String token,
            @RequestParam("customerId") Long customerId,
            @RequestParam("productId") Long productId
    );
}
