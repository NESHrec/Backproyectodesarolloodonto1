package com.clinicaserena.pagos.controller;

import com.clinicaserena.citas.dto.CrearCitaRequest;
import com.clinicaserena.citas.service.CitaService;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static com.clinicaserena.clinica.ClinicalFixture.APPT_X_ONE_FUTURE;
import static com.clinicaserena.clinica.ClinicalFixture.APPT_Y_ONE_NO_ARRIVAL;
import static com.clinicaserena.clinica.ClinicalFixture.CLOCK_OFFSET_HOURS;
import static com.clinicaserena.clinica.ClinicalFixture.DOCTOR_ONE;
import static com.clinicaserena.clinica.ClinicalFixture.PRACTITIONER_ONE;
import static com.clinicaserena.clinica.ClinicalFixture.RECEPTION;
import static com.clinicaserena.clinica.ClinicalFixture.SPECIALTY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(MutableClock.Config.class)
class ControlledClockBillingIntegrationTest {

    private static final String TEST_PATIENT = "98700000-0000-0000-0000-000000000001";
    private static final String TEST_BLOCK = "98600000-0000-0000-0000-000000000009";
    private static final String ATTENTION = """
            {"reason":"Consulta sintética","findings":"Hallazgo sintético",
             "diagnosis":"Diagnóstico sintético","treatmentPlan":"Seguimiento sintético",
             "prescription":[{"medicine":"Medicamento sintético","dose":"1 unidad",
             "frequency":"cada 12 horas","duration":"5 días","instructions":"Uso sintético"}]}""";

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired ObjectMapper objectMapper;
    @Autowired MutableClock clock;
    @Autowired CitaService citaService;

    private ClinicalFixture fixture;
    private String appointmentId;

    @BeforeEach
    void prepare() {
        fixture = new ClinicalFixture(jdbc);
        fixture.create(passwordEncoder, true);

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime scheduledAt = fixture.base().plusHours(2).withNano(0);
        jdbc.update("INSERT INTO pacientes(id, estado, creado_en) VALUES (?, 'ACTIVO', ?)", TEST_PATIENT, now);
        jdbc.update("INSERT INTO bloques_disponibilidad(id, medico_id, inicio, fin, disponible) VALUES (?, ?, ?, ?, true)",
                TEST_BLOCK, PRACTITIONER_ONE, scheduledAt, scheduledAt.plusMinutes(30));

        // La reserva inicial ocurre antes de avanzar el reloj al inicio del flujo clínico.
        clock.set(scheduledAt.minusMinutes(1).toInstant());
        appointmentId = citaService.reservar(TEST_PATIENT,
                new CrearCitaRequest(PRACTITIONER_ONE, SPECIALTY, scheduledAt, "Reserva sintética")).id();
        clock.set(scheduledAt.plusMinutes(30).toInstant());
    }

    @AfterEach
    void cleanup() {
        if (appointmentId != null) {
            jdbc.execute("ALTER TABLE pagos_citas DISABLE TRIGGER trg_pagos_citas_inmutables");
            jdbc.execute("ALTER TABLE cargos_citas_auditoria DISABLE TRIGGER trg_cargos_citas_auditoria_inmutables");
            try {
                jdbc.update("DELETE FROM pagos_citas WHERE cita_id = ?", appointmentId);
                jdbc.update("DELETE FROM cargos_citas_auditoria WHERE cita_id = ?", appointmentId);
            } finally {
                jdbc.execute("ALTER TABLE pagos_citas ENABLE TRIGGER trg_pagos_citas_inmutables");
                jdbc.execute("ALTER TABLE cargos_citas_auditoria ENABLE TRIGGER trg_cargos_citas_auditoria_inmutables");
            }
            jdbc.execute("ALTER TABLE receta_items DISABLE TRIGGER trg_receta_items_inmutables");
            jdbc.execute("ALTER TABLE atenciones_clinicas DISABLE TRIGGER trg_atenciones_clinicas_inmutables");
            try {
                jdbc.update("DELETE FROM receta_items WHERE atencion_id IN "
                        + "(SELECT id FROM atenciones_clinicas WHERE cita_id = ?)", appointmentId);
                jdbc.update("DELETE FROM atenciones_clinicas WHERE cita_id = ?", appointmentId);
            } finally {
                jdbc.execute("ALTER TABLE receta_items ENABLE TRIGGER trg_receta_items_inmutables");
                jdbc.execute("ALTER TABLE atenciones_clinicas ENABLE TRIGGER trg_atenciones_clinicas_inmutables");
            }
            jdbc.update("DELETE FROM expedientes_clinicos WHERE paciente_id = ?", TEST_PATIENT);
            jdbc.update("DELETE FROM citas WHERE id = ?", appointmentId);
        }
        jdbc.update("DELETE FROM bloques_disponibilidad WHERE id = ?", TEST_BLOCK);
        jdbc.update("DELETE FROM pacientes WHERE id = ?", TEST_PATIENT);
        if (fixture != null) fixture.cleanup();
    }

