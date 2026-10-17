package com.clinicaserena.push;

import com.clinicaserena.clinica.ClinicalFixture;
import com.clinicaserena.clinica.MutableClock;
import com.clinicaserena.push.job.AppointmentReminderJob;
import com.clinicaserena.push.sender.PushSender;
import com.clinicaserena.push.service.AppointmentReminderService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Configuración por defecto (sin Firebase): sin proveedor, sin tarea y el registro responde 409. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(MutableClock.Config.class)
// Contexto propio (propiedades push): se cierra al terminar para liberar sus conexiones a PostgreSQL.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PushDisabledIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired ApplicationContext context;
    @Autowired AppointmentReminderService reminders;
    @Autowired MutableClock clock;

    private ClinicalFixture fixture;
    private PushTestData data;

    @BeforeEach
    void prepare() {
        fixture = new ClinicalFixture(jdbc);
        fixture.create(passwordEncoder, true);
        data = new PushTestData(jdbc);
        clock.set(OffsetDateTime.now(ZoneOffset.UTC).toInstant());
    }

    @AfterEach
    void cleanup() {
        data.cleanup();
        fixture.cleanup();
    }

    @Test
    void sinProveedorElRegistroResponde409YNoGuardaNada() throws Exception {
        String bearer = data.patientToken(PushTestData.ACCOUNT_X, OffsetDateTime.now(ZoneOffset.UTC));
        mockMvc.perform(put("/api/v1/pacientes/me/dispositivos-push").header("Authorization", "Bearer " + bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"tokenRealDeDispositivo0123456789\",\"platform\":\"ANDROID\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PUSH_NOT_ENABLED"));
        assertThat(data.count("SELECT count(*) FROM dispositivos_push_paciente")).isZero();
    }

    @Test
    void sinProveedorNoHayEmisorNiTareaYUnaPasadaNoHaceNada() {
        assertThat(context.getBeansOfType(PushSender.class)).isEmpty();
        assertThat(context.getBeansOfType(AppointmentReminderJob.class)).isEmpty();
        data.appointment(ClinicalFixture.PATIENT_X, OffsetDateTime.now(ZoneOffset.UTC).plusHours(23), "PENDIENTE");

        assertThat(reminders.runOnce().claimed()).isZero();
        assertThat(data.count("SELECT count(*) FROM recordatorios_cita")).isZero();
    }
}
