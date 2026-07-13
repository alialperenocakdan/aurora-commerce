package com.aurora.order.controller;

import com.aurora.order.service.CartService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @PostMapping("/items")
    public ResponseEntity<?> addItemToCart(@RequestBody Map<String, Object> request) {
        try {
            Long customerId = Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString());
            Long productId = ((Number) request.get("productId")).longValue();
            Integer quantity = ((Number) request.get("quantity")).intValue();

            cartService.addItem(customerId, productId, quantity);

            // Ürün eklendikten sonra müşteriye güncel sepeti dönüyoruz
            return ResponseEntity.ok(cartService.getCart(customerId));

        } catch (RuntimeException e) {
            if ("not_found".equals(e.getMessage())) {
                return ResponseEntity.status(404).body(Map.of("error", "not_found"));
            }
            return ResponseEntity.status(422).body(Map.of("error", "invalid_request"));
        }
    }

    @GetMapping
    public ResponseEntity<?> viewCart() {
        Long customerId = Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString());
        return ResponseEntity.ok(cartService.getCart(customerId));
    }
}