    @Test
    void flujoCompletoDeAtencionYCobroConRelojControladoYPersistencia() throws Exception {
        String doctor = ClinicalFixture.login(mockMvc, objectMapper, "doctor.one@example.test");
        String reception = ClinicalFixture.login(mockMvc, objectMapper, "reception.clinical@example.test");

        // Permisos cruzados: el paciente no opera recepción ni medicina.
        mockMvc.perform(post("/api/v1/staff/agenda/{id}/arrival", appointmentId)
                        .with(user("synthetic-patient").roles("PACIENTE")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/medico/citas/{id}/atencion", appointmentId)
                        .with(user("synthetic-patient").roles("PACIENTE"))
                        .contentType(MediaType.APPLICATION_JSON).content(ATTENTION))
                .andExpect(status().isForbidden());

        // Permisos exclusivos: recepción no documenta y médico no registra llegada ni cobra.
        mockMvc.perform(post("/api/v1/medico/citas/{id}/atencion", appointmentId)
                        .header("Authorization", "Bearer " + reception)
                        .contentType(MediaType.APPLICATION_JSON).content(ATTENTION))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/staff/agenda/{id}/arrival", appointmentId)
                        .header("Authorization", "Bearer " + doctor))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/staff/billing/appointments/{id}", appointmentId)
                        .header("Authorization", "Bearer " + doctor))
                .andExpect(status().isForbidden());

