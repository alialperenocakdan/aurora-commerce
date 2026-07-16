package com.aurora.order.service;

import com.aurora.order.client.ProductClient;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import feign.FeignException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class CartService {

    private final StringRedisTemplate redisTemplate;
    private final ProductClient productClient;
    private static final String CART_PREFIX = "cart:";

    public CartService(StringRedisTemplate redisTemplate, ProductClient productClient) {
        this.redisTemplate = redisTemplate;
        this.productClient = productClient;
    }

    //SEPETE ÜRÜN EKLE
    public void addItem(Long customerId, Long productId, Integer quantity) {
        if (quantity <= 0) throw new IllegalArgumentException("invalid_request");

        // Ürün gerçekten var mı diye Ürün Servisine soruyoruz
        Map<String, Object> product = null;
        try {
            product = productClient.getProduct(productId);
        } catch (feign.FeignException.NotFound e) {
            throw new RuntimeException("not_found"); // Feign 404 verirse sepet hata fırlatır
        }

        String key = CART_PREFIX + customerId;
        HashOperations<String, String, String> hashOps = redisTemplate.opsForHash();

        // Sepette ürün zaten varsa üzerine ekle, yoksa sıfırdan başlat
        String currentQtyStr = hashOps.get(key, productId.toString());
        int currentQty = currentQtyStr != null ? Integer.parseInt(currentQtyStr) : 0;

        hashOps.put(key, productId.toString(), String.valueOf(currentQty + quantity));

        // Sepetin ömrü 24 saat TTL
        redisTemplate.expire(key, Duration.ofHours(24));
    }

    //SEPETTEN ÜRÜN ÇIKAR
    public void removeItem(Long customerId, Long productId) {
        redisTemplate.opsForHash().delete(CART_PREFIX + customerId, productId.toString());
    }

    //SEPETİ GÖRÜNTÜLE VE FİYATLARI HESAPLA
    public Map<String, Object> getCart(Long customerId) {
        String key = CART_PREFIX + customerId;
        Map<Object, Object> rawCart = redisTemplate.opsForHash().entries(key);

        List<Map<String, Object>> lines = new ArrayList<>();
        long total = 0L;

        for (Map.Entry<Object, Object> entry : rawCart.entrySet()) {
            Long productId = Long.parseLong(entry.getKey().toString());
            Integer quantity = Integer.parseInt(entry.getValue().toString());

            // Fiyatı ve bilgileri Product Service'den anlık alıyoruz
            Map<String, Object> product = null;
            try {
                // Feign ile ürünü çekmeye çalışıyoruz
                product = productClient.getProduct(productId);
            } catch (feign.FeignException.NotFound e) {
                // Ürün veritabanından silinmişse hiçbir şey yapma (product null kalır)
            } catch (Exception e) {
                // Ürün servisi kapalıysa hiçbir şey yapma (product null kalır)
            }
            // ----------------------------

            // Geri kalanı tamamen aynı kalıyor!
            if (product != null) {
                Long unitPrice = ((Number) product.get("unitPrice")).longValue();
                long lineTotal = unitPrice * quantity;

                Map<String, Object> line = new HashMap<>();
                line.put("productId", productId);
                line.put("quantity", quantity);
                line.put("unitPrice", unitPrice);
                line.put("lineTotal", lineTotal);
                lines.add(line);

                total += lineTotal;
            }
        }

        Map<String, Object> response = new HashMap<>();
        response.put("lines", lines);
        response.put("total", total);
        return response;
    }
}