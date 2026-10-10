package com.clinicaserena.recepcion.controller;

import com.clinicaserena.auditoria.service.BitacoraService;
import com.clinicaserena.auth.security.PatientPrincipal;
import com.clinicaserena.staff.entity.RolPersonal;
import com.clinicaserena.staff.security.StaffPrincipal;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PatientLinkAuditIntegrationTest {

    private static final String RECEPTION = "b1100000-0000-0000-0000-000000000001";
    private static final String DOCTOR = "b1100000-0000-0000-0000-000000000002";
    private static final String ADMIN_PATIENT_A = "b1200000-0000-0000-0000-000000000001";
    private static final String ADMIN_PATIENT_B = "b1200000-0000-0000-0000-000000000002";
    private static final String PATIENT = "b1200000-0000-0000-0000-000000000003";
    private static final String PATIENT_ACCOUNT = "b1300000-0000-0000-0000-000000000003";
    private static final String FIXTURE_AUDIT_EVENT = "b1400000-0000-0000-0000-000000000001";
    private static final String OTHER_ACTOR_AUDIT_EVENT = "b1400000-0000-0000-0000-000000000002";
    private static final String AUDIT_TRIGGER = "trg_bitacora_eventos_inmutables";

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper objectMapper;
    @MockitoSpyBean BitacoraService audit;

    @BeforeEach
    void prepare() {
        reset(audit);
        cleanupFixture();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).withNano(0);
        jdbc.update("INSERT INTO cuentas_personal(id,email_normalizado,nombre_completo,password_hash,rol,estado,creado_en,actualizada_en) "
                        + "VALUES (?,?,?,'hash-sintetico','RECEPCION','ACTIVA',?,?), "
                        + "(?,?,?,'hash-sintetico','MEDICO','ACTIVA',?,?)",
                RECEPTION, "audit.link.reception@example.test", "Recepción sintética", now, now,
                DOCTOR, "audit.link.doctor@example.test", "Médico sintético", now, now);
        jdbc.update("INSERT INTO pacientes(id,estado,creado_en) VALUES (?, 'ACTIVO', ?), (?, 'ACTIVO', ?), (?, 'ACTIVO', ?)",
                ADMIN_PATIENT_A, now, ADMIN_PATIENT_B, now, PATIENT, now);
        jdbc.update("INSERT INTO pacientes_administrativos(paciente_id,nombre_completo,telefono,telefono_normalizado,email_contacto,email_contacto_normalizado,creado_por_personal_id,creado_en) "
                        + "VALUES (?,?,?,?,?,?,?,?), (?,?,?,?,?,?,?,?)",
                ADMIN_PATIENT_A, "Paciente administrativo A", "+502 5000 0001", "50250000001",
                "admin.a@example.test", "admin.a@example.test", RECEPTION, now,
                ADMIN_PATIENT_B, "Paciente administrativo B", "+502 5000 0002", "50250000002",
                "admin.b@example.test", "admin.b@example.test", RECEPTION, now);
        jdbc.update("INSERT INTO cuentas_paciente(id,paciente_id,email_normalizado,password_hash,estado,creado_en,actualizada_en,email_verificado_en,nombre_completo) "
                        + "VALUES (?,?,?,'hash-sintetico','ACTIVA',?,?,?,?)",
                PATIENT_ACCOUNT, PATIENT, "linked.patient@example.test", now, now, now, "Paciente vinculado sintético");
    }

    @AfterEach
    void cleanup() {
        cleanupFixture();
    }

    @Test
    void iniciarRevocarYConfirmarPersistenUnEventoConActorYRecurso() throws Exception {
        JsonNode first = initiate(ADMIN_PATIENT_A);
        String firstRequest = first.get("requestId").asText();
        mockMvc.perform(delete("/api/v1/staff/patient-link-requests/{id}", firstRequest)
                        .with(staff(RECEPTION, RolPersonal.RECEPCION)))
                .andExpect(status().isNoContent());

        JsonNode second = initiate(ADMIN_PATIENT_B);
        String secondRequest = second.get("requestId").asText();
        mockMvc.perform(post("/api/v1/pacientes/me/administrative-link")
                        .with(patient()).contentType("application/json")
                        .content("{\"requestId\":\"" + secondRequest + "\",\"code\":\"" + second.get("oneTimeCode").asText() + "\"}"))
                .andExpect(status().isNoContent());

        assertStaffEvent("PATIENT_LINK_INITIATED", "PACIENTE_ADMINISTRATIVO", ADMIN_PATIENT_A, RECEPTION);
        assertStaffEvent("PATIENT_LINK_INITIATED", "PACIENTE_ADMINISTRATIVO", ADMIN_PATIENT_B, RECEPTION);
        assertStaffEvent("PATIENT_LINK_REVOKED", "SOLICITUD_VINCULACION", firstRequest, RECEPTION);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bitacora_eventos WHERE accion='PATIENT_LINK_CONFIRMED' "
                        + "AND actor_tipo='PACIENTE' AND actor_id=? AND actor_rol='PACIENTE' "
                        + "AND entidad_tipo='PACIENTE' AND entidad_id=?",
                Integer.class, PATIENT_ACCOUNT, PATIENT)).isOne();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bitacora_eventos WHERE accion='PATIENT_LINK_INITIATED'",
                Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bitacora_eventos WHERE accion='PATIENT_LINK_REVOKED'",
                Integer.class)).isOne();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bitacora_eventos WHERE accion='PATIENT_LINK_CONFIRMED'",
                Integer.class)).isOne();

        mockMvc.perform(post("/api/v1/staff/patients/{id}/link-requests", ADMIN_PATIENT_A)
                        .with(staff(DOCTOR, RolPersonal.MEDICO)).contentType("application/json")
                        .content("{\"identityVerifiedInPerson\":true}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/pacientes/me/administrative-link")
                        .with(patient()).contentType("application/json")
                        .content("{\"requestId\":\"" + firstRequest + "\",\"code\":\"00000000\"}"))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bitacora_eventos WHERE accion LIKE 'PATIENT_LINK_%'",
                Integer.class)).isEqualTo(4);
    }

    @Test
    void falloDeBitacoraRevierteInicio() throws Exception {
        doThrow(new IllegalStateException("fallo sintético de bitácora"))
                .when(audit).record(any(StaffPrincipal.class), eq("PATIENT_LINK_INITIATED"),
                        eq("PACIENTE_ADMINISTRATIVO"), eq(ADMIN_PATIENT_A), any(OffsetDateTime.class));
        mockMvc.perform(post("/api/v1/staff/patients/{id}/link-requests", ADMIN_PATIENT_A)
                        .with(staff(RECEPTION, RolPersonal.RECEPCION)).contentType("application/json")
                        .content("{\"identityVerifiedInPerson\":true}"))
                .andExpect(status().is5xxServerError());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM solicitudes_vinculacion_paciente WHERE paciente_administrativo_id=?",
                Integer.class, ADMIN_PATIENT_A)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bitacora_eventos WHERE accion='PATIENT_LINK_INITIATED'",
                Integer.class)).isZero();
    }

    @Test
    void falloDeBitacoraRevierteRevocacion() throws Exception {
        String requestId = initiate(ADMIN_PATIENT_A).get("requestId").asText();
        reset(audit);
        doThrow(new IllegalStateException("fallo sintético de bitácora"))
                .when(audit).record(any(StaffPrincipal.class), eq("PATIENT_LINK_REVOKED"),
                        eq("SOLICITUD_VINCULACION"), eq(requestId), any(OffsetDateTime.class));
        mockMvc.perform(delete("/api/v1/staff/patient-link-requests/{id}", requestId)
                        .with(staff(RECEPTION, RolPersonal.RECEPCION)))
                .andExpect(status().is5xxServerError());
        assertThat(jdbc.queryForObject("SELECT estado FROM solicitudes_vinculacion_paciente WHERE id=?",
                String.class, requestId)).isEqualTo("PENDIENTE");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bitacora_eventos WHERE accion='PATIENT_LINK_REVOKED'",
                Integer.class)).isZero();
    }

    @Test
    void falloDeBitacoraRevierteConfirmacion() throws Exception {
        JsonNode challenge = initiate(ADMIN_PATIENT_A);
        String requestId = challenge.get("requestId").asText();
        reset(audit);
        doThrow(new IllegalStateException("fallo sintético de bitácora"))
                .when(audit).recordPatient(any(PatientPrincipal.class), eq("PATIENT_LINK_CONFIRMED"),
                        eq("PACIENTE"), eq(PATIENT), any(OffsetDateTime.class));
        mockMvc.perform(post("/api/v1/pacientes/me/administrative-link")
                        .with(patient()).contentType("application/json")
                        .content("{\"requestId\":\"" + requestId + "\",\"code\":\"" + challenge.get("oneTimeCode").asText() + "\"}"))
                .andExpect(status().is5xxServerError());
        assertThat(jdbc.queryForObject("SELECT estado FROM solicitudes_vinculacion_paciente WHERE id=?",
                String.class, requestId)).isEqualTo("PENDIENTE");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pacientes_administrativos WHERE paciente_id=?",
                Integer.class, ADMIN_PATIENT_A)).isOne();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pacientes_administrativos WHERE paciente_id=?",
                Integer.class, PATIENT)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bitacora_eventos WHERE accion='PATIENT_LINK_CONFIRMED'",
                Integer.class)).isZero();
    }

    @Test
    void limpiezaDeBitacoraConservaEventosDeOtrosActoresYReactivaTrigger() {
        insertAuditEvent(FIXTURE_AUDIT_EVENT, "PERSONAL", RECEPTION, "RECEPCION");
        insertAuditEvent(OTHER_ACTOR_AUDIT_EVENT, "PERSONAL", DOCTOR, "MEDICO");

        try {
            deleteFixtureActorAuditEvents();

            assertThat(jdbc.queryForObject("SELECT count(*) FROM bitacora_eventos WHERE id=?",
                    Integer.class, FIXTURE_AUDIT_EVENT)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM bitacora_eventos WHERE id=? AND actor_id=?",
                    Integer.class, OTHER_ACTOR_AUDIT_EVENT, DOCTOR)).isOne();
            assertAuditTriggerEnabled();
        } finally {
            deleteAuditEventById(OTHER_ACTOR_AUDIT_EVENT);
        }
    }

    @Test
    void falloControladoDeLimpiezaReactivaTrigger() {
        // La clase no es transaccional: cada sentencia de JdbcTemplate confirma o
        // revierte por separado, por lo que el finally puede usar una transacción sana.
        assertThatThrownBy(() -> withAuditTriggerDisabled(
                () -> jdbc.update("DELETE FROM bitacora_eventos WHERE 1 / 0 = 0")))
                .isInstanceOf(DataAccessException.class);

        assertAuditTriggerEnabled();
    }

    private JsonNode initiate(String patientId) throws Exception {
        String response = mockMvc.perform(post("/api/v1/staff/patients/{id}/link-requests", patientId)
                        .with(staff(RECEPTION, RolPersonal.RECEPCION)).contentType("application/json")
                        .content("{\"identityVerifiedInPerson\":true}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }

    private void assertStaffEvent(String action, String entityType, String entityId, String actorId) {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bitacora_eventos WHERE accion=? "
                        + "AND actor_tipo='PERSONAL' AND actor_id=? AND actor_rol='RECEPCION' "
                        + "AND entidad_tipo=? AND entidad_id=?",
                Integer.class, action, actorId, entityType, entityId)).isOne();
    }

    private RequestPostProcessor staff(String accountId, RolPersonal role) {
        StaffPrincipal principal = new StaffPrincipal(accountId, "synthetic@example.test", "Actor sintético", role, "hash");
        return authentication(new UsernamePasswordAuthenticationToken(principal, null,
                List.of(() -> "ROLE_" + role.name())));
    }

    private RequestPostProcessor patient() {
        PatientPrincipal principal = new PatientPrincipal(PATIENT_ACCOUNT, PATIENT,
                "linked.patient@example.test", "ACTIVA", "hash");
        return authentication(new UsernamePasswordAuthenticationToken(principal, null,
                List.of(() -> "ROLE_PACIENTE")));
    }

    private void insertAuditEvent(String eventId, String actorType, String actorId, String actorRole) {
        jdbc.update("INSERT INTO bitacora_eventos(id,actor_personal_id,accion,entidad_tipo,entidad_id,ocurrido_en,"
                        + "actor_tipo,actor_id,actor_rol) VALUES (?,?,?,?,?,?,?,?,?)",
                eventId, "PERSONAL".equals(actorType) ? actorId : null, "SYNTHETIC_CLEANUP_TEST",
                "FIXTURE", eventId, OffsetDateTime.now(ZoneOffset.UTC).withNano(0), actorType, actorId, actorRole);
    }

    private void deleteFixtureActorAuditEvents() {
        withAuditTriggerDisabled(() -> jdbc.update("DELETE FROM bitacora_eventos "
                        + "WHERE (actor_tipo='PERSONAL' AND actor_id=?) "
                        + "OR (actor_tipo='PACIENTE' AND actor_id=?)",
                RECEPTION, PATIENT_ACCOUNT));
    }

    private void deleteAuditEventById(String eventId) {
        withAuditTriggerDisabled(() -> jdbc.update("DELETE FROM bitacora_eventos WHERE id=?", eventId));
    }

    private void withAuditTriggerDisabled(Runnable operation) {
        jdbc.execute("ALTER TABLE bitacora_eventos DISABLE TRIGGER " + AUDIT_TRIGGER);
        try {
            operation.run();
        } finally {
            jdbc.execute("ALTER TABLE bitacora_eventos ENABLE TRIGGER " + AUDIT_TRIGGER);
        }
    }

    private void assertAuditTriggerEnabled() {
        assertThat(jdbc.queryForObject("SELECT tgenabled::text FROM pg_trigger "
                        + "WHERE tgrelid='bitacora_eventos'::regclass AND tgname=?",
                String.class, AUDIT_TRIGGER)).isEqualTo("O");
    }

    private void cleanupFixture() {
        deleteFixtureActorAuditEvents();
        deleteAuditEventById(OTHER_ACTOR_AUDIT_EVENT);
        assertAuditTriggerEnabled();
        jdbc.update("DELETE FROM solicitudes_vinculacion_paciente WHERE creado_por_personal_id=?", RECEPTION);
        jdbc.update("DELETE FROM pacientes_administrativos WHERE creado_por_personal_id=?", RECEPTION);
        jdbc.update("DELETE FROM cuentas_paciente WHERE id=?", PATIENT_ACCOUNT);
        jdbc.update("DELETE FROM pacientes WHERE id IN (?,?,?)", ADMIN_PATIENT_A, ADMIN_PATIENT_B, PATIENT);
        jdbc.update("DELETE FROM cuentas_personal WHERE id IN (?,?)", RECEPTION, DOCTOR);
    }
}