        // Cobertura conservada de las reglas temporales publicadas.
        mockMvc.perform(post("/api/v1/medico/citas/{id}/atencion", APPT_X_ONE_FUTURE)
                        .header("Authorization", "Bearer " + doctor)
                        .contentType(MediaType.APPLICATION_JSON).content(ATTENTION))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("APPOINTMENT_NOT_STARTED"));
        mockMvc.perform(post("/api/v1/medico/citas/{id}/atencion", APPT_Y_ONE_NO_ARRIVAL)
                        .header("Authorization", "Bearer " + doctor)
                        .contentType(MediaType.APPLICATION_JSON).content(ATTENTION))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ARRIVAL_NOT_REGISTERED"));

        // Paciente reserva; la cita se evalúa como iniciada por MutableClock.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM citas WHERE id = ? AND paciente_id = ?",
                Integer.class, appointmentId, TEST_PATIENT)).isOne();
        mockMvc.perform(post("/api/v1/staff/agenda/{id}/arrival", appointmentId)
                        .header("Authorization", "Bearer " + reception))
                .andExpect(status().isOk()).andExpect(jsonPath("$.arrivalByAccountId").value(RECEPTION));
        assertThat(jdbc.queryForObject("SELECT llegada_por_personal_id FROM citas WHERE id = ?",
                String.class, appointmentId)).isEqualTo(RECEPTION);

        mockMvc.perform(post("/api/v1/medico/citas/{id}/atencion", appointmentId)
                        .header("Authorization", "Bearer " + doctor)
                        .contentType(MediaType.APPLICATION_JSON).content(ATTENTION))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.diagnosis").value("Diagnóstico sintético"))
                .andExpect(jsonPath("$.prescription", org.hamcrest.Matchers.hasSize(1)));

        assertThat(jdbc.queryForObject("SELECT estado FROM citas WHERE id = ?", String.class, appointmentId))
                .isEqualTo("COMPLETADA");
        String attentionId = jdbc.queryForObject("SELECT id FROM atenciones_clinicas WHERE cita_id = ?",
                String.class, appointmentId);
        assertThat(jdbc.queryForObject("SELECT diagnostico FROM atenciones_clinicas WHERE id = ?",
                String.class, attentionId)).isEqualTo("Diagnóstico sintético");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM receta_items WHERE atencion_id = ?",
                Integer.class, attentionId)).isOne();

        mockMvc.perform(get("/api/v1/staff/billing/appointments/{id}", appointmentId)
                        .header("Authorization", "Bearer " + reception))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETADA"))
                .andExpect(jsonPath("$.attended").value(true));
        mockMvc.perform(put("/api/v1/staff/billing/appointments/{id}/charge", appointmentId)
                        .header("Authorization", "Bearer " + reception)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":50000,\"currency\":\"GTQ\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.chargeAmount").value(50000));
        assertThat(jdbc.queryForObject("SELECT monto_nuevo_centavos FROM cargos_citas_auditoria WHERE cita_id = ?",
                Long.class, appointmentId)).isEqualTo(50000L);
        assertThat(jdbc.queryForObject("SELECT monto_centavos FROM citas WHERE id = ?", Long.class, appointmentId))
                .isEqualTo(50000L);

        String partialPayment = "{\"amount\":20000,\"method\":\"EFECTIVO\","
                + "\"reference\":\"Pago sintético parcial\",\"idempotencyKey\":\"controlled-partial\"}";
        mockMvc.perform(post("/api/v1/staff/billing/appointments/{id}/payments", appointmentId)
                        .header("Authorization", "Bearer " + reception)
                        .contentType(MediaType.APPLICATION_JSON).content(partialPayment))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.paidAmount").value(20000))
                .andExpect(jsonPath("$.balanceAmount").value(30000));
        mockMvc.perform(post("/api/v1/staff/billing/appointments/{id}/payments", appointmentId)
                        .header("Authorization", "Bearer " + reception)
                        .contentType(MediaType.APPLICATION_JSON).content(partialPayment))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.paidAmount").value(20000))
                .andExpect(jsonPath("$.balanceAmount").value(30000))
                .andExpect(jsonPath("$.payments", org.hamcrest.Matchers.hasSize(1)));
        mockMvc.perform(get("/api/v1/staff/billing/appointments/{id}", appointmentId)
                        .header("Authorization", "Bearer " + reception))
                .andExpect(status().isOk()).andExpect(jsonPath("$.paidAmount").value(20000))
                .andExpect(jsonPath("$.balanceAmount").value(30000))
                .andExpect(jsonPath("$.payments", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.payments[0].idempotencyKey").value("controlled-partial"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pagos_citas WHERE cita_id = ? AND idempotency_key = ?",
                Integer.class, appointmentId, "controlled-partial")).isOne();
        assertThat(jdbc.queryForObject("SELECT coalesce(sum(monto_centavos), 0) FROM pagos_citas WHERE cita_id = ?",
                Long.class, appointmentId)).isEqualTo(20000L);

        mockMvc.perform(post("/api/v1/staff/billing/appointments/{id}/payments", appointmentId)
                        .header("Authorization", "Bearer " + reception)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":30000,\"method\":\"TRANSFERENCIA\","
                                + "\"reference\":\"Pago sintético final\",\"idempotencyKey\":\"controlled-final\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.paidAmount").value(50000))
                .andExpect(jsonPath("$.balanceAmount").value(0))
                .andExpect(jsonPath("$.payments", org.hamcrest.Matchers.hasSize(2)));
        mockMvc.perform(get("/api/v1/staff/billing/appointments/{id}", appointmentId)
                        .header("Authorization", "Bearer " + reception))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETADA"))
                .andExpect(jsonPath("$.paidAmount").value(50000))
                .andExpect(jsonPath("$.balanceAmount").value(0))
                .andExpect(jsonPath("$.payments", org.hamcrest.Matchers.hasSize(2)));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pagos_citas WHERE cita_id = ?",
                Integer.class, appointmentId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pagos_citas WHERE cita_id = ? AND idempotency_key = ?",
                Integer.class, appointmentId, "controlled-final")).isOne();
        assertThat(jdbc.queryForObject("SELECT coalesce(sum(monto_centavos), 0) FROM pagos_citas WHERE cita_id = ?",
                Long.class, appointmentId)).isEqualTo(50000L);
    }
}
