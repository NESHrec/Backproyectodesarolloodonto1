package com.clinicaserena.push;

import com.clinicaserena.auth.security.TokenHasher;
import com.clinicaserena.clinica.ClinicalFixture;
import com.clinicaserena.clinica.MutableClock;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Registro y retiro de tokens push: solo PACIENTE, validación, idempotencia, logout y reset. */
@SpringBootTest(properties = "clinica.push.provider=SIMULATED")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(MutableClock.Config.class)
// Contexto propio (propiedades push): se cierra al terminar para liberar sus conexiones a PostgreSQL.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PushDeviceIntegrationTest {

    private static final String PATH = "/api/v1/pacientes/me/dispositivos-push";

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired ObjectMapper objectMapper;
    @Autowired MutableClock clock;

    private ClinicalFixture fixture;
    private PushTestData data;
    private OffsetDateTime now;

    @BeforeEach
    void prepare() {
        fixture = new ClinicalFixture(jdbc);
        fixture.create(passwordEncoder, true);
        data = new PushTestData(jdbc);
        now = OffsetDateTime.now(ZoneOffset.UTC).withNano(0);
        clock.set(now.toInstant());
    }

    @AfterEach
    void cleanup() {
        data.cleanup();
        fixture.cleanup();
    }

    private static String token() {
        return "SIMULADO-prueba-" + UUID.randomUUID();
    }

    private ResultActions register(String bearer, String json) throws Exception {
        var request = put(PATH).contentType(MediaType.APPLICATION_JSON).content(json);
        if (bearer != null) request.header("Authorization", "Bearer " + bearer);
        return mockMvc.perform(request);
    }

    private ResultActions remove(String bearer, String token) throws Exception {
        return mockMvc.perform(post(PATH + "/retiro").header("Authorization", "Bearer " + bearer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"" + token + "\"}"));
    }

    private static String body(String token) {
        return "{\"token\":\"" + token + "\",\"platform\":\"ANDROID\"}";
    }

    private int devices(String token) {
        return data.count("SELECT count(*) FROM dispositivos_push_paciente WHERE token_hash = ?", TokenHasher.sha256(token));
    }

    @Test
    void sinSesion401YConSesionDePersonal403() throws Exception {
        register(null, body(token())).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        String staff = ClinicalFixture.login(mockMvc, objectMapper, "doctor.one@example.test");
        register(staff, body(token())).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(post(PATH + "/retiro").contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"" + token() + "\"}"))
                .andExpect(status().isUnauthorized());
        assertThat(data.count("SELECT count(*) FROM dispositivos_push_paciente")).isZero();
    }

    @Test
    void validaTamanoFormatoPlataformaYCamposExtraSinRepetirElToken() throws Exception {
        String bearer = data.patientToken(PushTestData.ACCOUNT_X, now);
        String bad = "SIMULADO-con espacio-" + UUID.randomUUID();
        register(bearer, body("SIMULADO-corto")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        String response = register(bearer, body(bad)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR")).andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain(bad);
        register(bearer, body("SIMULADO-" + "x".repeat(4100))).andExpect(status().isBadRequest());
        register(bearer, "{\"token\":\"" + token() + "\",\"platform\":\"IOS\"}").andExpect(status().isBadRequest());
        register(bearer, "{\"token\":\"" + token() + "\"}").andExpect(status().isBadRequest());
        register(bearer, "{\"token\":\"" + token() + "\",\"platform\":\"ANDROID\",\"patientId\":\"otro\"}")
                .andExpect(status().isBadRequest());
        assertThat(data.count("SELECT count(*) FROM dispositivos_push_paciente")).isZero();
    }

    @Test
    void enModoSimuladoSoloAceptaTokensDePrueba() throws Exception {
        String bearer = data.patientToken(PushTestData.ACCOUNT_X, now);
        String realLooking = "fcmReal:APA91b" + "A".repeat(140);
        String response = register(bearer, body(realLooking)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PUSH_TOKEN_NOT_SIMULATED")).andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain(realLooking);
        assertThat(devices(realLooking)).isZero();
    }

    @Test
    void registroIdempotenteSinDevolverElTokenYConAuditoriaSinElToken() throws Exception {
        String bearer = data.patientToken(PushTestData.ACCOUNT_X, now);
        String token = token();

        String first = register(bearer, body(token)).andExpect(status().isNoContent())
                .andExpect(header().string("Cache-Control", "no-store")).andReturn().getResponse().getContentAsString();
        register(bearer, body(token)).andExpect(status().isNoContent());

        assertThat(first).isEmpty();
        assertThat(devices(token)).isEqualTo(1);
        assertThat(data.count("SELECT count(*) FROM bitacora_eventos WHERE accion = 'PUSH_DEVICE_REGISTERED' AND actor_id = ?",
                PushTestData.ACCOUNT_X)).isEqualTo(2);
        assertThat(data.count("SELECT count(*) FROM bitacora_eventos WHERE entidad_id = ? OR entidad_id = ?",
                token, TokenHasher.sha256(token))).isZero();
    }

    @Test
    void elMismoTelefonoConOtroPacienteSeReasigna() throws Exception {
        String token = token();
        register(data.patientToken(PushTestData.ACCOUNT_X, now), body(token)).andExpect(status().isNoContent());
        register(data.patientToken(PushTestData.ACCOUNT_Y, now), body(token)).andExpect(status().isNoContent());

        assertThat(devices(token)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT cuenta_paciente_id FROM dispositivos_push_paciente WHERE token_hash = ?",
                String.class, TokenHasher.sha256(token))).isEqualTo(PushTestData.ACCOUNT_Y);
    }

    @Test
    void retiroIdempotenteYUnTokenAjenoNoSeBorra() throws Exception {
        String x = data.patientToken(PushTestData.ACCOUNT_X, now);
        String y = data.patientToken(PushTestData.ACCOUNT_Y, now);
        String tokenX = token();
        register(x, body(tokenX)).andExpect(status().isNoContent());

        remove(y, tokenX).andExpect(status().isNoContent());
        assertThat(devices(tokenX)).as("el retiro de otra cuenta no lo borra").isEqualTo(1);

        remove(x, tokenX).andExpect(status().isNoContent());
        remove(x, tokenX).andExpect(status().isNoContent());
        assertThat(devices(tokenX)).isZero();
        assertThat(data.count("SELECT count(*) FROM bitacora_eventos WHERE accion = 'PUSH_DEVICE_REMOVED'")).isEqualTo(1);
    }

    @Test
    void elLogoutRetiraSoloLosDispositivosDeEsaSesion() throws Exception {
        String sessionOne = data.patientToken(PushTestData.ACCOUNT_X, now);
        String sessionTwo = data.patientToken(PushTestData.ACCOUNT_X, now);
        String phoneOne = token();
        String phoneTwo = token();
        register(sessionOne, body(phoneOne)).andExpect(status().isNoContent());
        register(sessionTwo, body(phoneTwo)).andExpect(status().isNoContent());

        String phoneOneId = jdbc.queryForObject("SELECT id FROM dispositivos_push_paciente WHERE token_hash = ?",
                String.class, TokenHasher.sha256(phoneOne));

        mockMvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + sessionOne))
                .andExpect(status().is2xxSuccessful());

        assertThat(devices(phoneOne)).isZero();
        assertThat(devices(phoneTwo)).isEqualTo(1);
        // Un PUSH_DEVICE_REMOVED por dispositivo retirado, con el id interno y el actor PACIENTE.
        assertThat(data.count("SELECT count(*) FROM bitacora_eventos WHERE accion = 'PUSH_DEVICE_REMOVED' AND entidad_tipo = 'DISPOSITIVO_PUSH' "
                + "AND entidad_id = ? AND actor_tipo = 'PACIENTE' AND actor_id = ?", phoneOneId, PushTestData.ACCOUNT_X)).isEqualTo(1);
        assertThat(data.count("SELECT count(*) FROM bitacora_eventos WHERE accion = 'PUSH_DEVICE_REMOVED'")).isEqualTo(1);
        assertThat(data.count("SELECT count(*) FROM bitacora_eventos WHERE entidad_id IN (?, ?, ?, ?)",
                phoneOne, phoneTwo, TokenHasher.sha256(phoneOne), TokenHasher.sha256(phoneTwo))).isZero();
    }

    @Test
    void unLogoutSinDispositivosNoRegistraRetiros() throws Exception {
        String session = data.patientToken(PushTestData.ACCOUNT_X, now);
        mockMvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + session))
                .andExpect(status().is2xxSuccessful());
        assertThat(data.count("SELECT count(*) FROM bitacora_eventos WHERE accion = 'PUSH_DEVICE_REMOVED'")).isZero();
    }

    @Test
    void restablecerLaContrasenaRetiraTodosLosDispositivosDeLaCuenta() throws Exception {
        String phoneOne = token();
        String phoneTwo = token();
        register(data.patientToken(PushTestData.ACCOUNT_X, now), body(phoneOne)).andExpect(status().isNoContent());
        register(data.patientToken(PushTestData.ACCOUNT_X, now), body(phoneTwo)).andExpect(status().isNoContent());
        String phoneY = token();
        register(data.patientToken(PushTestData.ACCOUNT_Y, now), body(phoneY)).andExpect(status().isNoContent());

        String recovery = "recuperacion-" + UUID.randomUUID();
        OffsetDateTime real = OffsetDateTime.now(ZoneOffset.UTC);
        jdbc.update("INSERT INTO tokens_cuenta_paciente(id,cuenta_id,tipo,token_hash,expira_en,creado_en) VALUES (?,?,'RECUPERACION_PASSWORD',?,?,?)",
                UUID.randomUUID().toString(), PushTestData.ACCOUNT_X, TokenHasher.sha256(recovery), real.plusMinutes(30), real.minusMinutes(1));
        mockMvc.perform(post("/api/v1/auth/password-reset").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + recovery + "\",\"password\":\"NuevaClaveSintetica!2026\"}"))
                .andExpect(status().is2xxSuccessful());

        assertThat(devices(phoneOne) + devices(phoneTwo)).isZero();
        assertThat(devices(phoneY)).as("otra cuenta no se toca").isEqualTo(1);
        // Sin actor PACIENTE autenticado (llega con el token de recuperación): por ahora no se audita.
        // Pendiente de decidir con el equipo; si cambia, esta aserción documenta el comportamiento actual.
        assertThat(data.count("SELECT count(*) FROM bitacora_eventos WHERE accion = 'PUSH_DEVICE_REMOVED'")).isZero();
    }
}
