package com.clinicaserena.odontograma.dto;

import java.time.OffsetDateTime;

public record DentalObservationResponse(
        String id,
        String patientId,
        String appointmentId,
        String practitionerId,
        String recordedByAccountId,
        int toothNumber,
        String surface,
        String observation,
        OffsetDateTime recordedAt
) {
}
