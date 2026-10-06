package com.clinicaserena.clinica.controller;

import com.clinicaserena.clinica.ClinicalFixture;
import com.clinicaserena.clinica.MutableClock;
import org.springframework.context.annotation.Import;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.clinicaserena.clinica.ClinicalFixture.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.HttpHeaders.CACHE_CONTROL;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(MutableClock.Config.class)
class MedicalCareIntegrationTest {

    private static final String VALID_ATTENTION = """
            {"reason":"Control sintético de prueba","findings":"Sin hallazgos relevantes",
             "diagnosis":"Diagnóstico sintético de prueba","treatmentPlan":"Seguimiento en 30 días",
             "prescription":[{"medicine":"Medicamento sintético A","dose":"1 tableta","frequency":"cada 12 horas",
             "duration":"5 días","instructions":"Tomar con alimentos"}]}""";

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired ObjectMapper objectMapper;
    @Autowired MutableClock clock;

    private ClinicalFixture fixture;

    @BeforeEach
    void prepare() {
        fixture = new ClinicalFixture(jdbc);
        fixture.create(passwordEncoder, true);
        clock.set(fixture.base().plusHours(CLOCK_OFFSET_HOURS).toInstant());
    }

    @AfterEach
    void cleanup() {
        fixture.cleanup();
    }

    @Test
    void cadaMedicoVeSoloSusCitasConPacienteDesdeLaRelacionPersistida() throws Exception {
        String one = login(mockMvc, objectMapper, "doctor.one@example.test");
        mockMvc.perform(get("/api/v1/medico/citas").header("Authorization", "Bearer " + one))
                .andExpect(status().isOk()).andExpect(header().string(CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$[*].id", containsInAnyOrder(APPT_X_ONE_PENDING, APPT_Y_ONE_CONFIRMED,
                        APPT_X_ONE_CANCELLED, APPT_Y_ONE_COMPLETED, APPT_X_ONE_CONCURRENT, APPT_X_ONE_FUTURE,
                        APPT_Y_ONE_NO_ARRIVAL)))
                .andExpect(jsonPath("$[?(@.id == '" + APPT_X_ONE_PENDING + "')].patientName").value("Paciente X sintético"))
                .andExpect(jsonPath("$[?(@.id == '" + APPT_X_ONE_PENDING + "')].canRecordAttention").value(true))
                .andExpect(jsonPath("$[?(@.id == '" + APPT_X_ONE_CANCELLED + "')].canRecordAttention").value(false));

