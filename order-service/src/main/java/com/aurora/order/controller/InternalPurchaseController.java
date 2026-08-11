package com.aurora.order.controller;

import com.aurora.order.repo.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

// Servisler arası kapı: "bu müşteri bu ürünü satın aldı mı?"
//
// Yorum yazma hakkı product-service'te kontrol ediliyor ama bu bilginin sahibi
// order-service. Sipariş tablosunu product-service'e açmak yerine yalnızca
// evet/hayır cevabını veriyoruz — servisler birbirinin verisine değil,
// birbirinin sorusuna bağlı kalıyor.
//
// Bu uç dışarıya kapalıdır: müşteri token'ı işe yaramaz, yalnızca servislerin
// bildiği X-Internal-Token geçer.
@RestController
@RequestMapping("/internal/purchases")
public class InternalPurchaseController {

    private static final Logger log = LoggerFactory.getLogger(InternalPurchaseController.class);

    private final OrderRepository orderRepository;

    @Value("${INTERNAL_TOKEN:local-internal-token}")
    private String internalToken;

    public InternalPurchaseController(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    // Sabit zamanlı karşılaştırma: String.equals ilk farklı karakterde durur,
    // bu da deneme-yanılmayla token'ı karakter karakter tahmin etmeye kapı açar.
    private boolean tokenValid(String candidate) {
        return candidate != null && java.security.MessageDigest.isEqual(
                internalToken.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                candidate.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @GetMapping
    public ResponseEntity<?> hasPurchased(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestParam Long customerId,
            @RequestParam Long productId) {

        if (!tokenValid(token)) {
            log.warn("Satın alma sorgusu reddedildi: gecersiz X-Internal-Token");
            return ResponseEntity.status(403).body(Map.of("error", "forbidden"));
        }

        boolean purchased = orderRepository.hasPurchased(customerId, productId);
        log.info("Satın alma sorgusu: customerId={}, productId={} -> {}",
                customerId, productId, purchased);
        return ResponseEntity.ok(Map.of("purchased", purchased));
    }
}
