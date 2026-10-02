package com.clinicaserena.odontograma.controller;

import com.clinicaserena.odontograma.dto.DentalObservationPageResponse;
import com.clinicaserena.odontograma.dto.DentalObservationResponse;
import com.clinicaserena.odontograma.dto.RecordDentalObservationRequest;
import com.clinicaserena.odontograma.service.OdontogramaService;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/medico")
public class OdontogramaController {

    private final OdontogramaService service;

    public OdontogramaController(OdontogramaService service) {
        this.service = service;
    }

    @GetMapping("/pacientes/{patientId}/odontograma")
    public ResponseEntity<DentalObservationPageResponse> list(@PathVariable String patientId,
                                                               Authentication authentication) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.listForPatient(
                (StaffPrincipal) authentication.getPrincipal(), patientId));
    }

    @PostMapping("/citas/{appointmentId}/odontograma")
    public ResponseEntity<DentalObservationResponse> record(@PathVariable String appointmentId,
                                                             @Valid @RequestBody RecordDentalObservationRequest request,
                                                             Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(service.record(
                (StaffPrincipal) authentication.getPrincipal(), appointmentId, request));
    }
}
