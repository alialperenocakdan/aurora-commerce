package com.aurora.product.web;

import com.aurora.product.service.StockService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/internal/stock")
public class InternalStockController {

    private final StockService stockService;

    // application.yml'den gelen veya varsayılan olan gizli şifre
    @Value("${INTERNAL_TOKEN:local-internal-token}")
    private String internalToken;
    public InternalStockController(StockService stockService) {
        this.stockService = stockService;
    }

    // Stok düş
    // Stok düş
    @PostMapping("/deduct")
    public ResponseEntity<?> deduct(@RequestHeader("X-Internal-Token") String token,
                                    @RequestBody Map<String, List<Map<String, Object>>> request) {

        // DEĞİŞKEN İSİMLERİ DÜZELTİLDİ
        System.out.println("🕵️ Order Servisinden Gelen Mühür: " + token);
        System.out.println("🔐 Product Servisinin Beklediği Mühür: " + internalToken);

        if (!internalToken.equals(token)) {
            return ResponseEntity.status(403).build(); // Yanlış şifreyse kov!
        }

        try {
            List<Map<String, Object>> result = stockService.deduct(request.get("lines"));
            return ResponseEntity.ok(Map.of("lines", result));
        } catch (RuntimeException e) {
            return ResponseEntity.status(409).body(Map.of("error", "out_of_stock"));
        }
    }
}