package com.aurora.order.service;

import com.aurora.order.client.ProductClient;
import com.aurora.order.domain.Order;
import com.aurora.order.domain.OrderItem;
import com.aurora.order.exception.DownstreamUnavailableException;
import com.aurora.order.exception.OutOfStockException;
import com.aurora.order.repo.OrderRepository;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductClient productClient;

    // internalToken'ı sınıfın içinde tanımlıyoruz
    @Value("${INTERNAL_TOKEN:local-internal-token}")
    private String internalToken;

    public OrderService(OrderRepository orderRepository, ProductClient productClient) {
        this.orderRepository = orderRepository;
        this.productClient = productClient;
    }

    @Transactional
    @CircuitBreaker(name = "productService", fallbackMethod = "checkoutFallback")
    public Order checkout(Long customerId, List<Map<String, Object>> requestLines, String idempotencyKey) {

        // 1. AYNI ÜRÜNLERİ TOPLA
        Map<Long, Integer> aggregatedLines = new HashMap<>();
        for (Map<String, Object> line : requestLines) {
            Long pId = ((Number) line.get("productId")).longValue();
            Integer qty = ((Number) line.get("quantity")).intValue();
            aggregatedLines.put(pId, aggregatedLines.getOrDefault(pId, 0) + qty);
        }

        // ProductClient formatına çevir
        List<Map<String, Object>> deductRequest = new ArrayList<>();
        aggregatedLines.forEach((pId, qty) -> deductRequest.add(Map.of("productId", pId, "quantity", qty)));

        // 2. STOKLARI DÜŞ VE FİYATLARI AL
        Map<String, Object> deductResponse; // Dönüş tipini Map yaptık
        try {
            // Feign Client üzerinden Ürün Servisine istek atıyoruz
            deductResponse = productClient.deduct(internalToken, Map.of("lines", deductRequest));
        } catch (FeignException.Conflict e) {
            throw new OutOfStockException();
        } catch (Exception e) {
            System.err.println(" FEIGN İLETİŞİM HATASI: " + e.getMessage());
            e.printStackTrace();
            throw new DownstreamUnavailableException("product_service_unreachable");
        }
        List<Map<String, Object>> pricedLines = (List<Map<String, Object>>) deductResponse.get("lines");

        // 3. İKİ AŞAMALI KAYIT (TWO-STEP SAVE)
        try {
            // AŞAMA 1: Sadece boş siparişi kaydet ki veritabanı bize bir "ID" üretsin
            Order order = new Order();
            order.setCustomerId(customerId);
            order.setIdempotencyKey(idempotencyKey);
            order.setTotal(0L); // Şimdilik 0

            order = orderRepository.save(order); // Sipariş Numarasını aldık!

            // AŞAMA 2: Bu ID'yi kullanarak kalemleri oluştur
            long totalAmount = 0L;
            for (Map<String, Object> pricedLine : pricedLines) {
                Long productId = ((Number) pricedLine.get("productId")).longValue();
                Integer quantity = ((Number) pricedLine.get("quantity")).intValue();
                Long unitPrice = ((Number) pricedLine.get("unitPrice")).longValue();

                OrderItem item = new OrderItem();
                item.setOrderId(order.getId());
                item.setProductId(productId);
                item.setQuantity(quantity);
                item.setUnitPrice(unitPrice);

                order.getItems().add(item);
                totalAmount += (unitPrice * quantity);
            }

            order.setTotal(totalAmount);

            // İçi dolan siparişi son haliyle tekrar kaydet (Güncelle)
            return orderRepository.save(order);

        } catch (Exception e) {
            // SAGA TELAFİSİ: Eğer veritabanına yazarken hata çıkarsa, düşülen stoku geri ver
            productClient.restore(internalToken, Map.of("lines", deductRequest));
            throw new RuntimeException("order_failed_stock_restored", e);
        }
    }

    // Şartel AÇIK (Open) konumdayken çağrılır: product-service'e hiç gitmeden anında hata döner.
    // Parametre tipi CallNotPermittedException olduğu için SADECE bu durumda devreye girer;
    // out_of_stock gibi iş kuralı hataları buraya uğramadan olduğu gibi yukarı fırlar.
    public Order checkoutFallback(Long customerId, List<Map<String, Object>> requestLines, String idempotencyKey, CallNotPermittedException ex) {
        System.err.println("CIRCUIT BREAKER AKTİF! product-service erişilemez durumda: " + ex.getMessage());
        throw new DownstreamUnavailableException("circuit_open");
    }
}