package com.aurora.order.controller;

import com.aurora.order.domain.Order;
import com.aurora.order.service.OrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/checkout") // Artık yolumuz /orders değil /checkout
public class CheckoutController {

    private final OrderService orderService;

    public CheckoutController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<?> placeOrder(@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                        @RequestBody Map<String, List<Map<String, Object>>> request) {
        try {
            // 1. Müşteriyi Token'dan bul
            String customerIdStr = SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString();
            Long customerId = Long.parseLong(customerIdStr);

            // 2. İdempotency anahtarı yoksa kendimiz üretelim (Çifte siparişi önlemek için)
            if (idempotencyKey == null) {
                idempotencyKey = UUID.randomUUID().toString();
            }

            // 3. Siparişi ver!
            Order order = orderService.checkout(customerId, request.get("lines"), idempotencyKey);

            // Müşteriye sadece Sipariş Numarasını dön
            return ResponseEntity.ok(Map.of("orderId", order.getId()));

        } catch (RuntimeException e) {
            if ("out_of_stock".equals(e.getMessage())) {
                return ResponseEntity.status(409).body(Map.of("error", "out_of_stock"));
            }
            return ResponseEntity.status(500).body(Map.of("error", "internal_error", "detail", e.getMessage()));
        }
    }
}