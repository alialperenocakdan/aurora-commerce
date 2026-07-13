package com.aurora.product.repo;

import com.aurora.product.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {

    // 1. STOK DÜŞÜMÜ (Atomik kilit ve fiyat yakalama)
    // Eğer stok yetersizse bu sorgu 0 satır günceller (ve null döner).
    // RETURNING sayesinde güncel fiyatı doğrudan veritabanından alıp sepete yansıtıyoruz.
    @Query(
            value = "UPDATE product.products SET stock = stock - :qty WHERE id = :id AND stock >= :qty RETURNING unit_price",
            nativeQuery = true
    )
    Long deductAndReturnPrice(@Param("id") Long id, @Param("qty") Integer qty);

    // 2. STOK İADESİ (Saga telafisi için)
    @Modifying
    @Query(
            value = "UPDATE product.products SET stock = stock + :qty WHERE id = :id",
            nativeQuery = true
    )
    void restoreStock(@Param("id") Long id, @Param("qty") Integer qty);
}