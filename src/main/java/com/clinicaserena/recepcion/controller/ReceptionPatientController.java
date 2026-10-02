package com.clinicaserena.recepcion.controller;

import com.clinicaserena.recepcion.dto.AdministrativePatientPageResponse;
import com.clinicaserena.recepcion.dto.AdministrativePatientResponse;
import com.clinicaserena.recepcion.dto.CreateAdministrativePatientRequest;
import com.clinicaserena.recepcion.service.PacienteAdministrativoService;
import com.clinicaserena.staff.security.StaffPrincipal;
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

@RestController
@RequestMapping("/api/v1/staff/patients")
public class ReceptionPatientController {

    private final PacienteAdministrativoService service;

    public ReceptionPatientController(PacienteAdministrativoService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<AdministrativePatientPageResponse> list(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.list(search, page, limit));
    }

    @PostMapping
    public ResponseEntity<AdministrativePatientResponse> create(
            @Valid @RequestBody CreateAdministrativePatientRequest request,
            Authentication authentication) {
        StaffPrincipal principal = (StaffPrincipal) authentication.getPrincipal();
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(service.create(principal, request));
    }
}
