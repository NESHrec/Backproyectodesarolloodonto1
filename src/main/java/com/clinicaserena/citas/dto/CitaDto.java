package com.clinicaserena.citas.dto;

import com.clinicaserena.citas.entity.EstadoCita;

import java.time.OffsetDateTime;

public record CitaDto(
        String id,
        String patientId,
        String practitionerId,
        String specialtyId,
        OffsetDateTime scheduledAt,
        EstadoCita status,
        String notes,
        Long amountCents,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
