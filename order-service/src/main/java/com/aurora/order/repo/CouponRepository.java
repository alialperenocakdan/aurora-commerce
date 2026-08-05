package com.aurora.order.repo;

import com.aurora.order.domain.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CouponRepository extends JpaRepository<Coupon, Long> {

    Optional<Coupon> findByCode(String code);

    // Kullanım sayacını veritabanı seviyesinde artırır. Java'da okuyup+1 yazmak
    // eşzamanlı iki checkout'ta sayacı kaybettirebilirdi; limiti de aynı sorguda
    // kontrol ederek limit aşımını atomik olarak engelliyoruz.
    @Modifying
    @Query("UPDATE Coupon c SET c.usedCount = c.usedCount + 1 " +
           "WHERE c.id = :id AND (c.usageLimit IS NULL OR c.usedCount < c.usageLimit)")
    int incrementUsage(@Param("id") Long id);
}
