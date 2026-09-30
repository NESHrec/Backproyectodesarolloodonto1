package com.clinicaserena.clinica.controller;

import com.clinicaserena.citas.entity.EstadoCita;
import com.clinicaserena.clinica.dto.AttentionResponse;
import com.clinicaserena.clinica.dto.ClinicalRecordResponse;
import com.clinicaserena.clinica.dto.MedicalAppointmentDetailResponse;
import com.clinicaserena.clinica.dto.MedicalAppointmentResponse;
import com.clinicaserena.clinica.dto.RecordAttentionRequest;
import com.clinicaserena.clinica.service.MedicalCareService;
import com.clinicaserena.staff.security.StaffPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;

/** Datos clínicos del profesional vinculado a la sesión MEDICO; nunca se cachean. */
@RestController
@RequestMapping("/api/v1/medico")
public class MedicalCareController {

    private final MedicalCareService service;

    public MedicalCareController(MedicalCareService service) {
        this.service = service;
    }

    @GetMapping("/citas")
    public ResponseEntity<List<MedicalAppointmentResponse>> listOwnAppointments(
            @RequestParam(required = false) OffsetDateTime from,
            @RequestParam(required = false) OffsetDateTime to,
            @RequestParam(required = false) EstadoCita status,
            @RequestParam(defaultValue = "50") int limit,
            Authentication authentication) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.listOwnAppointments(principal(authentication), from, to, status, limit));
    }

    @GetMapping("/citas/{appointmentId}")
    public ResponseEntity<MedicalAppointmentDetailResponse> getOwnAppointment(@PathVariable String appointmentId,
                                                                              Authentication authentication) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.getOwnAppointment(principal(authentication), appointmentId));
    }

    @GetMapping("/citas/{appointmentId}/expediente")
    public ResponseEntity<ClinicalRecordResponse> getRecord(@PathVariable String appointmentId,
                                                            Authentication authentication) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.getRecordForAppointment(principal(authentication), appointmentId));
    }

    @PostMapping("/citas/{appointmentId}/atencion")
    public ResponseEntity<AttentionResponse> recordAttention(@PathVariable String appointmentId,
                                                             @Valid @RequestBody RecordAttentionRequest request,
                                                             Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(service.recordAttention(principal(authentication), appointmentId, request));
    }

    private static StaffPrincipal principal(Authentication authentication) {
        return (StaffPrincipal) authentication.getPrincipal();
    }
}
