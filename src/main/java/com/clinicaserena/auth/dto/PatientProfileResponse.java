package com.clinicaserena.auth.dto;

import java.time.OffsetDateTime;

/** Datos del perfil propio; la identidad se resuelve desde la sesión autenticada. */
public record PatientProfileResponse(
        String patientId,
        String fullName,
        String email,
        String accountStatus,
        OffsetDateTime registeredAt
) {
}
