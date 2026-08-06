package com.aurora.order.repo;

import com.aurora.order.domain.Favorite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FavoriteRepository extends JpaRepository<Favorite, Long> {

    // En son eklenen favori en üstte
    List<Favorite> findByCustomerIdOrderByIdDesc(Long customerId);

    boolean existsByCustomerIdAndProductId(Long customerId, Long productId);

    @Modifying
    @Query("DELETE FROM Favorite f WHERE f.customerId = :customerId AND f.productId = :productId")
    int deleteByCustomerIdAndProductId(@Param("customerId") Long customerId,
                                       @Param("productId") Long productId);
}
