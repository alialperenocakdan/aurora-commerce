package com.aurora.order.domain;

import java.util.List;
import java.util.Map;

// Siparişin yaşam döngüsü. Durumlar veritabanında metin olarak tutuluyor
// (mevcut kayıtlarla uyumlu kalsın diye enum sütununa çevrilmedi); geçerli
// geçişler tek yerde burada tanımlı ki kural her yerde aynı uygulansın.
public final class OrderStatus {

    public static final String PENDING = "pending";       // Sipariş alındı
    public static final String PREPARING = "preparing";   // Hazırlanıyor
    public static final String SHIPPED = "shipped";       // Kargoya verildi
    public static final String DELIVERED = "delivered";   // Teslim edildi
    public static final String CANCELLED = "cancelled";   // İptal edildi

    // Bir durumdan hangi durumlara geçilebilir. Listede olmayan her geçiş yasak:
    // örneğin teslim edilmiş sipariş "hazırlanıyor"a geri döndürülemez.
    private static final Map<String, List<String>> ALLOWED = Map.of(
            PENDING, List.of(PREPARING, CANCELLED),
            PREPARING, List.of(SHIPPED, CANCELLED),
            SHIPPED, List.of(DELIVERED),
            DELIVERED, List.of(),
            CANCELLED, List.of()
    );

    // Yönetim panelindeki "bir sonraki adıma ilerlet" düğmesinin hedefi
    private static final Map<String, String> NEXT = Map.of(
            PENDING, PREPARING,
            PREPARING, SHIPPED,
            SHIPPED, DELIVERED
    );

    private OrderStatus() {}

    public static boolean isValid(String status) {
        return ALLOWED.containsKey(status);
    }

    public static boolean canTransition(String from, String to) {
        return ALLOWED.getOrDefault(from, List.of()).contains(to);
    }

    // Sipariş buradan sonra ilerlemiyorsa (teslim/iptal) null döner
    public static String next(String status) {
        return NEXT.get(status);
    }

    // İptal yalnızca kargoya verilmeden önce mümkün: yola çıkmış bir siparişi
    // iptal etmek stok/lojistik açısından yanlış olur.
    public static boolean isCancellable(String status) {
        return canTransition(status, CANCELLED);
    }

    public static String label(String status) {
        return switch (status) {
            case PENDING -> "Sipariş alındı";
            case PREPARING -> "Hazırlanıyor";
            case SHIPPED -> "Kargoya verildi";
            case DELIVERED -> "Teslim edildi";
            case CANCELLED -> "İptal edildi";
            default -> status;
        };
    }
}
