package com.clinicaserena.recepcion.dto;

import java.time.OffsetDateTime;

public record AdministrativePatientResponse(
        String patientId,
        String fullName,
        String phone,
        String email,
        String recordType,
        boolean patientAccountLinked,
        OffsetDateTime createdAt
) {
}