        String two = login(mockMvc, objectMapper, "doctor.two@example.test");
        mockMvc.perform(get("/api/v1/medico/citas").header("Authorization", "Bearer " + two))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(APPT_X_TWO_PENDING));
        mockMvc.perform(get("/api/v1/medico/citas").param("status", "CANCELADA").header("Authorization", "Bearer " + one))
                .andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].id").value(APPT_X_ONE_CANCELLED));
        mockMvc.perform(get("/api/v1/medico/citas").param("limit", "0").header("Authorization", "Bearer " + one))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("LIMIT_INVALID"));
    }

    @Test
    void otroMedicoNoPuedeAbrirConsultarNiDocumentarUnaCitaAjena() throws Exception {
        String two = login(mockMvc, objectMapper, "doctor.two@example.test");
        for (MockHttpServletRequestBuilder request : List.of(
                get("/api/v1/medico/citas/{id}", APPT_X_ONE_PENDING),
                get("/api/v1/medico/citas/{id}/expediente", APPT_X_ONE_PENDING),
                attention(APPT_X_ONE_PENDING, VALID_ATTENTION),
                get("/api/v1/medico/citas/{id}", "98500000-0000-0000-0000-00000000dead"))) {
            mockMvc.perform(request.header("Authorization", "Bearer " + two))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("APPOINTMENT_NOT_FOUND"));
        }
        assertThat(countAttentions(APPT_X_ONE_PENDING)).isZero();
        assertThat(jdbc.queryForObject("SELECT estado FROM citas WHERE id = ?", String.class, APPT_X_ONE_PENDING))
                .isEqualTo("PENDIENTE");
    }

    @Test
    void cuentaMedicaSinVinculoYRolesNoMedicosNoAccedenADatosClinicos() throws Exception {
        String unlinked = login(mockMvc, objectMapper, "doctor.unlinked@example.test");
        for (MockHttpServletRequestBuilder request : clinicalRequests()) {
            mockMvc.perform(request.header("Authorization", "Bearer " + unlinked))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PRACTITIONER_LINK_REQUIRED"));
        }
        for (String email : List.of("admin.clinical@example.test", "reception.clinical@example.test")) {
            String token = login(mockMvc, objectMapper, email);
            for (MockHttpServletRequestBuilder request : clinicalRequests()) {
                mockMvc.perform(request.header("Authorization", "Bearer " + token))
                        .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
            }
        }
        for (MockHttpServletRequestBuilder request : clinicalRequests()) {
            mockMvc.perform(request.with(user("patient").roles("PACIENTE"))).andExpect(status().isForbidden());
        }
        for (MockHttpServletRequestBuilder request : clinicalRequests()) {
            mockMvc.perform(request).andExpect(status().isUnauthorized());
        }
        assertThat(countAttentions(APPT_X_ONE_PENDING)).isZero();
    }

    @Test
    void atencionSePersisteCompletaLaCitaYSobreviveAlCierreDeSesion() throws Exception {
        String one = login(mockMvc, objectMapper, "doctor.one@example.test");
        mockMvc.perform(attention(APPT_X_ONE_PENDING, VALID_ATTENTION).header("Authorization", "Bearer " + one))
                .andExpect(status().isCreated()).andExpect(header().string(CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.appointmentId").value(APPT_X_ONE_PENDING))
                .andExpect(jsonPath("$.practitionerId").value(PRACTITIONER_ONE))
                .andExpect(jsonPath("$.authorAccountId").value(DOCTOR_ONE))
                .andExpect(jsonPath("$.authorName").value("Médico uno de prueba"))
                .andExpect(jsonPath("$.prescription[0].order").value(1))
                .andExpect(jsonPath("$.prescription[0].medicine").value("Medicamento sintético A"));
        assertThat(jdbc.queryForObject("SELECT estado FROM citas WHERE id = ?", String.class, APPT_X_ONE_PENDING))
                .isEqualTo("COMPLETADA");
        assertThat(jdbc.queryForObject("SELECT paciente_id FROM atenciones_clinicas WHERE cita_id = ?", String.class,
                APPT_X_ONE_PENDING)).isEqualTo(PATIENT_X);

        mockMvc.perform(post("/api/v1/staff/auth/logout").header("Authorization", "Bearer " + one))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/medico/citas/{id}", APPT_X_ONE_PENDING).header("Authorization", "Bearer " + one))
                .andExpect(status().isUnauthorized());

        String again = login(mockMvc, objectMapper, "doctor.one@example.test");
        mockMvc.perform(get("/api/v1/medico/citas/{id}", APPT_X_ONE_PENDING).header("Authorization", "Bearer " + again))
                .andExpect(status().isOk()).andExpect(header().string(CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.appointment.status").value("COMPLETADA"))
                .andExpect(jsonPath("$.appointment.attentionRecorded").value(true))
                .andExpect(jsonPath("$.appointment.canRecordAttention").value(false))
                .andExpect(jsonPath("$.attention.diagnosis").value("Diagnóstico sintético de prueba"))
                .andExpect(jsonPath("$.attention.prescription", hasSize(1)));
        mockMvc.perform(get("/api/v1/medico/citas/{id}/expediente", APPT_X_ONE_PENDING)
                        .header("Authorization", "Bearer " + again))
                .andExpect(status().isOk()).andExpect(header().string(CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.patient.id").value(PATIENT_X))
                .andExpect(jsonPath("$.patient.fullName").value("Paciente X sintético"))
                .andExpect(jsonPath("$.recordId").isString())
                .andExpect(jsonPath("$.attentions", hasSize(1)));
    }

    @Test
    void historialDelPacienteSeComparteEntreProfesionalesYSeparaPacientes() throws Exception {
        String one = login(mockMvc, objectMapper, "doctor.one@example.test");
        String two = login(mockMvc, objectMapper, "doctor.two@example.test");
        mockMvc.perform(attention(APPT_X_ONE_PENDING, VALID_ATTENTION).header("Authorization", "Bearer " + one))
                .andExpect(status().isCreated());
        mockMvc.perform(attention(APPT_X_TWO_PENDING, VALID_ATTENTION.replace("Control sintético", "Revisión sintética"))
                .header("Authorization", "Bearer " + two)).andExpect(status().isCreated());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM expedientes_clinicos WHERE paciente_id = ?", Integer.class,
                PATIENT_X)).isEqualTo(1);
        mockMvc.perform(get("/api/v1/medico/citas/{id}/expediente", APPT_X_ONE_PENDING).header("Authorization", "Bearer " + one))
                .andExpect(jsonPath("$.attentions", hasSize(2)))
                .andExpect(jsonPath("$.attentions[*].practitionerId", containsInAnyOrder(PRACTITIONER_ONE, PRACTITIONER_TWO)));
        mockMvc.perform(get("/api/v1/medico/citas/{id}/expediente", APPT_Y_ONE_CONFIRMED).header("Authorization", "Bearer " + one))
                .andExpect(status().isOk()).andExpect(jsonPath("$.patient.id").value(PATIENT_Y))
                .andExpect(jsonPath("$.recordId").doesNotExist())
                .andExpect(jsonPath("$.attentions", hasSize(0)));
    }

    @Test
    void estadosNoDocumentablesYSegundoEnvioNoSobrescriben() throws Exception {
        String one = login(mockMvc, objectMapper, "doctor.one@example.test");
        mockMvc.perform(attention(APPT_X_ONE_CANCELLED, VALID_ATTENTION).header("Authorization", "Bearer " + one))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("APPOINTMENT_NOT_DOCUMENTABLE"));
        mockMvc.perform(attention(APPT_Y_ONE_COMPLETED, VALID_ATTENTION).header("Authorization", "Bearer " + one))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("APPOINTMENT_NOT_DOCUMENTABLE"));
        mockMvc.perform(attention(APPT_Y_ONE_CONFIRMED, VALID_ATTENTION).header("Authorization", "Bearer " + one))
                .andExpect(status().isCreated());
        mockMvc.perform(attention(APPT_Y_ONE_CONFIRMED, VALID_ATTENTION.replace("Diagnóstico sintético", "Otro diagnóstico"))
                        .header("Authorization", "Bearer " + one))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ATTENTION_ALREADY_RECORDED"));
        assertThat(jdbc.queryForObject("SELECT diagnostico FROM atenciones_clinicas WHERE cita_id = ?", String.class,
                APPT_Y_ONE_CONFIRMED)).isEqualTo("Diagnóstico sintético de prueba");
        assertThat(countAttentions(APPT_X_ONE_CANCELLED) + countAttentions(APPT_Y_ONE_COMPLETED)).isZero();
    }

    @Test
    void dosEnviosConcurrentesSoloRegistranUnaAtencion() throws Exception {
        String one = login(mockMvc, objectMapper, "doctor.one@example.test");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        Callable<Integer> call = () -> {
            ready.countDown(); go.await();
            return mockMvc.perform(attention(APPT_X_ONE_CONCURRENT, VALID_ATTENTION).header("Authorization", "Bearer " + one))
                    .andReturn().getResponse().getStatus();
        };
        Future<Integer> first = pool.submit(call);
        Future<Integer> second = pool.submit(call);
        ready.await(); go.countDown();
        List<Integer> statuses = List.of(first.get(), second.get()).stream().sorted().toList();
        pool.shutdownNow();
        assertThat(statuses).containsExactly(201, 409);
        assertThat(countAttentions(APPT_X_ONE_CONCURRENT)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM receta_items r JOIN atenciones_clinicas a ON a.id = r.atencion_id "
                + "WHERE a.cita_id = ?", Integer.class, APPT_X_ONE_CONCURRENT)).isEqualTo(1);
    }

    @Test
    void validacionesRechazanDatosIncompletosYCamposDeIdentidadEnviadosPorElCliente() throws Exception {
        String one = login(mockMvc, objectMapper, "doctor.one@example.test");
        StringBuilder eleven = new StringBuilder("[");
        for (int index = 0; index < 11; index++) {
            eleven.append(index == 0 ? "" : ",")
                    .append("{\"medicine\":\"Med ").append(index).append("\",\"dose\":\"1\",\"frequency\":\"1\",\"duration\":\"1\"}");
        }
        eleven.append("]");
        List<String> invalidBodies = List.of(
                "{\"reason\":\"Control sintético\",\"prescription\":[]}",
                "{\"reason\":\"Control sintético\",\"diagnosis\":\"   ab\",\"prescription\":[]}",
                "{\"reason\":\"Control sintético\",\"diagnosis\":\"Diagnóstico\"}",
                "{\"reason\":\"Control sintético\",\"diagnosis\":\"Diagnóstico\",\"prescription\":" + eleven + "}",
                "{\"reason\":\"Control sintético\",\"diagnosis\":\"Diagnóstico\",\"prescription\":[{\"medicine\":\"Med\"}]}",
                "{\"reason\":\"Control sintético\",\"diagnosis\":\"Diagnóstico\",\"prescription\":[],\"patientId\":\"" + PATIENT_Y + "\"}",
                "{\"reason\":\"Control sintético\",\"diagnosis\":\"Diagnóstico\",\"prescription\":[],\"practitionerId\":\"" + PRACTITIONER_TWO + "\"}",
                "{\"reason\":\"" + "x".repeat(1001) + "\",\"diagnosis\":\"Diagnóstico\",\"prescription\":[]}");
        for (String body : invalidBodies) {
            mockMvc.perform(attention(APPT_X_ONE_PENDING, body).header("Authorization", "Bearer " + one))
                    .andExpect(status().isBadRequest());
        }
        assertThat(countAttentions(APPT_X_ONE_PENDING)).isZero();
        mockMvc.perform(attention(APPT_X_ONE_PENDING,
                        "{\"reason\":\"Control sintético\",\"diagnosis\":\"Diagnóstico sin receta\",\"prescription\":[]}")
                        .header("Authorization", "Bearer " + one))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.prescription", hasSize(0)))
                .andExpect(jsonPath("$.findings").doesNotExist());
    }

    @Test
    void registrosClinicosGuardadosNoAdmitenModificaciones() throws Exception {
        String one = login(mockMvc, objectMapper, "doctor.one@example.test");
        mockMvc.perform(attention(APPT_X_ONE_PENDING, VALID_ATTENTION).header("Authorization", "Bearer " + one))
                .andExpect(status().isCreated());
        assertThatThrownBy(() -> jdbc.update("UPDATE atenciones_clinicas SET diagnostico = 'alterado' WHERE cita_id = ?",
                APPT_X_ONE_PENDING)).hasMessageContaining("CLINICAL_RECORD_IMMUTABLE");
        assertThatThrownBy(() -> jdbc.update("UPDATE receta_items SET dosis = 'alterada' WHERE atencion_id IN "
                + "(SELECT id FROM atenciones_clinicas WHERE cita_id = ?)", APPT_X_ONE_PENDING))
                .hasMessageContaining("CLINICAL_RECORD_IMMUTABLE");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO atenciones_clinicas (id,cita_id,expediente_id,paciente_id,medico_id,"
                        + "autor_personal_id,motivo_consulta,diagnostico,registrada_en) SELECT '98700000-0000-0000-0000-000000000001', "
                        + "?, expediente_id, ?, medico_id, autor_personal_id, 'motivo', 'diagnostico', now() "
                        + "FROM atenciones_clinicas WHERE cita_id = ?", APPT_Y_ONE_CONFIRMED, PATIENT_X, APPT_X_ONE_PENDING))
                .hasMessageContaining("fk_atencion_cita_paciente_medico");
    }

    @Test
    void citaQueAunNoComienzaSeRechazaSinEfectosYSeAdmiteDesdeSuHoraExacta() throws Exception {
        String one = login(mockMvc, objectMapper, "doctor.one@example.test");
        mockMvc.perform(get("/api/v1/medico/citas/{id}", APPT_X_ONE_FUTURE).header("Authorization", "Bearer " + one))
                .andExpect(jsonPath("$.appointment.canRecordAttention").value(false))
                .andExpect(jsonPath("$.appointment.attentionBlockers", contains("NOT_STARTED")));
        mockMvc.perform(attention(APPT_X_ONE_FUTURE, VALID_ATTENTION).header("Authorization", "Bearer " + one))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("APPOINTMENT_NOT_STARTED"));
        OffsetDateTime start = fixture.base().plusHours(FUTURE_OFFSET_HOURS);
        clock.set(start.minusSeconds(1).toInstant());
        mockMvc.perform(attention(APPT_X_ONE_FUTURE, VALID_ATTENTION).header("Authorization", "Bearer " + one))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("APPOINTMENT_NOT_STARTED"));
        assertNoClinicalSideEffects(APPT_X_ONE_FUTURE, "PENDIENTE");
        assertThat(countRecords(PATIENT_X)).isZero();

        clock.set(start.toInstant());
        mockMvc.perform(attention(APPT_X_ONE_FUTURE, VALID_ATTENTION).header("Authorization", "Bearer " + one))
                .andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("SELECT estado FROM citas WHERE id = ?", String.class, APPT_X_ONE_FUTURE))
                .isEqualTo("COMPLETADA");
    }

    @Test
    void sinLlegadaSeRechazaHastaQueRecepcionLaRegistra() throws Exception {
        String one = login(mockMvc, objectMapper, "doctor.one@example.test");
        mockMvc.perform(get("/api/v1/medico/citas/{id}", APPT_Y_ONE_NO_ARRIVAL).header("Authorization", "Bearer " + one))
                .andExpect(jsonPath("$.appointment.canRecordAttention").value(false))
                .andExpect(jsonPath("$.appointment.attentionBlockers", contains("ARRIVAL_NOT_REGISTERED")));
        mockMvc.perform(attention(APPT_Y_ONE_NO_ARRIVAL, VALID_ATTENTION).header("Authorization", "Bearer " + one))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ARRIVAL_NOT_REGISTERED"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("SQL"))));
        assertNoClinicalSideEffects(APPT_Y_ONE_NO_ARRIVAL, "CONFIRMADA");
        assertThat(countRecords(PATIENT_Y)).isZero();

        String reception = login(mockMvc, objectMapper, "reception.clinical@example.test");
        mockMvc.perform(post("/api/v1/staff/agenda/{id}/arrival", APPT_Y_ONE_NO_ARRIVAL)
                        .header("Authorization", "Bearer " + reception))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/medico/citas/{id}", APPT_Y_ONE_NO_ARRIVAL).header("Authorization", "Bearer " + one))
                .andExpect(jsonPath("$.appointment.canRecordAttention").value(true))
                .andExpect(jsonPath("$.appointment.attentionBlockers", hasSize(0)));
        mockMvc.perform(attention(APPT_Y_ONE_NO_ARRIVAL, VALID_ATTENTION).header("Authorization", "Bearer " + one))
                .andExpect(status().isCreated());
    }

    @Test
    void solicitudesSimultaneasNoEludenHoraNiLlegada() throws Exception {
        String one = login(mockMvc, objectMapper, "doctor.one@example.test");
        for (String appointment : List.of(APPT_Y_ONE_NO_ARRIVAL, APPT_X_ONE_FUTURE)) {
            ExecutorService pool = Executors.newFixedThreadPool(2);
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch go = new CountDownLatch(1);
            Callable<Integer> call = () -> {
                ready.countDown(); go.await();
                return mockMvc.perform(attention(appointment, VALID_ATTENTION).header("Authorization", "Bearer " + one))
                        .andReturn().getResponse().getStatus();
            };
            Future<Integer> first = pool.submit(call);
            Future<Integer> second = pool.submit(call);
            ready.await(); go.countDown();
            List<Integer> statuses = List.of(first.get(), second.get());
            pool.shutdownNow();
            assertThat(statuses).containsExactly(409, 409);
        }
        assertNoClinicalSideEffects(APPT_Y_ONE_NO_ARRIVAL, "CONFIRMADA");
        assertNoClinicalSideEffects(APPT_X_ONE_FUTURE, "PENDIENTE");
    }

    @Test
    void otroMedicoNoEludeLasReglasEnviandoIdentificadores() throws Exception {
        String two = login(mockMvc, objectMapper, "doctor.two@example.test");
        for (String appointment : List.of(APPT_X_ONE_FUTURE, APPT_Y_ONE_NO_ARRIVAL, APPT_X_ONE_PENDING)) {
            mockMvc.perform(attention(appointment, VALID_ATTENTION).header("Authorization", "Bearer " + two))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("APPOINTMENT_NOT_FOUND"));
        }
        String withIds = VALID_ATTENTION.replace("{\"reason\"", "{\"appointmentId\":\"" + APPT_X_ONE_PENDING
                + "\",\"practitionerId\":\"" + PRACTITIONER_ONE + "\",\"patientId\":\"" + PATIENT_X + "\",\"reason\"");
        mockMvc.perform(attention(APPT_X_TWO_PENDING, withIds).header("Authorization", "Bearer " + two))
                .andExpect(status().isBadRequest());
        mockMvc.perform(attention(APPT_X_ONE_PENDING, withIds).header("Authorization", "Bearer " + two))
                .andExpect(status().isBadRequest());
        mockMvc.perform(attention(APPT_X_ONE_PENDING, VALID_ATTENTION).param("practitionerId", PRACTITIONER_ONE)
                        .header("Authorization", "Bearer " + two))
                .andExpect(status().isNotFound());
        for (String appointment : List.of(APPT_X_ONE_FUTURE, APPT_Y_ONE_NO_ARRIVAL, APPT_X_ONE_PENDING, APPT_X_TWO_PENDING)) {
            assertThat(countAttentions(appointment)).isZero();
        }
    }

    @Test
    void perfilClinicoVersionadoDerivaPacienteAutorYFechaYRechazaAccesoCruzado() throws Exception {
        String one=login(mockMvc,objectMapper,"doctor.one@example.test");
        String two=login(mockMvc,objectMapper,"doctor.two@example.test");
        String body="{\"allergies\":\"Alergia sintética declarada\",\"relevantConditions\":\"Condición sintética\",\"currentMedications\":\"Ninguno declarado\",\"dentalHistory\":\"Control previo sintético\"}";
        mockMvc.perform(post("/api/v1/medico/citas/{id}/expediente/perfil",APPT_X_ONE_PENDING).contentType(MediaType.APPLICATION_JSON).content(body).header("Authorization","Bearer "+one))
                .andExpect(status().isCreated()).andExpect(header().string(CACHE_CONTROL,"no-store"))
                .andExpect(jsonPath("$.authorAccountId").value(DOCTOR_ONE)).andExpect(jsonPath("$.recordedAt").isString());
        mockMvc.perform(post("/api/v1/medico/citas/{id}/expediente/perfil",APPT_X_ONE_PENDING).contentType(MediaType.APPLICATION_JSON).content(body.replace("Condición sintética","Segunda versión sintética")).header("Authorization","Bearer "+one))
                .andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM versiones_perfil_clinico WHERE paciente_id=?",Integer.class,PATIENT_X)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM versiones_perfil_clinico WHERE paciente_id=? AND autor_personal_id=?",Integer.class,PATIENT_X,DOCTOR_ONE)).isEqualTo(2);
        mockMvc.perform(get("/api/v1/medico/citas/{id}/expediente",APPT_X_ONE_PENDING).header("Authorization","Bearer "+one))
                .andExpect(jsonPath("$.clinicalProfile.relevantConditions").value("Segunda versión sintética"))
                .andExpect(jsonPath("$.clinicalProfileHistory",hasSize(2)));
        mockMvc.perform(post("/api/v1/medico/citas/{id}/expediente/perfil",APPT_X_ONE_PENDING).contentType(MediaType.APPLICATION_JSON).content(body).header("Authorization","Bearer "+two))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("APPOINTMENT_NOT_FOUND"));
    }

    @Test
    void perfilYAdendasExigenSesionRolYVinculacionYConservanOriginal() throws Exception {
        String one=login(mockMvc,objectMapper,"doctor.one@example.test");
        mockMvc.perform(attention(APPT_X_ONE_PENDING,VALID_ATTENTION).header("Authorization","Bearer "+one)).andExpect(status().isCreated());
        String attentionId=jdbc.queryForObject("SELECT id FROM atenciones_clinicas WHERE cita_id=?",String.class,APPT_X_ONE_PENDING);
        String original=jdbc.queryForObject("SELECT diagnostico FROM atenciones_clinicas WHERE id=?",String.class,attentionId);
        String path="/api/v1/medico/citas/"+APPT_X_ONE_PENDING+"/atenciones/"+attentionId+"/adendas";
        String addendum="{\"text\":\"Aclaración sintética posterior\",\"reason\":\"Precisión clínica sintética\"}";
        mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(addendum).header("Authorization","Bearer "+one))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.authorAccountId").value(DOCTOR_ONE));
        assertThat(jdbc.queryForObject("SELECT diagnostico FROM atenciones_clinicas WHERE id=?",String.class,attentionId)).isEqualTo(original);
        mockMvc.perform(get("/api/v1/medico/citas/{id}/expediente",APPT_X_ONE_PENDING).header("Authorization","Bearer "+one))
                .andExpect(jsonPath("$.attentions[0].addenda[0].text").value("Aclaración sintética posterior"));
        mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(addendum)).andExpect(status().isUnauthorized());
        for(String email:List.of("admin.clinical@example.test","reception.clinical@example.test")){
            String token=login(mockMvc,objectMapper,email);mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(addendum).header("Authorization","Bearer "+token)).andExpect(status().isForbidden());
        }
        String unlinked=login(mockMvc,objectMapper,"doctor.unlinked@example.test");
        mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(addendum).header("Authorization","Bearer "+unlinked)).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PRACTITIONER_LINK_REQUIRED"));
    }

    @Test
    void perfilesAdendasAtencionesYRecetasSonInmutablesEnSql() throws Exception {
        String one=login(mockMvc,objectMapper,"doctor.one@example.test");
        mockMvc.perform(post("/api/v1/medico/citas/{id}/expediente/perfil",APPT_X_ONE_PENDING).contentType(MediaType.APPLICATION_JSON).content("{\"allergies\":\"Dato sintético\"}").header("Authorization","Bearer "+one)).andExpect(status().isCreated());
        mockMvc.perform(attention(APPT_X_ONE_PENDING,VALID_ATTENTION).header("Authorization","Bearer "+one)).andExpect(status().isCreated());
        String attentionId=jdbc.queryForObject("SELECT id FROM atenciones_clinicas WHERE cita_id=?",String.class,APPT_X_ONE_PENDING);
        mockMvc.perform(post("/api/v1/medico/citas/{cita}/atenciones/{attention}/adendas",APPT_X_ONE_PENDING,attentionId).contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"Aclaración sintética\",\"reason\":\"Motivo sintético\"}").header("Authorization","Bearer "+one)).andExpect(status().isCreated());
        for(String sql:List.of("UPDATE versiones_perfil_clinico SET alergias='x'","DELETE FROM versiones_perfil_clinico","UPDATE adendas_atencion SET texto='xxx'","DELETE FROM adendas_atencion","UPDATE atenciones_clinicas SET diagnostico='xxx'","DELETE FROM atenciones_clinicas","UPDATE receta_items SET dosis='x'","DELETE FROM receta_items")){
            assertThatThrownBy(()->jdbc.update(sql)).hasMessageContaining("CLINICAL_RECORD_IMMUTABLE");
        }
    }

    private void assertNoClinicalSideEffects(String appointment, String expectedStatus) {
        assertThat(countAttentions(appointment)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM receta_items r JOIN atenciones_clinicas a ON a.id = r.atencion_id "
                + "WHERE a.cita_id = ?", Integer.class, appointment)).isZero();
        assertThat(jdbc.queryForObject("SELECT estado FROM citas WHERE id = ?", String.class, appointment))
                .isEqualTo(expectedStatus);
    }

    private int countRecords(String patient) {
        return jdbc.queryForObject("SELECT count(*) FROM expedientes_clinicos WHERE paciente_id = ?", Integer.class, patient);
    }

    private List<MockHttpServletRequestBuilder> clinicalRequests() {
        return List.of(get("/api/v1/medico/citas"),
                get("/api/v1/medico/citas/{id}", APPT_X_ONE_PENDING),
                get("/api/v1/medico/citas/{id}/expediente", APPT_X_ONE_PENDING),
                attention(APPT_X_ONE_PENDING, VALID_ATTENTION));
    }

    private MockHttpServletRequestBuilder attention(String appointment, String body) {
        return post("/api/v1/medico/citas/{id}/atencion", appointment).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private int countAttentions(String appointment) {
        return jdbc.queryForObject("SELECT count(*) FROM atenciones_clinicas WHERE cita_id = ?", Integer.class, appointment);
    }
}
