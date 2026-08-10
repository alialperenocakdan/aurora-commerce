package com.aurora.order.consumer;

import com.aurora.order.domain.Notification;
import com.aurora.order.domain.OrderStatus;
import com.aurora.order.event.OrderCreatedEvent;
import com.aurora.order.event.OrderStatusChangedEvent;
import com.aurora.order.repo.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// Sipariş olaylarını dinleyip müşteriye bildirim üreten tüketici.
//
// Bu sınıf, olayları yayınlayan koddan tamamen bağımsızdır: checkout bildirim
// oluşturmayı beklemez, bildirim üretimi başarısız olsa bile sipariş tamamlanır.
// Yayıncı ile abone arasındaki tek bağ Kafka konusudur (gevşek bağlılık).
//
// Kafka "en az bir kez" teslim garantisi verir; aynı olay tekrar işlenebilir.
// Bu yüzden bildirim üretimi idempotent tutuldu: (order_id, type) benzersiz.
@Component
public class NotificationConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationConsumer.class);

    private final NotificationRepository notificationRepository;

    public NotificationConsumer(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    // groupId burada bilerek yazılmadı: grup adı tek yerden,
    // spring.kafka.consumer.group-id (KAFKA_CONSUMER_GROUP) üzerinden gelir.
    @KafkaListener(topics = "${kafka.topics.order-created}")
    @Transactional
    public void onOrderCreated(OrderCreatedEvent event) {
        log.info("Olay alındı [order.created]: orderId={}, customerId={}",
                event.orderId(), event.customerId());

        save(new Notification(
                event.customerId(),
                event.orderId(),
                Notification.TYPE_ORDER_CREATED,
                "Siparişin alındı",
                "#" + event.orderId() + " numaralı siparişin oluşturuldu. "
                        + "Hazırlanmaya başlandığında haber vereceğiz."
        ));
    }

    @KafkaListener(topics = "${kafka.topics.order-status-changed}")
    @Transactional
    public void onStatusChanged(OrderStatusChangedEvent event) {
        log.info("Olay alındı [order.status.changed]: orderId={}, {} -> {}",
                event.orderId(), event.oldStatus(), event.newStatus());

        save(new Notification(
                event.customerId(),
                event.orderId(),
                Notification.statusType(event.newStatus()),
                bildirimBasligi(event.newStatus()),
                "#" + event.orderId() + " numaralı siparişin durumu güncellendi: "
                        + OrderStatus.label(event.newStatus()) + "."
        ));
    }

    private void save(Notification notification) {
        // Mükerrer olay: aynı sipariş+tip zaten varsa sessizce geç
        if (notificationRepository.existsByOrderIdAndType(
                notification.getOrderId(), notification.getType())) {
            log.debug("Bildirim zaten var, tekrar oluşturulmadı: orderId={}, type={}",
                    notification.getOrderId(), notification.getType());
            return;
        }
        try {
            notificationRepository.save(notification);
        } catch (DataIntegrityViolationException e) {
            // Eşzamanlı iki tüketici aynı anda yazmaya çalıştı: benzersizlik
            // kısıtı yakaladı, sorun değil.
            log.debug("Bildirim eşzamanlı olarak oluşturulmuş: orderId={}",
                    notification.getOrderId());
        }
    }

    private String bildirimBasligi(String status) {
        return switch (status) {
            case OrderStatus.PREPARING -> "Siparişin hazırlanıyor";
            case OrderStatus.SHIPPED -> "Siparişin kargoya verildi";
            case OrderStatus.DELIVERED -> "Siparişin teslim edildi";
            case OrderStatus.CANCELLED -> "Siparişin iptal edildi";
            default -> "Sipariş durumu güncellendi";
        };
    }
}
