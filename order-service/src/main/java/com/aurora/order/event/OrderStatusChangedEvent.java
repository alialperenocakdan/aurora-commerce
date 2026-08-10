package com.aurora.order.event;

import java.time.Instant;

// Siparişin durumu değiştiğinde yayınlanır. Tüketici bunu dinleyip müşteriye
// bildirim oluşturur; sipariş akışı bu olaya bağımlı değildir.
public record OrderStatusChangedEvent(
        Long orderId,
        Long customerId,
        String oldStatus,
        String newStatus,
        Instant changedAt
) {}
