package com.clinicaserena.pagos.controller;

import com.clinicaserena.clinica.ClinicalFixture;
import com.clinicaserena.clinica.MutableClock;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.clinicaserena.clinica.ClinicalFixture.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.HttpHeaders.CACHE_CONTROL;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(MutableClock.Config.class)
class ReceptionBillingIntegrationTest {

    private static final String VALID_ATTENTION = """
            {"reason":"Consulta lista para cobro","diagnosis":"Diagnóstico para cobro","prescription":[]}""";

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired ObjectMapper objectMapper;
    @Autowired MutableClock clock;

    private ClinicalFixture fixture;

    @BeforeEach
    void prepare() throws Exception {
        fixture = new ClinicalFixture(jdbc);
        fixture.create(passwordEncoder, true);
        clock.set(fixture.base().plusHours(CLOCK_OFFSET_HOURS).toInstant());
        String doctor = ClinicalFixture.login(mockMvc, objectMapper, "doctor.one@example.test");
        mockMvc.perform(post("/api/v1/medico/citas/{id}/atencion", APPT_X_ONE_PENDING)
                        .header("Authorization", "Bearer " + doctor)
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_ATTENTION))
                .andExpect(status().isCreated());
    }

    @AfterEach
    void cleanup() {
        fixture.cleanup();
    }

    @Test
    void recepcionFijaCargoRegistraPagoYConsultaSaldoPersistido() throws Exception {
        String reception = ClinicalFixture.login(mockMvc, objectMapper, "reception.clinical@example.test");
        mockMvc.perform(get("/api/v1/staff/billing/appointments/{id}", APPT_X_ONE_PENDING)
                        .header("Authorization", "Bearer " + reception))
                .andExpect(status().isOk()).andExpect(header().string(CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.patientName").value("Paciente X sintético"))
                .andExpect(jsonPath("$.attended").value(true))
                .andExpect(jsonPath("$.chargeDefined").value(false));

        mockMvc.perform(put("/api/v1/staff/billing/appointments/{id}/charge", APPT_X_ONE_PENDING)
                        .header("Authorization", "Bearer " + reception)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":50000,\"currency\":\"GTQ\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chargeAmount").value(50000))
                .andExpect(jsonPath("$.balanceAmount").value(50000));

        mockMvc.perform(post("/api/v1/staff/billing/appointments/{id}/payments", APPT_X_ONE_PENDING)
                        .header("Authorization", "Bearer " + reception)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":20000,\"method\":\"EFECTIVO\",\"reference\":\"REC-TEST-1\",\"idempotencyKey\":\"pay-1\"}"))
                .andExpect(status().isCreated()).andExpect(header().string(CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.paidAmount").value(20000))
                .andExpect(jsonPath("$.balanceAmount").value(30000))
                .andExpect(jsonPath("$.payments", hasSize(1)))
                .andExpect(jsonPath("$.payments[0].registeredByAccountId").value(RECEPTION));

        mockMvc.perform(post("/api/v1/staff/auth/logout").header("Authorization", "Bearer " + reception))
                .andExpect(status().isNoContent());
        String again = ClinicalFixture.login(mockMvc, objectMapper, "reception.clinical@example.test");
        mockMvc.perform(get("/api/v1/staff/billing/appointments/{id}", APPT_X_ONE_PENDING)
                        .header("Authorization", "Bearer " + again))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paidAmount").value(20000))
                .andExpect(jsonPath("$.balanceAmount").value(30000))
                .andExpect(jsonPath("$.payments[0].reference").value("REC-TEST-1"));
    }

    @Test
    void intencionPersistidaBloqueaCambiosYSeReconciliaAntesDeLiberarOtroPago() throws Exception {
        String reception = ClinicalFixture.login(mockMvc, objectMapper, "reception.clinical@example.test");
        mockMvc.perform(get("/api/v1/staff/billing/payment-intent")
                        .header("Authorization", "Bearer " + reception))
                .andExpect(status().isOk())
                .andExpect(header().string(CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.intent").value(org.hamcrest.Matchers.nullValue()));
        mockMvc.perform(put("/api/v1/staff/billing/appointments/{id}/charge", APPT_X_ONE_PENDING)
                        .header("Authorization", "Bearer " + reception)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":50000,\"currency\":\"GTQ\"}"))
                .andExpect(status().isOk());

        String intent = "{\"amount\":20000,\"method\":\"TRANSFERENCIA\","
                + "\"reference\":\"RECARGA-REAL\",\"idempotencyKey\":\"reload-key-1\"}";
        mockMvc.perform(put("/api/v1/staff/billing/appointments/{id}/payment-intent", APPT_X_ONE_PENDING)
                        .header("Authorization", "Bearer " + reception)
                        .contentType(MediaType.APPLICATION_JSON).content(intent))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PREPARADA"));

        mockMvc.perform(put("/api/v1/staff/billing/appointments/{id}/payment-intent", APPT_X_ONE_PENDING)
                        .header("Authorization", "Bearer " + reception)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":10000,\"method\":\"EFECTIVO\",\"idempotencyKey\":\"new-key\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_INTENT_ACTIVE"));

        mockMvc.perform(post("/api/v1/staff/billing/payment-intent/commit")
                        .header("Authorization", "Bearer " + reception))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paidAmount").value(20000))
                .andExpect(jsonPath("$.balanceAmount").value(30000))
                .andExpect(jsonPath("$.payments", hasSize(1)));

        mockMvc.perform(post("/api/v1/staff/billing/payment-intent/commit")
                        .header("Authorization", "Bearer " + reception))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.payments", hasSize(1)))
                .andExpect(jsonPath("$.balanceAmount").value(30000));

        mockMvc.perform(get("/api/v1/staff/billing/payment-intent")
                        .header("Authorization", "Bearer " + reception))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.intent.idempotencyKey").value("reload-key-1"))
                .andExpect(jsonPath("$.intent.status").value("COMPLETADA"));

        mockMvc.perform(delete("/api/v1/staff/billing/payment-intent")
                        .header("Authorization", "Bearer " + reception))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/staff/billing/payment-intent")
                        .header("Authorization", "Bearer " + reception))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.intent").value(org.hamcrest.Matchers.nullValue()));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pagos_citas WHERE cita_id = ?", Integer.class,
                APPT_X_ONE_PENDING)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT coalesce(sum(monto_centavos),0) FROM pagos_citas WHERE cita_id = ?",
                Long.class, APPT_X_ONE_PENDING)).isEqualTo(20000L);
    }

    @Test
    void rolesNoRecepcionNoRegistranCobros() throws Exception {
        String doctor = ClinicalFixture.login(mockMvc, objectMapper, "doctor.one@example.test");
        String admin = ClinicalFixture.login(mockMvc, objectMapper, "admin.clinical@example.test");
        for (String token : List.of(doctor, admin)) {
            mockMvc.perform(get("/api/v1/staff/billing/payment-intent")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/v1/staff/billing/appointments/{id}", APPT_X_ONE_PENDING)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
            mockMvc.perform(put("/api/v1/staff/billing/appointments/{id}/charge", APPT_X_ONE_PENDING)
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":50000,\"currency\":\"GTQ\"}"))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(post("/api/v1/staff/billing/appointments/{id}/payments", APPT_X_ONE_PENDING)
                        .with(user("patient").roles("PACIENTE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":1,\"method\":\"EFECTIVO\",\"idempotencyKey\":\"denied\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/staff/billing/appointments/{id}", APPT_X_ONE_PENDING))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/staff/billing/payment-intent"))
                .andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pagos_citas", Integer.class)).isZero();
    }

    @Test
    void validaElegibilidadMontosSaldoYDuplicados() throws Exception {
        String reception = ClinicalFixture.login(mockMvc, objectMapper, "reception.clinical@example.test");
        mockMvc.perform(put("/api/v1/staff/billing/appointments/{id}/charge", APPT_X_ONE_CANCELLED)
                        .header("Authorization", "Bearer " + reception)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":1000,\"currency\":\"GTQ\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("APPOINTMENT_NOT_BILLABLE"));
        mockMvc.perform(post("/api/v1/staff/billing/appointments/{id}/payments", APPT_X_ONE_PENDING)
                        .header("Authorization", "Bearer " + reception)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":1000,\"method\":\"EFECTIVO\",\"idempotencyKey\":\"without-charge\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CHARGE_REQUIRED"));
        mockMvc.perform(put("/api/v1/staff/billing/appointments/{id}/charge", APPT_X_ONE_PENDING)
                        .header("Authorization", "Bearer " + reception)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":1000,\"currency\":\"GTQ\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/staff/billing/appointments/{id}/charge", APPT_X_ONE_PENDING)
                        .header("Authorization", "Bearer " + reception)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":2000,\"currency\":\"GTQ\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CHARGE_ALREADY_DEFINED"));
        mockMvc.perform(post("/api/v1/staff/billing/appointments/{id}/payments", APPT_X_ONE_PENDING)
                        .header("Authorization", "Bearer " + reception)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":-1,\"method\":\"EFECTIVO\",\"idempotencyKey\":\"negative\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/staff/billing/appointments/{id}/payments", APPT_X_ONE_PENDING)
                        .header("Authorization", "Bearer " + reception)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":1001,\"method\":\"EFECTIVO\",\"idempotencyKey\":\"too-much\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PAYMENT_EXCEEDS_BALANCE"));
        mockMvc.perform(post("/api/v1/staff/billing/appointments/{id}/payments", APPT_X_ONE_PENDING)
                        .header("Authorization", "Bearer " + reception)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":500,\"method\":\"TRANSFERENCIA\",\"idempotencyKey\":\"same-key\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/staff/billing/appointments/{id}/payments", APPT_X_ONE_PENDING)
                        .header("Authorization", "Bearer " + reception)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":500,\"method\":\"TRANSFERENCIA\",\"idempotencyKey\":\"same-key\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.payments", hasSize(1)))
                .andExpect(jsonPath("$.balanceAmount").value(500));
        mockMvc.perform(post("/api/v1/staff/billing/appointments/{id}/payments", APPT_X_ONE_PENDING)
                        .header("Authorization", "Bearer " + reception)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":400,\"method\":\"TRANSFERENCIA\",\"idempotencyKey\":\"same-key\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_IDEMPOTENCY_KEY_REUSED"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pagos_citas WHERE cita_id = ?", Integer.class,
                APPT_X_ONE_PENDING)).isEqualTo(1);
    }

    @Test
    void historialDePagosYCargosRechazaUpdateYDeleteSinAlterarDatos() throws Exception {
        String reception = ClinicalFixture.login(mockMvc, objectMapper, "reception.clinical@example.test");
        mockMvc.perform(put("/api/v1/staff/billing/appointments/{id}/charge", APPT_X_ONE_PENDING)
                        .header("Authorization", "Bearer " + reception)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":1000,\"currency\":\"GTQ\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/staff/billing/appointments/{id}/payments", APPT_X_ONE_PENDING)
                        .header("Authorization", "Bearer " + reception)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":400,\"method\":\"EFECTIVO\",\"idempotencyKey\":\"immutable-key\"}"))
                .andExpect(status().isCreated());

        String paymentId = jdbc.queryForObject("SELECT id FROM pagos_citas WHERE cita_id = ?", String.class,
                APPT_X_ONE_PENDING);
        String auditId = jdbc.queryForObject("SELECT id FROM cargos_citas_auditoria WHERE cita_id = ?", String.class,
                APPT_X_ONE_PENDING);
        assertThatThrownBy(() -> jdbc.update("UPDATE pagos_citas SET monto_centavos = 1 WHERE id = ?", paymentId))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM pagos_citas WHERE id = ?", paymentId))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE cargos_citas_auditoria SET monto_nuevo_centavos = 1 WHERE id = ?", auditId))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM cargos_citas_auditoria WHERE id = ?", auditId))
                .isInstanceOf(DataAccessException.class);

        assertThat(jdbc.queryForObject("SELECT monto_centavos FROM pagos_citas WHERE id = ?", Long.class, paymentId))
                .isEqualTo(400L);
        assertThat(jdbc.queryForObject("SELECT monto_nuevo_centavos FROM cargos_citas_auditoria WHERE id = ?",
                Long.class, auditId)).isEqualTo(1000L);
    }

    @Test
    void pagosConcurrentesNoSobrepasanSaldo() throws Exception {
        String reception = ClinicalFixture.login(mockMvc, objectMapper, "reception.clinical@example.test");
        mockMvc.perform(put("/api/v1/staff/billing/appointments/{id}/charge", APPT_X_ONE_PENDING)
                        .header("Authorization", "Bearer " + reception)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":1000,\"currency\":\"GTQ\"}"))
                .andExpect(status().isOk());
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        Callable<Integer> call = () -> {
            ready.countDown(); go.await();
            String key = "concurrent-" + Thread.currentThread().getId();
            return mockMvc.perform(post("/api/v1/staff/billing/appointments/{id}/payments", APPT_X_ONE_PENDING)
                    .header("Authorization", "Bearer " + reception)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"amount\":1000,\"method\":\"EFECTIVO\",\"idempotencyKey\":\"" + key + "\"}"))
                    .andReturn().getResponse().getStatus();
        };
        Future<Integer> first = pool.submit(call);
        Future<Integer> second = pool.submit(call);
        ready.await(); go.countDown();
        List<Integer> statuses = List.of(first.get(), second.get()).stream().sorted().toList();
        pool.shutdownNow();
        assertThat(statuses).containsExactly(201, 409);
        assertThat(jdbc.queryForObject("SELECT coalesce(sum(monto_centavos),0) FROM pagos_citas WHERE cita_id = ?",
                Long.class, APPT_X_ONE_PENDING)).isEqualTo(1000L);
    }
}
