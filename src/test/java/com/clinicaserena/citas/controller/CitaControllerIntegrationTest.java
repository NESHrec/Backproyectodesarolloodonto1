package com.clinicaserena.citas.controller;

import com.clinicaserena.auditoria.service.BitacoraService;
import com.clinicaserena.auth.security.PatientPrincipal;
import com.clinicaserena.citas.dto.CrearCitaRequest;
import com.clinicaserena.citas.service.CitaService;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

@SpringBootTest
@AutoConfigureMockMvc
class CitaControllerIntegrationTest {

    private static final String PATIENT_A = "90000000-0000-0000-0000-000000000101";
    private static final String PATIENT_B = "90000000-0000-0000-0000-000000000102";
    private static final String ACCOUNT_A = "91000000-0000-0000-0000-000000000101";
    private static final String PASSWORD = "Tmp-" + UUID.randomUUID() + "!Aa1";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired CitaService citaService;
    @MockitoSpyBean BitacoraService audit;

    private String specialtyId;
    private String practitionerId;
    private String blockAId;
    private String blockBId;
    private OffsetDateTime slotA;
    private OffsetDateTime slotB;

    @BeforeEach
    void prepararFixtureAislado() {
        reset(audit);
        cleanupAudit();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        jdbc.update("INSERT INTO pacientes(id, estado, creado_en) VALUES (?, 'ACTIVO', ?)", PATIENT_A, now);
        jdbc.update("INSERT INTO pacientes(id, estado, creado_en) VALUES (?, 'ACTIVO', ?)", PATIENT_B, now);
        jdbc.update("""
                INSERT INTO cuentas_paciente(id, paciente_id, email_normalizado, password_hash, estado, creado_en, actualizada_en, email_verificado_en)
                VALUES (?, ?, ?, ?, 'ACTIVA', ?, ?, ?)
                """, ACCOUNT_A, PATIENT_A, "cita.prueba@example.test", passwordEncoder.encode(PASSWORD), now, now, now);

        specialtyId = UUID.randomUUID().toString();
        practitionerId = UUID.randomUUID().toString();
        blockAId = UUID.randomUUID().toString();
        blockBId = UUID.randomUUID().toString();
        slotA = now.plusDays(20).withNano(0);
        slotB = slotA.plusHours(1);
        jdbc.update("INSERT INTO especialidades(id, nombre, descripcion) VALUES (?, ?, ?)", specialtyId, "Prueba", "Prueba");
        jdbc.update("INSERT INTO medicos(id, nombre_completo, especialidad_id, numero_colegiado) VALUES (?, ?, ?, ?)", practitionerId, "Profesional prueba", specialtyId, "TEST-" + practitionerId);
        jdbc.update("INSERT INTO bloques_disponibilidad(id, medico_id, inicio, fin, disponible) VALUES (?, ?, ?, ?, true)", blockAId, practitionerId, slotA, slotA.plusMinutes(30));
        jdbc.update("INSERT INTO bloques_disponibilidad(id, medico_id, inicio, fin, disponible) VALUES (?, ?, ?, ?, true)", blockBId, practitionerId, slotB, slotB.plusMinutes(30));
    }

    @AfterEach
    void limpiarFixtureAislado() {
        cleanupAudit();
        if (blockAId != null) jdbc.update("DELETE FROM citas WHERE bloque_id IN (?, ?)", blockAId, blockBId);
        if (blockAId != null) jdbc.update("DELETE FROM bloques_disponibilidad WHERE id IN (?, ?)", blockAId, blockBId);
        if (practitionerId != null) jdbc.update("DELETE FROM medicos WHERE id = ?", practitionerId);
        if (specialtyId != null) jdbc.update("DELETE FROM especialidades WHERE id = ?", specialtyId);
        jdbc.update("DELETE FROM sesiones_paciente WHERE cuenta_id = ?", ACCOUNT_A);
        jdbc.update("DELETE FROM cuentas_paciente WHERE id = ?", ACCOUNT_A);
        jdbc.update("DELETE FROM pacientes WHERE id IN (?, ?)", PATIENT_A, PATIENT_B);
    }

