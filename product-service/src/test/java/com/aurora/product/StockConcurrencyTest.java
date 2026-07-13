package com.aurora.product;

import com.aurora.product.domain.Product;
import com.aurora.product.repo.ProductRepository;
import com.aurora.product.service.StockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@Testcontainers
class StockConcurrencyTest {


    // Test çalışırken gerçek bir Postgres ayağa kalkar!
    @Autowired
    private StockService stockService;

    @Autowired
    private ProductRepository productRepository;

    private Long testProductId;

    @BeforeEach
    void setUp() {
        productRepository.deleteAll(); // Veritabanını temizle
        // Test için stoku SADECE 1 olan bir ürün yaratıyoruz
        Product p = new Product();
        p.setName("Limited Edition Aurora Kupa");
        p.setUnitPrice(1499L);
        p.setStock(1);
        testProductId = productRepository.save(p).getId();
    }

    @Test
    void when20ConcurrentUsersTryToBuy1Item_thenExactly1SucceedsAnd19Fail() throws InterruptedException {
        int numberOfBuyers = 20;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfBuyers);
        CountDownLatch startGun = new CountDownLatch(1); // Herkesi aynı anda başlatmak için tabanca
        CountDownLatch finishLine = new CountDownLatch(numberOfBuyers); // Herkesin bitirmesini beklemek için

        AtomicInteger successfulPurchases = new AtomicInteger(0);
        AtomicInteger failedPurchases = new AtomicInteger(0);

        for (int i = 0; i < numberOfBuyers; i++) {
            executor.submit(() -> {
                try {
                    startGun.await(); // Tabanca patlayana kadar bekle

                    // Stoku düşmeye çalış
                    stockService.deduct(List.of(Map.of(
                            "productId", testProductId,
                            "quantity", 1
                    )));

                    successfulPurchases.incrementAndGet(); // Başarılıysa sayacı artır
                } catch (RuntimeException e) {
                    failedPurchases.incrementAndGet(); // 409 (OutOfStock) fırlatıldıysa sayacı artır
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    finishLine.countDown(); // Bitiş çizgisine ulaştı
                }
            });
        }

        // Bütün thread'ler hazır. Start tabancasını ateşle!
        startGun.countDown();

        // Herkes işlemi bitirene kadar bekle
        finishLine.await();
        executor.shutdown();

        // KANIT AŞAMASI:
        // 1. Sadece 1 kişi satın alabilmiş olmalı
        assertEquals(1, successfulPurchases.get(), "Sadece 1 adet başarılı satın alma olmalı!");

        // 2. 19 kişi "Stok yetersiz" hatası almış olmalı
        assertEquals(19, failedPurchases.get(), "19 kişi hataya düşmeli!");

        // 3. Veritabanında stok 0 olmalı (Eksiye düşmemeli!)
        Product finalProduct = productRepository.findById(testProductId).orElseThrow();
        assertEquals(0, finalProduct.getStock(), "Stok eksiye düşmüş olamaz, tam olarak 0 olmalı!");
    }
}