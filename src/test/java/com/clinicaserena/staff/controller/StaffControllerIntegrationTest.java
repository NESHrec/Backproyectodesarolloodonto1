package com.clinicaserena.staff.controller;

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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.CACHE_CONTROL;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class StaffControllerIntegrationTest {

    private static final String ADMIN_ID = "92000000-0000-0000-0000-000000000001";
    private static final String RECEPTION_ID = "92000000-0000-0000-0000-000000000002";
    private static final String MEDICO_ID = "92000000-0000-0000-0000-000000000003";
    private static final String PATIENT_ID = "93000000-0000-0000-0000-000000000001";
    private static final String SPECIALTY_ID = "94000000-0000-0000-0000-000000000001";
    private static final String PRACTITIONER_ID = "95000000-0000-0000-0000-000000000001";
    private static final String BLOCK_ID = "96000000-0000-0000-0000-000000000001";
    private static final String APPOINTMENT_ID = "97000000-0000-0000-0000-000000000001";
    private static final String PASSWORD = "StaffTestPassword!2026";

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
                ADMIN_ID, "admin.staff@example.test", "Admin de prueba", hash, now, now);
        jdbc.update("INSERT INTO cuentas_personal(id,email_normalizado,nombre_completo,password_hash,rol,estado,creado_en,actualizada_en) VALUES (?,?,? ,?,'RECEPCION','ACTIVA',?,?)",
                RECEPTION_ID, "reception.staff@example.test", "Recepción de prueba", hash, now, now);
        jdbc.update("INSERT INTO cuentas_personal(id,email_normalizado,nombre_completo,password_hash,rol,estado,creado_en,actualizada_en) VALUES (?,?,? ,?,'MEDICO','ACTIVA',?,?)",
                MEDICO_ID, "doctor.staff@example.test", "Médico de prueba", hash, now, now);
        jdbc.update("INSERT INTO pacientes(id,estado,creado_en) VALUES (?, 'ACTIVO', ?)", PATIENT_ID, now);
        jdbc.update("INSERT INTO especialidades(id,nombre,descripcion) VALUES (?,?,?)", SPECIALTY_ID, "Especialidad de prueba", "Solo fixture");
        jdbc.update("INSERT INTO medicos(id,nombre_completo,especialidad_id,numero_colegiado) VALUES (?,?,?,?)",
                PRACTITIONER_ID, "Profesional de prueba", SPECIALTY_ID, "STAFF-" + PRACTITIONER_ID);
        OffsetDateTime scheduledAt = now.plusDays(1).withNano(0);
        jdbc.update("INSERT INTO bloques_disponibilidad(id,medico_id,inicio,fin,disponible) VALUES (?,?,?,?,false)",
                BLOCK_ID, PRACTITIONER_ID, scheduledAt, scheduledAt.plusMinutes(30));
        jdbc.update("INSERT INTO citas(id,paciente_id,bloque_id,medico_id,especialidad_id,programada_en,estado,creada_en,actualizada_en) VALUES (?,?,?,?,?,?,'PENDIENTE',?,?)",
                APPOINTMENT_ID, PATIENT_ID, BLOCK_ID, PRACTITIONER_ID, SPECIALTY_ID, scheduledAt, now, now);
    }

    @AfterEach
    void cleanup() {
        cleanupFixture();
    }

    @Test
    void loginMeLogoutYRevocacionUsanSesionSeparada() throws Exception {
        String token = login("reception.staff@example.test");
        mockMvc.perform(get("/api/v1/staff/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(header().string(CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.role").value("RECEPCION"));
        mockMvc.perform(post("/api/v1/staff/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent()).andExpect(header().string(CACHE_CONTROL, "no-store"));
        mockMvc.perform(get("/api/v1/staff/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminCreaCuentaDeRecepcionPeroNoOtroAdmin() throws Exception {
        String adminToken = login("admin.staff@example.test");
        String email = "new.reception." + UUID.randomUUID() + "@example.test";
        mockMvc.perform(post("/api/v1/staff/accounts").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"fullName\":\"Recepción nueva\",\"role\":\"RECEPCION\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.role").value("RECEPCION"));
        mockMvc.perform(post("/api/v1/staff/accounts").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin-new@example.test\",\"fullName\":\"Admin no permitido\",\"role\":\"ADMIN\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isForbidden());
        String createdId = jdbc.queryForObject("SELECT id FROM cuentas_personal WHERE email_normalizado = ?", String.class, email);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM bitacora_eventos WHERE actor_id = ? AND actor_rol = 'ADMIN' AND accion = 'STAFF_ACCOUNT_CREATED' AND entidad_tipo = 'CUENTA_PERSONAL' AND entidad_id = ?",
                Integer.class, ADMIN_ID, createdId)).isEqualTo(1);
        jdbc.update("DELETE FROM cuentas_personal WHERE email_normalizado = ?", email);
    }

    @Test
    void recepcionConsultaAgendaYRegistraLlegadaUnaSolaVez() throws Exception {
        String token = login("reception.staff@example.test");
        mockMvc.perform(get("/api/v1/staff/agenda").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(APPOINTMENT_ID))
                .andExpect(jsonPath("$[0].arrivalAt").value(org.hamcrest.Matchers.nullValue()));
        mockMvc.perform(post("/api/v1/staff/agenda/{id}/arrival", APPOINTMENT_ID)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.arrivalAt").isString())
                .andExpect(jsonPath("$.arrivalByAccountId").value(RECEPTION_ID));
        mockMvc.perform(post("/api/v1/staff/agenda/{id}/arrival", APPOINTMENT_ID)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ARRIVAL_ALREADY_REGISTERED"));
    }

    @Test
    void rolesIncorrectosNoPuedenAdministrarNiRegistrarLlegadas() throws Exception {
        String medicoToken = login("doctor.staff@example.test");
        mockMvc.perform(get("/api/v1/staff/agenda").header("Authorization", "Bearer " + medicoToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/staff/agenda/{id}/arrival", APPOINTMENT_ID)
                        .header("Authorization", "Bearer " + medicoToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/staff/agenda").with(user("patient").roles("PACIENTE")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/staff/agenda")).andExpect(status().isUnauthorized());
    }

    @Test
    void dosLlegadasConcurrentesSoloUnaModificaLaCita() throws Exception {
        String token = login("reception.staff@example.test");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        Callable<Integer> call = () -> {
            ready.countDown(); go.await();
            return mockMvc.perform(post("/api/v1/staff/agenda/{id}/arrival", APPOINTMENT_ID)
                    .header("Authorization", "Bearer " + token)).andReturn().getResponse().getStatus();
        };
        Future<Integer> first = pool.submit(call); Future<Integer> second = pool.submit(call);
        ready.await(); go.countDown();
        List<Integer> statuses = List.of(first.get(), second.get()).stream().sorted().toList();
        pool.shutdownNow();
        assertThat(statuses).containsExactly(200, 409);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM citas WHERE id = ? AND llegada_en IS NOT NULL", Integer.class, APPOINTMENT_ID)).isEqualTo(1);
    }

    private String login(String email) throws Exception {
        String body = mockMvc.perform(post("/api/v1/staff/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andExpect(header().string(CACHE_CONTROL, "no-store"))
                .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(body);
        return json.get("accessToken").asText();
    }

    private void cleanupFixture() {
        jdbc.execute("TRUNCATE TABLE bitacora_eventos");
        jdbc.update("DELETE FROM sesiones_personal WHERE cuenta_id IN (?, ?, ?)", ADMIN_ID, RECEPTION_ID, MEDICO_ID);
        jdbc.update("UPDATE citas SET llegada_por_personal_id = NULL, llegada_en = NULL WHERE id = ?", APPOINTMENT_ID);
        jdbc.update("DELETE FROM citas WHERE id = ?", APPOINTMENT_ID);
        jdbc.update("DELETE FROM cuentas_personal WHERE id IN (?, ?, ?)", ADMIN_ID, RECEPTION_ID, MEDICO_ID);
        jdbc.update("DELETE FROM bloques_disponibilidad WHERE id = ?", BLOCK_ID);
        jdbc.update("DELETE FROM medicos WHERE id = ?", PRACTITIONER_ID);
        jdbc.update("DELETE FROM especialidades WHERE id = ?", SPECIALTY_ID);
        jdbc.update("DELETE FROM pacientes WHERE id = ?", PATIENT_ID);
    }
}
