package com.clinicaserena.clinica.dto;

import com.clinicaserena.citas.entity.EstadoCita;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Cita vista por su profesional asignado. {@code attentionBlockers} explica por qué aún
 * no puede documentarse; es informativo y el servidor vuelve a validarlo al guardar.
 */
public record MedicalAppointmentResponse(
        String id,
        String patientId,
        String patientName,
        String practitionerId,
        String specialtyId,
        String specialtyName,
        OffsetDateTime scheduledAt,
        EstadoCita status,
        String notes,
        OffsetDateTime arrivalAt,
        boolean attentionRecorded,
        boolean canRecordAttention,
        List<AttentionBlocker> attentionBlockers
) {

    public enum AttentionBlocker {
        /** Ya existe una atención para la cita. */
        ATTENTION_ALREADY_RECORDED,
        /** La cita está cancelada o completada. */
        STATUS_NOT_DOCUMENTABLE,
        /** La hora programada aún no comienza. */
        NOT_STARTED,
        /** Recepción no ha registrado la llegada del paciente. */
        ARRIVAL_NOT_REGISTERED
    }
}
