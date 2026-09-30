package com.clinicaserena.clinica.dto;

import com.clinicaserena.auth.entity.EstadoPaciente;

import java.time.OffsetDateTime;
import java.util.List;

/** Expediente básico del paciente de una cita e historial completo de atenciones. */
public record ClinicalRecordResponse(
        PatientSummary patient,
        String recordId,
        OffsetDateTime recordCreatedAt,
        List<AttentionResponse> attentions
) {

    public record PatientSummary(String id, String fullName, EstadoPaciente status, OffsetDateTime registeredAt) {
    }
}
