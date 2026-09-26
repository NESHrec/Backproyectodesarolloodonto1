package com.clinicaserena.auth.security;

public record PatientPrincipal(
        String accountId,
        String patientId,
        String email,
        String accountStatus,
        String tokenHash
) {
}
