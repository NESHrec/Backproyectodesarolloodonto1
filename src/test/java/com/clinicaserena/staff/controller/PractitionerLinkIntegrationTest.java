package com.clinicaserena.staff.controller;

import com.clinicaserena.clinica.ClinicalFixture;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.clinicaserena.clinica.ClinicalFixture.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpHeaders.CACHE_CONTROL;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PractitionerLinkIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired ObjectMapper objectMapper;

    private ClinicalFixture fixture;

    @BeforeEach
    void prepare() {
        fixture = new ClinicalFixture(jdbc);
        fixture.create(passwordEncoder, false);
    }

    @AfterEach
    void cleanup() {
        fixture.cleanup();
    }

    @Test
    void cuentasMedicasSinVinculoQuedanPendientesHastaQueAdminLasAsigne() throws Exception {
        String admin = login(mockMvc, objectMapper, "admin.clinical@example.test");
        mockMvc.perform(get("/api/v1/staff/accounts").param("role", "MEDICO").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andExpect(header().string(CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$[?(@.accountId == '" + DOCTOR_ONE + "')].practitionerLinkStatus")
                        .value("PENDIENTE_VINCULACION"))
                .andExpect(jsonPath("$[?(@.role != 'MEDICO')]").isEmpty());

        String doctor = login(mockMvc, objectMapper, "doctor.one@example.test");
        mockMvc.perform(get("/api/v1/staff/auth/me").header("Authorization", "Bearer " + doctor))
                .andExpect(status().isOk()).andExpect(jsonPath("$.practitionerLinkStatus").value("PENDIENTE_VINCULACION"))
                .andExpect(jsonPath("$.practitionerId").doesNotExist());
        mockMvc.perform(get("/api/v1/medico/citas").header("Authorization", "Bearer " + doctor))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PRACTITIONER_LINK_REQUIRED"));

        mockMvc.perform(link(admin, DOCTOR_ONE, PRACTITIONER_ONE))
                .andExpect(status().isOk()).andExpect(jsonPath("$.practitionerLinkStatus").value("VINCULADA"))
                .andExpect(jsonPath("$.practitionerName").value("Profesional uno de prueba"));
        mockMvc.perform(get("/api/v1/staff/auth/me").header("Authorization", "Bearer " + doctor))
                .andExpect(jsonPath("$.practitionerLinkStatus").value("VINCULADA"))
                .andExpect(jsonPath("$.practitionerId").value(PRACTITIONER_ONE));
        mockMvc.perform(get("/api/v1/medico/citas").header("Authorization", "Bearer " + doctor))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT medico_vinculado_por FROM cuentas_personal WHERE id = ?",
                String.class, DOCTOR_ONE)).isEqualTo(ADMIN);
    }

    @Test
    void correccionConservaHistorialYNoDuplicaRegistrosIdempotentes() throws Exception {
        String admin = login(mockMvc, objectMapper, "admin.clinical@example.test");
        mockMvc.perform(link(admin, DOCTOR_ONE, PRACTITIONER_ONE)).andExpect(status().isOk());
        mockMvc.perform(link(admin, DOCTOR_ONE, PRACTITIONER_ONE)).andExpect(status().isOk());
        mockMvc.perform(link(admin, DOCTOR_ONE, PRACTITIONER_TWO))
                .andExpect(status().isOk()).andExpect(jsonPath("$.practitionerId").value(PRACTITIONER_TWO));
        mockMvc.perform(delete("/api/v1/staff/accounts/{id}/practitioner", DOCTOR_ONE).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.practitionerLinkStatus").value("PENDIENTE_VINCULACION"));
        mockMvc.perform(delete("/api/v1/staff/accounts/{id}/practitioner", DOCTOR_ONE).header("Authorization", "Bearer " + admin))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PRACTITIONER_NOT_LINKED"));

        List<String> history = jdbc.queryForList("SELECT coalesce(medico_anterior_id,'-') || '>' || coalesce(medico_nuevo_id,'-') "
                + "FROM historial_vinculacion_medico WHERE cuenta_personal_id = ? ORDER BY realizado_en", String.class, DOCTOR_ONE);
        assertThat(history).containsExactly("->" + PRACTITIONER_ONE, PRACTITIONER_ONE + ">" + PRACTITIONER_TWO,
                PRACTITIONER_TWO + ">-");
        assertThatThrownBy(() -> jdbc.update("UPDATE historial_vinculacion_medico SET realizado_por = ? WHERE cuenta_personal_id = ?",
                DOCTOR_TWO, DOCTOR_ONE)).hasMessageContaining("CLINICAL_RECORD_IMMUTABLE");
    }

    @Test
    void unProfesionalNoPuedeAsignarseADosCuentasActivas() throws Exception {
        String admin = login(mockMvc, objectMapper, "admin.clinical@example.test");
        mockMvc.perform(link(admin, DOCTOR_ONE, PRACTITIONER_ONE)).andExpect(status().isOk());
        mockMvc.perform(link(admin, DOCTOR_TWO, PRACTITIONER_ONE))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PRACTITIONER_ALREADY_LINKED"));
        assertThatThrownBy(() -> jdbc.update("UPDATE cuentas_personal SET medico_id = ?, medico_vinculado_en = now(), "
                + "medico_vinculado_por = ? WHERE id = ?", PRACTITIONER_ONE, ADMIN, DOCTOR_TWO))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void asignacionesConcurrentesDelMismoProfesionalSoloAceptanUna() throws Exception {
        String admin = login(mockMvc, objectMapper, "admin.clinical@example.test");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        Callable<Integer> first = () -> { ready.countDown(); go.await();
            return mockMvc.perform(link(admin, DOCTOR_ONE, PRACTITIONER_FREE)).andReturn().getResponse().getStatus(); };
        Callable<Integer> second = () -> { ready.countDown(); go.await();
            return mockMvc.perform(link(admin, DOCTOR_TWO, PRACTITIONER_FREE)).andReturn().getResponse().getStatus(); };
        Future<Integer> a = pool.submit(first);
        Future<Integer> b = pool.submit(second);
        ready.await(); go.countDown();
        List<Integer> statuses = List.of(a.get(), b.get()).stream().sorted().toList();
        pool.shutdownNow();
        assertThat(statuses).containsExactly(200, 409);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM cuentas_personal WHERE medico_id = ?", Integer.class,
                PRACTITIONER_FREE)).isEqualTo(1);
    }

    @Test
    void soloAdminPuedeVincularYSoloCuentasMedicas() throws Exception {
        String admin = login(mockMvc, objectMapper, "admin.clinical@example.test");
        for (String email : List.of("reception.clinical@example.test", "doctor.one@example.test")) {
            String token = login(mockMvc, objectMapper, email);
            mockMvc.perform(link(token, DOCTOR_ONE, PRACTITIONER_ONE))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
            mockMvc.perform(get("/api/v1/staff/accounts").header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(put("/api/v1/staff/accounts/{id}/practitioner", DOCTOR_ONE).contentType(MediaType.APPLICATION_JSON)
                .content("{\"practitionerId\":\"" + PRACTITIONER_ONE + "\"}")).andExpect(status().isUnauthorized());
        mockMvc.perform(link(admin, RECEPTION, PRACTITIONER_ONE))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("STAFF_ACCOUNT_NOT_MEDICAL"));
        mockMvc.perform(link(admin, ADMIN, PRACTITIONER_ONE))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("STAFF_ACCOUNT_NOT_MEDICAL"));
        mockMvc.perform(link(admin, DOCTOR_ONE, "98200000-0000-0000-0000-00000000dead"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PRACTITIONER_NOT_FOUND"));
        mockMvc.perform(link(admin, "98000000-0000-0000-0000-00000000dead", PRACTITIONER_ONE))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("STAFF_ACCOUNT_NOT_FOUND"));
        mockMvc.perform(put("/api/v1/staff/accounts/{id}/practitioner", DOCTOR_ONE).header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"practitionerId\":\"" + PRACTITIONER_ONE + "\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isBadRequest());
        assertThatThrownBy(() -> jdbc.update("UPDATE cuentas_personal SET medico_id = ?, medico_vinculado_en = now(), "
                + "medico_vinculado_por = ? WHERE id = ?", PRACTITIONER_ONE, ADMIN, RECEPTION))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM cuentas_personal WHERE medico_id IS NOT NULL AND id IN (?, ?, ?)",
                Integer.class, DOCTOR_ONE, RECEPTION, ADMIN)).isZero();
    }

    private MockHttpServletRequestBuilder link(String token, String account, String practitioner) {
        return put("/api/v1/staff/accounts/{id}/practitioner", account).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"practitionerId\":\"" + practitioner + "\"}");
    }
}