    @Test
    void creaYListaSoloLaCitaDelPacienteAutenticado() throws Exception {
        String token = login();
        String request = """
                {"practitionerId":"%s","specialtyId":"%s","scheduledAt":"%s","notes":"Consulta"}
                """.formatted(practitionerId, specialtyId, slotA);

        mockMvc.perform(post("/api/v1/citas")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.patientId").value(PATIENT_A))
                .andExpect(jsonPath("$.practitionerId").value(practitionerId));

        String appointmentId = jdbc.queryForObject("SELECT id FROM citas WHERE bloque_id = ?", String.class, blockAId);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bitacora_eventos WHERE actor_tipo = 'PACIENTE' "
                + "AND actor_id = ? AND accion = 'APPOINTMENT_BOOKED' AND entidad_tipo = 'CITA' AND entidad_id = ?",
                Integer.class, ACCOUNT_A, appointmentId)).isOne();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bitacora_eventos b WHERE CAST(b AS text) LIKE ?",
                Integer.class, "%Consulta%")).isZero();

        citaService.reservar(PATIENT_B, new CrearCitaRequest(practitionerId, specialtyId, slotB, null));

        mockMvc.perform(get("/api/v1/pacientes/me/citas").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].patientId").value(PATIENT_A));
    }

    @Test
    void rechazaPatientIdAdicionalEnJsonConErrorSeguro() throws Exception {
        String token = login();
        String request = """
                {"patientId":"%s","practitionerId":"%s","specialtyId":"%s","scheduledAt":"%s"}
                """.formatted(PATIENT_B, practitionerId, specialtyId, slotA);

        mockMvc.perform(post("/api/v1/citas")
                        .queryParam("patientId", PATIENT_B)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mockMvc.perform(get("/api/v1/pacientes/me/citas")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bitacora_eventos WHERE accion = 'APPOINTMENT_BOOKED'",
                Integer.class)).isZero();
    }

    @Test
    void falloDeBitacoraRevierteLaReserva() throws Exception {
        String token = login();
        doThrow(new IllegalStateException("fallo sintético de bitácora"))
                .when(audit).recordPatient(any(PatientPrincipal.class), eq("APPOINTMENT_BOOKED"),
                        eq("CITA"), any(String.class), any(OffsetDateTime.class));

        mockMvc.perform(post("/api/v1/citas")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"practitionerId\":\"" + practitionerId + "\",\"specialtyId\":\""
                                + specialtyId + "\",\"scheduledAt\":\"" + slotA + "\"}"))
                .andExpect(status().is5xxServerError());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM citas WHERE bloque_id = ?", Integer.class, blockAId)).isZero();
        assertThat(jdbc.queryForObject("SELECT disponible FROM bloques_disponibilidad WHERE id = ?",
                Boolean.class, blockAId)).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bitacora_eventos WHERE accion = 'APPOINTMENT_BOOKED'",
                Integer.class)).isZero();
    }

    @Test
    void ignoraPatientIdManipuladoEnParametrosYNoExponeRutaDeOtroPaciente() throws Exception {
        String token = login();
        String request = """
                {"practitionerId":"%s","specialtyId":"%s","scheduledAt":"%s"}
                """.formatted(practitionerId, specialtyId, slotA);

        mockMvc.perform(post("/api/v1/citas")
                        .queryParam("patientId", PATIENT_B)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.patientId").value(PATIENT_A));

        mockMvc.perform(get("/api/v1/pacientes/me/citas")
                        .queryParam("patientId", PATIENT_B)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].patientId").value(PATIENT_A));

        mockMvc.perform(get("/api/v1/pacientes/{patientId}/citas", PATIENT_B)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void rechazaCitasYListadoSinBearer() throws Exception {
        mockMvc.perform(post("/api/v1/citas").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/pacientes/me/citas"))
                .andExpect(status().isUnauthorized());
    }

    private String login() throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"cita.prueba@example.test\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode response = objectMapper.readTree(body);
        return response.get("accessToken").asText();
    }

    private void cleanupAudit() {
        jdbc.execute("ALTER TABLE bitacora_eventos DISABLE TRIGGER trg_bitacora_eventos_inmutables");
        jdbc.update("DELETE FROM bitacora_eventos WHERE actor_id = ?", ACCOUNT_A);
        jdbc.execute("ALTER TABLE bitacora_eventos ENABLE TRIGGER trg_bitacora_eventos_inmutables");
    }
}
