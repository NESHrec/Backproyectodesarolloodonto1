package com.clinicaserena.auth.controller;

import com.clinicaserena.auth.dto.PatientProfileResponse;
import com.clinicaserena.auth.dto.UpdatePatientProfileRequest;
import com.clinicaserena.auth.security.PatientPrincipal;
import com.clinicaserena.auth.service.PatientProfileService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/pacientes/me/perfil")
public class PatientProfileController {

    private final PatientProfileService service;

    public PatientProfileController(PatientProfileService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<PatientProfileResponse> getOwn(Authentication authentication) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.getOwn((PatientPrincipal) authentication.getPrincipal()));
    }

    @PatchMapping
    public ResponseEntity<PatientProfileResponse> updateOwn(
            @Valid @RequestBody UpdatePatientProfileRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.updateOwn((PatientPrincipal) authentication.getPrincipal(), request));
    }
}
