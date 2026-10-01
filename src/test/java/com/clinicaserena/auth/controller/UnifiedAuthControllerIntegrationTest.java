package com.clinicaserena.auth.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.CACHE_CONTROL;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "clinica.auth.login-rate-limit.max-failures=20",
        "clinica.auth.login-rate-limit.max-failures-per-address=20",
        "clinica.auth.login-rate-limit.window-seconds=60",
        "clinica.auth.login-rate-limit.cooldown-seconds=1",
        "clinica.auth.login-rate-limit.max-entries=100",
        "clinica.auth.login-rate-limit.cleanup-interval-milliseconds=60000"
})
class UnifiedAuthControllerIntegrationTest {

    private static final String PATIENT_ID = "98000000-0000-0000-0000-000000000001";
    private static final String PATIENT_ACCOUNT_ID = "98100000-0000-0000-0000-000000000001";
    private static final String UNVERIFIED_PATIENT_ID = "98000000-0000-0000-0000-000000000002";
    private static final String UNVERIFIED_ACCOUNT_ID = "98100000-0000-0000-0000-000000000002";
    private static final String ADMIN_ID = "98200000-0000-0000-0000-000000000001";
    private static final String RECEPTION_ID = "98200000-0000-0000-0000-000000000002";
    private static final String DOCTOR_ID = "98200000-0000-0000-0000-000000000003";
    private static final String AMBIGUOUS_STAFF_ID = "98200000-0000-0000-0000-000000000004";

    private static final String PATIENT_EMAIL = "unified.patient@example.test";
    private static final String UNVERIFIED_EMAIL = "unified.unverified@example.test";
    private static final String ADMIN_EMAIL = "unified.admin@example.test";
    private static final String RECEPTION_EMAIL = "unified.reception@example.test";
    private static final String DOCTOR_EMAIL = "unified.doctor@example.test";
    private static final String PASSWORD = "Unified-Testing-2026!";

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired ObjectMapper objectMapper;

    @BeforeEach
    void prepareFixture() {
        cleanupFixture();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        jdbc.update("INSERT INTO pacientes(id, estado, creado_en) VALUES (?, 'ACTIVO', ?), (?, 'ACTIVO', ?)",
                PATIENT_ID, now, UNVERIFIED_PATIENT_ID, now);
        jdbc.update("""
                        INSERT INTO cuentas_paciente(
                            id, paciente_id, email_normalizado, password_hash, estado, creado_en, actualizada_en, email_verificado_en
                        ) VALUES (?, ?, ?, ?, 'ACTIVA', ?, ?, ?), (?, ?, ?, ?, 'ACTIVA', ?, ?, NULL)
                        """,
                PATIENT_ACCOUNT_ID, PATIENT_ID, PATIENT_EMAIL, passwordEncoder.encode(PASSWORD), now, now, now,
                UNVERIFIED_ACCOUNT_ID, UNVERIFIED_PATIENT_ID, UNVERIFIED_EMAIL, passwordEncoder.encode(PASSWORD), now, now);
        insertStaff(ADMIN_ID, ADMIN_EMAIL, "ADMIN");
        insertStaff(RECEPTION_ID, RECEPTION_EMAIL, "RECEPCION");
        insertStaff(DOCTOR_ID, DOCTOR_EMAIL, "MEDICO");
    }

    @AfterEach
    void cleanupFixtureAfterEach() {
        cleanupFixture();
    }

    @Test
    void loginUnificadoResuelvePacienteAdminRecepcionYMedico() throws Exception {
        assertUnifiedLogin(PATIENT_EMAIL, "PACIENTE", "PACIENTE", "patient");
        assertUnifiedLogin(ADMIN_EMAIL, "PERSONAL", "ADMIN", "admin");
        assertUnifiedLogin(RECEPTION_EMAIL, "PERSONAL", "RECEPCION", "reception");
        assertUnifiedLogin(DOCTOR_EMAIL, "PERSONAL", "MEDICO", "doctor");
    }

    @Test
    void rechazaIncorrectoNoVerificadoEInexistenteSinCrearSesion() throws Exception {
        int patientSessions = sessionCount("sesiones_paciente");
        int staffSessions = sessionCount("sesiones_personal");

        assertGenericFailure(PATIENT_EMAIL, "wrong-password", "wrong-password");
        assertGenericFailure(UNVERIFIED_EMAIL, PASSWORD, "unverified");
        assertGenericFailure("missing-unified@example.test", PASSWORD, "missing");

        assertThat(sessionCount("sesiones_paciente")).isEqualTo(patientSessions);
        assertThat(sessionCount("sesiones_personal")).isEqualTo(staffSessions);
    }

