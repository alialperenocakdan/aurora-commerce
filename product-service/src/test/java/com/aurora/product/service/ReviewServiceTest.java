package com.aurora.product.service;

import com.aurora.product.client.OrderClient;
import com.aurora.product.domain.Review;
import com.aurora.product.exception.ReviewException;
import com.aurora.product.repo.ProductRepository;
import com.aurora.product.repo.ReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

// "Yorum yazmak için o ürünü satın almış olmak" kuralının testleri.
// Kural iki servise yayıldığı için en kolay kırılacak yer burası:
// sipariş servisi hayır dediğinde ya da hiç cevap vermediğinde ne oluyor?
class ReviewServiceTest {

    private ReviewRepository reviewRepository;
    private ProductRepository productRepository;
    private OrderClient orderClient;
    private ReviewService service;

    @BeforeEach
    void setUp() {
        reviewRepository = mock(ReviewRepository.class);
        productRepository = mock(ProductRepository.class);
        orderClient = mock(OrderClient.class);
        service = new ReviewService(reviewRepository, productRepository, orderClient, "tok");

        when(productRepository.existsById(anyLong())).thenReturn(true);
        when(reviewRepository.findByProductIdAndCustomerId(anyLong(), anyLong()))
                .thenReturn(Optional.empty());
        when(reviewRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private void satinAldi(boolean purchased) {
        when(orderClient.hasPurchased(anyString(), anyLong(), anyLong()))
                .thenReturn(Map.of("purchased", purchased));
    }

    @Test
    void satinAlanYorumYazabilir() {
        satinAldi(true);

        Review review = service.create(1L, 7L, 5, "Harika ürün");

        assertEquals(5, review.getRating());
        assertEquals("Harika ürün", review.getComment());
        verify(reviewRepository).save(any());
    }

    @Test
    void satinAlmayanYorumYazamaz() {
        satinAldi(false);

        ReviewException e = assertThrows(ReviewException.class,
                () -> service.create(1L, 7L, 5, "Almadım ama beğendim"));

        assertEquals("not_purchased", e.getCode());
        assertEquals(403, e.getStatus());
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void siparisServisiCevapVermezseYorumYazilamaz() {
        // Alt servis çökünce kural delinmemeli: "bilmiyorum" = "izin yok".
        when(orderClient.hasPurchased(anyString(), anyLong(), anyLong()))
                .thenThrow(new RuntimeException("connection refused"));

        ReviewException e = assertThrows(ReviewException.class,
                () -> service.create(1L, 7L, 5, "test"));

        assertEquals("order_service_unavailable", e.getCode());
        assertEquals(503, e.getStatus());
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void ayniUruneIkinciYorumYazilamaz() {
        when(reviewRepository.findByProductIdAndCustomerId(1L, 7L))
                .thenReturn(Optional.of(new Review(1L, 7L, 4, "ilk yorum")));

        ReviewException e = assertThrows(ReviewException.class,
                () -> service.create(1L, 7L, 5, "ikinci yorum"));

        assertEquals("already_reviewed", e.getCode());
        // Zaten yorumu varsa sipariş servisini boşuna yormuyoruz
        verify(orderClient, never()).hasPurchased(anyString(), anyLong(), anyLong());
    }

    @Test
    void puanBirBesArasindaOlmali() {
        satinAldi(true);

        for (Integer invalid : new Integer[]{null, 0, 6, -1}) {
            ReviewException e = assertThrows(ReviewException.class,
                    () -> service.create(1L, 7L, invalid, "test"));
            assertEquals("invalid_rating", e.getCode());
        }
    }

    @Test
    void gecersizPuanSiparisServisineSorulmadanReddedilir() {
        // Doğrulama önce: hatalı istek için ağ üzerinden başka servise gidilmez
        satinAldi(true);

        assertThrows(ReviewException.class, () -> service.create(1L, 7L, 9, "test"));

        verify(orderClient, never()).hasPurchased(anyString(), anyLong(), anyLong());
    }

    @Test
    void olmayanUruneYorumYazilamaz() {
        when(productRepository.existsById(99L)).thenReturn(false);

        ReviewException e = assertThrows(ReviewException.class,
                () -> service.create(99L, 7L, 5, "test"));

        assertEquals("product_not_found", e.getCode());
    }

    @Test
    void bosYorumMetniNullOlarakSaklanir() {
        // Yalnızca yıldız verip yazı yazmayan müşteri: boş string yerine null
        satinAldi(true);

        Review review = service.create(1L, 7L, 4, "   ");

        assertNull(review.getComment());
    }

    @Test
    void cokUzunYorumReddedilir() {
        satinAldi(true);

        ReviewException e = assertThrows(ReviewException.class,
                () -> service.create(1L, 7L, 4, "a".repeat(501)));

        assertEquals("comment_too_long", e.getCode());
    }

    @Test
    void kendiYorumunuDuzeltmekIcinTekrarSatinAlmaSorulmaz() {
        // Yorumun varlığı, satın almanın zaten doğrulanmış olduğunun kanıtı
        when(reviewRepository.findByProductIdAndCustomerId(1L, 7L))
                .thenReturn(Optional.of(new Review(1L, 7L, 2, "eski")));

        Review updated = service.update(1L, 7L, 5, "fikrim değişti");

        assertEquals(5, updated.getRating());
        assertNotNull(updated.getUpdatedAt());
        verify(orderClient, never()).hasPurchased(anyString(), anyLong(), anyLong());
    }

    @Test
    void ortalamaTekOndalikaYuvarlanir() {
        // 4 + 5 + 5 = 14 / 3 = 4.666... → 4.7
        when(reviewRepository.averageOf(1L)).thenReturn(14 / 3.0);
        when(reviewRepository.countByProductId(1L)).thenReturn(3L);
        when(reviewRepository.distributionOf(1L)).thenReturn(List.of(
                new Object[]{5, 2L}, new Object[]{4, 1L}));

        Map<String, Object> summary = service.summary(1L);

        assertEquals(4.7, summary.get("average"));
        assertEquals(3L, summary.get("count"));
    }

    @Test
    void hicYorumYokkaOrtalamaSifir() {
        // AVG boş tabloda null döner; arayüze null puan göndermiyoruz
        when(reviewRepository.averageOf(1L)).thenReturn(null);
        when(reviewRepository.countByProductId(1L)).thenReturn(0L);
        when(reviewRepository.distributionOf(1L)).thenReturn(List.of());

        Map<String, Object> summary = service.summary(1L);

        assertEquals(0.0, summary.get("average"));
        assertEquals(0L, summary.get("count"));
    }

    @Test
    void dagilimdaBesBasamakDaBulunur() {
        // Hiç oy almayan basamak sorgudan gelmez; arayüz eksik anahtar görmesin
        when(reviewRepository.averageOf(1L)).thenReturn(5.0);
        when(reviewRepository.countByProductId(1L)).thenReturn(1L);
        // Tek elemanlı dizi listesinde tip açıkça yazılmalı, yoksa Java bunu
        // "iki ayrı eleman" sanıp List<Object> üretir.
        when(reviewRepository.distributionOf(1L))
                .thenReturn(List.<Object[]>of(new Object[]{5, 1L}));

        @SuppressWarnings("unchecked")
        Map<String, Long> distribution =
                (Map<String, Long>) service.summary(1L).get("distribution");

        assertEquals(5, distribution.size());
        assertEquals(1L, distribution.get("5"));
        assertEquals(0L, distribution.get("1"));
    }
}
