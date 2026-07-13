package com.aurora.order.domain;

import java.io.Serializable;
import java.util.Objects;

// Composite Key (Çoklu Birincil Anahtar) tanımı
public class OrderItemId implements Serializable {
    private Long orderId;
    private Long productId;

    public OrderItemId() {}
    public OrderItemId(Long orderId, Long productId) {
        this.orderId = orderId;
        this.productId = productId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OrderItemId that = (OrderItemId) o;
        return Objects.equals(orderId, that.orderId) && Objects.equals(productId, that.productId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(orderId, productId);
    }
}