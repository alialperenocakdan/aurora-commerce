package com.aurora.product.service;

import com.aurora.product.client.OrderClient;
import com.aurora.product.domain.Review;
import com.aurora.product.exception.ReviewException;
import com.aurora.product.repo.ProductRepository;
import com.aurora.product.repo.ReviewRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Yorum kurallarının tek sahibi.
//
// Buradaki asıl karar şu: "yorum yazabilmek için o ürünü satın almış olmak"
// kuralı BURADA uygulanıyor ama kuralın dayandığı VERİ order-service'te.
// İki seçenek vardı:
//   1) Sipariş tablosunu product-service'e açmak (ya da kopyalamak)
//   2) order-service'e "aldı mı?" diye sormak
// İkincisi seçildi: her servis kendi verisinin tek sahibi kalıyor, kopya veri
// eskimiyor ve sipariş şeması değişince product-service kırılmıyor.
@Service
public class ReviewService {

    private static final Logger log = LoggerFactory.getLogger(ReviewService.class);
    private static final int MAX_COMMENT = 500;

    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final OrderClient orderClient;
    private final String internalToken;

    public ReviewService(ReviewRepository reviewRepository,
                         ProductRepository productRepository,
                         OrderClient orderClient,
                         @Value("${order-service.internal-token}") String internalToken) {
        this.reviewRepository = reviewRepository;
        this.productRepository = productRepository;
        this.orderClient = orderClient;
        this.internalToken = internalToken;
    }

    @Transactional
    public Review create(Long productId, Long customerId, Integer rating, String comment) {
        requireProduct(productId);
        int validRating = validateRating(rating);
        String validComment = validateComment(comment);

        if (reviewRepository.findByProductIdAndCustomerId(productId, customerId).isPresent()) {
            throw new ReviewException("already_reviewed", 409);
        }
        // Kuralın kalbi: satın alma doğrulanmadan yorum yazılamaz
        if (!purchased(customerId, productId)) {
            throw new ReviewException("not_purchased", 403);
        }

        try {
            return reviewRepository.save(new Review(productId, customerId, validRating, validComment));
        } catch (DataIntegrityViolationException e) {
            // Aynı kişi iki isteği aynı anda gönderdi: benzersizlik kısıtı yakaladı.
            // Yukarıdaki kontrol yarışı kapatmaz, veritabanı kapatır.
            throw new ReviewException("already_reviewed", 409);
        }
    }

    // Kendi yorumunu düzeltmek satın alma kontrolünü tekrar gerektirmez:
    // yorumun varlığı zaten o kontrolden geçmiş olduğunun kanıtı.
    @Transactional
    public Review update(Long productId, Long customerId, Integer rating, String comment) {
        Review review = reviewRepository.findByProductIdAndCustomerId(productId, customerId)
                .orElseThrow(() -> new ReviewException("review_not_found", 404));
        review.setRating(validateRating(rating));
        review.setComment(validateComment(comment));
        review.setUpdatedAt(Instant.now());
        return reviewRepository.save(review);
    }

    @Transactional
    public void delete(Long productId, Long customerId) {
        Review review = reviewRepository.findByProductIdAndCustomerId(productId, customerId)
                .orElseThrow(() -> new ReviewException("review_not_found", 404));
        reviewRepository.delete(review);
    }

    public List<Review> list(Long productId) {
        return reviewRepository.findByProductIdOrderByIdDesc(productId);
    }

    public Review mine(Long productId, Long customerId) {
        return reviewRepository.findByProductIdAndCustomerId(productId, customerId).orElse(null);
    }

    // Ürün detayında formu göstermeden önce sorulur. Sipariş servisi cevap
    // vermezse "yazamaz" deyip sayfayı ayakta tutuyoruz: yorum yazma denemesi
    // yine de gerçek cevabı alacak (create() aynı kontrolü sert yapıyor).
    public boolean canReviewQuietly(Long productId, Long customerId) {
        if (reviewRepository.findByProductIdAndCustomerId(productId, customerId).isPresent()) {
            return false; // zaten yorumu var
        }
        try {
            return purchased(customerId, productId);
        } catch (ReviewException e) {
            log.warn("Yorum hakkı sorulamadı (sipariş servisi): productId={}", productId);
            return false;
        }
    }

    // Ortalama + adet + dağılım. Ortalama veritabanında hesaplanır;
    // burada yalnızca sunuma hazırlanır (tek ondalık, yorum yoksa 0).
    public Map<String, Object> summary(Long productId) {
        Double average = reviewRepository.averageOf(productId);
        long count = reviewRepository.countByProductId(productId);

        // 1'den 5'e kadar her basamak listede olsun; hiç oy almayan basamak
        // sorgudan gelmez, arayüz eksik anahtarla uğraşmasın.
        Map<String, Long> distribution = new LinkedHashMap<>();
        for (int star = 5; star >= 1; star--) distribution.put(String.valueOf(star), 0L);
        for (Object[] row : reviewRepository.distributionOf(productId)) {
            distribution.put(String.valueOf(row[0]), ((Number) row[1]).longValue());
        }

        return Map.of(
                "average", average == null ? 0.0 : Math.round(average * 10) / 10.0,
                "count", count,
                "distribution", distribution
        );
    }

    // Katalogdaki yıldızlar: ürün başına tek satır, tek sorgu
    public List<Map<String, Object>> allSummaries() {
        return reviewRepository.summaries().stream()
                .map(s -> Map.<String, Object>of(
                        "productId", s.getProductId(),
                        "average", s.getAverage() == null ? 0.0 : Math.round(s.getAverage() * 10) / 10.0,
                        "count", s.getCount()))
                .toList();
    }

    private void requireProduct(Long productId) {
        if (!productRepository.existsById(productId)) {
            throw new ReviewException("product_not_found", 404);
        }
    }

    private int validateRating(Integer rating) {
        if (rating == null || rating < 1 || rating > 5) {
            throw new ReviewException("invalid_rating", 422);
        }
        return rating;
    }

    private String validateComment(String comment) {
        if (comment == null) return null;
        String trimmed = comment.trim();
        if (trimmed.isEmpty()) return null; // yalnızca puan verilmiş
        if (trimmed.length() > MAX_COMMENT) {
            throw new ReviewException("comment_too_long", 422);
        }
        return trimmed;
    }

    // Servisler arası soru. Cevap alınamazsa "izin var" SAYILMAZ: alt servis
    // çökünce yorum kuralının delinmesi, yorum yazılamamasından daha kötü.
    private boolean purchased(Long customerId, Long productId) {
        try {
            Map<String, Object> body = orderClient.hasPurchased(internalToken, customerId, productId);
            return body != null && Boolean.TRUE.equals(body.get("purchased"));
        } catch (Exception e) {
            log.warn("Sipariş servisine ulaşılamadı: customerId={}, productId={}, hata={}",
                    customerId, productId, e.getMessage());
            throw new ReviewException("order_service_unavailable", 503);
        }
    }
}
