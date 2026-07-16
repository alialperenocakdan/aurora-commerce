package com.aurora.order.service;

import com.aurora.order.client.ProductClient;
import com.aurora.order.domain.Order;
import com.aurora.order.domain.OrderItem;
import com.aurora.order.exception.DownstreamUnavailableException;
import com.aurora.order.exception.DuplicateOrderException;
import com.aurora.order.exception.InvalidRequestException;
import com.aurora.order.exception.OutOfStockException;
import com.aurora.order.repo.OrderRepository;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
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

    public OrderService(OrderRepository orderRepository, ProductClient productClient, StringRedisTemplate redisTemplate) {
        this.orderRepository = orderRepository;
        this.productClient = productClient;
        this.redisTemplate = redisTemplate;
    }

    // Replay cevabı için: aynı Idempotency-Key ile daha önce oluşmuş siparişi bul
    public Optional<Order> findByIdempotencyKey(String key) {
        return orderRepository.findByIdempotencyKey(key);
    }

    @Transactional
    @CircuitBreaker(name = "productService", fallbackMethod = "checkoutFallback")
    public Order checkout(Long customerId, List<Map<String, Object>> requestLines, String idempotencyKey) {

        // 0. GÖVDE DOĞRULAMA: boş lines veya quantity <= 0 → 422.
        // Negatif quantity ayrıca "stock - (-3)" ile stok ARTIRABİLECEĞİ için burada kesilmesi şarttır.
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

        // 0.5 İDEMPOTENCY REPLAY: aynı anahtarla sipariş zaten varsa stoka hiç dokunmadan onu dön.
        if (idempotencyKey != null) {
            // Hızlı yol: Redis'te orderId yazılıysa DB'ye inmeden dön
            String cached = redisTemplate.opsForValue().get(IDEM_PREFIX + idempotencyKey);
            if (cached != null && !"PENDING".equals(cached)) {
                Order existing = orderRepository.findById(Long.parseLong(cached)).orElse(null);
                if (existing != null) return existing;
            }
            // Asıl garanti: DB'deki unique index'li kolon
            Optional<Order> existing = orderRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) return existing.get();

            // Ön kapı: anahtarı PENDING olarak rezerve et (24 saat)
            redisTemplate.opsForValue().setIfAbsent(IDEM_PREFIX + idempotencyKey, "PENDING", Duration.ofHours(24));
        }

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
            Order saved = orderRepository.save(order);

            // İdempotency anahtarını Redis'te siparişe bağla: replay artık DB'ye inmeden cevaplanır
            if (idempotencyKey != null) {
                redisTemplate.opsForValue().set(IDEM_PREFIX + idempotencyKey, saved.getId().toString(), Duration.ofHours(24));
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
            // SAGA TELAFİSİ: Eğer veritabanına yazarken hata çıkarsa, düşülen stoku geri ver
            productClient.restore(internalToken, Map.of("lines", deductRequest));
            // Anahtar PENDING'de kalmasın ki müşteri güvenle retry edebilsin
            if (idempotencyKey != null) {
                redisTemplate.delete(IDEM_PREFIX + idempotencyKey);
            }
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