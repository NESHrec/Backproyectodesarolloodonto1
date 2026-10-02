package com.clinicaserena.clinica.controller;

import com.clinicaserena.auth.security.PatientPrincipal;
import com.clinicaserena.clinica.dto.PatientCheckupResponse;
import com.clinicaserena.clinica.service.PatientCheckupService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/pacientes/me/chequeos")
public class PatientCheckupController {

    private final PatientCheckupService service;

    public PatientCheckupController(PatientCheckupService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<PatientCheckupResponse>> listOwn(Authentication authentication) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.listOwn((PatientPrincipal) authentication.getPrincipal()));
    }
}
