package com.aurora.order.repo;

import com.aurora.order.domain.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

// Raporlama sorguları. Ayrı bir repository'de duruyorlar çünkü OrderRepository
// tekil siparişle çalışır; buradakiler hiç Order nesnesi üretmez, doğrudan
// toplam üretir. Binlerce siparişi Java'ya çekip toplamak yerine toplamı
// veritabanına yaptırıyoruz.
//
// Hepsinde iki ortak kural var:
//   1) İptal edilen sipariş ciroya SAYILMAZ.
//   2) Gün sınırı UTC'ye göre değil mağazanın saat dilimine göre belirlenir;
//      aksi halde akşam 23:30'daki sipariş ertesi güne yazılırdı.
public interface ReportRepository extends JpaRepository<Order, Long> {

    // Günlük ciro serisi: [gün, ciro (kuruş), sipariş adedi]
    @Query(value = """
            SELECT (o.created_at AT TIME ZONE :zone)::date AS gun,
                   COALESCE(SUM(o.total), 0)              AS ciro,
                   COUNT(*)                               AS adet
            FROM orders.orders o
            WHERE o.status <> 'cancelled' AND o.created_at >= :since
            GROUP BY gun
            ORDER BY gun
            """, nativeQuery = true)
    List<Object[]> dailyRevenue(@Param("since") Instant since, @Param("zone") String zone);

    // Dönem toplamı: [ciro, sipariş adedi, toplam indirim]
    @Query(value = """
            SELECT COALESCE(SUM(o.total), 0),
                   COUNT(*),
                   COALESCE(SUM(o.discount_amount), 0)
            FROM orders.orders o
            WHERE o.status <> 'cancelled' AND o.created_at >= :since
            """, nativeQuery = true)
    Object[] totals(@Param("since") Instant since);

    // En çok satanlar: [productId, satılan adet, o üründen gelen ciro]
    //
    // Ciro burada order_items'tan hesaplanıyor (adet × satış anındaki fiyat),
    // orders.total'dan değil: bir siparişte birden çok ürün olabilir, siparişin
    // toplamını tek ürüne yazmak yanlış olurdu. Kupon indirimi ürün bazına
    // dağıtılmadığı için bu rakam indirim ÖNCESİ üründen gelen tutardır.
    @Query(value = """
            SELECT i.product_id,
                   SUM(i.quantity)                  AS adet,
                   SUM(i.quantity * i.unit_price)   AS ciro
            FROM orders.order_items i
            JOIN orders.orders o ON o.id = i.order_id
            WHERE o.status <> 'cancelled' AND o.created_at >= :since
            GROUP BY i.product_id
            ORDER BY adet DESC, ciro DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> topProducts(@Param("since") Instant since, @Param("limit") int limit);

    // Durum dağılımı: kaç sipariş hangi aşamada (iptaller de dahil)
    @Query(value = """
            SELECT o.status, COUNT(*)
            FROM orders.orders o
            WHERE o.created_at >= :since
            GROUP BY o.status
            """, nativeQuery = true)
    List<Object[]> statusBreakdown(@Param("since") Instant since);
}
