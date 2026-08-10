package com.aurora.order.repo;

import com.aurora.order.domain.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByCustomerIdOrderByIdDesc(Long customerId);

    long countByCustomerIdAndReadAtIsNull(Long customerId);

    boolean existsByOrderIdAndType(Long orderId, String type);

    @Modifying
    @Query("UPDATE Notification n SET n.readAt = :now " +
           "WHERE n.customerId = :customerId AND n.readAt IS NULL")
    int markAllRead(@Param("customerId") Long customerId, @Param("now") Instant now);
}
