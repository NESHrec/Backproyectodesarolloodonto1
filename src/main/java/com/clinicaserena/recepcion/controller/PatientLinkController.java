package com.clinicaserena.recepcion.controller;
import com.clinicaserena.auth.security.PatientPrincipal;import com.clinicaserena.recepcion.dto.*;import com.clinicaserena.recepcion.service.PatientLinkService;import com.clinicaserena.staff.security.StaffPrincipal;
import jakarta.validation.Valid;import org.springframework.http.*;import org.springframework.security.core.Authentication;import org.springframework.web.bind.annotation.*;
@RestController public class PatientLinkController {private final PatientLinkService service;public PatientLinkController(PatientLinkService service){this.service=service;}
 @PostMapping("/api/v1/staff/patients/{patientId}/link-requests") public ResponseEntity<PatientLinkChallengeResponse> initiate(@PathVariable String patientId,@Valid @RequestBody InitiatePatientLinkRequest body,Authentication auth){return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).body(service.initiate((StaffPrincipal)auth.getPrincipal(),patientId,body));}
 @DeleteMapping("/api/v1/staff/patient-link-requests/{requestId}") public ResponseEntity<Void> revoke(@PathVariable String requestId,Authentication auth){service.revoke((StaffPrincipal)auth.getPrincipal(),requestId);return ResponseEntity.noContent().build();}
 @PostMapping("/api/v1/pacientes/me/administrative-link") public ResponseEntity<Void> confirm(@Valid @RequestBody ConfirmPatientLinkRequest body,Authentication auth){service.confirm((PatientPrincipal)auth.getPrincipal(),body);return ResponseEntity.noContent().build();}
}
