package com.aurora.order.exception;

// Kupon reddedilme sebepleri tek bir istisnada toplanıyor: hepsi 422 döner,
// ayırt edici bilgi "code" alanında (ör. coupon_expired) taşınır ki arayüz
// kullanıcıya net sebebi gösterebilsin.
public class CouponException extends RuntimeException {

    private final String code;
    private final Long minOrderTotal; // yalnızca coupon_min_total için doludur

    public CouponException(String code) {
        this(code, null);
    }

    public CouponException(String code, Long minOrderTotal) {
        super(code);
        this.code = code;
        this.minOrderTotal = minOrderTotal;
    }

    public String getCode() { return code; }
    public Long getMinOrderTotal() { return minOrderTotal; }
}
