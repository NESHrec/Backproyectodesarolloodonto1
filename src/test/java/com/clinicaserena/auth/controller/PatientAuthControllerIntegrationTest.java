package com.clinicaserena.auth.controller;

import com.clinicaserena.auth.security.TokenHasher;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.List;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.HttpHeaders.CACHE_CONTROL;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "clinica.auth.login-rate-limit.max-failures=3",
        "clinica.auth.login-rate-limit.max-failures-per-address=3",
        "clinica.auth.login-rate-limit.window-seconds=1",
        "clinica.auth.login-rate-limit.cooldown-seconds=1",
        "clinica.auth.login-rate-limit.max-entries=6",
        "clinica.auth.login-rate-limit.cleanup-interval-milliseconds=60000",
        "clinica.auth.public-rate-limit.max-requests=20"
})
class PatientAuthControllerIntegrationTest {

    private static final String PATIENT_ID = "90000000-0000-0000-0000-000000000001";
    private static final String ACCOUNT_ID = "91000000-0000-0000-0000-000000000001";
    private static final String EMAIL = "Paciente.Prueba@Example.Test";
    private static final String NORMALIZED_EMAIL = "paciente.prueba@example.test";
    private static final String TEST_PASSWORD = "UnaClaveDePrueba!2026";

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired ObjectMapper objectMapper;

    @BeforeEach
    void prepararCuentaDeFixture() {
        limpiarFixture();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        jdbc.update("INSERT INTO pacientes(id, estado, creado_en) VALUES (?, 'ACTIVO', ?)", PATIENT_ID, now);
        jdbc.update("""
                        INSERT INTO cuentas_paciente(
                            id, paciente_id, email_normalizado, password_hash, estado, creado_en, actualizada_en, email_verificado_en
                        ) VALUES (?, ?, ?, ?, 'ACTIVA', ?, ?, ?)
                        """,
                ACCOUNT_ID, PATIENT_ID, NORMALIZED_EMAIL, passwordEncoder.encode(TEST_PASSWORD), now, now, now);
    }

    @AfterEach
    void limpiarFixtureDespuesDeCadaPrueba() {
        limpiarFixture();
    }

    @Test
    void loginCorrectoNormalizaCorreoYDevuelveBearer() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\",\"password\":\"" + TEST_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string(CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresInSeconds").value(1800));
    }

