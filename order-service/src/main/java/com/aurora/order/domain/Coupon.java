package com.aurora.order.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "coupons", schema = "orders")
public class Coupon {

    public static final String TYPE_PERCENT = "PERCENT";
    public static final String TYPE_AMOUNT = "AMOUNT";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(name = "discount_type", nullable = false)
    private String discountType;

    // PERCENT ise yüzde (1-100), AMOUNT ise kuruş
    @Column(name = "discount_value", nullable = false)
    private Long discountValue;

    @Column(name = "min_order_total", nullable = false)
    private Long minOrderTotal = 0L;

    // Yalnızca yüzde indirimlerde anlamlı: indirimin üst sınırı (kuruş)
    @Column(name = "max_discount")
    private Long maxDiscount;

    @Column(name = "valid_until")
    private Instant validUntil;

    @Column(name = "usage_limit")
    private Integer usageLimit;

    @Column(name = "used_count", nullable = false)
    private Integer usedCount = 0;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    // Bu kuponun verilen ara toplama uygulayacağı indirimi hesaplar (kuruş).
    // Hesap tek yerde: hem sepet önizlemesi hem checkout aynı sonucu üretsin.
    public long calculateDiscount(long subtotal) {
        long discount;
        if (TYPE_PERCENT.equals(discountType)) {
            discount = subtotal * discountValue / 100;
            if (maxDiscount != null && discount > maxDiscount) {
                discount = maxDiscount;
            }
        } else {
            discount = discountValue;
        }
        // İndirim asla sepetten büyük olamaz — toplam eksiye düşmemeli
        if (discount > subtotal) discount = subtotal;
        if (discount < 0) discount = 0;
        return discount;
    }

    public boolean isExpired() {
        return validUntil != null && Instant.now().isAfter(validUntil);
    }

    public boolean isExhausted() {
        return usageLimit != null && usedCount >= usageLimit;
    }

    // --- Getter / Setter ---
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getDiscountType() { return discountType; }
    public void setDiscountType(String discountType) { this.discountType = discountType; }

    public Long getDiscountValue() { return discountValue; }
    public void setDiscountValue(Long discountValue) { this.discountValue = discountValue; }

    public Long getMinOrderTotal() { return minOrderTotal; }
    public void setMinOrderTotal(Long minOrderTotal) { this.minOrderTotal = minOrderTotal; }

    public Long getMaxDiscount() { return maxDiscount; }
    public void setMaxDiscount(Long maxDiscount) { this.maxDiscount = maxDiscount; }

    public Instant getValidUntil() { return validUntil; }
    public void setValidUntil(Instant validUntil) { this.validUntil = validUntil; }

    public Integer getUsageLimit() { return usageLimit; }
    public void setUsageLimit(Integer usageLimit) { this.usageLimit = usageLimit; }

    public Integer getUsedCount() { return usedCount; }
    public void setUsedCount(Integer usedCount) { this.usedCount = usedCount; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
