package com.aurora.product.web;

import com.aurora.product.domain.Category;
import com.aurora.product.repo.CategoryRepository;
import com.aurora.product.repo.ProductRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Kategori kataloğu. Listeleme herkese açık (SecurityConfig'te GET izni),
// oluşturma/silme yalnızca admin.
@RestController
@RequestMapping("/categories")
public class CategoryController {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    public CategoryController(CategoryRepository categoryRepository,
                              ProductRepository productRepository) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
    }

    // Her kategoriyle birlikte kaç ürün içerdiği de döner: yönetim paneli
    // listeyi tek istekte çizebilsin.
    @GetMapping
    public ResponseEntity<?> list() {
        List<Map<String, Object>> result = categoryRepository.findAllByOrderByNameAsc()
                .stream()
                .map(c -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("id", c.getId());
                    item.put("name", c.getName());
                    item.put("productCount", productRepository.countByCategory(c.getName()));
                    return item;
                })
                .toList();
        return ResponseEntity.ok(result);
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String, Object> body) {
        Object raw = body.get("name");
        String name = raw == null ? "" : raw.toString().trim();
        if (name.isEmpty()) {
            return ResponseEntity.status(422).body(Map.of("error", "invalid_request"));
        }
        if (name.length() > 80) {
            return ResponseEntity.status(422).body(Map.of("error", "name_too_long"));
        }
        if (categoryRepository.findByName(name).isPresent()) {
            return ResponseEntity.status(409).body(Map.of("error", "name_taken"));
        }
        try {
            return ResponseEntity.status(201).body(categoryRepository.save(new Category(name)));
        } catch (DataIntegrityViolationException e) {
            // Eşzamanlı iki istek aynı adı eklemeye çalıştı
            return ResponseEntity.status(409).body(Map.of("error", "name_taken"));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        return categoryRepository.findById(id).<ResponseEntity<?>>map(category -> {
            // İçinde ürün varsa silmeyi engelliyoruz: aksi halde ürünler
            // listelenemeyen bir kategori adıyla ortada kalırdı.
            long count = productRepository.countByCategory(category.getName());
            if (count > 0) {
                return ResponseEntity.status(409)
                        .body(Map.of("error", "category_not_empty", "productCount", count));
            }
            categoryRepository.deleteById(id);
            return ResponseEntity.ok(Map.of("deleted", id));
        }).orElse(ResponseEntity.status(404).body(Map.of("error", "not_found")));
    }
}
