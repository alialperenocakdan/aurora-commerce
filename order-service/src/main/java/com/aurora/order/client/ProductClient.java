package com.aurora.order.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

// product-service.url'yi application.yml'den otomatik alır
@FeignClient(name = "product-service", url = "${product-service.url}")
public interface ProductClient {

    //ürün bilgisi çekme
    @GetMapping("/products/{id}")
    Map<String, Object> getProduct(@PathVariable("id") Long id);

    //Stok düşme
    @PostMapping(value = "/internal/stock/deduct", consumes = "application/json")
    Map<String, Object> deduct(
            @RequestHeader("X-Internal-Token") String token,
            @RequestBody Map<String, Object> request
    );

    //Stok iade etme
    @PostMapping(value = "/internal/stock/restore", consumes = "application/json")
    void restore(
            @RequestHeader("X-Internal-Token") String token,
            @RequestBody Map<String, Object> request
    );
}