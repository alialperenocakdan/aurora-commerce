package com.aurora.product.web;

import com.aurora.product.domain.Review;
import com.aurora.product.exception.ReviewException;
import com.aurora.product.service.ReviewService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    // Yorum listesi + puan özeti. Giriş yapmamış ziyaretçi de görebilir;
    // yalnızca "yorum yazabilir misin?" bilgisi için kimlik gerekiyor.
    @GetMapping("/products/{productId}/reviews")
    public ResponseEntity<?> list(@PathVariable Long productId) {
        Long customerId = currentCustomerId();
        List<Review> reviews = reviewService.list(productId);

        List<Map<String, Object>> items = new ArrayList<>();
        for (Review review : reviews) {
            items.add(toView(review, customerId));
        }

        Map<String, Object> body = new HashMap<>();
        body.put("summary", reviewService.summary(productId));
        body.put("items", items);
        // Giriş yoksa form gösterilmez; sunucuya boşuna sormuyoruz.
        String status = customerId == null
                ? "anonymous"
                : reviewService.reviewStatus(productId, customerId);
        body.put("canReview", ReviewService.ELIGIBLE.equals(status));
        // Arayüz doğru mesajı gösterebilsin diye sebebi de gönderiyoruz:
        // "almadın" ile "kontrol edemedim" farklı şeyler.
        body.put("reviewStatus", status);
        Review mine = customerId == null ? null : reviewService.mine(productId, customerId);
        body.put("mine", mine == null ? null : toView(mine, customerId));
        return ResponseEntity.ok(body);
    }

    // Katalogdaki yıldızlar: tüm ürünlerin özeti tek istekte.
    // Ürün listesinin cache'ine dokunmuyoruz — yorum yazılınca ürün cache'ini
    // boşaltmak gerekmesin diye puanlar ayrı bir uçta ve ayrı cache'te duruyor.
    @Cacheable(value = "ratings", key = "'all'")
    @GetMapping("/products/ratings")
    public ResponseEntity<?> ratings() {
        return ResponseEntity.ok(Map.of("items", reviewService.allSummaries()));
    }

    @CacheEvict(value = "ratings", allEntries = true)
    @PostMapping("/products/{productId}/reviews")
    public ResponseEntity<?> create(@PathVariable Long productId,
                                    @RequestBody Map<String, Object> request) {
        Long customerId = requireCustomerId();
        try {
            Review saved = reviewService.create(productId, customerId,
                    intOrNull(request.get("rating")), (String) request.get("comment"));
            return ResponseEntity.status(201).body(toView(saved, customerId));
        } catch (ReviewException e) {
            return error(e);
        }
    }

    @CacheEvict(value = "ratings", allEntries = true)
    @PutMapping("/products/{productId}/reviews/mine")
    public ResponseEntity<?> update(@PathVariable Long productId,
                                    @RequestBody Map<String, Object> request) {
        Long customerId = requireCustomerId();
        try {
            Review saved = reviewService.update(productId, customerId,
                    intOrNull(request.get("rating")), (String) request.get("comment"));
            return ResponseEntity.ok(toView(saved, customerId));
        } catch (ReviewException e) {
            return error(e);
        }
    }

    @CacheEvict(value = "ratings", allEntries = true)
    @DeleteMapping("/products/{productId}/reviews/mine")
    public ResponseEntity<?> delete(@PathVariable Long productId) {
        Long customerId = requireCustomerId();
        try {
            reviewService.delete(productId, customerId);
            return ResponseEntity.ok(Map.of("deleted", true));
        } catch (ReviewException e) {
            return error(e);
        }
    }

    private ResponseEntity<?> error(ReviewException e) {
        return ResponseEntity.status(e.getStatus()).body(Map.of("error", e.getCode()));
    }

    // Yorum sahibinin e-postası GÖSTERİLMEZ: product-service kişisel veriyi
    // hiç görmüyor, elinde yalnızca token'dan gelen müşteri numarası var.
    private Map<String, Object> toView(Review review, Long currentCustomerId) {
        Map<String, Object> view = new HashMap<>();
        view.put("id", review.getId());
        view.put("rating", review.getRating());
        view.put("comment", review.getComment());
        view.put("createdAt", review.getCreatedAt());
        view.put("updatedAt", review.getUpdatedAt());
        view.put("author", "Müşteri #" + review.getCustomerId());
        view.put("mine", review.getCustomerId().equals(currentCustomerId));
        return view;
    }

    private static Integer intOrNull(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    // Giriş yoksa null döner (liste ucu herkese açık)
    private Long currentCustomerId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return null;
        try {
            return Long.parseLong(auth.getPrincipal().toString());
        } catch (NumberFormatException e) {
            return null; // anonymousUser
        }
    }

    // Yazma uçlarında kimlik zorunlu; SecurityConfig zaten 401 veriyor,
    // buraya düşerse token beklenmedik biçimdedir.
    private Long requireCustomerId() {
        Long customerId = currentCustomerId();
        if (customerId == null) throw new ReviewException("unauthorized", 401);
        return customerId;
    }
}
