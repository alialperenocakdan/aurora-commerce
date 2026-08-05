package com.aurora.order.controller;

import com.aurora.order.domain.Coupon;
import com.aurora.order.repo.CouponRepository;
import com.aurora.order.service.CouponService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

// Kupon yönetimi — yalnızca admin. Erişim kontrolü SecurityConfig'te
// hasRole("ADMIN") ile yapılır (ürün yazma uçlarıyla aynı desen).
@RestController
@RequestMapping("/admin/coupons")
public class CouponAdminController {

    private final CouponRepository couponRepository;

    public CouponAdminController(CouponRepository couponRepository) {
        this.couponRepository = couponRepository;
    }

    @GetMapping
    public ResponseEntity<?> list() {
        return ResponseEntity.ok(couponRepository.findAll());
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String, Object> body) {
        String code = CouponService.normalize(str(body.get("code")));
        if (code.isEmpty()) {
            return ResponseEntity.status(422).body(Map.of("error", "invalid_request"));
        }
        if (couponRepository.findByCode(code).isPresent()) {
            return ResponseEntity.status(409).body(Map.of("error", "code_taken"));
        }

        Coupon coupon = new Coupon();
        coupon.setCode(code);
        String validationError = applyFields(coupon, body, true);
        if (validationError != null) {
            return ResponseEntity.status(422).body(Map.of("error", validationError));
        }
        return ResponseEntity.status(201).body(couponRepository.save(coupon));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return couponRepository.findById(id).<ResponseEntity<?>>map(coupon -> {
            String validationError = applyFields(coupon, body, false);
            if (validationError != null) {
                return ResponseEntity.status(422).body(Map.of("error", validationError));
            }
            return ResponseEntity.ok(couponRepository.save(coupon));
        }).orElse(ResponseEntity.status(404).body(Map.of("error", "not_found")));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        if (!couponRepository.existsById(id)) {
            return ResponseEntity.status(404).body(Map.of("error", "not_found"));
        }
        couponRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("deleted", id));
    }

    // Gövdedeki alanları kupona uygular; hata varsa hata kodunu döner.
    // required=true iken (yeni kupon) tür ve değer zorunludur.
    private String applyFields(Coupon coupon, Map<String, Object> body, boolean required) {
        Object type = body.get("discountType");
        if (type != null) {
            String t = type.toString().toUpperCase(java.util.Locale.ROOT);
            if (!Coupon.TYPE_PERCENT.equals(t) && !Coupon.TYPE_AMOUNT.equals(t)) {
                return "invalid_discount_type";
            }
            coupon.setDiscountType(t);
        } else if (required) {
            return "invalid_discount_type";
        }

        Object value = body.get("discountValue");
        if (value instanceof Number n) {
            long v = n.longValue();
            if (v <= 0) return "invalid_discount_value";
            // Yüzde indirim 100'ü aşamaz — aşarsa ürün bedava, hatta eksiye düşerdi
            if (Coupon.TYPE_PERCENT.equals(coupon.getDiscountType()) && v > 100) {
                return "invalid_discount_value";
            }
            coupon.setDiscountValue(v);
        } else if (required) {
            return "invalid_discount_value";
        }

        if (body.get("minOrderTotal") instanceof Number n) {
            if (n.longValue() < 0) return "invalid_request";
            coupon.setMinOrderTotal(n.longValue());
        }
        if (body.containsKey("maxDiscount")) {
            Object max = body.get("maxDiscount");
            coupon.setMaxDiscount(max instanceof Number n && n.longValue() > 0
                    ? n.longValue() : null);
        }
        if (body.containsKey("usageLimit")) {
            Object limit = body.get("usageLimit");
            coupon.setUsageLimit(limit instanceof Number n && n.intValue() > 0
                    ? n.intValue() : null);
        }
        if (body.containsKey("validUntil")) {
            Object until = body.get("validUntil");
            if (until == null || until.toString().isBlank()) {
                coupon.setValidUntil(null);
            } else {
                try {
                    coupon.setValidUntil(Instant.parse(until.toString()));
                } catch (Exception e) {
                    return "invalid_valid_until";
                }
            }
        }
        if (body.get("active") instanceof Boolean b) {
            coupon.setActive(b);
        }
        return null;
    }

    private String str(Object o) { return o == null ? null : o.toString(); }
}
