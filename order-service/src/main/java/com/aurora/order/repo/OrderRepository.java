package com.aurora.order.repo;

import com.aurora.order.domain.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {
    Optional<Order> findByIdempotencyKey(String idempotencyKey);

    // "Siparişlerim" listesi: en yeni en üstte
    List<Order> findByCustomerIdOrderByIdDesc(Long customerId);

    // "Bu müşteri bu ürünü gerçekten aldı mı?" — yorum yazma hakkının dayanağı.
    // Bu bilgi order-service'in verisidir, o yüzden karar da burada veriliyor;
    // product-service yalnızca sonucu soruyor (bkz. InternalPurchaseController).
    //
    // İptal edilen sipariş satın alma sayılmaz. Diğer tüm durumlar (pending →
    // delivered) sayılır: para ödenmiş, ürün müşterinin elinde ya da yolda.
    @Query("SELECT COUNT(i) > 0 FROM Order o JOIN o.items i " +
           "WHERE o.customerId = :customerId AND i.productId = :productId " +
           "AND o.status <> 'cancelled'")
    boolean hasPurchased(@Param("customerId") Long customerId,
                         @Param("productId") Long productId);
}