// 1. DİKKAT: Paket adı uygulamanın ana paketiyle (com.aurora.product) başlamak zorundadır!
package com.aurora.product.web;

import com.aurora.product.domain.Product;
import com.aurora.product.repo.ProductRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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
}