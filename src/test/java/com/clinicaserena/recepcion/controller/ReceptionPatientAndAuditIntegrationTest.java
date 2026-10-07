package com.clinicaserena.recepcion.controller;

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

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.springframework.http.HttpHeaders.CACHE_CONTROL;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ReceptionPatientAndAuditIntegrationTest {

    private static final String ADMIN_ID = "a1200000-0000-0000-0000-000000000001";
    private static final String RECEPTION_ID = "a1200000-0000-0000-0000-000000000002";
    private static final String REGISTERED_PATIENT_ID = "a1300000-0000-0000-0000-000000000001";
    private static final String HISTORICAL_PATIENT_ID = "a1300000-0000-0000-0000-000000000002";
    private static final String PASSWORD = "ReceptionPatientTest!2026";

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired ObjectMapper objectMapper;

    @BeforeEach
    void prepareFixture() {
        cleanupFixture();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String hash = passwordEncoder.encode(PASSWORD);
        jdbc.update("INSERT INTO cuentas_personal(id,email_normalizado,nombre_completo,password_hash,rol,estado,creado_en,actualizada_en) VALUES (?,?,? ,?,'ADMIN','ACTIVA',?,?)",
                ADMIN_ID, "audit.admin@example.test", "Administrador de bitácora", hash, now, now);
        jdbc.update("INSERT INTO cuentas_personal(id,email_normalizado,nombre_completo,password_hash,rol,estado,creado_en,actualizada_en) VALUES (?,?,? ,?,'RECEPCION','ACTIVA',?,?)",
                RECEPTION_ID, "patients.reception@example.test", "Recepción de pacientes", hash, now, now);
    }

    @AfterEach
    void cleanup() {
        cleanupFixture();
    }

    @Test
    void recepcionCreaBuscaYNoCreaCuentaPaciente() throws Exception {
        String receptionToken = login("patients.reception@example.test");
        String body = "{\"fullName\":\"Paciente administrativo de prueba\",\"phone\":\"5555-2211\",\"email\":\"contacto.admin@example.test\"}";
        String response = mockMvc.perform(post("/api/v1/staff/patients")
                        .header("Authorization", "Bearer " + receptionToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(header().string(CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.recordType").value("EXPEDIENTE_ADMINISTRATIVO"))
                .andExpect(jsonPath("$.patientAccountLinked").value(false))
                .andReturn().getResponse().getContentAsString();
        String patientId = objectMapper.readTree(response).get("patientId").asText();

        mockMvc.perform(get("/api/v1/staff/patients?search=55552211")
                        .header("Authorization", "Bearer " + receptionToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].patientId").value(patientId));
        mockMvc.perform(post("/api/v1/staff/patients")
                        .header("Authorization", "Bearer " + receptionToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PATIENT_RECORD_EXISTS"));
        mockMvc.perform(post("/api/v1/staff/patients")
                        .header("Authorization", "Bearer " + receptionToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"fullName\":\"Sin teléfono válido\",\"phone\":\"12\"}"))
                .andExpect(status().isBadRequest());
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM cuentas_paciente WHERE paciente_id = ?", Integer.class, patientId)).isZero();
    }

    @Test
    void permisosYBitacoraPersistidaSoloParaAdmin() throws Exception {
        String receptionToken = login("patients.reception@example.test");
        mockMvc.perform(post("/api/v1/staff/patients")
                        .header("Authorization", "Bearer " + receptionToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Paciente de auditoría\",\"phone\":\"5555-3322\"}"))
                .andExpect(status().isCreated());
        String adminToken = login("audit.admin@example.test");
        mockMvc.perform(get("/api/v1/staff/audit-events?limit=10")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()).andExpect(header().string(CACHE_CONTROL, "no-store"));
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM bitacora_eventos WHERE actor_id=? AND accion='PATIENT_ADMINISTRATIVE_RECORD_CREATED'",
                Integer.class, RECEPTION_ID)).isPositive();
        mockMvc.perform(get("/api/v1/staff/audit-events").header("Authorization", "Bearer " + receptionToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/staff/patients")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"No permitido\",\"phone\":\"5555-3323\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/staff/patients").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void pacienteYAnonimoNoAccedenAlDirectorio() throws Exception {
        mockMvc.perform(get("/api/v1/staff/patients").with(user("patient").roles("PACIENTE")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/staff/patients")).andExpect(status().isUnauthorized());
    }

    @Test
    void directorioUneOrigenesPorPacienteIdSinDuplicarYPagina() throws Exception {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String hash = passwordEncoder.encode(PASSWORD);
        jdbc.update("INSERT INTO pacientes(id,estado,creado_en) VALUES (?, 'ACTIVO', ?), (?, 'ACTIVO', ?)",
                REGISTERED_PATIENT_ID, now, HISTORICAL_PATIENT_ID, now);
        jdbc.update("INSERT INTO cuentas_paciente(id,paciente_id,email_normalizado,password_hash,estado,creado_en,actualizada_en,email_verificado_en,nombre_completo) VALUES (?,?,?,?, 'ACTIVA',?,?,?,?)",
                "a1400000-0000-0000-0000-000000000001", REGISTERED_PATIENT_ID,
                "autorregistrado@example.test", hash, now, now, now, "Paciente autorregistrado");

        String receptionToken = login("patients.reception@example.test");
        mockMvc.perform(get("/api/v1/staff/patients?limit=1")
                        .header("Authorization", "Bearer " + receptionToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.hasNext").value(true));
        mockMvc.perform(get("/api/v1/staff/patients?search=" + REGISTERED_PATIENT_ID)
                        .header("Authorization", "Bearer " + receptionToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].recordType").value("AUTORREGISTRADO"))
                .andExpect(jsonPath("$.items[0].patientAccountLinked").value(true))
                .andExpect(jsonPath("$.items[0].email").value("autorregistrado@example.test"));
        mockMvc.perform(get("/api/v1/staff/patients?search=" + HISTORICAL_PATIENT_ID)
                        .header("Authorization", "Bearer " + receptionToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].recordType").value("HISTORICO"))
                .andExpect(jsonPath("$.items[0].fullName").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.items[0].phone").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.items[0].email").value(org.hamcrest.Matchers.nullValue()));
    }

    private String login(String email) throws Exception {
        String body = mockMvc.perform(post("/api/v1/staff/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(body);
        return json.get("accessToken").asText();
    }

    private void cleanupFixture() {
        jdbc.update("DELETE FROM sesiones_personal WHERE cuenta_id IN (?, ?)", ADMIN_ID, RECEPTION_ID);
        jdbc.execute("ALTER TABLE bitacora_eventos DISABLE TRIGGER trg_bitacora_eventos_inmutables");
        jdbc.update("DELETE FROM bitacora_eventos WHERE actor_personal_id IN (?, ?)", ADMIN_ID, RECEPTION_ID);
        jdbc.execute("ALTER TABLE bitacora_eventos ENABLE TRIGGER trg_bitacora_eventos_inmutables");
        jdbc.execute("ALTER TABLE observaciones_odontograma DISABLE TRIGGER trg_observaciones_odontograma_inmutables");
        jdbc.update("DELETE FROM observaciones_odontograma WHERE autor_personal_id IN (?, ?)", ADMIN_ID, RECEPTION_ID);
        jdbc.execute("ALTER TABLE observaciones_odontograma ENABLE TRIGGER trg_observaciones_odontograma_inmutables");
        var patientIds = jdbc.queryForList(
                "SELECT paciente_id FROM pacientes_administrativos WHERE creado_por_personal_id IN (?, ?)",
                String.class, ADMIN_ID, RECEPTION_ID);
        jdbc.update("DELETE FROM pacientes_administrativos WHERE creado_por_personal_id IN (?, ?)", ADMIN_ID, RECEPTION_ID);
        for (String patientId : patientIds) jdbc.update("DELETE FROM pacientes WHERE id = ?", patientId);
        jdbc.update("DELETE FROM cuentas_personal WHERE id IN (?, ?)", ADMIN_ID, RECEPTION_ID);
        jdbc.update("DELETE FROM cuentas_paciente WHERE paciente_id IN (?, ?)", REGISTERED_PATIENT_ID, HISTORICAL_PATIENT_ID);
        jdbc.update("DELETE FROM pacientes WHERE id IN (?, ?)", REGISTERED_PATIENT_ID, HISTORICAL_PATIENT_ID);
    }
}
