package com.aurora.order.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// Sipariş yaşam döngüsünün kuralları: yanlış bir geçişe izin vermek stok ve
// lojistik tarafında gerçek zarar doğurur, o yüzden burada kilitleniyor.
class OrderStatusTest {

    @Test
    void ileriDogruGecislerGecerli() {
        assertTrue(OrderStatus.canTransition(OrderStatus.PENDING, OrderStatus.PREPARING));
        assertTrue(OrderStatus.canTransition(OrderStatus.PREPARING, OrderStatus.SHIPPED));
        assertTrue(OrderStatus.canTransition(OrderStatus.SHIPPED, OrderStatus.DELIVERED));
    }

    @Test
    void geriyeDonusYasak() {
        assertFalse(OrderStatus.canTransition(OrderStatus.DELIVERED, OrderStatus.SHIPPED));
        assertFalse(OrderStatus.canTransition(OrderStatus.SHIPPED, OrderStatus.PREPARING));
        assertFalse(OrderStatus.canTransition(OrderStatus.PREPARING, OrderStatus.PENDING));
    }

    @Test
    void adimAtlanamaz() {
        // "Sipariş alındı"dan doğrudan "teslim edildi"ye geçilemez
        assertFalse(OrderStatus.canTransition(OrderStatus.PENDING, OrderStatus.DELIVERED));
        assertFalse(OrderStatus.canTransition(OrderStatus.PENDING, OrderStatus.SHIPPED));
    }

    @Test
    void iptalYalnizcaKargodanOnce() {
        assertTrue(OrderStatus.isCancellable(OrderStatus.PENDING));
        assertTrue(OrderStatus.isCancellable(OrderStatus.PREPARING));
        // Yola çıkmış veya teslim edilmiş sipariş iptal edilemez
        assertFalse(OrderStatus.isCancellable(OrderStatus.SHIPPED));
        assertFalse(OrderStatus.isCancellable(OrderStatus.DELIVERED));
        assertFalse(OrderStatus.isCancellable(OrderStatus.CANCELLED));
    }

    @Test
    void bitmisDurumlardanCikisYok() {
        assertFalse(OrderStatus.canTransition(OrderStatus.CANCELLED, OrderStatus.PENDING));
        assertFalse(OrderStatus.canTransition(OrderStatus.CANCELLED, OrderStatus.PREPARING));
        assertFalse(OrderStatus.canTransition(OrderStatus.DELIVERED, OrderStatus.CANCELLED));
        assertNull(OrderStatus.next(OrderStatus.DELIVERED));
        assertNull(OrderStatus.next(OrderStatus.CANCELLED));
    }

    @Test
    void sonrakiAdimZinciriDogru() {
        assertEquals(OrderStatus.PREPARING, OrderStatus.next(OrderStatus.PENDING));
        assertEquals(OrderStatus.SHIPPED, OrderStatus.next(OrderStatus.PREPARING));
        assertEquals(OrderStatus.DELIVERED, OrderStatus.next(OrderStatus.SHIPPED));
    }

    @Test
    void tanimsizDurumGecersiz() {
        assertFalse(OrderStatus.isValid("uydurma"));
        assertFalse(OrderStatus.canTransition(OrderStatus.PENDING, "uydurma"));
        assertTrue(OrderStatus.isValid(OrderStatus.PENDING));
    }

    @Test
    void etiketlerTurkce() {
        assertEquals("Kargoya verildi", OrderStatus.label(OrderStatus.SHIPPED));
        assertEquals("Teslim edildi", OrderStatus.label(OrderStatus.DELIVERED));
    }
}
