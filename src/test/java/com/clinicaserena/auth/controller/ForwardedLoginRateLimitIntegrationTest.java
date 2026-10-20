package com.clinicaserena.auth.controller;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "clinica.auth.login-rate-limit.max-failures=10",
        "clinica.auth.login-rate-limit.max-failures-per-address=2",
        "clinica.auth.login-rate-limit.window-seconds=60",
        "clinica.auth.login-rate-limit.cooldown-seconds=60",
        "clinica.auth.login-rate-limit.max-entries=100"
})
class ForwardedLoginRateLimitIntegrationTest {

    private static final String ACCOUNT_ID = "91000000-0000-0000-0000-000000000001";
    private static final String EMAIL = "proxy-rate-limit@example.test";
    private static final String PASSWORD = "Synthetic-only-42!";

    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        jdbc.update("DELETE FROM sesiones_personal WHERE cuenta_id = ?", ACCOUNT_ID);
        jdbc.update("DELETE FROM cuentas_personal WHERE id = ?", ACCOUNT_ID);
        jdbc.update("""
                INSERT INTO cuentas_personal(
                    id, email_normalizado, nombre_completo, password_hash, rol, estado, creado_en, actualizada_en
                ) VALUES (?, ?, 'Cuenta proxy QA', ?, 'ADMIN', 'ACTIVA', ?, ?)
                """, ACCOUNT_ID, EMAIL, passwordEncoder.encode(PASSWORD), now, now);
    }

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM sesiones_personal WHERE cuenta_id = ?", ACCOUNT_ID);
        jdbc.update("DELETE FROM cuentas_personal WHERE id = ?", ACCOUNT_ID);
    }

    @Test
    void usaIpReenviadaPorProxyConfiableYElExitoNoReiniciaSuContador() {
        assertThat(login(EMAIL, PASSWORD, null).getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(login("first-miss@example.test", "wrong", "198.51.100.10").getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(login("second-miss@example.test", "wrong", "198.51.100.10").getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(login(EMAIL, PASSWORD, "198.51.100.10").getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(login(EMAIL, PASSWORD, "198.51.100.11").getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(login("third-miss@example.test", "wrong", "198.51.100.12").getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(login(EMAIL, PASSWORD, "198.51.100.12").getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(login("fourth-miss@example.test", "wrong", "198.51.100.12").getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(login(EMAIL, PASSWORD, "198.51.100.12").getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private ResponseEntity<String> login(String email, String password, String forwardedFor) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (forwardedFor != null) headers.set("X-Forwarded-For", forwardedFor);
        return http.postForEntity("/api/v1/auth/login-unified",
                new HttpEntity<>(Map.of("email", email, "password", password), headers), String.class);
    }
}
