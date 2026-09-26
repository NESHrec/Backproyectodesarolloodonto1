package com.clinicaserena.auth.dto;

public record PatientIdentityResponse(
        String patientId,
        String email,
        String accountStatus
) {
}
