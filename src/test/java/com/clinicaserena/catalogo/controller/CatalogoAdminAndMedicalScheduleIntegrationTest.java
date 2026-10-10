package com.clinicaserena.catalogo.controller;

import com.clinicaserena.auditoria.service.BitacoraService;
import com.clinicaserena.clinica.MutableClock;
import com.clinicaserena.staff.security.StaffPrincipal;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(MutableClock.Config.class)
class CatalogoAdminAndMedicalScheduleIntegrationTest {

    private static final String ADMIN = "a2000000-0000-0000-0000-000000000001";
    private static final String RECEPTION = "a2000000-0000-0000-0000-000000000002";
    private static final String DOCTOR_A = "a2000000-0000-0000-0000-000000000003";
    private static final String DOCTOR_B = "a2000000-0000-0000-0000-000000000004";
    private static final String UNLINKED = "a2000000-0000-0000-0000-000000000005";
    private static final String PATIENT = "a2100000-0000-0000-0000-000000000001";
    private static final String SPECIALTY = "a2200000-0000-0000-0000-000000000001";
    private static final String PRACTITIONER_A = "a2300000-0000-0000-0000-000000000001";
    private static final String PRACTITIONER_B = "a2300000-0000-0000-0000-000000000002";
    private static final String PASSWORD = "TandaDosTestPassword!2026";

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired ObjectMapper objectMapper;
    @Autowired MutableClock clock;
    @MockitoSpyBean BitacoraService audit;

