package com.clinicaserena.citas.controller;

import com.clinicaserena.auth.security.PatientPrincipal;
import com.clinicaserena.citas.dto.CitaDto;
import com.clinicaserena.citas.dto.CrearCitaRequest;
import com.clinicaserena.citas.entity.EstadoCita;
import com.clinicaserena.citas.service.CitaService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** HTTP boundary for appointments owned by the authenticated patient only. */
@RestController
@RequestMapping("/api/v1")
public class CitaController {

    private final CitaService citaService;

    public CitaController(CitaService citaService) {
        this.citaService = citaService;
    }

    @PostMapping("/citas")
    public ResponseEntity<CitaDto> crear(
            @Valid @RequestBody CrearCitaRequest request,
            Authentication authentication
    ) {
        PatientPrincipal principal = (PatientPrincipal) authentication.getPrincipal();
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(citaService.reservar(principal.patientId(), request));
    }

    @GetMapping("/pacientes/me/citas")
    public ResponseEntity<List<CitaDto>> listarPropias(
            @RequestParam(required = false) EstadoCita estado,
            Authentication authentication
    ) {
        PatientPrincipal principal = (PatientPrincipal) authentication.getPrincipal();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(citaService.listarDelPaciente(principal.patientId(), estado));
    }
}
