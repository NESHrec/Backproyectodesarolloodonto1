package com.clinicaserena.catalogo.controller;

import com.clinicaserena.catalogo.dto.AvailabilitySlotDto;
import com.clinicaserena.catalogo.dto.PractitionerDto;
import com.clinicaserena.catalogo.dto.SpecialtyDto;
import com.clinicaserena.catalogo.service.CatalogoService;
import com.clinicaserena.common.exception.ApiException;
import com.clinicaserena.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CatalogoController.class)
@Import(SecurityConfig.class)
class CatalogoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CatalogoService catalogoService;

    @Test
    void listaEspecialidadesConContratoPublico() throws Exception {
        when(catalogoService.listarEspecialidades()).thenReturn(List.of(
                new SpecialtyDto("specialty-1", "Ortodoncia", "Corrección dental")
        ));

        mockMvc.perform(get("/api/v1/especialidades"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("specialty-1"))
                .andExpect(jsonPath("$[0].name").value("Ortodoncia"))
                .andExpect(jsonPath("$[0].description").value("Corrección dental"));
    }

    @Test
    void filtraMedicosPorSpecialtyId() throws Exception {
        when(catalogoService.listarMedicos("specialty-1")).thenReturn(List.of(
                new PractitionerDto("doctor-1", "Dra. Elena Morales", "specialty-1", "Ortodoncia", "COL-FICT-1")
        ));

        mockMvc.perform(get("/api/v1/medicos").param("specialtyId", "specialty-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fullName").value("Dra. Elena Morales"))
                .andExpect(jsonPath("$[0].specialtyId").value("specialty-1"))
                .andExpect(jsonPath("$[0].specialtyName").value("Ortodoncia"))
                .andExpect(jsonPath("$[0].licenseNumber").value("COL-FICT-1"));
    }

    @Test
    void listaDisponibilidadConFechasIso8601() throws Exception {
        when(catalogoService.obtenerDisponibilidad(eq("doctor-1"), any(), any())).thenReturn(List.of(
                new AvailabilitySlotDto(
                        "slot-1",
                        "doctor-1",
                        OffsetDateTime.parse("2030-01-10T15:00:00Z"),
                        OffsetDateTime.parse("2030-01-10T15:30:00Z")
                )
        ));

        mockMvc.perform(get("/api/v1/medicos/doctor-1/disponibilidad")
                        .param("desde", "2030-01-01")
                        .param("hasta", "2030-01-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].practitionerId").value("doctor-1"))
                .andExpect(jsonPath("$[0].startAt").value("2030-01-10T15:00:00Z"))
                .andExpect(jsonPath("$[0].endAt").value("2030-01-10T15:30:00Z"));
    }

    @Test
    void medicoInexistenteRetornaErrorEstandar() throws Exception {
        when(catalogoService.obtenerDisponibilidad(eq("missing"), any(), any()))
                .thenThrow(new ApiException(HttpStatus.NOT_FOUND, "PRACTITIONER_NOT_FOUND", "El médico no existe"));

        mockMvc.perform(get("/api/v1/medicos/missing/disponibilidad"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("PRACTITIONER_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("El médico no existe"));
    }

    @Test
    void fechaInvalidaRetornaErrorControlado() throws Exception {
        mockMvc.perform(get("/api/v1/medicos/doctor-1/disponibilidad").param("desde", "no-es-fecha"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    @Test
    void metodosDeEscrituraYRecursosFueraDelCatalogoSiguenProtegidos() throws Exception {
        mockMvc.perform(post("/api/v1/especialidades"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/pacientes/me/citas"))
                .andExpect(status().isUnauthorized());
    }
}
