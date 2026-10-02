package com.clinicaserena.clinica.controller;

import com.clinicaserena.auth.security.TokenHasher;
import com.clinicaserena.clinica.ClinicalFixture;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static com.clinicaserena.clinica.ClinicalFixture.*;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PatientPortalIntegrationTest {

    private static final String RECORD_X = "98900000-0000-0000-0000-000000000001";
    private static final String ATTENTION_X = "98700000-0000-0000-0000-000000000001";
    private static final String ITEM_X = "98800000-0000-0000-0000-000000000001";

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired ObjectMapper objectMapper;

    private ClinicalFixture fixture;

    @BeforeEach
    void createFixture() {
        fixture = new ClinicalFixture(jdbc);
        fixture.create(passwordEncoder, true);
    }

    @AfterEach
    void cleanupFixture() {
        fixture.cleanup();
    }

    @Test
    void patientReadsAndUpdatesOnlyOwnProfileAndChangePersists() throws Exception {
        String token = patientToken(PATIENT_X);

        mockMvc.perform(get("/api/v1/pacientes/me/perfil")
                        .queryParam("patientId", PATIENT_Y)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.patientId").value(PATIENT_X))
                .andExpect(jsonPath("$.fullName").value("Paciente X sintético"))
                .andExpect(jsonPath("$.email").value("patient.x@example.test"))
                .andExpect(jsonPath("$.accountStatus").value("ACTIVA"))
                .andExpect(jsonPath("$.registeredAt").isString());

        mockMvc.perform(patch("/api/v1/pacientes/me/perfil")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .content("{\"fullName\":\"Paciente X actualizado\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patientId").value(PATIENT_X))
                .andExpect(jsonPath("$.fullName").value("Paciente X actualizado"));

        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "SELECT nombre_completo FROM cuentas_paciente WHERE paciente_id = ?", String.class, PATIENT_X))
                .isEqualTo("Paciente X actualizado");

        mockMvc.perform(get("/api/v1/pacientes/me/perfil")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Paciente X actualizado"));
    }

    @Test
    void profileRejectsInvalidAndCrossPatientFields() throws Exception {
        String token = patientToken(PATIENT_X);

        mockMvc.perform(patch("/api/v1/pacientes/me/perfil")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .content("{\"fullName\":\"   \"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(patch("/api/v1/pacientes/me/perfil")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .content("{\"fullName\":\"Paciente X\",\"patientId\":\"" + PATIENT_Y + "\"}"))
                .andExpect(status().isBadRequest());

        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "SELECT nombre_completo FROM cuentas_paciente WHERE paciente_id = ?", String.class, PATIENT_Y))
                .isEqualTo("Paciente Y sintético");
    }

    @Test
    void patientSeesOnlyPersistedOwnCheckupsAndNoFutureFollowupIsInvented() throws Exception {
        mockMvc.perform(get("/api/v1/pacientes/me/chequeos")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + patientToken(PATIENT_Y)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(content().json("[]"));

        OffsetDateTime recordedAt = fixture.base().plusHours(6);
        jdbc.update("INSERT INTO expedientes_clinicos(id,paciente_id,creado_en,creado_por_personal_id) VALUES (?,?,?,?)",
                RECORD_X, PATIENT_X, recordedAt.minusMinutes(5), DOCTOR_ONE);
        jdbc.update("INSERT INTO atenciones_clinicas(id,cita_id,expediente_id,paciente_id,medico_id,autor_personal_id,"
                        + "motivo_consulta,hallazgos,diagnostico,plan_tratamiento,registrada_en) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                ATTENTION_X, APPT_X_ONE_PENDING, RECORD_X, PATIENT_X, PRACTITIONER_ONE, DOCTOR_ONE,
                "Consulta sintética", "Hallazgos persistidos", "Diagnóstico persistido",
                "Plan persistido", recordedAt);
        jdbc.update("INSERT INTO receta_items(id,atencion_id,orden,medicamento,dosis,frecuencia,duracion,indicaciones) "
                        + "VALUES (?,?,1,?,?,?,?,?)", ITEM_X, ATTENTION_X, "Medicamento persistido",
                "1 tableta", "Cada 12 horas", "5 días", "Indicación persistida");

        mockMvc.perform(get("/api/v1/pacientes/me/chequeos")
                        .queryParam("patientId", PATIENT_Y)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + patientToken(PATIENT_X)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(ATTENTION_X))
                .andExpect(jsonPath("$[0].appointmentId").value(APPT_X_ONE_PENDING))
                .andExpect(jsonPath("$[0].practitionerName").value("Profesional uno de prueba"))
                .andExpect(jsonPath("$[0].reason").value("Consulta sintética"))
                .andExpect(jsonPath("$[0].findings").value("Hallazgos persistidos"))
                .andExpect(jsonPath("$[0].diagnosis").value("Diagnóstico persistido"))
                .andExpect(jsonPath("$[0].treatmentPlan").value("Plan persistido"))
                .andExpect(jsonPath("$[0].prescription", hasSize(1)))
                .andExpect(jsonPath("$[0].prescription[0].medicine").value("Medicamento persistido"))
                .andExpect(jsonPath("$[0].patientId").doesNotExist())
                .andExpect(jsonPath("$[0].nextReview").doesNotExist())
                .andExpect(jsonPath("$[0].progress").doesNotExist());
    }

    @Test
    void portalRequiresPatientRoleAndSession() throws Exception {
        mockMvc.perform(get("/api/v1/pacientes/me/perfil"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/pacientes/me/chequeos"))
                .andExpect(status().isUnauthorized());

        for (String email : new String[]{
                "admin.clinical@example.test",
                "reception.clinical@example.test",
                "doctor.one@example.test"}) {
            String staffToken = ClinicalFixture.login(mockMvc, objectMapper, email);
            mockMvc.perform(get("/api/v1/pacientes/me/perfil")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + staffToken))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/v1/pacientes/me/chequeos")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + staffToken))
                    .andExpect(status().isForbidden());
        }
    }

    private String patientToken(String patientId) {
        String rawToken = "patient-portal-" + UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String accountId = patientId.replace("98300000", "98400000");
        jdbc.update("INSERT INTO sesiones_paciente(id,cuenta_id,token_hash,expira_en,creada_en) VALUES (?,?,?,?,?)",
                UUID.randomUUID().toString(), accountId, TokenHasher.sha256(rawToken), now.plusMinutes(30), now);
        return rawToken;
    }
}
