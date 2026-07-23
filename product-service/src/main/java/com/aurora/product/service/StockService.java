package com.aurora.product.service;

import com.aurora.product.repo.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class StockService {

    private static final Logger log = LoggerFactory.getLogger(StockService.class);

    private final ProductRepository repository;

    public StockService(ProductRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public List<Map<String, Object>> deduct(List<Map<String, Object>> lines) {
        return lines.stream().map(line -> {
            Long productId = ((Number) line.get("productId")).longValue();
            Integer quantity = ((Number) line.get("quantity")).intValue();

            // Veritabanına emri yolla ve fiyatı al
            Long unitPrice = repository.deductAndReturnPrice(productId, quantity);

            if (unitPrice == null) {
                // Eğer SQL 'null' döndüyse ürün yoktur veya stok yetmemiştir!
                log.warn("Stok guard reddetti: productId={}, quantity={}", productId, quantity);
                throw new RuntimeException("out_of_stock");
            }
            log.info("Stok düşüldü: productId={}, quantity={}, unitPrice={}", productId, quantity, unitPrice);


            return Map.<String, Object>of(
                    "productId", productId,
                    "quantity", quantity,
                    "unitPrice", unitPrice
            );
        }).collect(Collectors.toList());
    }

    @Transactional
    public void restore(List<Map<String, Object>> lines) {
        for (Map<String, Object> line : lines) {
            Long productId = ((Number) line.get("productId")).longValue();
            Integer quantity = ((Number) line.get("quantity")).intValue();
            repository.restoreStock(productId, quantity);
            log.info("Stok iade edildi: productId={}, quantity={}", productId, quantity);
        }
    }
}