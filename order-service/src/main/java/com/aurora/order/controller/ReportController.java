package com.aurora.order.controller;

import com.aurora.order.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// Satış raporları. /admin/** altında olduğu için SecurityConfig gereği
// yalnızca admin claim'i taşıyan token geçer — sıradan müşteri ciro göremez.
@RestController
@RequestMapping("/admin/reports")
@Tag(name = "Raporlar", description = "Satış özeti ve en çok satan ürünler (yalnızca admin)")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @Operation(summary = "Satış özeti",
            description = "Verilen gün sayısı için ciro, sipariş adedi, ortalama sepet, "
                    + "günlük ciro serisi ve durum dağılımı. Gün sınırı mağazanın "
                    + "saat dilimine göre hesaplanır; iptal edilen siparişler ciroya dahil değildir.")
    @GetMapping("/summary")
    public ResponseEntity<?> summary(@RequestParam(defaultValue = "7") int days) {
        return ResponseEntity.ok(reportService.summary(days));
    }

    @Operation(summary = "En çok satan ürünler",
            description = "Satılan adede göre sıralı liste. Ürün adı içermez: "
                    + "isim katalog verisidir, yönetim paneli id'den çözer.")
    @GetMapping("/top-products")
    public ResponseEntity<?> topProducts(@RequestParam(defaultValue = "30") int days,
                                         @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(java.util.Map.of(
                "items", reportService.topProducts(days, limit)));
    }
}
