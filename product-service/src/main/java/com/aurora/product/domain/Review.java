package com.aurora.product.domain;

import jakarta.persistence.*;
import java.time.Instant;

// Bir müşterinin bir ürüne verdiği puan ve yorumu.
//
// customerId burada yalnızca bir sayı olarak tutulur: product-service auth
// veritabanına bakmaz, kimliği JWT'den okur. E-posta gibi kişisel bilgiyi
// kopyalamıyoruz — yorum listesinde de gösterilmez.
@Entity
@Table(name = "reviews", schema = "product")
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false, updatable = false)
    private Long productId;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private Long customerId;

    @Column(nullable = false)
    private Integer rating;

    @Column(length = 500)
    private String comment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt;

    public Review() {}

    public Review(Long productId, Long customerId, Integer rating, String comment) {
        this.productId = productId;
        this.customerId = customerId;
        this.rating = rating;
        this.comment = comment;
    }

    public Long getId() { return id; }

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }

    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }

    public Integer getRating() { return rating; }
    public void setRating(Integer rating) { this.rating = rating; }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
