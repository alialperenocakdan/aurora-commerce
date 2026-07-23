package com.aurora.order.controller;

import com.aurora.order.domain.Order;
import com.aurora.order.exception.ForbiddenException;
import com.aurora.order.exception.NotCancellableException;
import com.aurora.order.exception.OrderNotFoundException;
import com.aurora.order.repo.OrderRepository;
import com.aurora.order.service.OrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/orders") // DİKKAT: Burası /checkout değil, /orders!
public class OrderQueryController {

    private final OrderRepository orderRepository;
    private final OrderService orderService;

    public OrderQueryController(OrderRepository orderRepository, OrderService orderService) {
        this.orderRepository = orderRepository;
        this.orderService = orderService;
    }

    // İnsan-onaylı yazma sınırı: state'i değiştiren TEK endpoint burası.
    // Gemini bunu asla doğrudan çağıramaz — sadece POST /support üzerinden "öner"ebilir.
    @PostMapping("/{id}/cancel")
    public ResponseEntity<?> cancelOrder(@PathVariable String id, @RequestBody(required = false) Map<String, Object> body) {
        Long orderId = parseNumericId(id);
        if (orderId == null) {
            return ResponseEntity.status(404).body(Map.of("error", "not_found"));
        }

        // Path otoritedir: action "cancel" olmalı, gövdedeki orderId (varsa) path ile eşleşmeli.
        if (body == null || !"cancel".equals(body.get("action"))) {
            return ResponseEntity.status(422).body(Map.of("error", "invalid_request"));
        }
        Object echoedId = body.get("orderId");
        if (echoedId != null && (!(echoedId instanceof Number) || ((Number) echoedId).longValue() != orderId)) {
            return ResponseEntity.status(422).body(Map.of("error", "invalid_request"));
        }

        Long customerId = Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString());

        try {
            Order cancelled = orderService.cancel(orderId, customerId);
            return ResponseEntity.ok(Map.of("orderId", cancelled.getId(), "status", cancelled.getStatus()));
        } catch (OrderNotFoundException e) {
            return ResponseEntity.status(404).body(Map.of("error", "not_found"));
        } catch (ForbiddenException e) {
            return ResponseEntity.status(403).body(Map.of("error", "forbidden"));
        } catch (NotCancellableException e) {
            return ResponseEntity.status(409).body(Map.of("error", "not_cancellable"));
        }
    }

    private Long parseNumericId(String id) {
        for (char c : id.toCharArray()) {
            if (c < '0' || c > '9') return null;
        }
        return id.isEmpty() ? null : Long.parseLong(id);
    }

    // Token'daki müşterinin tüm siparişleri (en yeni en üstte)
    @GetMapping
    public ResponseEntity<?> getMyOrders() {
        Long customerId = Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString());
        return ResponseEntity.ok(orderRepository.findByCustomerIdOrderByIdDesc(customerId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getOrder(@PathVariable String id) {

        //Sayısal Kontrol
        char[] chars = id.toCharArray();
        int charCount = 0;
        for (char c : chars) {
            if (c < '0' || c > '9') return ResponseEntity.status(404).body(Map.of("error", "not_found"));
            charCount++;
        }
        if (charCount == 0) return ResponseEntity.status(404).body(Map.of("error", "not_found"));

        //Veritabanından bul
        Long orderId = Long.parseLong(id);
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) return ResponseEntity.status(404).body(Map.of("error", "not_found"));

        //Token'dan ID'yi al
        String customerIdStr = SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString();
        Long customerId = Long.parseLong(customerIdStr);

        //Yetki Kontrolü
        if (!order.getCustomerId().equals(customerId)) {
            return ResponseEntity.status(403).body(Map.of("error", "forbidden"));
        }

        //Her şey yolundaysa siparişi dön
        return ResponseEntity.ok(order);
    }
}