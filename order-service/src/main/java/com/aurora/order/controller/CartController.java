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

        } catch (com.aurora.order.exception.DownstreamUnavailableException e) {
            // RuntimeException'dan önce yakalanmalı: ürün servisi geçici olarak
            // erişilemez, müşteriye "geçersiz istek" demek yanlış olur.
            return ResponseEntity.status(503).body(Map.of("error", "service_unavailable"));
        } catch (RuntimeException e) {
            if ("not_found".equals(e.getMessage())) {
                return ResponseEntity.status(404).body(Map.of("error", "not_found"));
            }
            return ResponseEntity.status(422).body(Map.of("error", "invalid_request"));
        }
    }

    // Adedi tam olarak ayarla (arayüzdeki +/- düğmeleri). quantity=0 ürünü kaldırır.
    @PutMapping("/items/{productId}")
    public ResponseEntity<?> updateItemQuantity(@PathVariable Long productId,
                                                @RequestBody Map<String, Object> request) {
        Long customerId = Long.parseLong(
                SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString());
        try {
            Object raw = request.get("quantity");
            if (!(raw instanceof Number)) {
                return ResponseEntity.status(422).body(Map.of("error", "invalid_request"));
            }
            cartService.setQuantity(customerId, productId, ((Number) raw).intValue());
            return ResponseEntity.ok(cartService.getCart(customerId));

        } catch (com.aurora.order.exception.DownstreamUnavailableException e) {
            return ResponseEntity.status(503).body(Map.of("error", "service_unavailable"));
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

    @DeleteMapping("/items/{productId}")
    public ResponseEntity<?> removeItemFromCart(@PathVariable Long productId) {
        Long customerId = Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString());
        cartService.removeItem(customerId, productId);
        // Silme sonrası güncel sepeti dönüyoruz
        return ResponseEntity.ok(cartService.getCart(customerId));
    }

    // Sepeti tamamen boşalt — başarılı checkout sonrası istemci çağırır
    @DeleteMapping
    public ResponseEntity<?> clearCart() {
        Long customerId = Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString());
        cartService.clearCart(customerId);
        return ResponseEntity.ok(cartService.getCart(customerId));
    }
}