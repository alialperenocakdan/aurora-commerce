package com.aurora.auth.repo;

import com.aurora.auth.domain.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

// JpaRepository sayesinde INSERT, SELECT, UPDATE gibi SQL komutlarını yazmaktan kurtuluyoruz.
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    // Spring Data JPA, sadece bu metodun ismine bakarak arka planda
    // "SELECT * FROM customers WHERE email = ?" SQL sorgusunu otomatik üretir!
    Optional<Customer> findByEmail(String email);

    boolean existsByEmail(String email);
}