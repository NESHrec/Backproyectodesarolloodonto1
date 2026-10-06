package com.clinicaserena.odontograma.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@AutoConfigureMockMvc
class OdontogramaIntegrationTest {

    private static final String ADMIN_ID = "b1200000-0000-0000-0000-000000000001";
    private static final String DOCTOR_ID = "b1200000-0000-0000-0000-000000000002";
    private static final String PATIENT_ID = "b1300000-0000-0000-0000-000000000001";
    private static final String OTHER_PATIENT_ID = "b1300000-0000-0000-0000-000000000002";
    private static final String SPECIALTY_ID = "b1400000-0000-0000-0000-000000000001";
    private static final String PRACTITIONER_ID = "b1500000-0000-0000-0000-000000000001";
    private static final String BLOCK_ID = "b1600000-0000-0000-0000-000000000001";
    private static final String APPOINTMENT_ID = "b1700000-0000-0000-0000-000000000001";
    private static final String PASSWORD = "OdontogramTest!2026";

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired ObjectMapper objectMapper;
    @Autowired PlatformTransactionManager transactionManager;

    @BeforeEach
    void prepareFixture() {
        cleanupFixture();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).withNano(0);
        String hash = passwordEncoder.encode(PASSWORD);
        jdbc.update("INSERT INTO cuentas_personal(id,email_normalizado,nombre_completo,password_hash,rol,estado,creado_en,actualizada_en) VALUES (?,?,? ,?,'ADMIN','ACTIVA',?,?)",
                ADMIN_ID, "odontogram.admin@example.test", "Admin odontograma", hash, now, now);
        jdbc.update("INSERT INTO pacientes(id,estado,creado_en) VALUES (?, 'ACTIVO', ?), (?, 'ACTIVO', ?)",
                PATIENT_ID, now, OTHER_PATIENT_ID, now);
        jdbc.update("INSERT INTO especialidades(id,nombre,descripcion) VALUES (?,?,?)", SPECIALTY_ID, "Odontología de prueba", "Fixture aislado");
        jdbc.update("INSERT INTO medicos(id,nombre_completo,especialidad_id,numero_colegiado) VALUES (?,?,?,?)",
                PRACTITIONER_ID, "Profesional odontograma", SPECIALTY_ID, "ODONTO-" + PRACTITIONER_ID);
        jdbc.update("INSERT INTO cuentas_personal(id,email_normalizado,nombre_completo,password_hash,rol,estado,creado_en,actualizada_en,medico_id,medico_vinculado_en,medico_vinculado_por) VALUES (?,?,? ,?,'MEDICO','ACTIVA',?,?,?,?,?)",
                DOCTOR_ID, "odontogram.doctor@example.test", "Doctor odontograma", hash, now, now,
                PRACTITIONER_ID, now, ADMIN_ID);
        OffsetDateTime scheduled = now.minusMinutes(30);
        jdbc.update("INSERT INTO bloques_disponibilidad(id,medico_id,inicio,fin,disponible) VALUES (?,?,?,?,false)",
                BLOCK_ID, PRACTITIONER_ID, scheduled, scheduled.plusMinutes(30));
        jdbc.update("INSERT INTO citas(id,paciente_id,bloque_id,medico_id,especialidad_id,programada_en,estado,llegada_en,llegada_por_personal_id,creada_en,actualizada_en) VALUES (?,?,?,?,?,?,'PENDIENTE',?,?,?,?)",
                APPOINTMENT_ID, PATIENT_ID, BLOCK_ID, PRACTITIONER_ID, SPECIALTY_ID, scheduled, now, ADMIN_ID, now, now);
    }

    @AfterEach
    void cleanup() { cleanupFixture(); }

    @Test
    void registraConsultaYConservaTrazabilidad() throws Exception {
        String token = login();
        mockMvc.perform(post("/api/v1/medico/citas/{id}/odontograma", APPOINTMENT_ID)
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toothNumber\":55,\"surface\":\"OCLUSAL\",\"observation\":\"Restauración temporal observada\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.patientId").value(PATIENT_ID))
                .andExpect(jsonPath("$.appointmentId").value(APPOINTMENT_ID));
        mockMvc.perform(get("/api/v1/medico/pacientes/{id}/odontograma", PATIENT_ID)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.observations[0].toothNumber").value(55))
                .andExpect(jsonPath("$.observations[0].surface").value("OCLUSAL"))
                .andExpect(jsonPath("$.observations[0].recordedByAccountId").value(DOCTOR_ID));
        mockMvc.perform(post("/api/v1/medico/citas/{id}/odontograma", APPOINTMENT_ID)
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toothNumber\":19,\"surface\":\"OCLUSAL\",\"observation\":\"Pieza inválida\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TOOTH_INVALID"));
        mockMvc.perform(get("/api/v1/medico/pacientes/{id}/odontograma", OTHER_PATIENT_ID)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM observaciones_odontograma WHERE cita_id = ?", Integer.class, APPOINTMENT_ID)).isEqualTo(1);
    }

    @Test
    void soloMedicoPuedeUsarElOdontograma() throws Exception {
        mockMvc.perform(get("/api/v1/medico/pacientes/{id}/odontograma", PATIENT_ID)
                        .with(user("patient").roles("PACIENTE"))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/medico/pacientes/{id}/odontograma", PATIENT_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void laBaseRechazaIdentidadesCruzadas() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO observaciones_odontograma(id,paciente_id,cita_id,medico_id,autor_personal_id,pieza_dental,observacion,registrada_en) VALUES (?,?,?,?,?,?,?,?)",
                "b1800000-0000-0000-0000-000000000001", OTHER_PATIENT_ID, APPOINTMENT_ID, PRACTITIONER_ID,
                DOCTOR_ID, 16, "Cruce inválido", OffsetDateTime.now(ZoneOffset.UTC)))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void laObservacionEsperaElBloqueoYVeLaCitaCompletada() throws Exception {
        String token = login();
        CountDownLatch locked = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> completion = executor.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbc.queryForObject("SELECT id FROM citas WHERE id = ? FOR UPDATE", String.class, APPOINTMENT_ID);
            locked.countDown();
            jdbc.update("UPDATE citas SET estado = 'COMPLETADA', actualizada_en = ? WHERE id = ?",
                    OffsetDateTime.now(ZoneOffset.UTC), APPOINTMENT_ID);
        }));
        try {
            assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
            mockMvc.perform(post("/api/v1/medico/citas/{id}/odontograma", APPOINTMENT_ID)
                            .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"toothNumber\":16,\"surface\":\"VESTIBULAR\",\"observation\":\"No debe intercalarse\"}"))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("APPOINTMENT_NOT_DOCUMENTABLE"));
            completion.get(5, TimeUnit.SECONDS);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM observaciones_odontograma WHERE cita_id = ?", Integer.class, APPOINTMENT_ID)).isZero();
        } finally {
            executor.shutdownNow();
        }
    }

    private String login() throws Exception {
        String body = mockMvc.perform(post("/api/v1/staff/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"odontogram.doctor@example.test\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(body);
        return json.get("accessToken").asText();
    }

    private void cleanupFixture() {
        jdbc.update("DELETE FROM sesiones_personal WHERE cuenta_id IN (?, ?)", ADMIN_ID, DOCTOR_ID);
        jdbc.execute("ALTER TABLE observaciones_odontograma DISABLE TRIGGER trg_observaciones_odontograma_inmutables");
        jdbc.update("DELETE FROM observaciones_odontograma WHERE cita_id = ?", APPOINTMENT_ID);
        jdbc.execute("ALTER TABLE observaciones_odontograma ENABLE TRIGGER trg_observaciones_odontograma_inmutables");
        jdbc.update("DELETE FROM citas WHERE id = ?", APPOINTMENT_ID);
        jdbc.update("DELETE FROM bloques_disponibilidad WHERE id = ?", BLOCK_ID);
        jdbc.update("DELETE FROM cuentas_personal WHERE id IN (?, ?)", DOCTOR_ID, ADMIN_ID);
        jdbc.update("DELETE FROM medicos WHERE id = ?", PRACTITIONER_ID);
        jdbc.update("DELETE FROM especialidades WHERE id = ?", SPECIALTY_ID);
        jdbc.update("DELETE FROM pacientes WHERE id IN (?, ?)", PATIENT_ID, OTHER_PATIENT_ID);
    }
}
