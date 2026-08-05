package com.aurora.order.service;

import com.aurora.order.domain.Coupon;
import com.aurora.order.exception.CouponException;
import com.aurora.order.repo.CouponRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CouponServiceTest {

    private CouponRepository repository;
    private CouponService service;

    @BeforeEach
    void setUp() {
        repository = mock(CouponRepository.class);
        service = new CouponService(repository);
    }

    private Coupon validCoupon() {
        Coupon c = new Coupon();
        c.setId(1L);
        c.setCode("KOD10");
        c.setDiscountType(Coupon.TYPE_PERCENT);
        c.setDiscountValue(10L);
        c.setMinOrderTotal(0L);
        c.setActive(true);
        c.setUsedCount(0);
        return c;
    }

    @Test
    void normalize_buyukHarfeCevirirVeBosluklariAtar() {
        assertEquals("KOD10", CouponService.normalize(" kod10 "));
        assertEquals("KOD10", CouponService.normalize("KoD10"));
        assertEquals("", CouponService.normalize(null));
    }

    @Test
    void validate_kucukHarfliKodDaBulunur() {
        when(repository.findByCode("KOD10")).thenReturn(Optional.of(validCoupon()));
        assertNotNull(service.validate("kod10", 10000));
    }

    @Test
    void validate_olmayanKod_couponNotFound() {
        when(repository.findByCode("YOK")).thenReturn(Optional.empty());
        CouponException e = assertThrows(CouponException.class, () -> service.validate("YOK", 10000));
        assertEquals("coupon_not_found", e.getCode());
    }

    @Test
    void validate_pasifKupon_couponInactive() {
        Coupon c = validCoupon();
        c.setActive(false);
        when(repository.findByCode("KOD10")).thenReturn(Optional.of(c));
        assertEquals("coupon_inactive",
                assertThrows(CouponException.class, () -> service.validate("KOD10", 10000)).getCode());
    }

    @Test
    void validate_suresiDolmus_couponExpired() {
        Coupon c = validCoupon();
        c.setValidUntil(Instant.now().minus(1, ChronoUnit.DAYS));
        when(repository.findByCode("KOD10")).thenReturn(Optional.of(c));
        assertEquals("coupon_expired",
                assertThrows(CouponException.class, () -> service.validate("KOD10", 10000)).getCode());
    }

    @Test
    void validate_limitDolmus_couponExhausted() {
        Coupon c = validCoupon();
        c.setUsageLimit(5);
        c.setUsedCount(5);
        when(repository.findByCode("KOD10")).thenReturn(Optional.of(c));
        assertEquals("coupon_exhausted",
                assertThrows(CouponException.class, () -> service.validate("KOD10", 10000)).getCode());
    }

    @Test
    void validate_minimumTutarAltinda_sebepVeMinimumDoner() {
        Coupon c = validCoupon();
        c.setMinOrderTotal(15000L);
        when(repository.findByCode("KOD10")).thenReturn(Optional.of(c));

        CouponException e = assertThrows(CouponException.class,
                () -> service.validate("KOD10", 10000));
        assertEquals("coupon_min_total", e.getCode());
        // Arayüz "en az 150,00 TL" diyebilsin diye eşik de taşınır
        assertEquals(15000L, e.getMinOrderTotal());
    }

    @Test
    void validateQuietly_gecersizKuponaHataFirlatmaz() {
        when(repository.findByCode("YOK")).thenReturn(Optional.empty());
        assertTrue(service.validateQuietly("YOK", 10000).isEmpty());
    }

    @Test
    void consume_limitDolduysaFalseDoner() {
        Coupon c = validCoupon();
        when(repository.incrementUsage(1L)).thenReturn(0); // hiçbir satır güncellenmedi
        assertFalse(service.consume(c));

        when(repository.incrementUsage(1L)).thenReturn(1);
        assertTrue(service.consume(c));
    }
}
