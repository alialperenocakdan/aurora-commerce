package com.aurora.order.consumer;

import com.aurora.order.domain.Notification;
import com.aurora.order.domain.OrderStatus;
import com.aurora.order.event.OrderCreatedEvent;
import com.aurora.order.event.OrderStatusChangedEvent;
import com.aurora.order.repo.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// Kafka "en az bir kez" teslim eder: aynı olay birden fazla kez gelebilir.
// Müşteriye aynı bildirimin iki kez düşmemesi bu sınıfın sorumluluğu,
// o yüzden mükerrer davranışı burada kilitleniyor.
class NotificationConsumerTest {

    private NotificationRepository repository;
    private NotificationConsumer consumer;

    @BeforeEach
    void setUp() {
        repository = mock(NotificationRepository.class);
        consumer = new NotificationConsumer(repository);
    }

    @Test
    void siparisOlusturuldugundaBildirimYazilir() {
        consumer.onOrderCreated(new OrderCreatedEvent(7L, 3L, 1000L, Instant.now()));

        Notification saved = kaydedileni();
        assertEquals(3L, saved.getCustomerId());
        assertEquals(7L, saved.getOrderId());
        assertEquals(Notification.TYPE_ORDER_CREATED, saved.getType());
        assertTrue(saved.getBody().contains("#7"));
    }

    @Test
    void ayniOlayTekrarGelirseIkinciBildirimYazilmaz() {
        when(repository.existsByOrderIdAndType(7L, Notification.TYPE_ORDER_CREATED))
                .thenReturn(true);

        consumer.onOrderCreated(new OrderCreatedEvent(7L, 3L, 1000L, Instant.now()));

        verify(repository, never()).save(any());
    }

    @Test
    void esZamanliYazmaCakismasiTuketiciyiDusurmez() {
        // İki tüketici aynı anda yazarsa benzersizlik kısıtı patlar; bu bir hata
        // değil, beklenen yarış sonucudur. Olay yeniden denenirse sonsuz döngü olur.
        when(repository.save(any())).thenThrow(new DataIntegrityViolationException("unique"));

        assertDoesNotThrow(() ->
                consumer.onOrderCreated(new OrderCreatedEvent(7L, 3L, 1000L, Instant.now())));
    }

    @Test
    void durumDegisimiKendiTipiyleYazilir() {
        consumer.onStatusChanged(new OrderStatusChangedEvent(
                7L, 3L, OrderStatus.PREPARING, OrderStatus.SHIPPED, Instant.now()));

        Notification saved = kaydedileni();
        assertEquals(Notification.statusType(OrderStatus.SHIPPED), saved.getType());
        assertEquals("Siparişin kargoya verildi", saved.getTitle());
    }

    @Test
    void herDurumFarkliTipUretirBoyleceHepsiAyriBildirimOlur() {
        // Tipler çakışsaydı "hazırlanıyor" bildirimi "kargoya verildi"yi bastırırdı.
        assertNotEquals(Notification.statusType(OrderStatus.PREPARING),
                Notification.statusType(OrderStatus.SHIPPED));
        assertNotEquals(Notification.statusType(OrderStatus.SHIPPED),
                Notification.statusType(OrderStatus.DELIVERED));
        assertNotEquals(Notification.TYPE_ORDER_CREATED,
                Notification.statusType(OrderStatus.PREPARING));
    }

    private Notification kaydedileni() {
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }
}
