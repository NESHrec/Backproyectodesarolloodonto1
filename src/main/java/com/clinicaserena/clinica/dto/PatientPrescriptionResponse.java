package com.clinicaserena.clinica.dto;

import java.time.OffsetDateTime;
import java.util.List;

/** Receta persistida perteneciente exclusivamente al paciente autenticado. */
public record PatientPrescriptionResponse(
        String id,
        String appointmentId,
        OffsetDateTime appointmentScheduledAt,
        OffsetDateTime issuedAt,
        String practitionerId,
        String practitionerName,
        List<PrescriptionItem> items
) {

    public record PrescriptionItem(
            String id,
            int order,
            String medicine,
            String dose,
            String frequency,
            String duration,
            String instructions
    ) {
    }
}
