package com.aurora.product.web;

import com.aurora.product.service.StockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/internal/stock")
public class InternalStockController {

    private static final Logger log = LoggerFactory.getLogger(InternalStockController.class);

    private final StockService stockService;

    // application.yml'den gelen veya varsayılan olan gizli şifre
    @Value("${INTERNAL_TOKEN:local-internal-token}")
    private String internalToken;
    public InternalStockController(StockService stockService) {
        this.stockService = stockService;
    }

    // Stok düş
    @PostMapping("/deduct")
    public ResponseEntity<?> deduct(@RequestHeader("X-Internal-Token") String token,
                                    @RequestBody Map<String, List<Map<String, Object>>> request) {

        if (!internalToken.equals(token)) {
            log.warn("Deduct isteği reddedildi: gecersiz X-Internal-Token");
            return ResponseEntity.status(403).build(); // Yanlış şifreyse kov!
        }

        log.info("Stok düşme isteği alındı: lines={}", request.get("lines"));
        try {
            List<Map<String, Object>> result = stockService.deduct(request.get("lines"));
            log.info("Stok düşme başarılı: result={}", result);
            return ResponseEntity.ok(Map.of("lines", result));
        } catch (RuntimeException e) {
            log.warn("Stok yetersiz: lines={}", request.get("lines"));
            return ResponseEntity.status(409).body(Map.of("error", "out_of_stock"));
        }
    }

    // Stok iade (Saga Telafisi)
    @PostMapping("/restore")
    public ResponseEntity<?> restore(@RequestHeader("X-Internal-Token") String token,
                                     @RequestBody Map<String, List<Map<String, Object>>> request) {

        if (!internalToken.equals(token)) {
            log.warn("Restore isteği reddedildi: gecersiz X-Internal-Token");
            return ResponseEntity.status(403).build();
        }

        log.info("Stok iade isteği alındı: lines={}", request.get("lines"));
        try {
            stockService.restore(request.get("lines"));
            log.info("Stok başarıyla iade edildi: lines={}", request.get("lines"));
            return ResponseEntity.ok(Map.of("restored", true));
        } catch (Exception e) {
            log.error("Stok iade işlemi sırasında hata: {}", e.getMessage(), e);
            return ResponseEntity.status(500).build();
        }
    }
}