    @Test
    void correoNormalizadoAmbiguoSeRechazaSinEscogerRolNiCrearSesion() throws Exception {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        jdbc.update("""
                        INSERT INTO cuentas_personal(
                            id, email_normalizado, nombre_completo, password_hash, rol, estado, creado_en, actualizada_en
                        ) VALUES (?, ?, ?, ?, 'RECEPCION', 'ACTIVA', ?, ?)
                        """,
                AMBIGUOUS_STAFF_ID, PATIENT_EMAIL, "Cuenta ambigua temporal", passwordEncoder.encode(PASSWORD), now, now);

        int patientSessions = sessionCount("sesiones_paciente");
        int staffSessions = sessionCount("sesiones_personal");

        mockMvc.perform(post("/api/v1/auth/login-unified")
                        .with(remoteAddress("ambiguous"))
                        .contentType(APPLICATION_JSON)
                        .content(loginBody("UNIFIED.PATIENT@example.test", PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(CACHE_CONTROL, org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Credenciales inválidas"));

        assertThat(sessionCount("sesiones_paciente")).isEqualTo(patientSessions);
        assertThat(sessionCount("sesiones_personal")).isEqualTo(staffSessions);
    }

    @Test
    void endpointsDeLoginSeparadosSiguenDisponibles() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .with(remoteAddress("legacy-patient"))
                        .contentType(APPLICATION_JSON)
                        .content(loginBody(PATIENT_EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                        .andExpect(header().string(CACHE_CONTROL, org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(jsonPath("$.tokenType").value("Bearer"));

        mockMvc.perform(post("/api/v1/staff/auth/login")
                        .with(remoteAddress("legacy-staff"))
                        .contentType(APPLICATION_JSON)
                        .content(loginBody(ADMIN_EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(header().string(CACHE_CONTROL, org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    private void assertUnifiedLogin(String email, String accountType, String role, String address) throws Exception {
        mockMvc.perform(post("/api/v1/auth/login-unified")
                        .with(remoteAddress(address))
                        .contentType(APPLICATION_JSON)
                        .content(loginBody(email, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(header().string(CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresInSeconds").value(1800))
                .andExpect(jsonPath("$.accountType").value(accountType))
                .andExpect(jsonPath("$.role").value(role));
    }

    private void assertGenericFailure(String email, String password, String address) throws Exception {
        mockMvc.perform(post("/api/v1/auth/login-unified")
                        .with(remoteAddress(address))
                        .contentType(APPLICATION_JSON)
                        .content(loginBody(email, password)))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(CACHE_CONTROL, org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Credenciales inválidas"));
    }

    private String loginBody(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    private RequestPostProcessor remoteAddress(String address) {
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }

    private void insertStaff(String id, String email, String role) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        jdbc.update("""
                        INSERT INTO cuentas_personal(
                            id, email_normalizado, nombre_completo, password_hash, rol, estado, creado_en, actualizada_en
                        ) VALUES (?, ?, ?, ?, ?, 'ACTIVA', ?, ?)
                        """,
                id, email, "Cuenta " + role, passwordEncoder.encode(PASSWORD), role, now, now);
    }

    private int sessionCount(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    private void cleanupFixture() {
        jdbc.update("DELETE FROM sesiones_personal WHERE cuenta_id IN (?, ?, ?, ?)",
                ADMIN_ID, RECEPTION_ID, DOCTOR_ID, AMBIGUOUS_STAFF_ID);
        jdbc.update("DELETE FROM cuentas_personal WHERE id IN (?, ?, ?, ?)",
                ADMIN_ID, RECEPTION_ID, DOCTOR_ID, AMBIGUOUS_STAFF_ID);
        jdbc.update("DELETE FROM sesiones_paciente WHERE cuenta_id IN (?, ?)",
                PATIENT_ACCOUNT_ID, UNVERIFIED_ACCOUNT_ID);
        jdbc.update("DELETE FROM cuentas_paciente WHERE id IN (?, ?)",
                PATIENT_ACCOUNT_ID, UNVERIFIED_ACCOUNT_ID);
        jdbc.update("DELETE FROM pacientes WHERE id IN (?, ?)", PATIENT_ID, UNVERIFIED_PATIENT_ID);
    }
}
