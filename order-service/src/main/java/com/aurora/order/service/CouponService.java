package com.aurora.order.service;

import com.aurora.order.domain.Coupon;
import com.aurora.order.exception.CouponException;
import com.aurora.order.repo.CouponRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class CouponService {

    private static final Logger log = LoggerFactory.getLogger(CouponService.class);

    private final CouponRepository couponRepository;

    public CouponService(CouponRepository couponRepository) {
        this.couponRepository = couponRepository;
    }

    // Kodlar her zaman büyük harf ve boşluksuz karşılaştırılır: kullanıcı
    // "kod10" ya da " KOD10 " yazsa da aynı kuponu bulmalı.
    public static String normalize(String code) {
        return code == null ? "" : code.trim().toUpperCase(java.util.Locale.ROOT);
    }

    // Kuponu bulur ve verilen ara toplam için geçerliliğini doğrular.
    // Geçersizse sebebiyle birlikte CouponException fırlatır.
    public Coupon validate(String rawCode, long subtotal) {
        String code = normalize(rawCode);
        if (code.isEmpty()) throw new CouponException("coupon_not_found");

        Coupon coupon = couponRepository.findByCode(code)
                .orElseThrow(() -> new CouponException("coupon_not_found"));

        if (!coupon.isActive()) throw new CouponException("coupon_inactive");
        if (coupon.isExpired()) throw new CouponException("coupon_expired");
        if (coupon.isExhausted()) throw new CouponException("coupon_exhausted");
        if (subtotal < coupon.getMinOrderTotal()) {
            throw new CouponException("coupon_min_total", coupon.getMinOrderTotal());
        }
        return coupon;
    }

    // Sepet önizlemesi için: kupon artık geçersizse (ör. süresi doldu, sepet
    // tutarı minimumun altına düştü) hata fırlatmak yerine boş döner —
    // sepeti görüntülemek bir hataya takılmamalı.
    public Optional<Coupon> validateQuietly(String rawCode, long subtotal) {
        try {
            return Optional.of(validate(rawCode, subtotal));
        } catch (CouponException e) {
            log.debug("Sepetteki kupon artık geçersiz: code={}, sebep={}", rawCode, e.getCode());
            return Optional.empty();
        }
    }

    // Checkout başarıyla tamamlandığında kullanım sayacını artırır.
    // Sayaç atomik artırılır; limit bu arada dolduysa false döner.
    @Transactional
    public boolean consume(Coupon coupon) {
        int updated = couponRepository.incrementUsage(coupon.getId());
        if (updated == 0) {
            log.warn("Kupon kullanım limiti bu sırada doldu: code={}", coupon.getCode());
            return false;
        }
        return true;
    }
}
