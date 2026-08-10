package com.aurora.order.controller;

import com.aurora.order.domain.Order;
import com.aurora.order.domain.OrderStatus;
import com.aurora.order.exception.InvalidStatusTransitionException;
import com.aurora.order.exception.NotCancellableException;
import com.aurora.order.exception.OrderNotFoundException;
import com.aurora.order.repo.OrderRepository;
import com.aurora.order.service.OrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Sipariş yönetimi — yalnızca admin (SecurityConfig'te /admin/** kuralı).
@RestController
@RequestMapping("/admin/orders")
public class OrderAdminController {

    private final OrderRepository orderRepository;
    private final OrderService orderService;

    public OrderAdminController(OrderRepository orderRepository, OrderService orderService) {
        this.orderRepository = orderRepository;
        this.orderService = orderService;
    }

    // Tüm siparişler, en yeni en üstte. Her siparişe bir sonraki adımın ne
    // olduğu da eklenir ki panel düğmeyi kendisi hesaplamak zorunda kalmasın.
    @GetMapping
    public ResponseEntity<?> list() {
        List<Map<String, Object>> result = orderRepository.findAll().stream()
                .sorted((a, b) -> b.getId().compareTo(a.getId()))
                .map(this::toView)
                .toList();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> detail(@PathVariable Long id) {
        return orderRepository.findById(id).<ResponseEntity<?>>map(order -> {
            Map<String, Object> view = toView(order);
            view.put("items", order.getItems());
            view.put("history", orderService.getStatusHistory(order.getId()));
            return ResponseEntity.ok(view);
        }).orElse(ResponseEntity.status(404).body(Map.of("error", "not_found")));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<?> changeStatus(@PathVariable Long id,
                                          @RequestBody Map<String, Object> body) {
        Object raw = body.get("status");
        if (raw == null) {
            return ResponseEntity.status(422).body(Map.of("error", "invalid_request"));
        }
        try {
            Order updated = orderService.changeStatus(id, raw.toString());
            return ResponseEntity.ok(toView(updated));
        } catch (OrderNotFoundException e) {
            return ResponseEntity.status(404).body(Map.of("error", "not_found"));
        } catch (InvalidStatusTransitionException e) {
            // Panelin nedeni gösterebilmesi için hangi geçişin reddedildiği de dönülür
            return ResponseEntity.status(409).body(Map.of(
                    "error", "invalid_status_transition",
                    "from", e.getFrom(),
                    "to", e.getTo()));
        } catch (NotCancellableException e) {
            return ResponseEntity.status(409).body(Map.of("error", "not_cancellable"));
        }
    }

    private Map<String, Object> toView(Order order) {
        Map<String, Object> view = new HashMap<>();
        view.put("id", order.getId());
        view.put("customerId", order.getCustomerId());
        view.put("status", order.getStatus());
        view.put("statusLabel", OrderStatus.label(order.getStatus()));
        view.put("nextStatus", OrderStatus.next(order.getStatus()));
        view.put("cancellable", OrderStatus.isCancellable(order.getStatus()));
        view.put("total", order.getTotal());
        view.put("discountAmount", order.getDiscountAmount());
        view.put("couponCode", order.getCouponCode());
        view.put("itemCount", order.getItems().size());
        view.put("createdAt", order.getCreatedAt().toString());
        return view;
    }
}
