package com.clinicaserena.staff.dto;

import com.clinicaserena.citas.entity.EstadoCita;

import java.time.OffsetDateTime;

public record ReceptionAppointmentResponse(
        String id,
        String patientId,
        String practitionerId,
        String specialtyId,
        OffsetDateTime scheduledAt,
        EstadoCita status,
        OffsetDateTime arrivalAt,
        String arrivalByAccountId
) {
}
