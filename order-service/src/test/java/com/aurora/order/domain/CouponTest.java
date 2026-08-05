package com.aurora.order.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

// İndirim hesabı para hesabıdır: sınır durumları burada kilitleniyor.
class CouponTest {

    private Coupon percent(long value, Long maxDiscount) {
        Coupon c = new Coupon();
        c.setDiscountType(Coupon.TYPE_PERCENT);
        c.setDiscountValue(value);
        c.setMaxDiscount(maxDiscount);
        return c;
    }

    private Coupon amount(long kurus) {
        Coupon c = new Coupon();
        c.setDiscountType(Coupon.TYPE_AMOUNT);
        c.setDiscountValue(kurus);
        return c;
    }

    @Test
    void yuzdeIndirimi_dogruHesaplar() {
        // 100,00 TL sepette %10 → 10,00 TL
        assertEquals(1000, percent(10, null).calculateDiscount(10000));
        assertEquals(2500, percent(25, null).calculateDiscount(10000));
    }

    @Test
    void yuzdeIndirimi_ustSinirlaKisitlanir() {
        // %50 ama en fazla 20,00 TL: 100,00 TL sepette 50,00 değil 20,00 iner
        assertEquals(2000, percent(50, 2000L).calculateDiscount(10000));
        // Üst sınırın altında kalıyorsa sınır devreye girmez
        assertEquals(1000, percent(50, 2000L).calculateDiscount(2000));
    }

    @Test
    void sabitTutarIndirimi() {
        assertEquals(5000, amount(5000).calculateDiscount(20000));
    }

    @Test
    void indirimSepettenBuyukOlamaz() {
        // 50,00 TL indirim, 30,00 TL sepet → toplam eksiye düşmemeli
        assertEquals(3000, amount(5000).calculateDiscount(3000));
        assertEquals(0, amount(5000).calculateDiscount(0));
    }

    @Test
    void kurusYuvarlamasi_asagiDogru() {
        // 33,33 TL'nin %10'u = 3,333 TL → tam sayı kuruşa iner (333)
        assertEquals(333, percent(10, null).calculateDiscount(3333));
    }

    @Test
    void sonKullanmaTarihi() {
        Coupon c = percent(10, null);
        assertFalse(c.isExpired(), "tarih yoksa süresiz geçerli");

        c.setValidUntil(Instant.now().minus(1, ChronoUnit.DAYS));
        assertTrue(c.isExpired());

        c.setValidUntil(Instant.now().plus(1, ChronoUnit.DAYS));
        assertFalse(c.isExpired());
    }

    @Test
    void kullanimLimiti() {
        Coupon c = percent(10, null);
        assertFalse(c.isExhausted(), "limit yoksa sınırsız");

        c.setUsageLimit(2);
        c.setUsedCount(1);
        assertFalse(c.isExhausted());

        c.setUsedCount(2);
        assertTrue(c.isExhausted());
        c.setUsedCount(3); // limitin üstüne çıkmış olsa da tükenmiş sayılır
        assertTrue(c.isExhausted());
    }
}
