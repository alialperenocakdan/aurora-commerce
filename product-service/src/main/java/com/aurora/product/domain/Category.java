package com.aurora.product.domain;

import jakarta.persistence.*;
import java.time.Instant;

// Tanımlı kategoriler kataloğu. Ürünler bu tabloya foreign key ile bağlı
// DEĞİLDİR (products.category hâlâ metin) — bu tablo yalnızca yönetim
// panelinde önceden kategori açabilmek ve boş kategorileri de listeleyebilmek
// için var.
@Entity
@Table(name = "categories", schema = "product")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Category() {}

    public Category(String name) {
        this.name = name;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
