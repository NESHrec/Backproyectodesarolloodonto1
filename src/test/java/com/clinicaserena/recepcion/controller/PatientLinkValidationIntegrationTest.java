package com.clinicaserena.recepcion.controller;

import com.clinicaserena.auth.security.PatientPrincipal;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PatientLinkValidationIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"requestId\":\"00000000-0000-0000-0000-000000000001\",\"code\":null}",
            "{\"requestId\":\"00000000-0000-0000-0000-000000000001\",\"code\":\"\"}",
            "{\"requestId\":\"00000000-0000-0000-0000-000000000001\",\"code\":\"12AB\"}"
    })
    void codigoNuloVacioOInvalidoResponde4xxSinConfirmarSolicitud(String body) throws Exception {
        Integer confirmedBefore = jdbc.queryForObject("SELECT count(*) FROM solicitudes_vinculacion_paciente WHERE estado='CONFIRMADA'", Integer.class);
        PatientPrincipal principal = new PatientPrincipal("synthetic-account", "synthetic-patient",
                "synthetic@example.test", "ACTIVA", "synthetic-token-hash");
        var auth = new UsernamePasswordAuthenticationToken(principal, null, List.of(() -> "ROLE_PACIENTE"));

        mockMvc.perform(post("/api/v1/pacientes/me/administrative-link")
                        .with(authentication(auth)).contentType("application/json").content(body))
                .andExpect(status().isBadRequest());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM solicitudes_vinculacion_paciente WHERE estado='CONFIRMADA'", Integer.class))
                .isEqualTo(confirmedBefore);
    }
}
