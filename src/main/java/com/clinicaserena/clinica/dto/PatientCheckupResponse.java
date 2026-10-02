package com.clinicaserena.clinica.dto;

import java.time.OffsetDateTime;
import java.util.List;

/** Seguimiento derivado únicamente de una atención clínica persistida. */
public record PatientCheckupResponse(
        String id,
        String appointmentId,
        OffsetDateTime appointmentScheduledAt,
        OffsetDateTime recordedAt,
        String practitionerId,
        String practitionerName,
        String reason,
        String findings,
        String diagnosis,
        String treatmentPlan,
        List<PrescriptionItem> prescription
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
