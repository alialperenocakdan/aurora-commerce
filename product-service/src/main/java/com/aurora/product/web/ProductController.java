// 1. DİKKAT: Paket adı uygulamanın ana paketiyle (com.aurora.product) başlamak zorundadır!
package com.aurora.product.web;

import com.aurora.product.domain.Product;
import com.aurora.product.repo.ProductRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import java.util.List;
import java.util.Map;

// 2. DİKKAT: Sadece @Controller değil, @RestController olmak zorunda!
@RestController
public class ProductController {

    private final ProductRepository productRepository;

    public ProductController(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    // 3. DİKKAT: Adresin tam olarak "/products" olduğundan emin ol!
    @GetMapping("/products")
    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }
    // Tek ürün görüntüleme — sepet servisi (order-service) ürün doğrulaması için bunu çağırır
    @GetMapping("/products/{id}")
    public ResponseEntity<?> getProduct(@PathVariable Long id) {
        return productRepository.findById(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(404).body(Map.of("error", "not_found")));
    }

    // Ürün ekleme metodu (POST)
    @PostMapping("/products")
    public Product createProduct(@RequestBody Product product) {
        // Gelen JSON verisini Product entity'sine dönüştürüp veritabanına kaydeder
        return productRepository.save(product);
    }
}