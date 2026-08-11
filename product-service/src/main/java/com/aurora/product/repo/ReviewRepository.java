package com.aurora.product.repo;

import com.aurora.product.domain.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    // Ürün detayındaki liste: en yeni yorum en üstte
    List<Review> findByProductIdOrderByIdDesc(Long productId);

    Optional<Review> findByProductIdAndCustomerId(Long productId, Long customerId);

    // Ortalama puan Java'da değil veritabanında hesaplanır: bütün yorumları
    // çekip toplamaya gerek yok, ürün başına tek satır dönüyor.
    // COUNT/AVG yorum yoksa (average null) gelir; çağıran taraf bunu 0 sayar.
    @Query("SELECT r.productId AS productId, AVG(r.rating) AS average, COUNT(r) AS count " +
           "FROM Review r GROUP BY r.productId")
    List<RatingSummary> summaries();

    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.productId = :productId")
    Double averageOf(@Param("productId") Long productId);

    long countByProductId(Long productId);

    // Puan dağılımı (5 yıldız kaç kişi, 4 yıldız kaç kişi...). Ortalamanın tek
    // başına anlatmadığını gösterir: 3,0 ortalama "herkes 3 verdi" de olabilir,
    // "yarısı 1 yarısı 5 verdi" de.
    @Query("SELECT r.rating, COUNT(r) FROM Review r WHERE r.productId = :productId " +
           "GROUP BY r.rating")
    List<Object[]> distributionOf(@Param("productId") Long productId);

    // Ürün silinince yorumları da gitsin (reviews'ta foreign key yok:
    // katalog verisiyle yorumu birbirine kilitlemek istemedik).
    void deleteByProductId(Long productId);

    // Katalogdaki yıldızlar için tek sorguda tüm ürünlerin özeti
    interface RatingSummary {
        Long getProductId();
        Double getAverage();
        Long getCount();
    }
}
