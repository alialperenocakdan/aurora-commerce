package com.aurora.product.exception;

// Yorum reddedilme sebepleri tek istisnada toplanıyor. Kupon tarafındaki
// desenin aynısı: ayırt edici bilgi "code" alanında taşınır ki arayüz
// kullanıcıya net sebebi gösterebilsin ("bu ürünü satın almadın" ile
// "zaten yorum yaptın" çok farklı iki durum).
//
// HTTP durumu da burada taşınır çünkü sebepler farklı sınıflardan:
// 403 yetki, 409 çakışma, 422 geçersiz veri, 503 alt servis kapalı.
public class ReviewException extends RuntimeException {

    private final String code;
    private final int status;

    public ReviewException(String code, int status) {
        super(code);
        this.code = code;
        this.status = status;
    }

    public String getCode() { return code; }
    public int getStatus() { return status; }
}
