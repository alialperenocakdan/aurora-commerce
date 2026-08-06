package com.aurora.order.controller;

import com.aurora.order.domain.Favorite;
import com.aurora.order.repo.FavoriteRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

// Favoriler kullanıcıya özeldir: müşteri kimliği her zaman token'dan alınır,
// istemcinin gönderdiği bir id'ye asla güvenilmez.
//
// Yalnızca ürün id'leri döner; ürün adı/fiyatı istemcide zaten yüklü olan
// katalogdan çözülür. Böylece favori listesi için product-service'e N ayrı
// istek atılmasına gerek kalmaz.
@RestController
@RequestMapping("/favorites")
public class FavoriteController {

    private final FavoriteRepository favoriteRepository;

    public FavoriteController(FavoriteRepository favoriteRepository) {
        this.favoriteRepository = favoriteRepository;
    }

    @GetMapping
    public ResponseEntity<?> list() {
        List<Long> productIds = favoriteRepository
                .findByCustomerIdOrderByIdDesc(currentCustomerId())
                .stream()
                .map(Favorite::getProductId)
                .toList();
        return ResponseEntity.ok(Map.of("productIds", productIds));
    }

    @PostMapping
    public ResponseEntity<?> add(@RequestBody Map<String, Object> request) {
        Object raw = request.get("productId");
        if (!(raw instanceof Number)) {
            return ResponseEntity.status(422).body(Map.of("error", "invalid_request"));
        }
        Long customerId = currentCustomerId();
        Long productId = ((Number) raw).longValue();

        // Zaten favorideyse hata değil: "ekle" isteği tekrarlansa da aynı
        // sonucu vermeli (kalbe iki kez basmak listeyi bozmamalı).
        if (!favoriteRepository.existsByCustomerIdAndProductId(customerId, productId)) {
            try {
                favoriteRepository.save(new Favorite(customerId, productId));
            } catch (DataIntegrityViolationException e) {
                // Eşzamanlı iki istek: benzersizlik kısıtı yakaladı, sorun değil
            }
        }
        return ResponseEntity.ok(Map.of("productId", productId, "favorite", true));
    }

    @DeleteMapping("/{productId}")
    @Transactional
    public ResponseEntity<?> remove(@PathVariable Long productId) {
        favoriteRepository.deleteByCustomerIdAndProductId(currentCustomerId(), productId);
        // Favoride olmayan ürünü silmek de başarı sayılır (idempotent)
        return ResponseEntity.ok(Map.of("productId", productId, "favorite", false));
    }

    private Long currentCustomerId() {
        return Long.parseLong(
                SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString());
    }
}