    @BeforeEach
    void prepareFixture() {
        reset(audit);
        cleanupFixture();
        clock.set(OffsetDateTime.of(2026, 10, 1, 15, 0, 0, 0, ZoneOffset.UTC).toInstant());
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).withNano(0);
        String hash = passwordEncoder.encode(PASSWORD);
        staff(ADMIN, "t2.admin@example.test", "Admin Tanda 2", "ADMIN", hash, now);
        staff(RECEPTION, "t2.reception@example.test", "Recepción Tanda 2", "RECEPCION", hash, now);
        staff(DOCTOR_A, "t2.doctor.a@example.test", "Médico A Tanda 2", "MEDICO", hash, now);
        staff(DOCTOR_B, "t2.doctor.b@example.test", "Médico B Tanda 2", "MEDICO", hash, now);
        staff(UNLINKED, "t2.unlinked@example.test", "Médico sin vínculo Tanda 2", "MEDICO", hash, now);
        jdbc.update("INSERT INTO especialidades(id,nombre,descripcion) VALUES (?,?,?)",
                SPECIALTY, "Especialidad Tanda Dos", "Especialidad sintética");
        practitioner(PRACTITIONER_A, "Profesional A Tanda 2");
        practitioner(PRACTITIONER_B, "Profesional B Tanda 2");
        jdbc.update("UPDATE cuentas_personal SET medico_id=?, medico_vinculado_en=?, medico_vinculado_por=? WHERE id=?",
                PRACTITIONER_A, now, ADMIN, DOCTOR_A);
        jdbc.update("UPDATE cuentas_personal SET medico_id=?, medico_vinculado_en=?, medico_vinculado_por=? WHERE id=?",
                PRACTITIONER_B, now, ADMIN, DOCTOR_B);
        jdbc.update("INSERT INTO pacientes(id,estado,creado_en) VALUES (?, 'ACTIVO', ?)", PATIENT, now);
    }

    @AfterEach
    void cleanup() {
        cleanupFixture();
    }

    @Test
    void adminCreaEditaPersisteYConservaLaRelacionDeEspecialidad() throws Exception {
        String adminToken = login("t2.admin@example.test");
        String created = mockMvc.perform(post("/api/v1/staff/especialidades")
                        .header("Authorization", "Bearer " + adminToken).contentType("application/json")
                        .content("{\"name\":\"  Tanda Clínica  \",\"description\":\"Descripción persistida de prueba\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.name").value("tanda clínica"))
                .andReturn().getResponse().getContentAsString();
        String specialtyId = objectMapper.readTree(created).get("id").asText();
        jdbc.update("INSERT INTO medicos(id,nombre_completo,especialidad_id,numero_colegiado) VALUES (?,?,?,?)",
                "a2300000-0000-0000-0000-000000000003", "Profesional asociado", specialtyId, "T2-ASSOCIATED");

        mockMvc.perform(patch("/api/v1/staff/especialidades/{id}", specialtyId)
                        .header("Authorization", "Bearer " + adminToken).contentType("application/json")
                        .content("{\"name\":\" TANDA CLÍNICA EDITADA \",\"description\":\"Descripción actualizada\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(specialtyId))
                .andExpect(jsonPath("$.name").value("tanda clínica editada"));
        assertThat(jdbc.queryForObject("SELECT especialidad_id FROM medicos WHERE id = ?", String.class,
                "a2300000-0000-0000-0000-000000000003")).isEqualTo(specialtyId);
        mockMvc.perform(get("/api/v1/especialidades")).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + specialtyId + "')].name").value("tanda clínica editada"));
        List<String> auditActions = jdbc.queryForList(
                "SELECT accion FROM bitacora_eventos WHERE actor_id = ? AND entidad_id = ? ORDER BY ocurrido_en",
                String.class, ADMIN, specialtyId);
        assertThat(auditActions).containsExactly("SPECIALTY_CREATED", "SPECIALTY_UPDATED");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM bitacora_eventos WHERE entidad_id = ? AND entidad_tipo = 'ESPECIALIDAD' AND actor_rol = 'ADMIN'",
                Integer.class, specialtyId)).isEqualTo(2);
    }

    @Test
    void especialidadesRechazanVaciosLongitudYDuplicadosNormalizados() throws Exception {
        String token = login("t2.admin@example.test");
        mockMvc.perform(post("/api/v1/staff/especialidades").header("Authorization", "Bearer " + token)
                        .contentType("application/json").content("{\"name\":\"   \",\"description\":\"válida\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/staff/especialidades").header("Authorization", "Bearer " + token)
                        .contentType("application/json").content("{\"name\":\"" + "x".repeat(121) + "\",\"description\":\"válida\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/staff/especialidades").header("Authorization", "Bearer " + token)
                        .contentType("application/json").content("{\"name\":\"  ESPECIALIDAD TANDA DOS  \",\"description\":\"otra descripción\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SPECIALTY_DUPLICATE"));
    }

    @Test
    void soloAdminAdministraEspecialidadesYLaCuentaPacienteNoObtienePermiso() throws Exception {
        String adminToken = login("t2.admin@example.test");
        String receptionToken = login("t2.reception@example.test");
        String doctorToken = login("t2.doctor.a@example.test");
        mockMvc.perform(get("/api/v1/staff/especialidades").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/staff/especialidades").header("Authorization", "Bearer " + receptionToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/staff/especialidades").header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/staff/especialidades").with(user("synthetic-patient").roles("PACIENTE")))
                .andExpect(status().isForbidden());
    }

    @Test
    void medicoConsultaYAgregaSoloSusBloquesYElBloquePersiste() throws Exception {
        String token = login("t2.doctor.a@example.test");
        OffsetDateTime start = OffsetDateTime.now(ZoneOffset.UTC).plusDays(20).withHour(9).withMinute(0).withSecond(0).withNano(0);
        String body = "{\"startAt\":\"" + start + "\",\"endAt\":\"" + start.plusMinutes(30) + "\"}";
        String created = mockMvc.perform(post("/api/v1/staff/medico/horarios").header("Authorization", "Bearer " + token)
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.practitionerId").value(PRACTITIONER_A))
                .andExpect(jsonPath("$.available").value(true)).andReturn().getResponse().getContentAsString();
        String blockId = objectMapper.readTree(created).get("id").asText();
        mockMvc.perform(get("/api/v1/staff/medico/horarios").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id=='" + blockId + "')].practitionerId").value(PRACTITIONER_A));
        assertThat(jdbc.queryForObject("SELECT medico_id FROM bloques_disponibilidad WHERE id = ?", String.class, blockId))
                .isEqualTo(PRACTITIONER_A);
    }

    @Test
    void eventosDeHorarioPersistenActorRecursoYUnRechazoNoLosDuplica() throws Exception {
        String doctor = login("t2.doctor.a@example.test");
        String admin = login("t2.admin@example.test");
        OffsetDateTime start = OffsetDateTime.now(ZoneOffset.UTC).plusDays(25).withHour(9).withMinute(0)
                .withSecond(0).withNano(0);
        String blockId = createBlock(doctor, start, 30);
        OffsetDateTime updated = start.plusDays(1);

        mockMvc.perform(patch("/api/v1/staff/medico/horarios/{id}", blockId)
                        .header("Authorization", "Bearer " + doctor).contentType("application/json")
                        .content("{\"startAt\":\"" + updated + "\",\"endAt\":\"" + updated.plusMinutes(45) + "\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/staff/medico/horarios/{id}", blockId)
                        .header("Authorization", "Bearer " + doctor))
                .andExpect(status().isNoContent());

        for (String action : List.of("SCHEDULE_BLOCK_CREATED", "SCHEDULE_BLOCK_UPDATED", "SCHEDULE_BLOCK_RETIRED")) {
            assertThat(jdbc.queryForObject("SELECT count(*) FROM bitacora_eventos WHERE accion=? "
                            + "AND actor_tipo='PERSONAL' AND actor_id=? AND actor_rol='MEDICO' "
                            + "AND entidad_tipo='BLOQUE_DISPONIBILIDAD' AND entidad_id=?",
                    Integer.class, action, DOCTOR_A, blockId)).isOne();
        }

        mockMvc.perform(post("/api/v1/staff/medico/horarios").header("Authorization", "Bearer " + admin)
                        .contentType("application/json")
                        .content("{\"startAt\":\"" + start.plusDays(3) + "\",\"endAt\":\""
                                + start.plusDays(3).plusMinutes(30) + "\"}"))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bitacora_eventos WHERE accion LIKE 'SCHEDULE_BLOCK_%'",
                Integer.class)).isEqualTo(3);
    }

    @Test
    void falloDeBitacoraRevierteAltaEdicionYRetiroDeHorario() throws Exception {
        String doctor = login("t2.doctor.a@example.test");
        OffsetDateTime start = OffsetDateTime.now(ZoneOffset.UTC).plusDays(26).withHour(10).withMinute(0)
                .withSecond(0).withNano(0);
        doThrow(new IllegalStateException("fallo sintético de bitácora"))
                .when(audit).record(any(StaffPrincipal.class), eq("SCHEDULE_BLOCK_CREATED"),
                        eq("BLOQUE_DISPONIBILIDAD"), anyString(), any(OffsetDateTime.class));
        mockMvc.perform(post("/api/v1/staff/medico/horarios").header("Authorization", "Bearer " + doctor)
                        .contentType("application/json")
                        .content("{\"startAt\":\"" + start + "\",\"endAt\":\"" + start.plusMinutes(30) + "\"}"))
                .andExpect(status().is5xxServerError());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bloques_disponibilidad WHERE medico_id=? AND inicio=?",
                Integer.class, PRACTITIONER_A, start)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bitacora_eventos WHERE accion='SCHEDULE_BLOCK_CREATED'",
                Integer.class)).isZero();

        reset(audit);
        String blockId = createBlock(doctor, start.plusDays(1), 30);
        OffsetDateTime original = jdbc.queryForObject("SELECT inicio FROM bloques_disponibilidad WHERE id=?",
                OffsetDateTime.class, blockId);
        OffsetDateTime changed = original.plusDays(1);
        doThrow(new IllegalStateException("fallo sintético de bitácora"))
                .when(audit).record(any(StaffPrincipal.class), eq("SCHEDULE_BLOCK_UPDATED"),
                        eq("BLOQUE_DISPONIBILIDAD"), eq(blockId), any(OffsetDateTime.class));
        mockMvc.perform(patch("/api/v1/staff/medico/horarios/{id}", blockId)
                        .header("Authorization", "Bearer " + doctor).contentType("application/json")
                        .content("{\"startAt\":\"" + changed + "\",\"endAt\":\"" + changed.plusMinutes(30) + "\"}"))
                .andExpect(status().is5xxServerError());
        assertThat(jdbc.queryForObject("SELECT inicio FROM bloques_disponibilidad WHERE id=?",
                OffsetDateTime.class, blockId)).isEqualTo(original);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bitacora_eventos WHERE accion='SCHEDULE_BLOCK_UPDATED'",
                Integer.class)).isZero();

        reset(audit);
        doThrow(new IllegalStateException("fallo sintético de bitácora"))
                .when(audit).record(any(StaffPrincipal.class), eq("SCHEDULE_BLOCK_RETIRED"),
                        eq("BLOQUE_DISPONIBILIDAD"), eq(blockId), any(OffsetDateTime.class));
        mockMvc.perform(delete("/api/v1/staff/medico/horarios/{id}", blockId)
                        .header("Authorization", "Bearer " + doctor))
                .andExpect(status().is5xxServerError());
        assertThat(jdbc.queryForObject("SELECT retirado_en IS NULL AND disponible=true FROM bloques_disponibilidad WHERE id=?",
                Boolean.class, blockId)).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bitacora_eventos WHERE accion='SCHEDULE_BLOCK_RETIRED'",
                Integer.class)).isZero();
    }

    @Test
    void horarioRechazaIntervaloInvalidoSolapamientoYMedicoIdArbitrario() throws Exception {
        String token = login("t2.doctor.a@example.test");
        OffsetDateTime start = OffsetDateTime.now(ZoneOffset.UTC).plusDays(21).withHour(10).withMinute(0).withSecond(0).withNano(0);
        String valid = "{\"startAt\":\"" + start + "\",\"endAt\":\"" + start.plusMinutes(30) + "\"}";
        mockMvc.perform(post("/api/v1/staff/medico/horarios").header("Authorization", "Bearer " + token)
                        .contentType("application/json").content(valid)).andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/staff/medico/horarios").header("Authorization", "Bearer " + token)
                        .contentType("application/json").content("{\"startAt\":\"" + start.plusMinutes(15) + "\",\"endAt\":\"" + start.plusMinutes(45) + "\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SCHEDULE_OVERLAP"));
        mockMvc.perform(post("/api/v1/staff/medico/horarios").header("Authorization", "Bearer " + token)
                        .contentType("application/json").content("{\"startAt\":\"" + start.plusHours(2) + "\",\"endAt\":\"" + start.plusHours(1) + "\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/staff/medico/horarios").header("Authorization", "Bearer " + token)
                        .contentType("application/json").content("{\"medicoId\":\"" + PRACTITIONER_B + "\",\"startAt\":\"" + start.plusHours(3) + "\",\"endAt\":\"" + start.plusHours(4) + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void horarioRechazaInicioPasadoAceptaLimiteExactoYFuturo() throws Exception {
        String token = login("t2.doctor.a@example.test");
        OffsetDateTime now = OffsetDateTime.of(2026, 10, 1, 15, 0, 0, 0, ZoneOffset.UTC);
        mockMvc.perform(post("/api/v1/staff/medico/horarios").header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"startAt\":\"" + now.minusMinutes(1) + "\",\"endAt\":\"" + now.plusMinutes(29) + "\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SCHEDULE_START_IN_PAST"));

        String exactId = createBlock(token, now, 30);
        String futureId = createBlock(token, now.plusHours(1), 30);
        assertThat(jdbc.queryForObject("SELECT inicio FROM bloques_disponibilidad WHERE id = ?", OffsetDateTime.class, exactId))
                .isEqualTo(now);
        assertThat(jdbc.queryForObject("SELECT inicio FROM bloques_disponibilidad WHERE id = ?", OffsetDateTime.class, futureId))
                .isEqualTo(now.plusHours(1));
    }

    @Test
    void disponibilidadExcluyePasadoIncluyeFuturoLibreYExcluyeFuturoOcupado() throws Exception {
        OffsetDateTime now = OffsetDateTime.of(2026, 10, 1, 15, 0, 0, 0, ZoneOffset.UTC);
        String pastId = "a2500000-0000-0000-0000-000000000001";
        String futureId = "a2500000-0000-0000-0000-000000000002";
        String occupiedId = "a2500000-0000-0000-0000-000000000003";
        OffsetDateTime past = now.minusHours(1);
        OffsetDateTime future = now.plusHours(1);
        OffsetDateTime occupied = now.plusHours(2);
        jdbc.update("INSERT INTO bloques_disponibilidad(id,medico_id,inicio,fin,disponible) VALUES (?,?,?,?,?)",
                pastId, PRACTITIONER_A, past, past.plusMinutes(30), true);
        jdbc.update("INSERT INTO bloques_disponibilidad(id,medico_id,inicio,fin,disponible) VALUES (?,?,?,?,?)",
                futureId, PRACTITIONER_A, future, future.plusMinutes(30), true);
        jdbc.update("INSERT INTO bloques_disponibilidad(id,medico_id,inicio,fin,disponible) VALUES (?,?,?,?,?)",
                occupiedId, PRACTITIONER_A, occupied, occupied.plusMinutes(30), false);
        jdbc.update("INSERT INTO citas(id,paciente_id,bloque_id,medico_id,especialidad_id,programada_en,estado,creada_en,actualizada_en) VALUES (?,?,?,?,?,?,'PENDIENTE',?,?)",
                "a2400000-0000-0000-0000-000000000001", PATIENT, occupiedId, PRACTITIONER_A, SPECIALTY, occupied, now, now);

        mockMvc.perform(get("/api/v1/medicos/{id}/disponibilidad", PRACTITIONER_A)
                        .param("desde", "2026-10-01").param("hasta", "2026-10-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + pastId + "')]").isEmpty())
                .andExpect(jsonPath("$[?(@.id=='" + futureId + "')].id").value(futureId))
                .andExpect(jsonPath("$[?(@.id=='" + occupiedId + "')]").isEmpty());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bloques_disponibilidad WHERE id = ?", Integer.class, pastId)).isOne();
    }

    @Test
    void medicoAisladoNoPuedeCruzarNiOtroRolModificarHorarios() throws Exception {
        String doctorA = login("t2.doctor.a@example.test");
        String doctorB = login("t2.doctor.b@example.test");
        String admin = login("t2.admin@example.test");
        String reception = login("t2.reception@example.test");
        OffsetDateTime start = OffsetDateTime.now(ZoneOffset.UTC).plusDays(22).withHour(11).withMinute(0).withSecond(0).withNano(0);
        String body = "{\"startAt\":\"" + start + "\",\"endAt\":\"" + start.plusMinutes(30) + "\"}";
        mockMvc.perform(post("/api/v1/staff/medico/horarios").header("Authorization", "Bearer " + doctorB)
                        .contentType("application/json").content(body)).andExpect(status().isCreated());
        mockMvc.perform(get("/api/v1/staff/medico/horarios").header("Authorization", "Bearer " + doctorA))
                .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.practitionerId=='" + PRACTITIONER_B + "')]").isEmpty());
        mockMvc.perform(post("/api/v1/staff/medico/horarios").header("Authorization", "Bearer " + admin)
                        .contentType("application/json").content(body)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/staff/medico/horarios").header("Authorization", "Bearer " + reception))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/staff/medico/horarios").with(user("synthetic-patient").roles("PACIENTE")))
                .andExpect(status().isForbidden());
        String unlinked = login("t2.unlinked@example.test");
        mockMvc.perform(get("/api/v1/staff/medico/horarios").header("Authorization", "Bearer " + unlinked))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PRACTITIONER_LINK_REQUIRED"));
    }

    @Test
    void citaReservadaConservaSuBloqueYSeExcluyeDeDisponibilidadPublica() throws Exception {
        String token = login("t2.doctor.a@example.test");
        OffsetDateTime start = OffsetDateTime.now(ZoneOffset.UTC).plusDays(23).withHour(12).withMinute(0).withSecond(0).withNano(0);
        String first = createBlock(token, start, 30);
        String second = createBlock(token, start.plusHours(2), 30);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        jdbc.update("INSERT INTO citas(id,paciente_id,bloque_id,medico_id,especialidad_id,programada_en,estado,creada_en,actualizada_en) VALUES (?,?,?,?,?,?,'PENDIENTE',?,?)",
                "a2400000-0000-0000-0000-000000000001", PATIENT, first, PRACTITIONER_A, SPECIALTY, start, now, now);
        jdbc.update("UPDATE bloques_disponibilidad SET disponible=false WHERE id=?", first);
        mockMvc.perform(get("/api/v1/medicos/{id}/disponibilidad", PRACTITIONER_A)
                        .param("desde", start.toLocalDate().toString()).param("hasta", start.toLocalDate().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id=='" + first + "')]").isEmpty())
                .andExpect(jsonPath("$[?(@.id=='" + second + "')].id").value(second));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM citas WHERE id=? AND bloque_id=?", Integer.class,
                "a2400000-0000-0000-0000-000000000001", first)).isEqualTo(1);
    }

    @Test
    void editarBloqueRechazaNuevoInicioPasadoYAceptaInicioFuturoSegunClock() throws Exception {
        String token = login("t2.doctor.a@example.test");
        OffsetDateTime now = OffsetDateTime.of(2026, 10, 1, 15, 0, 0, 0, ZoneOffset.UTC);
        String blockId = createBlock(token, now.plusDays(2), 30);

        mockMvc.perform(patch("/api/v1/staff/medico/horarios/{id}", blockId)
                        .header("Authorization", "Bearer " + token).contentType("application/json")
                        .content("{\"startAt\":\"" + now.minusMinutes(1) + "\",\"endAt\":\"" + now.plusMinutes(29) + "\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SCHEDULE_START_IN_PAST"));
        assertThat(jdbc.queryForObject("SELECT inicio FROM bloques_disponibilidad WHERE id=?", OffsetDateTime.class, blockId))
                .isEqualTo(now.plusDays(2));

        OffsetDateTime future = now.plusDays(3);
        mockMvc.perform(patch("/api/v1/staff/medico/horarios/{id}", blockId)
                        .header("Authorization", "Bearer " + token).contentType("application/json")
                        .content("{\"startAt\":\"" + future + "\",\"endAt\":\"" + future.plusMinutes(45) + "\"}"))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT inicio FROM bloques_disponibilidad WHERE id=?", OffsetDateTime.class, blockId))
                .isEqualTo(future);
    }

    @Test
    void retirarBloqueConCitaConservaCitaYLoExcluyeDeNuevasReservas() throws Exception {
        String token = login("t2.doctor.a@example.test");
        OffsetDateTime now = OffsetDateTime.of(2026, 10, 1, 15, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime start = now.plusDays(4);
        String blockId = createBlock(token, start, 30);
        String appointmentId = "a2400000-0000-0000-0000-000000000001";
        jdbc.update("INSERT INTO citas(id,paciente_id,bloque_id,medico_id,especialidad_id,programada_en,estado,creada_en,actualizada_en) VALUES (?,?,?,?,?,?,'PENDIENTE',?,?)",
                appointmentId, PATIENT, blockId, PRACTITIONER_A, SPECIALTY, start, now, now);
        jdbc.update("UPDATE bloques_disponibilidad SET disponible=false WHERE id=?", blockId);

        mockMvc.perform(delete("/api/v1/staff/medico/horarios/{id}", blockId)
                        .header("Authorization", "Bearer " + token)).andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM citas WHERE id=? AND bloque_id=? AND estado='PENDIENTE'",
                Integer.class, appointmentId, blockId)).isOne();
        assertThat(jdbc.queryForObject("SELECT retirado_en IS NOT NULL AND disponible=false FROM bloques_disponibilidad WHERE id=?",
                Boolean.class, blockId)).isTrue();
        mockMvc.perform(get("/api/v1/medicos/{id}/disponibilidad", PRACTITIONER_A)
                        .param("desde", start.toLocalDate().toString()).param("hasta", start.toLocalDate().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id=='" + blockId + "')]").isEmpty());
    }

    @Test
    void dosAltasConcurrentesDelMismoMedicoNoCreanSolapamiento() throws Exception {
        String token = login("t2.doctor.a@example.test");
        OffsetDateTime start = OffsetDateTime.now(ZoneOffset.UTC).plusDays(24).withHour(13).withMinute(0).withSecond(0).withNano(0);
        String body = "{\"startAt\":\"" + start + "\",\"endAt\":\"" + start.plusMinutes(30) + "\"}";
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        Callable<Integer> call = () -> { ready.countDown(); go.await(); return mockMvc.perform(post("/api/v1/staff/medico/horarios")
                .header("Authorization", "Bearer " + token).contentType("application/json").content(body)).andReturn().getResponse().getStatus(); };
        Future<Integer> first = pool.submit(call);
        Future<Integer> second = pool.submit(call);
        ready.await(); go.countDown();
        List<Integer> statuses = List.of(first.get(), second.get()).stream().sorted().toList();
        pool.shutdownNow();
        assertThat(statuses).containsExactly(201, 409);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bloques_disponibilidad WHERE medico_id=? AND inicio=?", Integer.class,
                PRACTITIONER_A, start)).isEqualTo(1);
    }

    private String createBlock(String token, OffsetDateTime start, int minutes) throws Exception {
        String body = "{\"startAt\":\"" + start + "\",\"endAt\":\"" + start.plusMinutes(minutes) + "\"}";
        return objectMapper.readTree(mockMvc.perform(post("/api/v1/staff/medico/horarios").header("Authorization", "Bearer " + token)
                .contentType("application/json").content(body)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asText();
    }

    private String login(String email) throws Exception {
        String body = mockMvc.perform(post("/api/v1/staff/auth/login").contentType("application/json")
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(body);
        return json.get("accessToken").asText();
    }

    private void staff(String id, String email, String name, String role, String hash, OffsetDateTime now) {
        jdbc.update("INSERT INTO cuentas_personal(id,email_normalizado,nombre_completo,password_hash,rol,estado,creado_en,actualizada_en) VALUES (?,?,?,?,?,'ACTIVA',?,?)",
                id, email, name, hash, role, now, now);
    }

    private void practitioner(String id, String name) {
        jdbc.update("INSERT INTO medicos(id,nombre_completo,especialidad_id,numero_colegiado) VALUES (?,?,?,?)",
                id, name, SPECIALTY, "T2-" + id);
    }

    private void cleanupFixture() {
        jdbc.execute("TRUNCATE TABLE bitacora_eventos");
        jdbc.update("DELETE FROM citas WHERE id = ?", "a2400000-0000-0000-0000-000000000001");
        jdbc.update("DELETE FROM sesiones_personal WHERE cuenta_id IN (?,?,?,?,?)", ADMIN, RECEPTION, DOCTOR_A, DOCTOR_B, UNLINKED);
        jdbc.update("UPDATE cuentas_personal SET medico_id=NULL, medico_vinculado_en=NULL, medico_vinculado_por=NULL WHERE id IN (?,?,?,?,?)",
                ADMIN, RECEPTION, DOCTOR_A, DOCTOR_B, UNLINKED);
        jdbc.update("DELETE FROM bloques_disponibilidad WHERE medico_id IN (?,?)", PRACTITIONER_A, PRACTITIONER_B);
        jdbc.update("DELETE FROM cuentas_personal WHERE id IN (?,?,?,?,?)", ADMIN, RECEPTION, DOCTOR_A, DOCTOR_B, UNLINKED);
        jdbc.update("DELETE FROM medicos WHERE id IN (?,?) OR numero_colegiado LIKE 'T2-%'", PRACTITIONER_A, PRACTITIONER_B);
        jdbc.update("DELETE FROM especialidades WHERE id = ? OR nombre LIKE 'tanda clínica%' OR nombre LIKE 'Tanda Clínica%'", SPECIALTY);
        jdbc.update("DELETE FROM pacientes WHERE id = ?", PATIENT);
    }
}
