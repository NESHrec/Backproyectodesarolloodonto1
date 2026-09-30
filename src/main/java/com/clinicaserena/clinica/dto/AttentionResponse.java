package com.clinicaserena.clinica.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record AttentionResponse(
        String id,
        String appointmentId,
        OffsetDateTime appointmentScheduledAt,
        String practitionerId,
        String practitionerName,
        String authorAccountId,
        String authorName,
        String reason,
        String findings,
        String diagnosis,
        String treatmentPlan,
        OffsetDateTime recordedAt,
        List<PrescriptionItemResponse> prescription
) {

    public record PrescriptionItemResponse(int order, String medicine, String dose, String frequency,
                                           String duration, String instructions) {
    }
}
