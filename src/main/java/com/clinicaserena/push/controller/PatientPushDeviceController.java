package com.clinicaserena.push.controller;

import com.clinicaserena.auth.security.PatientPrincipal;
import com.clinicaserena.push.dto.RegisterPushDeviceRequest;
import com.clinicaserena.push.dto.RemovePushDeviceRequest;
import com.clinicaserena.push.service.PushDeviceService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Dispositivos push del paciente autenticado (rol PACIENTE vía /api/v1/pacientes/**). */
@RestController
@RequestMapping("/api/v1/pacientes/me/dispositivos-push")
public class PatientPushDeviceController {

    private final PushDeviceService service;

    public PatientPushDeviceController(PushDeviceService service) {
        this.service = service;
    }

    @PutMapping
    public ResponseEntity<Void> register(@Valid @RequestBody RegisterPushDeviceRequest request, Authentication authentication) {
        service.register((PatientPrincipal) authentication.getPrincipal(), request);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    /** Retiro por POST: el token nunca viaja en la URL (quedaría en los logs de acceso). */
    @PostMapping("/retiro")
    public ResponseEntity<Void> remove(@Valid @RequestBody RemovePushDeviceRequest request, Authentication authentication) {
        service.remove((PatientPrincipal) authentication.getPrincipal(), request);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
