package com.aurora.order.repo;

import com.aurora.order.domain.OrderStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderStatusHistoryRepository extends JpaRepository<OrderStatusHistory, Long> {

    // Takip çubuğu için: en eski adım en üstte
    List<OrderStatusHistory> findByOrderIdOrderByIdAsc(Long orderId);
}
