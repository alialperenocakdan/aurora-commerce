package com.aurora.order.repo;

import com.aurora.order.domain.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {
    Optional<Order> findByIdempotencyKey(String idempotencyKey);

    // "Siparişlerim" listesi: en yeni en üstte
    List<Order> findByCustomerIdOrderByIdDesc(Long customerId);
}