package com.aurora.order.service;

import com.aurora.order.repo.ReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

// Rapor rakamları yönetim kararlarına dayanak olur; sessizce yanlış olmaları
// hatadan beter. Buradaki testler iki tuzağı kilitliyor: boş günlerin
// atlanması ve gün sınırının yanlış saat diliminde hesaplanması.
class ReportServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Istanbul");

    private ReportRepository repository;
    private ReportService service;

    @BeforeEach
    void setUp() {
        repository = mock(ReportRepository.class);
        service = new ReportService(repository, "Europe/Istanbul");
        when(repository.totals(any())).thenReturn(new Object[]{0L, 0L, 0L});
        when(repository.dailyRevenue(any(), anyString())).thenReturn(List.of());
        when(repository.statusBreakdown(any())).thenReturn(List.of());
    }

    @Test
    void satisOlmayanGunlerSifirlaDoldurulur() {
        // Yalnızca bugün satış var; 7 günlük seride 7 nokta olmalı.
        // Boş günleri atlarsak grafik kesintisiz görünür ve satışlar
        // olduğundan iyi görünürdü.
        LocalDate today = LocalDate.now(ZONE);
        when(repository.dailyRevenue(any(), anyString())).thenReturn(
                List.<Object[]>of(new Object[]{Date.valueOf(today), 5000L, 2L}));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> daily =
                (List<Map<String, Object>>) service.summary(7).get("daily");

        assertEquals(7, daily.size());
        assertEquals(0L, daily.get(0).get("revenue"));
        assertEquals(5000L, daily.get(6).get("revenue"));
        assertEquals(today.toString(), daily.get(6).get("date"));
    }

    @Test
    void gunSiniriMagazaninSaatDiliminegoreHesaplanir() {
        // UTC'ye göre hesaplansaydı, Türkiye'de gece yarısına yakın verilen
        // siparişler yanlış güne yazılırdı.
        ArgumentCaptor<Instant> captor = ArgumentCaptor.forClass(Instant.class);

        service.summary(1);

        verify(repository).totals(captor.capture());
        Instant beklenen = LocalDate.now(ZONE).atStartOfDay(ZONE).toInstant();
        assertEquals(beklenen, captor.getValue());
    }

    @Test
    void ortalamaSepetSifiraBolunmez() {
        // Hiç sipariş yokken ortalama hesaplamak çökme sebebiydi
        when(repository.totals(any())).thenReturn(new Object[]{0L, 0L, 0L});

        Map<String, Object> summary = service.summary(7);

        assertEquals(0L, summary.get("averageOrder"));
        assertEquals(0L, summary.get("orderCount"));
    }

    @Test
    void ortalamaSepetDogruHesaplanir() {
        // 30.000 kuruş / 4 sipariş = 7.500 kuruş (75,00 ₺)
        when(repository.totals(any())).thenReturn(new Object[]{30000L, 4L, 500L});

        Map<String, Object> summary = service.summary(7);

        assertEquals(7500L, summary.get("averageOrder"));
        assertEquals(30000L, summary.get("revenue"));
        assertEquals(500L, summary.get("discountTotal"));
    }

    @Test
    void anlamsizGunSayisiSinirlanir() {
        // 0 gün diye bir dönem yok; çok büyük değer de sorguyu boşuna yorar
        assertEquals(1, service.summary(0).get("days"));
        assertEquals(1, service.summary(-5).get("days"));
        assertEquals(365, service.summary(99999).get("days"));
    }

    @Test
    void enCokSatanlardaListeBoyutuSinirlanir() {
        when(repository.topProducts(any(), anyInt())).thenReturn(List.of());

        service.topProducts(30, 9999);

        ArgumentCaptor<Integer> limit = ArgumentCaptor.forClass(Integer.class);
        verify(repository).topProducts(any(), limit.capture());
        assertEquals(50, limit.getValue());
    }

    @Test
    void enCokSatanlarUrunAdiTasimaz() {
        // İsim katalog verisidir; order-service'in bilmediği bir şeyi
        // uydurmasındansa yönetim paneli id'den çözer.
        when(repository.topProducts(any(), anyInt())).thenReturn(
                List.<Object[]>of(new Object[]{7L, 12L, 26400L}));

        Map<String, Object> item = service.topProducts(30, 10).get(0);

        assertEquals(7L, item.get("productId"));
        assertEquals(12L, item.get("quantity"));
        assertEquals(26400L, item.get("revenue"));
        assertFalse(item.containsKey("name"));
    }

    @Test
    void tekSatirlikToplamSorgusuSarilmisGelseDeOkunur() {
        // Bazı sürücüler tek satırlık native sorguyu Object[]{Object[]}
        // olarak sarar; sarılı gelmesi rakamları sıfırlamamalı.
        when(repository.totals(any())).thenReturn(new Object[]{new Object[]{900L, 3L, 0L}});

        Map<String, Object> summary = service.summary(7);

        assertEquals(900L, summary.get("revenue"));
        assertEquals(3L, summary.get("orderCount"));
    }
}
