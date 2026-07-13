package com.aurora.order.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "order_items", schema = "orders")
@IdClass(OrderItemId.class) // İki anahtarımız olduğunu belirtiyoruz
public class OrderItem {

    @Id
    @Column(name = "order_id")
    private Long orderId;

    @Id
    @Column(name = "product_id")
    private Long productId;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", nullable = false)
    private Long unitPrice;

    // --- Getter ve Setter ---
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public Long getUnitPrice() { return unitPrice; }
    public void setUnitPrice(Long unitPrice) { this.unitPrice = unitPrice; }
}