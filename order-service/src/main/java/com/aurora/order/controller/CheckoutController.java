package com.aurora.order.controller;

import com.aurora.order.domain.Order;
import com.aurora.order.exception.DownstreamUnavailableException;
import com.aurora.order.exception.DuplicateOrderException;
import com.aurora.order.exception.InvalidRequestException;
import com.aurora.order.exception.OutOfStockException;
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

        } catch (InvalidRequestException e) {
            return ResponseEntity.status(422).body(Map.of("error", "invalid_request"));
        } catch (DuplicateOrderException e) {
            // İdempotent replay: aynı anahtarla oluşmuş orijinal siparişi bul ve 200 dön
            return orderService.findByIdempotencyKey(e.getIdempotencyKey())
                    .<ResponseEntity<?>>map(original -> ResponseEntity.ok(Map.of("orderId", original.getId())))
                    .orElse(ResponseEntity.status(500).body(Map.of("error", "internal_error")));
        } catch (OutOfStockException e) {
            return ResponseEntity.status(409).body(Map.of("error", "out_of_stock"));
        } catch (DownstreamUnavailableException e) {
            // Circuit breaker açık veya product-service'e ulaşılamıyor: 503 Service Unavailable
            return ResponseEntity.status(503).body(Map.of("error", "service_unavailable", "detail", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(500).body(Map.of("error", "internal_error", "detail", e.getMessage()));
        }
    }
}