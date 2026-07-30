package com.aurora.auth.service;

import com.aurora.auth.domain.Customer;
import com.aurora.auth.exception.EmailTakenException;
import com.aurora.auth.exception.InvalidCredentialsException;
import com.aurora.auth.exception.InvalidRequestException;
import com.aurora.auth.jwt.JwtService;
import com.aurora.auth.repo.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

// Saf birim testleri: Spring context'i olmadan, repository/encoder mock'lanarak
// register ve login'in iş kuralları doğrulanır.
class AuthServiceTest {

    private CustomerRepository repository;
    private PasswordEncoder encoder;
    private JwtService jwtService;
    private AuthService service;

    @BeforeEach
    void setUp() {
        repository = mock(CustomerRepository.class);
        encoder = mock(PasswordEncoder.class);
        jwtService = mock(JwtService.class);
        service = new AuthService(repository, encoder, jwtService);
    }

    // --- register ---

    @Test
    void register_gecersizEposta_422() {
        assertThrows(InvalidRequestException.class, () -> service.register("bozuk-eposta", "password123"));
        assertThrows(InvalidRequestException.class, () -> service.register(null, "password123"));
        verifyNoInteractions(repository);
    }

    @Test
    void register_kisaSifre_422() {
        assertThrows(InvalidRequestException.class, () -> service.register("a@b.com", "kisa"));
        assertThrows(InvalidRequestException.class, () -> service.register("a@b.com", null));
    }

    @Test
    void register_72BayttanUzunSifre_422() {
        // BCrypt yalnızca ilk 72 baytı işler; daha uzunu baştan reddedilmeli
        String uzun = "a".repeat(73);
        assertThrows(InvalidRequestException.class, () -> service.register("a@b.com", uzun));
    }

    @Test
    void register_alinmisEposta_409() {
        when(repository.existsByEmail("a@b.com")).thenReturn(true);
        assertThrows(EmailTakenException.class, () -> service.register("a@b.com", "password123"));
        verify(repository, never()).save(any());
    }

    @Test
    void register_basarili_hashKaydedilir() {
        when(repository.existsByEmail("a@b.com")).thenReturn(false);
        when(encoder.encode("password123")).thenReturn("$2a$hash");
        when(repository.save(any(Customer.class))).thenAnswer(inv -> {
            Customer c = inv.getArgument(0);
            c.setId(42L);
            return c;
        });

        Long id = service.register("a@b.com", "password123");

        assertEquals(42L, id);
        verify(encoder).encode("password123"); // düz şifre asla kaydedilmez
    }

    // --- login ---

    @Test
    void login_olmayanKullanici_401() {
        when(repository.findByEmail("yok@b.com")).thenReturn(Optional.empty());
        assertThrows(InvalidCredentialsException.class, () -> service.login("yok@b.com", "password123"));
    }

    @Test
    void login_yanlisSifre_401() {
        Customer c = new Customer();
        c.setPasswordHash("$2a$hash");
        when(repository.findByEmail("a@b.com")).thenReturn(Optional.of(c));
        when(encoder.matches("yanlis", "$2a$hash")).thenReturn(false);
        assertThrows(InvalidCredentialsException.class, () -> service.login("a@b.com", "yanlis"));
    }

    @Test
    void login_nullGirdiler_401() {
        assertThrows(InvalidCredentialsException.class, () -> service.login(null, "x"));
        assertThrows(InvalidCredentialsException.class, () -> service.login("a@b.com", null));
    }

    @Test
    void login_basarili_tokenDoner() {
        Customer c = new Customer();
        c.setId(7L);
        c.setEmail("a@b.com");
        c.setPasswordHash("$2a$hash");
        when(repository.findByEmail("a@b.com")).thenReturn(Optional.of(c));
        when(encoder.matches("password123", "$2a$hash")).thenReturn(true);
        when(jwtService.generateToken(7L, "a@b.com", false)).thenReturn("jwt-token");
        when(jwtService.getExpirationSeconds()).thenReturn(3600L);

        Map<String, Object> result = service.login("a@b.com", "password123");

        assertEquals("jwt-token", result.get("accessToken"));
        assertEquals(3600L, result.get("expiresIn"));
    }

    @Test
    void login_adminHesap_isAdminTrueOlarakTokenaGecer() {
        Customer c = new Customer();
        c.setId(1L);
        c.setEmail("admin@gmail.com");
        c.setPasswordHash("$2a$hash");
        c.setAdmin(true);
        when(repository.findByEmail("admin@gmail.com")).thenReturn(Optional.of(c));
        when(encoder.matches("password123", "$2a$hash")).thenReturn(true);
        when(jwtService.generateToken(1L, "admin@gmail.com", true)).thenReturn("admin-jwt");
        when(jwtService.getExpirationSeconds()).thenReturn(3600L);

        Map<String, Object> result = service.login("admin@gmail.com", "password123");

        assertEquals("admin-jwt", result.get("accessToken"));
    }
}
