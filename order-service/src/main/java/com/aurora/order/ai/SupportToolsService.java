package com.aurora.order.ai;

import com.aurora.order.client.ProductClient;
import com.aurora.order.domain.Order;
import com.aurora.order.domain.OrderItem;
import com.aurora.order.repo.OrderRepository;
import feign.FeignException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

// Gemini'nin çağırabildiği ÜÇ salt-okunur tool. Hiçbiri state değiştirmez.
// Sahiplik kontrolü modele değil, buraya (sunucuya) emanet — model orderId'yi
// yanlış/kötü niyetli verse bile başkasının siparişi asla sızdırılmaz.
@Service
public class SupportToolsService {

    private final OrderRepository orderRepository;
    private final ProductClient productClient;

    public SupportToolsService(OrderRepository orderRepository, ProductClient productClient) {
        this.orderRepository = orderRepository;
        this.productClient = productClient;
    }

    public Map<String, Object> getOrderStatus(Long orderId, Long callerCustomerId) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null || !order.getCustomerId().equals(callerCustomerId)) {
            // Var olan ama başkasına ait siparişle, hiç var olmayan sipariş aynı cevabı alır —
            // aksi halde "bulunamadı" ile "sana ait değil" ayrımı sızıntı olurdu.
            return Map.of("found", false);
        }
        List<Map<String, Object>> items = order.getItems().stream()
                .map(this::itemToMap)
                .toList();
        return Map.of(
                "found", true,
                "orderId", order.getId(),
                "status", order.getStatus(),
                "total", order.getTotal(),
                "items", items
        );
    }

    public Map<String, Object> checkStock(Long productId) {
        try {
            Map<String, Object> product = productClient.getProduct(productId);
            return Map.of(
                    "found", true,
                    "productId", productId,
                    "name", product.get("name"),
                    "stock", product.get("stock")
            );
        } catch (FeignException.NotFound e) {
            return Map.of("found", false);
        }
    }

    public Map<String, Object> estimateRestock(Long productId) {
        try {
            Map<String, Object> product = productClient.getProduct(productId);
            int stock = ((Number) product.get("stock")).intValue();
            // Kaba tahmin: gerçek bir tedarik zinciri sinyaline dayanmaz, sadece mevcut stok
            // seviyesinden kabaca bir metin üretir (spec'in istediği "kaba tahmin" bu kadar).
            String estimate;
            if (stock <= 0) {
                estimate = "Şu an stokta yok; yeniden ne zaman geleceği belirsiz.";
            } else if (stock < 10) {
                estimate = "Stok azalıyor, önümüzdeki birkaç gün içinde tükenebilir.";
            } else {
                estimate = "Stok seviyesi yeterli görünüyor, yakın zamanda tükenme beklenmiyor.";
            }
            return Map.of("found", true, "productId", productId, "stock", stock, "estimate", estimate);
        } catch (FeignException.NotFound e) {
            return Map.of("found", false);
        }
    }

    private Map<String, Object> itemToMap(OrderItem item) {
        return Map.of(
                "productId", item.getProductId(),
                "quantity", item.getQuantity(),
                "unitPrice", item.getUnitPrice()
        );
    }
}
