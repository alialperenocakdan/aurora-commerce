package com.aurora.order.event;

import java.time.Instant;

public record OrderCreatedEvent(
        Long orderId,
        Long customerId,
        Long total,
        Instant createdAt
) {}