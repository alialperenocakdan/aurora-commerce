package com.aurora.order.controller;

import com.aurora.order.domain.Order;
import com.aurora.order.repo.OrderRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/orders") // DİKKAT: Burası /checkout değil, /orders!
public class OrderQueryController {

    private final OrderRepository orderRepository;

    public OrderQueryController(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getOrder(@PathVariable String id) {

        // 1. Manuel Sayısal Kontrol (Harf varsa 404 dön)
        char[] chars = id.toCharArray();
        int charCount = 0;
        for (char c : chars) {
            if (c < '0' || c > '9') return ResponseEntity.status(404).body(Map.of("error", "not_found"));
            charCount++;
        }
        if (charCount == 0) return ResponseEntity.status(404).body(Map.of("error", "not_found"));

        // 2. Veritabanından bul
        Long orderId = Long.parseLong(id);
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) return ResponseEntity.status(404).body(Map.of("error", "not_found"));

        // 3. Token'dan ID'yi al
        String customerIdStr = SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString();
        Long customerId = Long.parseLong(customerIdStr);

        // 4. Yetki Kontrolü
        if (!order.getCustomerId().equals(customerId)) {
            return ResponseEntity.status(403).body(Map.of("error", "forbidden"));
        }

        // 5. Her şey yolundaysa siparişi dön
        return ResponseEntity.ok(order);
    }
}