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

    @Column(name = "image_url")
    private String imageUrl;

    // Serbest metin — "Süt Ürünleri", "İçecek" vb. Katalog filtrelemesi için;
    // boş bırakılabilir (nullable), zorunlu değil.
    @Column(name = "category")
    private String category;

    // İndirimsiz fiyat (kuruş). NULL ise ürün indirimde değil; unitPrice'tan
    // büyükse indirimdedir ve vitrinde üstü çizili olarak gösterilir.
    @Column(name = "old_price")
    private Long oldPrice;

    // --- Getter ve Setter Metodları ---
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Long getUnitPrice() { return unitPrice; }
    public void setUnitPrice(Long unitPrice) { this.unitPrice = unitPrice; }

    public Integer getStock() { return stock; }
    public void setStock(Integer stock) { this.stock = stock; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public Long getOldPrice() { return oldPrice; }
    public void setOldPrice(Long oldPrice) { this.oldPrice = oldPrice; }
}