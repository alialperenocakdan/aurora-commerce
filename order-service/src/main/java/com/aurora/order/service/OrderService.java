package com.aurora.order.service;

import com.aurora.order.client.ProductClient;
import com.aurora.order.domain.Order;
import com.aurora.order.domain.OrderItem;
import com.aurora.order.event.OrderCreatedEvent;
import com.aurora.order.exception.DownstreamUnavailableException;
import com.aurora.order.exception.DuplicateOrderException;
import com.aurora.order.exception.ForbiddenException;
import com.aurora.order.exception.InvalidRequestException;
import com.aurora.order.exception.NotCancellableException;
import com.aurora.order.exception.OrderNotFoundException;
import com.aurora.order.exception.OutOfStockException;
import com.aurora.order.repo.OrderRepository;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Optional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductClient productClient;
    private final StringRedisTemplate redisTemplate;
    private static final String IDEM_PREFIX = "idem:";

    // internalToken'ı sınıfın içinde tanımlıyoruz
    @Value("${INTERNAL_TOKEN:local-internal-token}")
    private String internalToken;

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${kafka.topics.order-created}")
    private String orderCreatedTopic;

    public OrderService(OrderRepository orderRepository, ProductClient productClient,
                        StringRedisTemplate redisTemplate, KafkaTemplate<String, Object> kafkaTemplate) {
        this.orderRepository = orderRepository;
        this.productClient = productClient;
        this.redisTemplate = redisTemplate;
        this.kafkaTemplate = kafkaTemplate;
    }

    //aynı Idempotency-Key ile daha önce oluşmuş siparişi bul
    public Optional<Order> findByIdempotencyKey(String key) {
        return orderRepository.findByIdempotencyKey(key);
    }


    private String safeRedisGet(String key) {
        try { return redisTemplate.opsForValue().get(key); }
        catch (Exception e) { System.err.println("Redis erisilemedi (get), DB garantisiyle devam: " + e.getMessage()); return null; }
    }

    private void safeRedisSet(String key, String value) {
        try { redisTemplate.opsForValue().set(key, value, Duration.ofHours(24)); }
        catch (Exception e) { System.err.println("Redis erisilemedi (set): " + e.getMessage()); }
    }

    private void safeRedisSetIfAbsent(String key, String value) {
        try { redisTemplate.opsForValue().setIfAbsent(key, value, Duration.ofHours(24)); }
        catch (Exception e) { System.err.println("Redis erisilemedi (setnx): " + e.getMessage()); }
    }

    private void safeRedisDelete(String key) {
        try { redisTemplate.delete(key); }
        catch (Exception e) { System.err.println("Redis erisilemedi (del): " + e.getMessage()); }
    }

    // Kafka publish'i best-effort yapar: broker'a ulaşılamasa bile checkout'u ASLA düşürmez/geri aldırmaz.
    private void safeKafkaSend(Order order) {
        try {
            kafkaTemplate.send(orderCreatedTopic, order.getId().toString(),
                    new OrderCreatedEvent(order.getId(), order.getCustomerId(), order.getTotal(), order.getCreatedAt()));
        } catch (Exception e) {
            System.err.println("Kafka'ya event gonderilemedi (checkout etkilenmedi): " + e.getMessage());
        }
    }

    @Transactional
    @CircuitBreaker(name = "productService", fallbackMethod = "checkoutFallback")
    public Order checkout(Long customerId, List<Map<String, Object>> requestLines, String idempotencyKey) {


        if (requestLines == null || requestLines.isEmpty()) {
            throw new InvalidRequestException();
        }
        for (Map<String, Object> line : requestLines) {
            Object pId = line.get("productId");
            Object qty = line.get("quantity");
            if (!(pId instanceof Number) || !(qty instanceof Number) || ((Number) qty).intValue() <= 0) {
                throw new InvalidRequestException();
            }
        }


        if (idempotencyKey != null) {
            // Hızlı yol: Redis'te orderId yazılıysa DB'ye inmeden dön
            String cached = safeRedisGet(IDEM_PREFIX + idempotencyKey);
            if (cached != null && !"PENDING".equals(cached)) {
                Order existing = orderRepository.findById(Long.parseLong(cached)).orElse(null);
                if (existing != null) return existing;
            }
            // Asıl garanti: DB'deki unique index'li kolon
            Optional<Order> existing = orderRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) return existing.get();

            // Ön kapı: anahtarı PENDING olarak rezerve et (24 saat)
            safeRedisSetIfAbsent(IDEM_PREFIX + idempotencyKey, "PENDING");
        }

        //AYNI ÜRÜNLERİ TOPLA
        Map<Long, Integer> aggregatedLines = new HashMap<>();
        for (Map<String, Object> line : requestLines) {
            Long pId = ((Number) line.get("productId")).longValue();
            Integer qty = ((Number) line.get("quantity")).intValue();
            aggregatedLines.put(pId, aggregatedLines.getOrDefault(pId, 0) + qty);
        }

        // ProductClient formatına çevir
        List<Map<String, Object>> deductRequest = new ArrayList<>();
        aggregatedLines.forEach((pId, qty) -> deductRequest.add(Map.of("productId", pId, "quantity", qty)));

        //STOKLARI DÜŞ VE FİYATLARI AL
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

        //İKİ AŞAMALI KAYIT (TWO-STEP SAVE)
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

            // İçi dolan siparişi son haliyle tekrar kaydet
            Order saved = orderRepository.save(order);
            safeKafkaSend(saved);

            // İdempotency anahtarını Redis'te siparişe bağla: replay artık DB'ye inmeden cevaplanır
            if (idempotencyKey != null) {
                safeRedisSet(IDEM_PREFIX + idempotencyKey, saved.getId().toString());
            }
            return saved;

        } catch (DataIntegrityViolationException e) {
            // Aynı Idempotency-Key ile eşzamanlı ikinci istek: unique index yakaladı.
            // Bu isteğin düşürdüğü stok iade edilir; orijinal sipariş controller'da bulunup 200 dönülür.
            productClient.restore(internalToken, Map.of("lines", deductRequest));
            if (idempotencyKey != null) {
                throw new DuplicateOrderException(idempotencyKey);
            }
            throw new RuntimeException("order_failed_stock_restored", e);
        } catch (Exception e) {
            // SAGA TELAFİSİ
            productClient.restore(internalToken, Map.of("lines", deductRequest));
            // Anahtar PENDING'de kalmasın ki müşteri güvenle retry edebilsin
            if (idempotencyKey != null) {
                safeRedisDelete(IDEM_PREFIX + idempotencyKey);
            }
            throw new RuntimeException("order_failed_stock_restored", e);
        }
    }


    // out_of_stock gibi iş kuralı hataları buraya uğramadan olduğu gibi yukarı fırlar.
    public Order checkoutFallback(Long customerId, List<Map<String, Object>> requestLines, String idempotencyKey, CallNotPermittedException ex) {
        System.err.println("CIRCUIT BREAKER AKTİF! product-service erişilemez durumda: " + ex.getMessage());
        throw new DownstreamUnavailableException("circuit_open");
    }

    // Saga'nın tersi: sadece 'pending' siparişi iptal eder, stoğu restore ile geri yükler.
    // Replay güvenliği state'ten gelir — zaten 'cancelled' bulunan sipariş aynı cevabı döner,
    // stoğa ikinci kez dokunulmaz.
    @Transactional
    public Order cancel(Long orderId, Long customerId) {
        Order order = orderRepository.findById(orderId).orElseThrow(OrderNotFoundException::new);

        if (!order.getCustomerId().equals(customerId)) {
            throw new ForbiddenException();
        }
        if ("cancelled".equals(order.getStatus())) {
            return order; // idempotent replay: ikinci kez restock etmeden aynı cevabı dön
        }
        if (!"pending".equals(order.getStatus())) {
            throw new NotCancellableException();
        }

        List<Map<String, Object>> lines = order.getItems().stream()
                .map(item -> Map.<String, Object>of("productId", item.getProductId(), "quantity", item.getQuantity()))
                .toList();

        order.setStatus("cancelled");
        Order saved = orderRepository.save(order);
        // restore başarısız olursa exception yukarı fırlar, @Transactional status'u geri alır —
        // sipariş 'pending' kalır ve müşteri güvenle tekrar deneyebilir.
        productClient.restore(internalToken, Map.of("lines", lines));
        return saved;
    }
}