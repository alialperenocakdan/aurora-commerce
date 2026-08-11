package com.aurora.order.service;

import com.aurora.order.repo.ReportRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

// Satış raporları.
//
// Sorgular veritabanında toplama yapıyor (bkz. ReportRepository); burada
// yalnızca sunuma hazırlanıyor. İki karar dikkat ister:
//
//  1) Gün sınırı: "bugünün cirosu" mağazanın saat dilimine göre hesaplanır.
//     UTC'ye göre yapılsaydı, Türkiye'de akşam 23:30'da verilen sipariş
//     ertesi güne yazılır ve gün sonu raporu tutmazdı.
//
//  2) Boş günler: hiç sipariş olmayan gün SQL'den satır olarak dönmez.
//     Grafikte o günü atlarsak çizgi kesintisiz görünür ve satışlar
//     olduğundan iyi görünür. Bu yüzden aradaki boş günler 0 ile doldurulur.
@Service
public class ReportService {

    private static final int MAX_DAYS = 365;

    private final ReportRepository reportRepository;
    private final ZoneId zone;

    public ReportService(ReportRepository reportRepository,
                         @Value("${report.timezone:Europe/Istanbul}") String timezone) {
        this.reportRepository = reportRepository;
        this.zone = ZoneId.of(timezone);
    }

    public Map<String, Object> summary(int requestedDays) {
        int days = clampDays(requestedDays);
        LocalDate firstDay = LocalDate.now(zone).minusDays(days - 1L);
        Instant since = firstDay.atStartOfDay(zone).toInstant();

        Object[] totals = reportRepository.totals(since);
        // Tek satırlık sorgu bazı sürücülerde Object[]{Object[]} olarak sarılı gelir
        Object[] row = (totals != null && totals.length == 1 && totals[0] instanceof Object[] inner)
                ? inner : totals;

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("days", days);
        body.put("timezone", zone.getId());
        body.put("from", firstDay.toString());
        body.put("to", LocalDate.now(zone).toString());
        body.put("revenue", asLong(row, 0));
        body.put("orderCount", asLong(row, 1));
        body.put("discountTotal", asLong(row, 2));
        long revenue = asLong(row, 0);
        long orders = asLong(row, 1);
        // Ortalama sepet: sıfıra bölmeyi burada engelliyoruz, arayüz uğraşmasın
        body.put("averageOrder", orders == 0 ? 0L : revenue / orders);
        body.put("daily", dailySeries(since, firstDay, days));
        body.put("statusBreakdown", statusBreakdown(since));
        return body;
    }

    public List<Map<String, Object>> topProducts(int requestedDays, int limit) {
        int days = clampDays(requestedDays);
        int safeLimit = Math.max(1, Math.min(limit, 50));
        Instant since = LocalDate.now(zone).minusDays(days - 1L).atStartOfDay(zone).toInstant();

        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] row : reportRepository.topProducts(since, safeLimit)) {
            Map<String, Object> item = new LinkedHashMap<>();
            // Ürün ADI burada yok: isim katalog verisidir, order-service'in
            // sahip olduğu bilgi değil. Yönetim paneli id'yi kendi ürün
            // listesinden çözer — böylece isim tek bir yerde tutulur.
            item.put("productId", asLong(row, 0));
            item.put("quantity", asLong(row, 1));
            item.put("revenue", asLong(row, 2));
            result.add(item);
        }
        return result;
    }

    private List<Map<String, Object>> dailySeries(Instant since, LocalDate firstDay, int days) {
        Map<LocalDate, Object[]> byDay = new HashMap<>();
        for (Object[] row : reportRepository.dailyRevenue(since, zone.getId())) {
            byDay.put(toLocalDate(row[0]), row);
        }

        List<Map<String, Object>> series = new ArrayList<>();
        for (int i = 0; i < days; i++) {
            LocalDate day = firstDay.plusDays(i);
            Object[] row = byDay.get(day);
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("date", day.toString());
            point.put("revenue", row == null ? 0L : asLong(row, 1));
            point.put("orderCount", row == null ? 0L : asLong(row, 2));
            series.add(point);
        }
        return series;
    }

    private Map<String, Long> statusBreakdown(Instant since) {
        Map<String, Long> breakdown = new LinkedHashMap<>();
        for (Object[] row : reportRepository.statusBreakdown(since)) {
            breakdown.put(String.valueOf(row[0]), asLong(row, 1));
        }
        return breakdown;
    }

    // 0 veya negatif gün anlamsız; çok büyük değer de sorguyu boşuna yorar
    private int clampDays(int days) {
        return Math.max(1, Math.min(days, MAX_DAYS));
    }

    private static long asLong(Object[] row, int index) {
        if (row == null || index >= row.length || row[index] == null) return 0L;
        return ((Number) row[index]).longValue();
    }

    // Postgres ::date sürücüye göre java.sql.Date veya LocalDate olarak gelir
    private static LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate localDate) return localDate;
        if (value instanceof Date sqlDate) return sqlDate.toLocalDate();
        return LocalDate.parse(value.toString());
    }
}
