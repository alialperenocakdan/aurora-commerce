package com.aurora.order.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

// product-service.url'yi application.yml'den otomatik alır
@FeignClient(name = "product-service", url = "${product-service.url}")
public interface ProductClient {

    // 1. Sepet için ürün bilgisi çekme (GET /products/{id})
    @GetMapping("/products/{id}")
    Map<String, Object> getProduct(@PathVariable("id") Long id);

    // 2. Stok düşme (POST /internal/stock/deduct)
    @PostMapping(value = "/internal/stock/deduct", consumes = "application/json")
    Map<String, Object> deduct(
            @RequestHeader("X-Internal-Token") String token,
            @RequestBody Map<String, Object> request
    );

    // 3. Stok iade etme (POST /internal/stock/restore)
    @PostMapping(value = "/internal/stock/restore", consumes = "application/json")
    void restore(
            @RequestHeader("X-Internal-Token") String token,
            @RequestBody Map<String, Object> request
    );
}