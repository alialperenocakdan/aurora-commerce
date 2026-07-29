
package com.aurora.product.web;

import com.aurora.product.domain.Product;
import com.aurora.product.repo.ProductRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import java.util.List;
import java.util.Map;


@RestController
public class ProductController {

    private final ProductRepository productRepository;

    public ProductController(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    // Cache-aside: cevap 60sn Redis'te tutulur (bkz. CacheConfig). Fiyat/stok kararları
    // asla buradan okunmaz — checkout hep deduct'ın RETURNING'inden gerçek veriyi alır.
    @Cacheable(value = "products", key = "'all'")
    @GetMapping("/products")
    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    // Tek ürün görüntüleme — sepet servisi (order-service) ürün doğrulaması için bunu çağırır
    @Cacheable(value = "products", key = "#id", unless = "#result.statusCode.value() == 404")
    @GetMapping("/products/{id}")
    public ResponseEntity<?> getProduct(@PathVariable Long id) {
        return productRepository.findById(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(404).body(Map.of("error", "not_found")));
    }

    // Ürün ekleme metodu (POST) — yeni/güncellenmiş ürün cache'i bayatlatır, tamamen boşaltılır.
    @CacheEvict(value = "products", allEntries = true)
    @PostMapping("/products")
    public Product createProduct(@RequestBody Product product) {
        // Gelen JSON verisini Product entity'sine dönüştürüp veritabanına kaydeder
        return productRepository.save(product);
    }

    // Ürün silme — POST gibi JWT korumalıdır (SecurityConfig'te yalnızca GET'ler herkese açık).
    // Geçmiş siparişler etkilenmez: order_items kendi şemasında productId kopyası tutar.
    @CacheEvict(value = "products", allEntries = true)
    @org.springframework.web.bind.annotation.DeleteMapping("/products/{id}")
    public ResponseEntity<?> deleteProduct(@PathVariable Long id) {
        if (!productRepository.existsById(id)) {
            return ResponseEntity.status(404).body(Map.of("error", "not_found"));
        }
        productRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("deleted", id));
    }

    // Var olan ürünü güncelleme (ör. kategori atama) — POST yalnızca YENİ ürün
    // yaratır ve isim benzersizlik kısıtına takılır; mevcut kaydı değiştirmek
    // için bu gerekiyordu.
    @CacheEvict(value = "products", allEntries = true)
    @org.springframework.web.bind.annotation.PutMapping("/products/{id}")
    public ResponseEntity<?> updateProduct(@PathVariable Long id, @RequestBody Product patch) {
        return productRepository.findById(id).<ResponseEntity<?>>map(existing -> {
            if (patch.getName() != null) existing.setName(patch.getName());
            if (patch.getUnitPrice() != null) existing.setUnitPrice(patch.getUnitPrice());
            if (patch.getStock() != null) existing.setStock(patch.getStock());
            if (patch.getImageUrl() != null) existing.setImageUrl(patch.getImageUrl());
            if (patch.getCategory() != null) existing.setCategory(patch.getCategory());
            return ResponseEntity.ok(productRepository.save(existing));
        }).orElse(ResponseEntity.status(404).body(Map.of("error", "not_found")));
    }
}