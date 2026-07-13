package com.aurora.product.domain;

import jakarta.persistence.*;
import java.io.Serializable;

@Entity
@Table(name = "products", schema = "product")
public class Product implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(name = "unit_price", nullable = false)
    private Long unitPrice;

    @Column(nullable = false)
    private Integer stock;

    // --- Getter ve Setter Metodları ---
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Long getUnitPrice() { return unitPrice; }
    public void setUnitPrice(Long unitPrice) { this.unitPrice = unitPrice; }

    public Integer getStock() { return stock; }
    public void setStock(Integer stock) { this.stock = stock; }
}