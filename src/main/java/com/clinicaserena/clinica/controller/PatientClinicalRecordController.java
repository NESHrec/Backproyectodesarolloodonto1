package com.clinicaserena.clinica.controller;
import com.clinicaserena.auth.security.PatientPrincipal;
import com.clinicaserena.clinica.dto.ClinicalRecordResponse;
import com.clinicaserena.clinica.service.MedicalCareService;
import org.springframework.http.CacheControl;import org.springframework.http.ResponseEntity;import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;import org.springframework.web.bind.annotation.RequestMapping;import org.springframework.web.bind.annotation.RestController;
@RestController @RequestMapping("/api/v1/pacientes/me/expediente")
public class PatientClinicalRecordController {
 private final MedicalCareService service; public PatientClinicalRecordController(MedicalCareService service){this.service=service;}
 @GetMapping public ResponseEntity<ClinicalRecordResponse> own(Authentication authentication){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.getOwnPatientRecord((PatientPrincipal)authentication.getPrincipal()));}
}
