package com.clinicaserena.clinica.controller;

import com.clinicaserena.clinica.ClinicalFixture;
import com.clinicaserena.auth.security.TokenHasher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static com.clinicaserena.clinica.ClinicalFixture.*;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PatientPrescriptionIntegrationTest {

    private static final String PRESCRIPTION_ATTENTION = "98700000-0000-0000-0000-000000000001";
    private static final String EMPTY_ATTENTION = "98700000-0000-0000-0000-000000000002";
    private static final String ITEM = "98800000-0000-0000-0000-000000000001";
    private static final String RECORD_X = "98900000-0000-0000-0000-000000000001";

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired ObjectMapper objectMapper;

    private ClinicalFixture fixture;

    @BeforeEach
    void createFixture() {
        fixture = new ClinicalFixture(jdbc);
        fixture.create(passwordEncoder, true);
        OffsetDateTime issuedAt = fixture.base().plusHours(6);
        jdbc.update("INSERT INTO expedientes_clinicos(id,paciente_id,creado_en,creado_por_personal_id) VALUES (?,?,?,?)",
                RECORD_X, PATIENT_X, issuedAt.minusMinutes(5), DOCTOR_ONE);
        attention(PRESCRIPTION_ATTENTION, APPT_X_ONE_PENDING, PRACTITIONER_ONE, issuedAt);
        jdbc.update("INSERT INTO receta_items(id,atencion_id,orden,medicamento,dosis,frecuencia,duracion,indicaciones) "
                        + "VALUES (?,?,1,?,?,?,?,?)", ITEM, PRESCRIPTION_ATTENTION, "Medicamento sintético de receta",
                "1 tableta", "Cada 12 horas", "5 días", "Tomar después de alimentos");
        attention(EMPTY_ATTENTION, APPT_X_TWO_PENDING, PRACTITIONER_TWO, issuedAt.minusMinutes(10));
    }

    @AfterEach
    void cleanupFixture() {
        fixture.cleanup();
    }

    @Test
    void patientSeesOnlyOwnPersistedPrescriptionsAndIgnoresPatientIdQuery() throws Exception {
        String token = patientToken(PATIENT_X);

        mockMvc.perform(get("/api/v1/pacientes/me/recetas")
                        .queryParam("patientId", PATIENT_Y)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(PRESCRIPTION_ATTENTION))
                .andExpect(jsonPath("$[0].appointmentId").value(APPT_X_ONE_PENDING))
                .andExpect(jsonPath("$[0].appointmentScheduledAt").isString())
                .andExpect(jsonPath("$[0].issuedAt").isString())
                .andExpect(jsonPath("$[0].practitionerId").value(PRACTITIONER_ONE))
                .andExpect(jsonPath("$[0].practitionerName").value("Profesional uno de prueba"))
                .andExpect(jsonPath("$[0].items", hasSize(1)))
                .andExpect(jsonPath("$[0].items[0].medicine").value("Medicamento sintético de receta"))
                .andExpect(jsonPath("$[0].items[0].instructions").value("Tomar después de alimentos"))
                .andExpect(jsonPath("$[0].patientId").doesNotExist())
                .andExpect(jsonPath("$[0].diagnosis").doesNotExist());

        mockMvc.perform(get("/api/v1/pacientes/{patientId}/recetas", PATIENT_Y)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void patientWithoutPrescriptionGetsEmptyList() throws Exception {
        mockMvc.perform(get("/api/v1/pacientes/me/recetas")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + patientToken(PATIENT_Y)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(content().json("[]"));
    }

    @Test
    void unauthenticatedAndStaffRolesAreRejected() throws Exception {
        mockMvc.perform(get("/api/v1/pacientes/me/recetas"))
                .andExpect(status().isUnauthorized());
        String staffToken = ClinicalFixture.login(mockMvc, objectMapper, "admin.clinical@example.test");
        mockMvc.perform(get("/api/v1/pacientes/me/recetas")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + staffToken))
                .andExpect(status().isForbidden());
    }

    private void attention(String id, String appointmentId, String practitionerId, OffsetDateTime issuedAt) {
        jdbc.update("INSERT INTO atenciones_clinicas(id,cita_id,expediente_id,paciente_id,medico_id,autor_personal_id,"
                        + "motivo_consulta,diagnostico,registrada_en) VALUES (?,?,?,?,?,?,?, ?,?)",
                id, appointmentId, RECORD_X, PATIENT_X, practitionerId, DOCTOR_ONE,
                "Consulta sintética", "Diagnóstico sintético", issuedAt);
    }

    private String patientToken(String patientId) {
        String rawToken = "patient-prescription-" + UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String accountId = patientId.replace("98300000", "98400000");
        jdbc.update("INSERT INTO sesiones_paciente(id,cuenta_id,token_hash,expira_en,creada_en) VALUES (?,?,?,?,?)",
                UUID.randomUUID().toString(), accountId, TokenHasher.sha256(rawToken), now.plusMinutes(30), now);
        return rawToken;
    }
}
