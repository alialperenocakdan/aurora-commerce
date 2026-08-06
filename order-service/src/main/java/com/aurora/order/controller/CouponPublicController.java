package com.aurora.order.controller;

import com.aurora.order.domain.Coupon;
import com.aurora.order.repo.CouponRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Vitrindeki kampanya şeridi için: şu an kullanılabilir kuponlar.
// Herkese açıktır (giriş yapmamış ziyaretçi de kampanyaları görebilmeli).
//
// Yalnızca müşteriyi ilgilendiren alanlar döner; kaç kez kullanıldığı,
// limitin ne olduğu gibi işletme bilgileri DIŞARI SIZDIRILMAZ.
@RestController
@RequestMapping("/coupons")
public class CouponPublicController {

    private final CouponRepository couponRepository;

    public CouponPublicController(CouponRepository couponRepository) {
        this.couponRepository = couponRepository;
    }

    @GetMapping("/active")
    public ResponseEntity<?> active() {
        List<Map<String, Object>> result = couponRepository.findAll().stream()
                .filter(Coupon::isActive)
                .filter(c -> !c.isExpired())
                .filter(c -> !c.isExhausted())
                .map(this::toPublicView)
                .toList();
        return ResponseEntity.ok(result);
    }

    private Map<String, Object> toPublicView(Coupon c) {
        Map<String, Object> view = new HashMap<>();
        view.put("code", c.getCode());
        view.put("discountType", c.getDiscountType());
        view.put("discountValue", c.getDiscountValue());
        view.put("minOrderTotal", c.getMinOrderTotal());
        view.put("maxDiscount", c.getMaxDiscount());
        view.put("validUntil", c.getValidUntil() == null ? null : c.getValidUntil().toString());
        return view;
    }
}
