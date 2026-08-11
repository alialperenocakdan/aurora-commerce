package com.aurora.product.web;

import com.aurora.product.domain.Product;
import com.aurora.product.repo.ProductRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

// Katalog tarafındaki raporlar. /admin/** altında olduğu için yalnızca admin.
@RestController
@RequestMapping("/admin")
@Tag(name = "Katalog raporları", description = "Stok uyarıları (yalnızca admin)")
public class AdminReportController {

    private static final int DEFAULT_THRESHOLD = 5;

    private final ProductRepository productRepository;

    public AdminReportController(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Operation(summary = "Düşük stoklu ürünler",
            description = "Stoğu eşiğin altına düşen ürünler, en azdan çoğa sıralı. "
                    + "Filtreleme veritabanında yapılır: yönetim paneli sırf birkaç "
                    + "uyarı için tüm katalogu indirmek zorunda kalmasın.")
    @GetMapping("/low-stock")
    public ResponseEntity<?> lowStock(
            @RequestParam(defaultValue = "" + DEFAULT_THRESHOLD) int threshold) {
        // Negatif eşik anlamsız; 0 ise yalnızca tükenenler listelenir
        int safeThreshold = Math.max(0, Math.min(threshold, 1000));
        List<Product> items = productRepository.findByStockLessThanEqualOrderByStockAsc(safeThreshold);
        return ResponseEntity.ok(Map.of(
                "threshold", safeThreshold,
                "count", items.size(),
                "items", items));
    }
}
