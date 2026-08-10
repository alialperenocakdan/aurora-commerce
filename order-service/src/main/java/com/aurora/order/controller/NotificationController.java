package com.aurora.order.controller;

import com.aurora.order.domain.Notification;
import com.aurora.order.repo.NotificationRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

// Bildirimler Kafka tüketicisi tarafından üretilir; burada yalnızca okunur
// ve okundu olarak işaretlenir. Müşteri kimliği her zaman token'dan alınır.
@RestController
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationRepository notificationRepository;

    public NotificationController(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @GetMapping
    public ResponseEntity<?> list() {
        Long customerId = currentCustomerId();
        List<Notification> items =
                notificationRepository.findByCustomerIdOrderByIdDesc(customerId);
        return ResponseEntity.ok(Map.of(
                "items", items,
                "unreadCount", notificationRepository.countByCustomerIdAndReadAtIsNull(customerId)
        ));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<?> unreadCount() {
        return ResponseEntity.ok(Map.of("unreadCount",
                notificationRepository.countByCustomerIdAndReadAtIsNull(currentCustomerId())));
    }

    @PostMapping("/read-all")
    @Transactional
    public ResponseEntity<?> markAllRead() {
        int updated = notificationRepository.markAllRead(currentCustomerId(), Instant.now());
        return ResponseEntity.ok(Map.of("marked", updated));
    }

    private Long currentCustomerId() {
        return Long.parseLong(
                SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString());
    }
}
