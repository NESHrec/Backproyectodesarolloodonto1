package com.clinicaserena.clinica.dto;

/** Detalle de una cita propia con la atención documentada, si existe. */
public record MedicalAppointmentDetailResponse(
        MedicalAppointmentResponse appointment,
        AttentionResponse attention
) {
}