    @Test
    void credencialesIncorrectasTienenRespuestaGenerica() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\",\"password\":\"clave-incorrecta\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Credenciales inválidas"));
    }

    @Test
    void identidadRequiereAutenticacion() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void identidadDevuelvePacienteDeLaSesionYLogoutLaRevoca() throws Exception {
        String token = loginAndReadToken();

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string(CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.patientId").value(PATIENT_ID))
                .andExpect(jsonPath("$.email").value(NORMALIZED_EMAIL))
                .andExpect(jsonPath("$.accountStatus").value("ACTIVA"));

        mockMvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent())
                .andExpect(header().string(CACHE_CONTROL, "no-store"));

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void limitaIntentosPorCorreoYDireccionYPermiteRecuperacion() throws Exception {
        RequestPostProcessor remoteAddress = remoteAddress("rate-limit-client");
        for (int attempt = 0; attempt < 3; attempt++) {
            login(EMAIL, "wrong-password", remoteAddress)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));
        }

        login(EMAIL, TEST_PASSWORD, remoteAddress)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));

        login(EMAIL, TEST_PASSWORD, remoteAddress("different-client"))
                .andExpect(status().isOk())
                .andExpect(header().string(CACHE_CONTROL, "no-store"));

        Thread.sleep(1200);

        login(EMAIL, TEST_PASSWORD, remoteAddress)
                .andExpect(status().isOk())
                .andExpect(header().string(CACHE_CONTROL, "no-store"));
    }

    @Test
    void variarElCorreoNoEludeElLimiteDeUnaDireccion() throws Exception {
        RequestPostProcessor remoteAddress = remoteAddress("shared-client");
        for (int attempt = 0; attempt < 3; attempt++) {
            login("different-" + attempt + "@example.test", "wrong-password", remoteAddress)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));
        }

        login(EMAIL, TEST_PASSWORD, remoteAddress)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));
        login(EMAIL, TEST_PASSWORD, remoteAddress("another-client"))
                .andExpect(status().isOk())
                .andExpect(header().string(CACHE_CONTROL, "no-store"));
    }

    @Test
    void unaIdentidadSinRolPacienteNoPuedeUsarElPortal() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me").with(user("staff").roles("ADMIN")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/auth/logout").with(user("staff").roles("ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void sesionExpiradaNoPermiteConsultarIdentidad() throws Exception {
        OffsetDateTime created = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(40);
        String rawToken = "expired-fixture-token";
        jdbc.update("""
                        INSERT INTO sesiones_paciente(id, cuenta_id, token_hash, expira_en, creada_en)
                        VALUES (?, ?, ?, ?, ?)
                        """,
                UUID.randomUUID().toString(), ACCOUNT_ID, TokenHasher.sha256(rawToken),
                created.plusMinutes(1), created);

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + rawToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenDeVerificacionSoloSeConsumeUnaVezInclusoEnConcurrencia() throws Exception {
        jdbc.update("UPDATE cuentas_paciente SET email_verificado_en = NULL WHERE id = ?", ACCOUNT_ID);
        String rawToken="verify-"+UUID.randomUUID(); OffsetDateTime now=OffsetDateTime.now(ZoneOffset.UTC);
        jdbc.update("INSERT INTO tokens_cuenta_paciente(id,cuenta_id,tipo,token_hash,expira_en,creado_en) VALUES (?,?,'VERIFICACION_EMAIL',?,?,?)",
                UUID.randomUUID().toString(),ACCOUNT_ID,TokenHasher.sha256(rawToken),now.plusMinutes(5),now);
        ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch ready=new CountDownLatch(2),go=new CountDownLatch(1);
        Callable<Integer> call=()->{ready.countDown();go.await();return mockMvc.perform(post("/api/v1/auth/verify-email").contentType(APPLICATION_JSON)
                .content("{\"token\":\""+rawToken+"\"}")).andReturn().getResponse().getStatus();};
        Future<Integer> first=pool.submit(call),second=pool.submit(call);ready.await();go.countDown();
        List<Integer> statuses=java.util.stream.Stream.of(first.get(),second.get()).sorted().toList();pool.shutdownNow();
        assertThat(statuses).containsExactly(204,400);
        mockMvc.perform(post("/api/v1/auth/verify-email").contentType(APPLICATION_JSON).content("{\"token\":\""+rawToken+"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reenvioInvalidaEnlaceAnteriorYConservaRespuestaGenerica() throws Exception {
        jdbc.update("UPDATE cuentas_paciente SET email_verificado_en = NULL WHERE id = ?", ACCOUNT_ID);
        String body="{\"email\":\""+EMAIL+"\"}";
        String first=mockMvc.perform(post("/api/v1/auth/resend-verification").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        String missing=mockMvc.perform(post("/api/v1/auth/resend-verification").contentType(APPLICATION_JSON).content("{\"email\":\"missing@example.test\"}"))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        mockMvc.perform(post("/api/v1/auth/resend-verification").contentType(APPLICATION_JSON).content(body)).andExpect(status().isAccepted());
        assertThat(first).isEqualTo(missing);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tokens_cuenta_paciente WHERE cuenta_id=? AND tipo='VERIFICACION_EMAIL' AND usado_en IS NULL",Integer.class,ACCOUNT_ID)).isEqualTo(1);
    }

    @Test
    void dosReenviosSimultaneosDejanUnSoloTokenActivoEInvalidanElAnterior() throws Exception {
        jdbc.update("UPDATE cuentas_paciente SET email_verificado_en = NULL WHERE id = ?", ACCOUNT_ID);
        String oldRaw="old-verify-"+UUID.randomUUID();OffsetDateTime now=OffsetDateTime.now(ZoneOffset.UTC);
        jdbc.update("INSERT INTO tokens_cuenta_paciente(id,cuenta_id,tipo,token_hash,expira_en,creado_en) VALUES (?,?,'VERIFICACION_EMAIL',?,?,?)",
                UUID.randomUUID().toString(),ACCOUNT_ID,TokenHasher.sha256(oldRaw),now.plusMinutes(5),now);
        String body="{\"email\":\""+EMAIL+"\"}";ExecutorService pool=Executors.newFixedThreadPool(2);
        CountDownLatch ready=new CountDownLatch(2),go=new CountDownLatch(1);
        Callable<org.springframework.test.web.servlet.MvcResult> call=()->{ready.countDown();go.await();return mockMvc.perform(
                post("/api/v1/auth/resend-verification").contentType(APPLICATION_JSON).content(body)).andReturn();};
        Future<org.springframework.test.web.servlet.MvcResult> first=pool.submit(call),second=pool.submit(call);ready.await();go.countDown();
        var firstResult=first.get();var secondResult=second.get();pool.shutdownNow();
        assertThat(firstResult.getResponse().getStatus()).isEqualTo(202);
        assertThat(secondResult.getResponse().getStatus()).isEqualTo(202);
        assertThat(firstResult.getResponse().getContentAsString()).isEqualTo(secondResult.getResponse().getContentAsString());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tokens_cuenta_paciente WHERE cuenta_id=? AND tipo='VERIFICACION_EMAIL' AND usado_en IS NULL",Integer.class,ACCOUNT_ID)).isEqualTo(1);
        mockMvc.perform(post("/api/v1/auth/verify-email").contentType(APPLICATION_JSON)
                .content("{\"token\":\""+oldRaw+"\"}")).andExpect(status().isBadRequest());
    }

    private String loginAndReadToken() throws Exception {
        String body = login(EMAIL, TEST_PASSWORD, remoteAddress("default-test-client"))
                        .andExpect(status().isOk())
                        .andExpect(header().string(CACHE_CONTROL, "no-store"))
                        .andReturn().getResponse().getContentAsString();
        JsonNode response = objectMapper.readTree(body);
        return response.get("accessToken").asText();
    }

    private org.springframework.test.web.servlet.ResultActions login(
            String email,
            String password,
            RequestPostProcessor remoteAddress
    ) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .with(remoteAddress)
                .contentType(APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }

    private RequestPostProcessor remoteAddress(String address) {
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }

    private void limpiarFixture() {
        jdbc.update("DELETE FROM sesiones_paciente WHERE cuenta_id = ?", ACCOUNT_ID);
        jdbc.update("DELETE FROM cuentas_paciente WHERE id = ?", ACCOUNT_ID);
        jdbc.update("DELETE FROM pacientes WHERE id = ?", PATIENT_ID);
    }
}
