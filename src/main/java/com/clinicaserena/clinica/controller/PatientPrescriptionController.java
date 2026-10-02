package com.clinicaserena.clinica.controller;

import com.clinicaserena.auth.security.PatientPrincipal;
import com.clinicaserena.clinica.dto.PatientPrescriptionResponse;
import com.clinicaserena.clinica.service.PatientPrescriptionService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/pacientes/me/recetas")
public class PatientPrescriptionController {

    private final PatientPrescriptionService service;

    public PatientPrescriptionController(PatientPrescriptionService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<PatientPrescriptionResponse>> listOwn(Authentication authentication) {
        PatientPrincipal principal = (PatientPrincipal) authentication.getPrincipal();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.listOwn(principal));
    }
}
